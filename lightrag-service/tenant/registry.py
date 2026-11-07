"""租户实例注册表。

把"哪个用户对应哪个 LightRAG 实例与上传目录"集中到一处，避免实例散落在启动逻辑里。
注册表只做装配与查找，不承担鉴权；路由归属必须由可信业务服务决定。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterator, Sequence

from tenant.config import TenantConfig


class UnknownTenantError(LookupError):
    """请求的 user_id 没有注册；此时必须失败，绝不能回退到其他租户的实例。"""


@dataclass(frozen=True)
class TenantContext:
    """单个租户的运行期上下文：一个实例、一个文档管理器、一套目录。"""

    user_id: str
    workspace: str
    rag: Any
    doc_manager: Any

    @property
    def input_dir(self) -> Path:
        """返回该租户的上传目录，便于自检与排错时直接展示。"""
        return Path(self.doc_manager.input_dir)


class TenantRegistry:
    """按 user_id 保存租户上下文，并提供顺序访问接口供路由注册使用。"""

    def __init__(self) -> None:
        self._contexts: dict[str, TenantContext] = {}

    def register(self, context: TenantContext) -> None:
        """登记租户上下文；重复的 user_id 直接报错，避免静默覆盖导致串数据。"""
        if context.user_id in self._contexts:
            raise ValueError(f"租户 {context.user_id!r} 已注册，不能重复登记")
        self._contexts[context.user_id] = context

    def get(self, user_id: str) -> TenantContext:
        """按 user_id 取出上下文；未注册时抛出 UnknownTenantError。"""
        context = self._contexts.get(user_id)
        if context is None:
            raise UnknownTenantError(f"未注册的租户：{user_id}")
        return context

    def all(self) -> Iterator[TenantContext]:
        """按注册顺序迭代全部上下文，供启动与关闭阶段统一处理。"""
        return iter(self._contexts.values())

    def __len__(self) -> int:
        """返回已注册的租户数量。"""
        return len(self._contexts)


def assemble_registry(
    tenants: Sequence[TenantConfig],
    rag_builder: Callable[[str], Any],
    doc_manager_builder: Callable[[Path, str], Any],
) -> TenantRegistry:
    """按配置装配全部租户上下文。

    两个构造器都以参数注入，测试可传入替身，从而在不引入数据库与模型的前提下
    验证装配逻辑本身。

    Args:
        tenants: 已通过隔离校验的租户配置。
        rag_builder: 接收 workspace 并返回 LightRAG 实例的构造器。
        doc_manager_builder: 接收基目录与 workspace 并返回 DocumentManager 的构造器。

    Returns:
        登记完毕的注册表。
    """
    registry = TenantRegistry()
    for tenant in tenants:
        registry.register(
            TenantContext(
                user_id=tenant.user_id,
                workspace=tenant.workspace,
                rag=rag_builder(tenant.workspace),
                doc_manager=doc_manager_builder(
                    tenant.input_dir.parent, tenant.workspace
                ),
            )
        )
    return registry
