"""隔离校验单测：重复标识、非法 workspace、目录冲突与全局环境变量。"""

from __future__ import annotations

import pytest

from conftest import make_tenant
from tenant.config import TenantConfig, TenantConfigError
from tenant.settings import ServiceSettings
from tenant.validator import validate_tenants


def test_valid_tenants_pass(
    base_settings: ServiceSettings, two_tenants: list[TenantConfig]
) -> None:
    """两个不同 workspace 的租户通过校验，且环境变量为空。"""
    validate_tenants(two_tenants, {})


def test_duplicate_workspace_rejected_with_name(
    base_settings: ServiceSettings,
) -> None:
    """重复 workspace 被拒绝，错误信息包含重复的具体名称与相关租户。"""
    tenants = [
        make_tenant("user_1", "ws_001", base_settings),
        make_tenant("user_2", "ws_001", base_settings),
    ]

    with pytest.raises(TenantConfigError) as error:
        validate_tenants(tenants, {})

    message = str(error.value)
    assert "workspace 重复" in message
    assert "ws_001" in message
    assert "user_1" in message and "user_2" in message


def test_duplicate_user_id_rejected(base_settings: ServiceSettings) -> None:
    """重复 user_id 被拒绝，避免注册表静默覆盖。"""
    tenants = [
        make_tenant("user_1", "ws_001", base_settings),
        make_tenant("user_1", "ws_002", base_settings),
    ]

    with pytest.raises(TenantConfigError, match="user_id 重复"):
        validate_tenants(tenants, {})


@pytest.mark.parametrize("workspace", ["", "ws/001", "ws\\001", "ws-001", "ws 001"])
def test_invalid_workspace_rejected(
    base_settings: ServiceSettings, workspace: str
) -> None:
    """空值、路径分隔符与白名单外字符都会被拒绝。"""
    tenants = [make_tenant("user_1", workspace, base_settings)]

    with pytest.raises(TenantConfigError):
        validate_tenants(tenants, {})


def test_empty_workspace_message_mentions_global_namespace(
    base_settings: ServiceSettings,
) -> None:
    """空 workspace 的错误信息点明它会退化成全局命名空间。"""
    tenants = [make_tenant("user_1", "", base_settings)]

    with pytest.raises(TenantConfigError, match="全局命名空间"):
        validate_tenants(tenants, {})


def test_path_separator_message_is_specific(base_settings: ServiceSettings) -> None:
    """含路径分隔符时错误信息明确指出分隔符问题。"""
    tenants = [make_tenant("user_1", "ws/001", base_settings)]

    with pytest.raises(TenantConfigError, match="路径分隔符"):
        validate_tenants(tenants, {})


def test_nested_directory_rejected(base_settings: ServiceSettings) -> None:
    """上传目录与工作目录重叠时被拒绝：两个租户会互相覆盖文件。"""
    tenants = [
        TenantConfig(
            user_id="user_1",
            workspace="ws_001",
            input_dir=base_settings.input_base_dir / "ws_001",
            working_dir=base_settings.working_base_dir / "ws_001",
        ),
        TenantConfig(
            user_id="user_2",
            workspace="ws_002",
            input_dir=base_settings.input_base_dir / "ws_001" / "inner",
            working_dir=base_settings.working_base_dir / "ws_002",
        ),
    ]

    with pytest.raises(TenantConfigError, match="目录冲突"):
        validate_tenants(tenants, {})


def test_empty_tenant_list_rejected() -> None:
    """没有租户时拒绝启动，避免服务空转。"""
    with pytest.raises(TenantConfigError, match="租户列表为空"):
        validate_tenants([], {})


@pytest.mark.parametrize(
    "variable", ["POSTGRES_WORKSPACE", "NEO4J_WORKSPACE", "WORKSPACE"]
)
def test_forbidden_env_var_rejected(
    base_settings: ServiceSettings, variable: str
) -> None:
    """会覆盖实例级 workspace 的全局变量被拒绝，信息中包含变量名。"""
    tenants = [make_tenant("user_1", "ws_001", base_settings)]

    with pytest.raises(TenantConfigError) as error:
        validate_tenants(tenants, {variable: "shared_ws"})

    assert variable in str(error.value)


def test_blank_forbidden_env_var_is_ignored(base_settings: ServiceSettings) -> None:
    """变量存在但为空值时不算覆盖，允许通过。"""
    tenants = [make_tenant("user_1", "ws_001", base_settings)]

    validate_tenants(tenants, {"POSTGRES_WORKSPACE": "   "})


def test_all_problems_are_reported_at_once(base_settings: ServiceSettings) -> None:
    """多处配置错误一次性全部列出，避免反复试错。"""
    tenants = [
        make_tenant("user_1", "ws_001", base_settings),
        make_tenant("user_1", "ws_001", base_settings),
    ]

    with pytest.raises(TenantConfigError) as error:
        validate_tenants(tenants, {"NEO4J_WORKSPACE": "shared"})

    message = str(error.value)
    assert "user_id 重复" in message
    assert "workspace 重复" in message
    assert "NEO4J_WORKSPACE" in message
