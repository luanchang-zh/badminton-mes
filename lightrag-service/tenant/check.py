"""租户配置自检 CLI。

用途：在不启动 HTTP 服务、不连接 PostgreSQL / Neo4j、不调用模型的前提下，
确认"配置读得进来、隔离前提成立、每个租户的实例与目录都能各自对上"。

运行方式（在 lightrag-service 目录下）：

    python -m tenant.check
    python -m tenant.check --config config/tenants.yaml

退出码 0 表示自检通过，非 0 表示存在必须修复的配置问题。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Callable, Mapping, Sequence

from tenant.config import TenantConfig, TenantConfigError, load_tenants
from tenant.registry import TenantContext, assemble_registry
from tenant.settings import ServiceSettings
from tenant.validator import validate_tenants

EXIT_OK = 0
EXIT_CONFIG_ERROR = 1
SERVICE_ROOT = Path(__file__).resolve().parents[1]


def _load_dotenv_if_present() -> None:
    """从服务目录加载 .env；已存在的真实环境变量优先，不会被文件覆盖。

    这一步让"复制 .env.example 为 .env"这条说明真正生效；没有 .env 时静默跳过，
    依赖缺失时也不报错，避免为了读配置反而阻塞自检。
    """
    env_path = SERVICE_ROOT / ".env"
    if not env_path.is_file():
        return
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    load_dotenv(dotenv_path=env_path, override=False)


def _format_tenant_line(context: TenantContext, working_dir: Path) -> str:
    """把一个租户的装配结果格式化为单行摘要。"""
    return (
        f"  {context.user_id}  workspace={context.workspace}  "
        f"input={context.input_dir}  working={working_dir}"
    )


def _build_contexts(
    tenants: Sequence[TenantConfig],
    settings: ServiceSettings,
) -> tuple[list[TenantContext], Path]:
    """装配租户上下文；缺少模型配置或依赖时抛出明确异常，由调用方降级处理。"""
    from tenant.factory import build_doc_manager, build_rag

    registry = assemble_registry(
        tenants,
        rag_builder=lambda workspace: build_rag(workspace, settings),
        doc_manager_builder=build_doc_manager,
    )
    return list(registry.all()), settings.working_base_dir


def _report_config_only(
    tenants: Sequence[TenantConfig], speaker: Callable[[str], None]
) -> None:
    """依赖或模型缺失时的降级输出：只展示配置推导结果。"""
    speaker("以下为配置推导结果（未构建真实实例）:")
    for tenant in tenants:
        speaker(
            f"  {tenant.user_id}  workspace={tenant.workspace}  "
            f"input={tenant.input_dir}  working={tenant.working_dir}"
        )


def run_check(
    config_path: Path | str | None = None,
    environ: Mapping[str, str] | None = None,
    speaker: Callable[[str], None] = print,
) -> int:
    """执行自检并返回退出码。

    Args:
        config_path: 租户配置文件路径；缺省使用 ``config/tenants.yaml``。
        environ: 校验用的环境变量快照；缺省读取当前进程环境。
        speaker: 输出函数，测试时可替换为收集器。

    Returns:
        EXIT_OK（0）表示通过；EXIT_CONFIG_ERROR（1）表示配置或隔离校验失败。
    """
    settings = ServiceSettings.from_env(environ)

    try:
        tenants = load_tenants(config_path, settings)
        validate_tenants(tenants, environ)
    except TenantConfigError as error:
        speaker(str(error))
        return EXIT_CONFIG_ERROR

    speaker(f"租户配置自检通过，共 {len(tenants)} 个租户。")

    missing_models = settings.missing_model_settings()
    if missing_models:
        speaker(f"警告：模型配置未齐全（{', '.join(missing_models)}），跳过实例构建。")
        _report_config_only(tenants, speaker)
        return EXIT_OK

    try:
        contexts, working_base_dir = _build_contexts(tenants, settings)
    except ImportError as error:
        speaker(f"警告：LightRAG 依赖未安装（{error}），跳过实例构建。")
        _report_config_only(tenants, speaker)
        return EXIT_OK

    for context in contexts:
        working_dir = working_base_dir / context.workspace
        speaker(_format_tenant_line(context, working_dir))
    return EXIT_OK


def main(argv: Sequence[str] | None = None) -> int:
    """解析命令行参数并执行自检。"""
    # Windows 控制台默认不是 UTF-8，中文提示会变成乱码；这里统一成 UTF-8，
    # 保证命令行输出与日志文件一致可读。
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    _load_dotenv_if_present()

    parser = argparse.ArgumentParser(description="LightRAG 多租户配置自检")
    parser.add_argument(
        "--config",
        default=None,
        help="租户配置文件路径，缺省为 config/tenants.yaml",
    )
    arguments = parser.parse_args(argv)
    return run_check(arguments.config)


if __name__ == "__main__":
    sys.exit(main())
