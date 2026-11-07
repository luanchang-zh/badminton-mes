"""注册表装配单测：用替身验证实例与目录都不共用，且未注册租户会被拒绝。"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

from tenant.config import TenantConfig
from tenant.registry import (
    TenantContext,
    TenantRegistry,
    UnknownTenantError,
    assemble_registry,
)


class FakeRag:
    """替身实例：只记录 workspace，用于断言装配结果。"""

    def __init__(self, workspace: str) -> None:
        self.workspace = workspace


class FakeDocManager:
    """替身文档管理器：按官方规则由 base/workspace 推导上传目录。"""

    def __init__(self, base_dir: Path, workspace: str) -> None:
        self.workspace = workspace
        self.input_dir = Path(base_dir) / workspace


def assemble(tenants: list[TenantConfig]) -> TenantRegistry:
    """用替身构造器装配注册表，全程不加载 LightRAG。"""
    return assemble_registry(
        tenants,
        rag_builder=FakeRag,
        doc_manager_builder=FakeDocManager,
    )


def test_two_tenants_get_isolated_contexts(
    base_settings: Any, two_tenants: list[TenantConfig]
) -> None:
    """两个租户的实例与上传目录都不共用，workspace 与配置一致。"""
    registry = assemble(two_tenants)

    first = registry.get("user_1")
    second = registry.get("user_2")

    assert len(registry) == 2
    assert first.rag is not second.rag
    assert first.workspace == "ws_001"
    assert second.workspace == "ws_002"
    assert first.rag.workspace == "ws_001"
    assert second.rag.workspace == "ws_002"
    assert first.input_dir == base_settings.input_base_dir / "ws_001"
    assert second.input_dir == base_settings.input_base_dir / "ws_002"
    assert first.input_dir != second.input_dir


def test_unknown_tenant_raises_with_user_id(two_tenants: list[TenantConfig]) -> None:
    """未注册的 user_id 抛出 UnknownTenantError，信息中包含该标识。"""
    registry = assemble(two_tenants)

    with pytest.raises(UnknownTenantError) as error:
        registry.get("user_unknown")

    assert "user_unknown" in str(error.value)


def test_register_rejects_duplicate_user_id() -> None:
    """重复登记同一个 user_id 时直接报错，不覆盖原上下文。"""
    registry = TenantRegistry()
    context = TenantContext(
        user_id="user_1",
        workspace="ws_001",
        rag=FakeRag("ws_001"),
        doc_manager=FakeDocManager(Path("inputs"), "ws_001"),
    )
    registry.register(context)

    with pytest.raises(ValueError, match="已注册"):
        registry.register(context)


def test_all_preserves_registration_order(two_tenants: list[TenantConfig]) -> None:
    """迭代顺序与配置顺序一致，便于按固定顺序注册路由与统一收尾。"""
    registry = assemble(two_tenants)

    assert [context.user_id for context in registry.all()] == ["user_1", "user_2"]
