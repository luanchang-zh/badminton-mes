"""路由检查用的占位实例。

``--print-routes`` 的目的是确认「前缀与路由条数」，而不是验证模型。
上游路由工厂在**注册阶段**只持有 ``rag`` 与 ``doc_manager`` 引用，不会调用其方法，
因此用占位对象也能让真实工厂代码完整执行一遍，得到与生产一致的路由表。

占位对象只在打印路由时使用；正常启动必须提供真实模型配置。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class PlaceholderRag:
    """只承载 workspace 的占位实例；任何方法调用都会明确失败。"""

    workspace: str
    extras: dict[str, Any] = field(default_factory=dict)

    def __getattr__(self, name: str) -> Any:
        """拒绝未实现的属性访问，避免占位对象被误当成真实实例使用。"""
        raise AttributeError(
            f"占位实例不支持 {name!r}；该对象仅用于打印路由表，不能处理真实请求"
        )


@dataclass
class PlaceholderDocManager:
    """按官方规则推导上传目录的占位文档管理器。"""

    base_dir: Path
    workspace: str

    @property
    def input_dir(self) -> Path:
        """与官方 DocumentManager 一致：上传目录为 ``base/workspace``。"""
        return Path(self.base_dir) / self.workspace


def build_placeholder_rag(workspace: str) -> PlaceholderRag:
    """构造占位实例；不做任何外部连接。"""
    return PlaceholderRag(workspace=workspace)


def build_placeholder_doc_manager(base_dir: Path, workspace: str) -> PlaceholderDocManager:
    """构造占位文档管理器；不做任何外部连接。"""
    return PlaceholderDocManager(base_dir=Path(base_dir), workspace=workspace)
