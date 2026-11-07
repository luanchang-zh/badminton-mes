"""共享测试夹具：构造临时基目录与租户配置，避免测试互相污染或写入仓库。"""

from __future__ import annotations

from pathlib import Path

import pytest

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
