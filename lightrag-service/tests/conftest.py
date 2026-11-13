"""共享测试夹具：构造临时基目录与租户配置，避免测试互相污染或写入仓库。"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Iterator

import pytest

# P2：官方路由工厂需要 vendored 源码在导入路径上。
# P3: isolation scripts live under scripts/; tests need 'isolation.*' and 'fakes'.
SCRIPTS_DIR = Path(__file__).resolve().parents[1] / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

VENDORED_ROOT = Path(__file__).resolve().parents[1] / "LightRAG"
if str(VENDORED_ROOT) not in sys.path:
    sys.path.insert(0, str(VENDORED_ROOT))

from tenant.config import TenantConfig
from tenant.settings import ServiceSettings


@pytest.fixture
def base_settings(tmp_path: Path) -> ServiceSettings:
    """指向临时目录的配置，确保推导出的目录不会落在仓库里。"""
    return ServiceSettings.from_env(
        {
            "LR_INPUT_DIR": str(tmp_path / "inputs"),
            "LR_WORKING_DIR": str(tmp_path / "rag_storage"),
        }
    )


def make_tenant(
    user_id: str, workspace: str, settings: ServiceSettings
) -> TenantConfig:
    """按运行期配置推导目录，生成与生产代码一致的租户配置。"""
    return TenantConfig(
        user_id=user_id,
        workspace=workspace,
        input_dir=settings.input_base_dir / workspace,
        working_dir=settings.working_base_dir / workspace,
    )


@pytest.fixture
def two_tenants(base_settings: ServiceSettings) -> list[TenantConfig]:
    """默认的两个租户，workspace 分别为 ws_001 与 ws_002。"""
    return [
        make_tenant("user_1", "ws_001", base_settings),
        make_tenant("user_2", "ws_002", base_settings),
    ]

# ============================================================================
# P2 夹具
# ============================================================================

@pytest.fixture
def p2_settings(tmp_path: Path) -> ServiceSettings:
    """P2 测试配置：回环地址、固定内部密钥、有限并发。"""
    return ServiceSettings.from_env(
        {
            "LR_INPUT_DIR": str(tmp_path / "inputs"),
            "LR_WORKING_DIR": str(tmp_path / "rag_storage"),
            "LR_INTERNAL_API_KEY": "test-internal-key",
            "LR_LLM_CONCURRENCY": "3",
            "LR_HOST": "127.0.0.1",
            "LR_PORT": "9621",
        }
    )


@pytest.fixture
def p2_tenants(p2_settings: ServiceSettings) -> list[TenantConfig]:
    """两个演示租户。"""
    return [
        make_tenant("user_1", "ws_001", p2_settings),
        make_tenant("user_2", "ws_002", p2_settings),
    ]


@pytest.fixture
def prepared_upstream() -> Iterator[None]:
    """初始化上游全局配置，使官方路由工厂可被导入。

    上游配置代理首次访问会解析 ``sys.argv``，因此测试里也固定一次，
    避免 pytest 自身的参数被上游 argparse 解析。
    """
    from tenant.server import prepare_upstream_config

    prepare_upstream_config(ServiceSettings.from_env({}))
    yield
