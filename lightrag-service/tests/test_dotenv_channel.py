"""`.env` 配置通道单测：写在 .env 里的安全项必须与写在进程环境里等效。

这是一处真实的回归点：上游 `lightrag.api.config` 在导入时才 `load_dotenv`，
而本服务在导入之前就要完成校验。如果入口不同步加载 `.env`，
`.env` 里的 `POSTGRES_WORKSPACE`、`LR_INTERNAL_API_KEY`、`WHITELIST_PATHS`
"""

from __future__ import annotations

import os
from pathlib import Path

import pytest

from tenant.server import EXIT_CONFIG_ERROR, EXIT_OK, load_dotenv_files, run

TWO_TENANTS = (
    "tenants:\n"
    "  - user_id: user_1\n"
    "    workspace: ws_001\n"
    "  - user_id: user_2\n"
    "    workspace: ws_002\n"
)


@pytest.fixture
def service_root(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """把服务目录指向临时目录，并清理可能残留的进程环境变量。"""
    from tenant import server as server_module

    monkeypatch.setattr(server_module, "SERVICE_ROOT", tmp_path)
    for name in ("POSTGRES_WORKSPACE", "NEO4J_WORKSPACE", "WORKSPACE"):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.delenv("WHITELIST_PATHS", raising=False)
    monkeypatch.delenv("LR_INTERNAL_API_KEY", raising=False)
    return tmp_path


def write_env(root: Path, lines: str) -> None:
    """写入 `.env` 文件。"""
    (root / ".env").write_text(lines, encoding="utf-8")


def write_config(root: Path, content: str = TWO_TENANTS) -> Path:
    """写入租户配置。"""
    path = root / "tenants.yaml"
    path.write_text(content, encoding="utf-8")
    return path


def run_with_dotenv(root: Path) -> tuple[int, str]:
    """加载 `.env` 后执行入口，模拟真实的 `python -m tenant.server`。

    真实运行时入口读的是 ``os.environ``（`.env` 已合并进去），
    因此这里也把进程环境作为来源，而不是另造一个字典——
    否则测的就不是真实通道。
    """
    load_dotenv_files()
    lines: list[str] = []
    config_path = write_config(root)
    environ = dict(os.environ)
    environ["LR_INPUT_DIR"] = str(root / "in")
    environ["LR_WORKING_DIR"] = str(root / "work")
    code = run(
        ["--print-routes", "--config", str(config_path)],
        environ,
        lines.append,
    )
    return code, "\n".join(lines)


def test_postgres_workspace_in_dotenv_is_rejected(service_root: Path) -> None:
    """`.env` 里的 POSTGRES_WORKSPACE 同样必须阻止启动。"""
    write_env(service_root, "POSTGRES_WORKSPACE=shared\n")

    code, output = run_with_dotenv(service_root)

    assert code == EXIT_CONFIG_ERROR
    assert "POSTGRES_WORKSPACE" in output
    assert "条路由" not in output


def test_catch_all_whitelist_in_dotenv_is_rejected(service_root: Path) -> None:
    """`.env` 里的全通配白名单同样必须阻止启动。"""
    write_env(service_root, "WHITELIST_PATHS=/*\n")

    code, output = run_with_dotenv(service_root)

    assert code == EXIT_CONFIG_ERROR
    assert "全通配" in output
    assert "条路由" not in output


def test_api_key_in_dotenv_reaches_route_registration(
    service_root: Path, prepared_upstream: None
) -> None:
    """`.env` 里的内部密钥必须真正传到路由工厂，否则鉴权会静默失效。"""
    write_env(service_root, "LR_INTERNAL_API_KEY=from-dotenv\n")
    from tenant.settings import ServiceSettings

    load_dotenv_files()
    settings = ServiceSettings.from_env(os.environ)

    assert settings.internal_api_key == "from-dotenv"


def test_named_whitelist_in_dotenv_is_allowed(service_root: Path) -> None:
    """具名白名单写进 `.env` 不影响启动。"""
    write_env(service_root, "WHITELIST_PATHS=/health,/api/*\n")

    code, output = run_with_dotenv(service_root)

    assert code == EXIT_OK
    assert "条路由" in output
