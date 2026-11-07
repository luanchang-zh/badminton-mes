"""源码引入方式单测：确认使用的是本仓库内的 v1.5.7 源码，且不带原仓库的 Git 历史。"""

from __future__ import annotations

import re
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parents[1]
VENDORED_ROOT = SERVICE_ROOT / "LightRAG"
EXPECTED_VERSION = "1.5.7"


def test_vendored_package_is_importable_layout() -> None:
    """vendored 目录包含 lightrag 包与安装所需的 pyproject.toml。"""
    assert (VENDORED_ROOT / "lightrag" / "__init__.py").is_file()
    assert (VENDORED_ROOT / "pyproject.toml").is_file()


def test_vendored_version_is_pinned() -> None:
    """版本常量固定为 v1.5.7，避免上游升级悄悄改变二开基线。"""
    version_source = (VENDORED_ROOT / "lightrag" / "_version.py").read_text(
        encoding="utf-8"
    )
    match = re.search(r'__version__\s*=\s*"([^"]+)"', version_source)

    assert match is not None
    assert match.group(1) == EXPECTED_VERSION


def test_vendored_source_has_no_git_history() -> None:
    """删除原仓库的 .git，保证后续改动由本仓库统一管理。"""
    assert not (VENDORED_ROOT / ".git").exists()


def test_document_manager_supports_workspace_isolation() -> None:
    """二开依赖官方 DocumentManager 的 workspace 入参，用于按租户隔离上传目录。"""
    routes_source = (
        VENDORED_ROOT / "lightrag" / "api" / "routers" / "document_routes.py"
    ).read_text(encoding="utf-8")

    assert "class DocumentManager" in routes_source
    assert "workspace: str = \"\"" in routes_source


def test_core_workspace_defaults_to_env_so_explicit_argument_matters() -> None:
    """源码默认 workspace 取自环境变量，因此二开必须显式传参覆盖它。"""
    core_source = (VENDORED_ROOT / "lightrag" / "lightrag.py").read_text(
        encoding="utf-8"
    )

    assert 'os.getenv("WORKSPACE"' in core_source
