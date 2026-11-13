"""自检 CLI 单测：正常输出、各类隔离错误与退出码，全程不连数据库、不调模型。"""

from __future__ import annotations

from pathlib import Path

from tenant.check import EXIT_CONFIG_ERROR, EXIT_OK, run_check


def write_config(path: Path, content: str) -> Path:
    """写入临时 YAML 配置并返回路径。"""
    path.write_text(content, encoding="utf-8")
    return path


def collect(config_path: Path, environ: dict[str, str] | None = None) -> tuple[int, str]:
    """执行自检并收集输出，避免测试污染标准输出。"""
    lines: list[str] = []
    exit_code = run_check(config_path, environ or {}, speaker=lines.append)
    return exit_code, "\n".join(lines)


TWO_TENANTS_CONFIG = (
    "tenants:\n"
    "  - user_id: user_1\n"
    "    workspace: ws_001\n"
    "  - user_id: user_2\n"
    "    workspace: ws_002\n"
)


def test_default_config_passes_with_two_tenants(tmp_path: Path) -> None:
    """默认两租户配置：退出码 0，输出各自 workspace 与互不相同的目录。"""
    exit_code, output = collect(write_config(tmp_path / "t.yaml", TWO_TENANTS_CONFIG))

    assert exit_code == EXIT_OK
    assert "共 2 个租户" in output
    assert "user_1" in output and "ws_001" in output
    assert "user_2" in output and "ws_002" in output
    tenant_lines = [line for line in output.splitlines() if "workspace=" in line]
    assert len(tenant_lines) == 2
    first_input = tenant_lines[0].split("input=")[1].split("  working=")[0]
    second_input = tenant_lines[1].split("input=")[1].split("  working=")[0]
    assert first_input != second_input
    assert first_input.endswith("ws_001")
    assert second_input.endswith("ws_002")


def test_duplicate_workspace_fails_without_results(tmp_path: Path) -> None:
    """重复 workspace：退出码非 0，错误信息含重复名称，且不打印装配结果。"""
    config_path = write_config(
        tmp_path / "t.yaml",
        "tenants:\n"
        "  - user_id: user_1\n"
        "    workspace: ws_001\n"
        "  - user_id: user_2\n"
        "    workspace: ws_001\n",
    )

    exit_code, output = collect(config_path)

    assert exit_code == EXIT_CONFIG_ERROR
    assert "workspace 重复" in output
    assert "ws_001" in output
    assert "自检通过" not in output
    assert "input=" not in output


def test_invalid_workspace_fails(tmp_path: Path) -> None:
    """空 workspace 与含分隔符的 workspace 都被拒绝，退出码非 0。"""
    config_path = write_config(
        tmp_path / "t.yaml",
        "tenants:\n"
        "  - user_id: user_1\n"
        "    workspace: ''\n"
        "  - user_id: user_2\n"
        "    workspace: ws/002\n",
    )

    exit_code, output = collect(config_path)

    assert exit_code == EXIT_CONFIG_ERROR
    assert "全局命名空间" in output
    assert "路径分隔符" in output


def test_forbidden_env_var_fails(tmp_path: Path) -> None:
    """设置 POSTGRES_WORKSPACE 时失败，并在信息中点名该变量。"""
    config_path = write_config(
        tmp_path / "t.yaml",
        "tenants:\n  - user_id: user_1\n    workspace: ws_001\n",
    )

    exit_code, output = collect(config_path, {"POSTGRES_WORKSPACE": "shared"})

    assert exit_code == EXIT_CONFIG_ERROR
    assert "POSTGRES_WORKSPACE" in output


def test_missing_model_settings_keeps_exit_code_zero(tmp_path: Path) -> None:
    """模型未配置时只给警告，自检仍然通过并输出配置推导结果。"""
    config_path = write_config(
        tmp_path / "t.yaml",
        "tenants:\n  - user_id: user_1\n    workspace: ws_001\n",
    )

    exit_code, output = collect(config_path)

    assert exit_code == EXIT_OK
    assert "未齐全" in output
    assert "未构建真实实例" in output


def test_missing_config_file_fails(tmp_path: Path) -> None:
    """配置文件不存在时退出码非 0，并给出具体路径。"""
    exit_code, output = collect(tmp_path / "absent.yaml")

    assert exit_code == EXIT_CONFIG_ERROR
    assert "absent.yaml" in output
