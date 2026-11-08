"""内部密钥鉴权单测：无密钥与错误密钥被拒，正确密钥放行。"""

from __future__ import annotations

from typing import Any

import pytest

from tenant.auth import SecurityConfigError, validate_security_settings

CORRECT_KEY = "test-internal-key"
PROBE_PATH = "/tenant/user_1/documents/status_counts"


@pytest.fixture
def client(p2_settings: Any, p2_tenants: list[Any], prepared_upstream: None) -> Any:
    """构建带真实鉴权依赖的测试客户端。"""
    from fastapi.testclient import TestClient

    from tenant.app import build_app
    from tenant.placeholder import (
        build_placeholder_doc_manager,
        build_placeholder_rag,
    )
    from tenant.registry import assemble_registry

    registry = assemble_registry(
        p2_tenants, build_placeholder_rag, build_placeholder_doc_manager
    )
    app = build_app(list(registry.all()), p2_settings)
    # 只构建不启动：不进入 lifespan，因此不会触碰任何存储。
    return TestClient(app)


def test_missing_key_is_rejected(client: Any) -> None:
    """不带密钥时返回 401 或 403。"""
    response = client.get(PROBE_PATH)

    assert response.status_code in (401, 403)


def test_wrong_key_is_rejected(client: Any) -> None:
    """错误密钥同样被拒绝。"""
    response = client.get(PROBE_PATH, headers={"X-API-Key": "wrong-key"})

    assert response.status_code in (401, 403)


def test_correct_key_is_not_rejected(client: Any) -> None:
    """正确密钥不再被鉴权拦截（具体状态取决于业务逻辑，不应是鉴权失败）。"""
    response = client.get(PROBE_PATH, headers={"X-API-Key": CORRECT_KEY})

    assert response.status_code not in (401, 403)


def test_catch_all_whitelist_is_rejected(p2_settings: Any) -> None:
    """全通配白名单会让租户路由免鉴权，必须拒绝启动。"""
    with pytest.raises(SecurityConfigError, match="全通配"):
        validate_security_settings(p2_settings, [("", True)])


def test_named_whitelist_is_allowed(p2_settings: Any) -> None:
    """只放行 /health 这类具名路径不影响租户鉴权。"""
    validate_security_settings(p2_settings, [("/health", False)])


def test_non_loopback_without_key_is_rejected(p2_settings: Any) -> None:
    """监听非回环地址且没有内部密钥时拒绝启动。"""
    from dataclasses import replace

    exposed = replace(p2_settings, host="0.0.0.0", internal_api_key=None)

    with pytest.raises(SecurityConfigError, match="LR_INTERNAL_API_KEY"):
        validate_security_settings(exposed, [])


def test_non_loopback_with_key_is_allowed(p2_settings: Any) -> None:
    """配置了内部密钥后允许监听非回环地址。"""
    from dataclasses import replace

    exposed = replace(p2_settings, host="0.0.0.0")

    validate_security_settings(exposed, [])
