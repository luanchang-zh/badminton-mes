"""全局模型并发上限。

多个租户共享同一份模型 API 配额。限流如果加在上传接口上，异步索引会在请求返回后
继续调用模型，配额照样被打满；因此这里在模型调用层限流：所有租户共用同一个
``asyncio.Semaphore``，真正发起的上游调用数不超过配置上限。

未配置上限时退化为直通包装，不引入任何额外等待，行为与直接用原始函数一致。
"""

from __future__ import annotations

import asyncio
from typing import Any, Awaitable, Callable


class ModelConcurrencyLimiter:
    """在所有租户间共享一个并发上限的模型调用包装器。

    Args:
        limit: 允许同时执行的模型调用数；``None`` 或 ``0`` 表示不限制。
    """

    def __init__(self, limit: int | None = None) -> None:
        self._limit = limit if limit and limit > 0 else None
        self._semaphore = asyncio.Semaphore(self._limit) if self._limit else None
        self._in_flight = 0
        self._peak_in_flight = 0

    @property
    def limit(self) -> int | None:
        """返回并发上限；``None`` 表示不限制。"""
        return self._limit

    @property
    def peak_in_flight(self) -> int:
        """返回观测到的同时在执行调用数的峰值，供自检与排错使用。"""
        return self._peak_in_flight

    @property
    def in_flight(self) -> int:
        """返回当前正在执行的调用数。"""
        return self._in_flight

    async def run(self, call: Callable[[], Awaitable[Any]]) -> Any:
        """在并发上限内执行一次模型调用。

        未配置上限时直接 await，不经过信号量；配置了上限时先取得名额再执行，
        名额在调用结束（含异常）时一定归还。
        """
        if self._semaphore is None:
            return await call()

        async with self._semaphore:
            self._in_flight += 1
            self._peak_in_flight = max(self._peak_in_flight, self._in_flight)
            try:
                return await call()
            finally:
                self._in_flight -= 1

    def wrap(self, func: Callable[..., Awaitable[Any]]) -> Callable[..., Awaitable[Any]]:
        """把任意异步模型函数包装成受并发上限约束的版本。

        包装后的函数保留原始参数，调用方无需感知限流存在。
        """
        async def limited(*args: Any, **kwargs: Any) -> Any:
            return await self.run(lambda: func(*args, **kwargs))

        return limited
