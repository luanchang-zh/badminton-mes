"""租户配置解析单测：目录推导、字段缺失与非法结构。"""

from __future__ import annotations

from pathlib import Path

import pytest

from tenant.config import TenantConfigError, load_tenants
from tenant.settings import ServiceSettings


def write_config(path: Path, content: str) -> Path:
    """把 YAML 文本写入临时文件并返回路径。"""
    path.write_text(content, encoding="utf-8")
    return path


def test_load_two_tenants_derives_distinct_dirs(
    tmp_path: Path, base_settings: ServiceSettings
) -> None:
    """两个租户各自推导出独立目录，目录规则为 <base>/<workspace>。"""
    config_path = write_config(
        tmp_path / "tenants.yaml",
        "tenants:\n"
        "  - user_id: user_1\n"
        "    workspace: ws_001\n"
        "  - user_id: user_2\n"
        "    workspace: ws_002\n",
    )

    tenants = load_tenants(config_path, base_settings)

    assert [tenant.user_id for tenant in tenants] == ["user_1", "user_2"]
    assert [tenant.workspace for tenant in tenants] == ["ws_001", "ws_002"]
    assert tenants[0].input_dir == base_settings.input_base_dir / "ws_001"
    assert tenants[0].working_dir == base_settings.working_base_dir / "ws_001"
    assert tenants[0].input_dir != tenants[1].input_dir
    assert tenants[0].working_dir != tenants[1].working_dir


def test_missing_file_raises_with_path(tmp_path: Path) -> None:
    """配置文件不存在时错误信息包含具体路径，便于直接定位。"""
    missing_path = tmp_path / "absent.yaml"

    with pytest.raises(TenantConfigError, match="不存在"):
        load_tenants(missing_path)


def test_invalid_yaml_raises(tmp_path: Path) -> None:
    """YAML 语法错误转成配置错误，不向上抛原始解析异常。"""
    config_path = write_config(tmp_path / "bad.yaml", "tenants: [unclosed\n")

    with pytest.raises(TenantConfigError, match="解析失败"):
        load_tenants(config_path)


def test_missing_tenants_key_raises(tmp_path: Path) -> None:
    """顶层缺少 tenants 列表时给出明确原因。"""
    config_path = write_config(tmp_path / "empty.yaml", "other: 1\n")

    with pytest.raises(TenantConfigError, match="tenants"):
        load_tenants(config_path)


def test_non_string_workspace_raises(tmp_path: Path) -> None:
    """workspace 缺失或不是字符串时报错，并指出是第几个租户。"""
    config_path = write_config(
        tmp_path / "broken.yaml", "tenants:\n  - user_id: user_1\n"
    )

    with pytest.raises(TenantConfigError, match="workspace"):
        load_tenants(config_path)


def test_blank_workspace_is_deferred_to_validator(tmp_path: Path) -> None:
    """空 workspace 在解析阶段放行，由校验器给出"全局命名空间"的语义解释。"""
    config_path = write_config(
        tmp_path / "blank.yaml",
        "tenants:\n  - user_id: user_1\n    workspace: ''\n",
    )

    tenants = load_tenants(config_path)

    assert tenants[0].workspace == ""


def test_blank_user_id_raises(tmp_path: Path) -> None:
    """user_id 为空白字符串视为缺失，不能当成合法租户。"""
    config_path = write_config(
        tmp_path / "broken.yaml",
        "tenants:\n  - user_id: '   '\n    workspace: ws_001\n",
    )

    with pytest.raises(TenantConfigError, match="user_id"):
        load_tenants(config_path)


def test_non_mapping_entry_raises(tmp_path: Path) -> None:
    """租户条目不是映射时报错，避免把列表或标量当成配置。"""
    config_path = write_config(tmp_path / "broken.yaml", "tenants:\n  - user_1\n")

    with pytest.raises(TenantConfigError, match="必须是映射"):
        load_tenants(config_path)


def test_empty_tenant_list_is_allowed_at_parse_time(tmp_path: Path) -> None:
    """解析阶段允许空列表；是否可装配由校验器决定，职责保持单一。"""
    config_path = write_config(tmp_path / "empty.yaml", "tenants: []\n")

    assert load_tenants(config_path) == []
