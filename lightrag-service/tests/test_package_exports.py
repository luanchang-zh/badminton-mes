"""包导出单测：确认调用方从 tenant 顶层导入能得到与内部模块相同的对象。"""

from __future__ import annotations

import tenant
import tenant.config
import tenant.registry
import tenant.settings
import tenant.validator


def test_public_symbols_are_reexported() -> None:
    """P1 约定的公开类型必须始终存在于 __all__ 中，且确实可访问。

    后续阶段会继续往包里加符号，因此这里只断言「至少包含」，
    避免每加一个出口就重伤这份契约测试。
    """
    required = {
        "DEFAULT_TENANTS_FILE",
        "ServiceSettings",
        "TenantConfig",
        "TenantConfigError",
        "TenantContext",
        "TenantRegistry",
        "UnknownTenantError",
        "assemble_registry",
        "build_doc_manager",
        "build_embedding_func",
        "build_llm_func",
        "build_rag",
        "load_tenants",
        "validate_tenants",
    }

    assert required <= set(tenant.__all__)
    for name in required:
        assert hasattr(tenant, name), f"tenant 缺少公开符号 {name}"


def test_exception_class_is_the_same_object() -> None:
    """顶层异常类必须是内部模块的同一个对象，否则 except 会漏掉实际抛出的异常。"""
    assert tenant.UnknownTenantError is tenant.registry.UnknownTenantError
    assert tenant.TenantConfigError is tenant.config.TenantConfigError


def test_registry_exception_is_catchable_from_package_root() -> None:
    """从顶层捕获内部抛出的异常实际可行，这是 P2 依赖该导出的原因。"""
    registry = tenant.TenantRegistry()
    caught = False

    try:
        registry.get("user_missing")
    except tenant.UnknownTenantError as error:
        caught = True
        assert "user_missing" in str(error)

    assert caught


def test_reexported_types_are_identical() -> None:
    """其余公开类型同样保持同一对象，避免出现两套类型判断。"""
    assert tenant.TenantConfig is tenant.config.TenantConfig
    assert tenant.TenantContext is tenant.registry.TenantContext
    assert tenant.ServiceSettings is tenant.settings.ServiceSettings
    assert tenant.validate_tenants is tenant.validator.validate_tenants
