"""LightRAG 多租户二开的公共入口。

对外只暴露这一份符号：调用方（如 P2 的服务入口）从 ``tenant`` 导入即可，
不要直接依赖内部模块路径。重导出采用同一对象，因此
``tenant.UnknownTenantError is tenant.registry.UnknownTenantError`` 成立，
顶层捕获异常不会漏掉内部抛出的实例。
"""

from tenant.config import (
    DEFAULT_TENANTS_FILE,
    TenantConfig,
    TenantConfigError,
    load_tenants,
)
from tenant.app import (
    ROUTES_PER_TENANT,
    build_app,
    collect_tenant_routes,
    register_tenant_routes,
)
from tenant.auth import SecurityConfigError, validate_security_settings
from tenant.factory import (
    build_doc_manager,
    build_embedding_func,
    build_llm_func,
    build_rag,
    get_shared_limiter,
)
from tenant.lifespan import DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, build_lifespan
from tenant.llm_limiter import ModelConcurrencyLimiter
from tenant.registry import (
    TenantContext,
    TenantRegistry,
    UnknownTenantError,
    assemble_registry,
)
from tenant.settings import ServiceSettings
from tenant.validator import validate_tenants

__all__ = [
    "DEFAULT_TENANTS_FILE",
    "DEFAULT_SHUTDOWN_TIMEOUT_SECONDS",
    "ModelConcurrencyLimiter",
    "ROUTES_PER_TENANT",
    "SecurityConfigError",
    "ServiceSettings",
    "TenantConfig",
    "TenantConfigError",
    "TenantContext",
    "TenantRegistry",
    "UnknownTenantError",
    "assemble_registry",
    "build_app",
    "build_doc_manager",
    "build_embedding_func",
    "build_lifespan",
    "build_llm_func",
    "build_rag",
    "collect_tenant_routes",
    "get_shared_limiter",
    "load_tenants",
    "register_tenant_routes",
    "validate_security_settings",
    "validate_tenants",
]
