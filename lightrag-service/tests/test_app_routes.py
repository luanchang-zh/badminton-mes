"""路由注册单测：每个租户各有 33 条带前缀路由，且不存在未加前缀的入口。"""

from __future__ import annotations

from typing import Any

import pytest

from tenant.app import (
    ROUTES_PER_TENANT,
    build_app,
    collect_tenant_routes,
    find_unprefixed_routes,
)


@pytest.fixture
def built_app(
    p2_settings: Any, p2_tenants: list[Any], prepared_upstream: None
) -> Any:
    """用占位实例构建真实路由树；不连数据库、不调模型。"""
    from tenant.placeholder import (
        build_placeholder_doc_manager,
        build_placeholder_rag,
    )
    from tenant.registry import assemble_registry

    registry = assemble_registry(
        p2_tenants, build_placeholder_rag, build_placeholder_doc_manager
    )
    return build_app(list(registry.all()), p2_settings)


def test_each_tenant_has_full_route_set(built_app: Any) -> None:
    """两个租户各有 33 条路由，数量一致。"""
    grouped = collect_tenant_routes(built_app)

    assert set(grouped) == {"user_1", "user_2"}
    assert len(grouped["user_1"]) == ROUTES_PER_TENANT
    assert len(grouped["user_2"]) == ROUTES_PER_TENANT


def test_routes_are_prefixed_per_tenant(built_app: Any) -> None:
    """每条路由都以所属租户的前缀开头。"""
    grouped = collect_tenant_routes(built_app)

    for user_id, routes in grouped.items():
        for route in routes:
            assert route["path"].startswith(f"/tenant/{user_id}/")


def test_no_unprefixed_business_routes(built_app: Any) -> None:
    """不存在未加租户前缀的文档、查询与图谱入口。"""
    assert find_unprefixed_routes(built_app) == []


def test_sample_routes_exist_under_prefix(built_app: Any) -> None:
    """抽查三个关键入口都出现在租户前缀下。"""
    grouped = collect_tenant_routes(built_app)
    paths = {route["path"] for route in grouped["user_1"]}

    assert "/tenant/user_1/documents/upload" in paths
    assert "/tenant/user_1/query" in paths
    assert "/tenant/user_1/graphs" in paths


def test_health_route_is_outside_tenant_prefix(built_app: Any) -> None:
    """健康检查不参与租户隔离，且不被误算进租户路由。"""
    grouped = collect_tenant_routes(built_app)

    assert "/health" not in {
        route["path"] for routes in grouped.values() for route in routes
    }
