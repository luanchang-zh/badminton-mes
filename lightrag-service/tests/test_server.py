"""启动入口单测：打印路由、非法配置拒绝、安全配置拒绝。"""

from __future__ import annotations

from pathlib import Path

from tenant.server import EXIT_CONFIG_ERROR, EXIT_OK, run

TWO_TENANTS = (
    "tenants:\n"
    "  - user_id: user_1\n"
    "    workspace: ws_001\n"
    "  - user_id: user_2\n"
    "    workspace: ws_002\n"
)


def write_config(tmp_path: Path, content: str) -> Path:
    """写入临时租户配置并返回路径。"""
    path = tmp_path / "tenants.yaml"
    path.write_text(content, encoding="utf-8")
    return path


def run_cli(config_path: Path, environ: dict[str, str]) -> tuple[int, str]:
    """执行入口并收集输出，避免污染标准输出。"""
    lines: list[str] = []
    code = run(["--print-routes", "--config", str(config_path)], environ, lines.append)
    return code, "\n".join(lines)


def test_print_routes_lists_two_tenants(tmp_path: Path) -> None:
    """打印路由时输出两个租户，且每条路由都带各自前缀。"""
    config_path = write_config(tmp_path, TWO_TENANTS)

    code, output = run_cli(
        config_path, {"LR_INPUT_DIR": str(tmp_path / "in"), "LR_WORKING_DIR": str(tmp_path / "work")}
    )

    assert code == EXIT_OK
    assert "[user_1] 33 条路由" in output
    assert "[user_2] 33 条路由" in output
    assert "/tenant/user_1/documents/upload" in output
    assert "/tenant/user_2/documents/upload" in output


def test_duplicate_workspace_refuses_to_start(tmp_path: Path) -> None:
    """重复 workspace 时非 0 退出且不打印任何路由。"""
    config_path = write_config(
        tmp_path,
        "tenants:\n"
        "  - user_id: user_1\n"
        "    workspace: ws_001\n"
        "  - user_id: user_2\n"
        "    workspace: ws_001\n",
    )

    code, output = run_cli(
        config_path, {"LR_INPUT_DIR": str(tmp_path / "in"), "LR_WORKING_DIR": str(tmp_path / "work")}
    )

    assert code == EXIT_CONFIG_ERROR
    assert "workspace 重复" in output
    assert "条路由" not in output


def test_forbidden_env_var_refuses_to_start(tmp_path: Path) -> None:
    """设置 POSTGRES_WORKSPACE 时拒绝启动，且不打印路由。"""
    config_path = write_config(tmp_path, TWO_TENANTS)

    code, output = run_cli(
        config_path,
        {
            "LR_INPUT_DIR": str(tmp_path / "in"),
            "LR_WORKING_DIR": str(tmp_path / "work"),
            "POSTGRES_WORKSPACE": "shared",
        },
    )

    assert code == EXIT_CONFIG_ERROR
    assert "POSTGRES_WORKSPACE" in output
    assert "条路由" not in output


def test_catch_all_whitelist_refuses_to_start(tmp_path: Path) -> None:
    """全通配白名单会让租户路由免鉴权，必须以非 0 退出。"""
    config_path = write_config(tmp_path, TWO_TENANTS)

    code, output = run_cli(
        config_path,
        {
            "LR_INPUT_DIR": str(tmp_path / "in"),
            "LR_WORKING_DIR": str(tmp_path / "work"),
            "WHITELIST_PATHS": "/*",
        },
    )

    assert code == EXIT_CONFIG_ERROR
    assert "全通配" in output


def test_non_loopback_without_key_refuses_to_start(tmp_path: Path) -> None:
    """监听非回环地址且没有内部密钥时拒绝启动。"""
    config_path = write_config(tmp_path, TWO_TENANTS)

    code, output = run_cli(
        config_path,
        {
            "LR_INPUT_DIR": str(tmp_path / "in"),
            "LR_WORKING_DIR": str(tmp_path / "work"),
            "LR_HOST": "0.0.0.0",
        },
    )

    assert code == EXIT_CONFIG_ERROR
    assert "LR_INTERNAL_API_KEY" in output


def test_print_routes_does_not_require_model_settings(tmp_path: Path) -> None:
    """打印路由不要求模型配置：模型缺省时依然能以 0 退出。"""
    config_path = write_config(tmp_path, TWO_TENANTS)

    code, output = run_cli(
        config_path,
        {
            "LR_INPUT_DIR": str(tmp_path / "in"),
            "LR_WORKING_DIR": str(tmp_path / "work"),
            "LR_LLM_MODEL": "",
            "LR_EMBEDDING_MODEL": "",
        },
    )

    assert code == EXIT_OK
    assert "条路由" in output
