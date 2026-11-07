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
from tenant.factory import build_doc_manager, build_embedding_func, build_llm_func, build_rag
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
]
