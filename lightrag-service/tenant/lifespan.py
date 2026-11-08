"""统一生命周期：全部租户实例的启动与关闭。

关键约束来自上游源码的事实：后台任务集合挂在 ``app.state.background_tasks`` 上，
是全应用共享的，而不是按 workspace 拆分。因此关闭时只能整体等待一次，
再逐个释放实例；共享状态（连接池、进程级缓存）最后只清理一次。

启动阶段任何一个实例失败都必须中止启动，并把已初始化的实例按逆序释放，
避免留下半初始化的连接。
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Sequence

LOGGER = logging.getLogger(__name__)

# 关闭时等待后台索引任务的上限：超时后仍需继续释放实例，避免进程卡死。
DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30.0


async def initialize_tenant(context: Any) -> None:
    """初始化单个租户实例的存储。

    优先走 ``initialize_storages()``；它内部会按该实例的 workspace 初始化 pipeline 状态。
    随后执行数据迁移检查，使重启后的状态与存储保持一致。
    """
    await context.rag.initialize_storages()
    migrate = getattr(context.rag, "check_and_migrate_data", None)
    if migrate is not None:
        await migrate()


async def release_tenant(context: Any) -> None:
    """释放单个租户实例的存储连接。"""
    await context.rag.finalize_storages()


async def drain_background_tasks(
    background_tasks: set, timeout: float = DEFAULT_SHUTDOWN_TIMEOUT_SECONDS
) -> None:
    """等待应用级后台任务结束。

    上游的 ``drain_reserved_background_tasks`` 会先取消再 join；这里直接复用，
    超时后记录告警但不阻塞后续释放流程。
    """
    if not background_tasks:
        return

    from lightrag.kg.shared_storage import drain_reserved_background_tasks

    try:
        await asyncio.wait_for(
            drain_reserved_background_tasks(background_tasks), timeout=timeout
        )
    except asyncio.TimeoutError:
        LOGGER.warning(
            "等待后台任务超时（%.0f 秒），继续释放实例；未完成的任务会在进程退出时终止",
            timeout,
        )


def finalize_shared_state() -> None:
    """清理全进程共享的存储状态；整个进程生命周期内只应调用一次。"""
    from lightrag.kg.shared_storage import finalize_share_data

    finalize_share_data()


async def _start_all(contexts: Sequence[Any]) -> list[Any]:
    """按顺序初始化全部租户；中途失败时逆序回滚已初始化的实例。

    Returns:
        已成功初始化的上下文列表。
    """
    started: list[Any] = []
    try:
        for context in contexts:
            await initialize_tenant(context)
            started.append(context)
    except Exception:
        for context in reversed(started):
            try:
                await release_tenant(context)
            except Exception as release_error:  # noqa: BLE001 - 回滚失败不应掩盖原始错误
                LOGGER.warning(
                    "启动回滚时释放租户 %s 失败：%s", context.user_id, release_error
                )
        raise
    return started


async def _stop_all(
    contexts: Sequence[Any],
    background_tasks: set,
    timeout: float = DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
) -> None:
    """先等待后台任务，再逐个释放实例，最后清理共享状态。

    顺序不可调整：后台任务仍可能持有存储连接与 pipeline 名额，
    先释放实例会让它们带着失效句柄退出。
    """
    await drain_background_tasks(background_tasks, timeout=timeout)

    for context in contexts:
        await release_tenant(context)

    finalize_shared_state()


def build_lifespan(
    contexts: Sequence[Any],
    shutdown_timeout: float = DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
):
    """构造应用 lifespan。

    Args:
        contexts: 已装配的租户上下文；顺序即初始化顺序，关闭时按同一顺序释放。
        shutdown_timeout: 等待后台任务的上限秒数。

    Returns:
        可直接传给 ``FastAPI(lifespan=...)`` 的异步上下文管理器工厂。
    """

    @asynccontextmanager
    async def lifespan(app: Any) -> AsyncIterator[None]:
        # 后台任务集合必须由应用创建：上游路由通过 app.state 取用它。
        app.state.background_tasks = set()
        await _start_all(contexts)
        LOGGER.info("多租户服务已就绪，共 %d 个租户", len(contexts))
        try:
            yield
        finally:
            await _stop_all(contexts, app.state.background_tasks, shutdown_timeout)

    return lifespan
