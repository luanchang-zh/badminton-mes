"""多租户服务启动入口。

上游 `lightrag.api.config` 的 ``global_args`` 是「首次访问即初始化」的代理：
导入任意 `lightrag.api.*` 模块都会触发 argparse 解析进程命令行。因此本入口
必须先把自己的参数解析完、把上游需要的配置一次性写好，再导入上游模块，
否则会与上游的 argparse 抢参数、甚至直接退出。

``--print-routes`` 用于本机验收：只构建应用并打印真实路由表，
不启动 uvicorn、不执行 lifespan、不连接数据库、不调用模型。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Mapping, Sequence

from tenant.auth import SecurityConfigError, validate_security_settings
from tenant.config import TenantConfigError, load_tenants
from tenant.settings import ServiceSettings
from tenant.validator import validate_tenants

EXIT_OK = 0
EXIT_CONFIG_ERROR = 1

# 上游 argparse 需要的参数；这里给出受控默认值，避免它解析到我们的参数。
UPSTREAM_ARGV = ["lightrag-multitenant-server"]

# 服务目录：`.env` 等本地文件以它为基准。
SERVICE_ROOT = Path(__file__).resolve().parents[1]

# vendored 源码目录：以 ``python -m tenant.server`` 直接运行时，它需要显式加入导入路径。
VENDORED_ROOT = SERVICE_ROOT / "LightRAG"


def ensure_vendored_importable() -> None:
    """把 vendored 源码加入导入路径，使官方模块可被导入。

    以可编辑方式安装后这步是冗余的，但直接运行源码时必需；
    重复加入同一路径不会产生副作用。
    """
    candidate = str(VENDORED_ROOT)
    if VENDORED_ROOT.is_dir() and candidate not in sys.path:
        sys.path.insert(0, candidate)


def load_dotenv_files() -> None:
    """在读取任何配置之前加载 `.env`。

    上游 `lightrag.api.config` 在导入时也会 `load_dotenv(".env")`，但那时本服务
    早已读完配置并做过校验。若不同步这一份，就会出现「`.env` 里写了
    `POSTGRES_WORKSPACE` / `LR_INTERNAL_API_KEY` / `WHITELIST_PATHS`，校验却看不到」
    的漏洞——校验判通过、运行时却按 `.env` 生效。

    这里主动先加载一次，保证校验与运行时读到同一份配置快照。
    已存在的真实环境变量优先，不会被文件覆盖。
    """
    env_path = SERVICE_ROOT / ".env"
    if not env_path.is_file():
        return
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    load_dotenv(dotenv_path=env_path, override=False)


def _build_parser() -> argparse.ArgumentParser:
    """构造本服务自己的命令行解析器。"""
    parser = argparse.ArgumentParser(
        prog="tenant.server", description="LightRAG 多租户服务入口"
    )
    parser.add_argument("--config", default=None, help="租户配置文件路径")
    parser.add_argument("--host", default=None, help="监听地址，缺省取 LR_HOST")
    parser.add_argument("--port", type=int, default=None, help="监听端口，缺省取 LR_PORT")
    parser.add_argument(
        "--print-routes",
        action="store_true",
        help="只打印租户路由表，不启动服务、不连数据库、不调模型",
    )
    parser.add_argument(
        "--docs",
        dest="enable_docs",
        action="store_true",
        default=None,
        help="开放 /docs 与 /openapi.json",
    )
    return parser


def _resolved_settings(
    arguments: argparse.Namespace, environ: Mapping[str, str] | None
) -> ServiceSettings:
    """把命令行覆盖项合入运行期配置。

    未显式传入 ``environ`` 时读取进程环境；传入字典时（主要供单测）以该字典为准，
    调用方负责在其中包含所有会参与校验的键。
    """
    settings = ServiceSettings.from_env(environ)
    overrides = {
        "host": arguments.host,
        "port": arguments.port,
        "enable_api_docs": arguments.enable_docs,
    }
    applied = {
        name: value for name, value in overrides.items() if value is not None
    }
    if not applied:
        return settings

    from dataclasses import replace

    return replace(settings, **applied)


def prepare_upstream_config(settings: ServiceSettings) -> None:
    """在导入任何上游 API 模块之前固定上游配置。

    上游的全局配置代理会在首次访问时解析 ``sys.argv``，因此这里先替换成受控参数，
    再显式初始化，使后续导入拿到的是我们准备好的配置。
    """
    original_argv = sys.argv
    sys.argv = list(UPSTREAM_ARGV)
    try:
        from lightrag.api.config import initialize_config

        args = initialize_config()
        args.working_dir = str(settings.working_base_dir)
        args.input_dir = str(settings.input_base_dir)
        args.kv_storage = settings.kv_storage
        args.vector_storage = settings.vector_storage
        args.graph_storage = settings.graph_storage
        args.doc_status_storage = settings.doc_status_storage
        args.top_k = settings.top_k
        args.max_pending_documents = settings.max_pending_documents
    finally:
        sys.argv = original_argv


def load_and_validate(
    config_path: Path | str | None,
    settings: ServiceSettings,
    environ: Mapping[str, str] | None = None,
) -> list[Any]:
    """读取并校验租户配置；任何问题都以异常形式抛出。"""
    tenants = load_tenants(config_path, settings)
    validate_tenants(tenants, environ)
    return tenants


def build_application(
    tenants: Sequence[Any],
    settings: ServiceSettings,
    rag_builder: Any = None,
    doc_manager_builder: Any = None,
    require_real_instances: bool = True,
) -> Any:
    """装配租户上下文并构建应用。

    构造器可注入，便于单测在不引入真实存储与模型的情况下验证路由注册。

    Args:
        tenants: 已校验的租户配置。
        settings: 运行期配置。
        rag_builder: 自定义实例构造器；为空时按配置构造真实实例。
        doc_manager_builder: 自定义文档管理器构造器。
        require_real_instances: 为 ``False`` 时使用占位实例，仅用于打印路由表；
            此时不要求模型配置，也不会建立任何外部连接。
    """
    from tenant.app import build_app
    from tenant.registry import assemble_registry

    if rag_builder is None or doc_manager_builder is None:
        if require_real_instances:
            from tenant.factory import build_doc_manager, build_rag

            rag_builder = rag_builder or (
                lambda workspace: build_rag(workspace, settings)
            )
            doc_manager_builder = doc_manager_builder or build_doc_manager
        else:
            from tenant.placeholder import (
                build_placeholder_doc_manager,
                build_placeholder_rag,
            )

            rag_builder = rag_builder or build_placeholder_rag
            doc_manager_builder = (
                doc_manager_builder or build_placeholder_doc_manager
            )

    registry = assemble_registry(tenants, rag_builder, doc_manager_builder)
    return build_app(list(registry.all()), settings)


def format_route_table(app: Any, host: str, port: int) -> list[str]:
    """把租户路由表格式化为可打印的行。"""
    from tenant.app import collect_tenant_routes

    grouped = collect_tenant_routes(app)
    lines: list[str] = []
    for user_id, routes in grouped.items():
        lines.append(f"[{user_id}] {len(routes)} 条路由")
        for route in routes:
            methods = ",".join(route["methods"]) or "-"
            lines.append(f"  {methods:12s} http://{host}:{port}{route['path']}")
    return lines


def run(
    argv: Sequence[str] | None = None,
    environ: Mapping[str, str] | None = None,
    speaker: Any = print,
) -> int:
    """执行入口逻辑并返回退出码。

    Args:
        argv: 命令行参数；缺省解析进程命令行。
        environ: 环境变量来源；缺省读取 ``os.environ``（此时 ``.env`` 已由 main 加载）。
        speaker: 输出函数，测试可替换。

    Returns:
        ``EXIT_OK`` 表示成功；配置或安全校验失败返回 ``EXIT_CONFIG_ERROR``。
    """
    arguments = _build_parser().parse_args(argv)
    settings = _resolved_settings(arguments, environ)

    try:
        tenants = load_and_validate(arguments.config, settings, environ)
        # 校验必须看到与运行时同一份配置，因此这里同样使用 environ 而非进程环境。
        prepare_upstream_config(settings)
        # 直接按本次配置解析白名单：上游模块全局变量在导入时就已固化，
        # 读它反而会漏掉本次启动真正生效的取值。
        validate_security_settings(settings)
    except (TenantConfigError, SecurityConfigError, ValueError) as error:
        speaker(str(error))
        return EXIT_CONFIG_ERROR

    if arguments.print_routes:
        # 打印路由只验证前缀与条数，用占位实例即可；避免要求模型配置。
        app = build_application(tenants, settings, require_real_instances=False)
        for line in format_route_table(app, settings.host, settings.port):
            speaker(line)
        return EXIT_OK

    app = build_application(tenants, settings)
    import uvicorn

    uvicorn.run(app, host=settings.host, port=settings.port, log_level="info")
    return EXIT_OK


def main(argv: Sequence[str] | None = None) -> int:
    """命令行入口。"""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ensure_vendored_importable()
    # 必须在读取配置之前加载：否则 `.env` 里的安全相关项会绕过启动校验。
    load_dotenv_files()
    return run(argv)


if __name__ == "__main__":
    sys.exit(main())
