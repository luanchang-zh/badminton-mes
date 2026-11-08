"""全局并发上限单测：峰值不超过配置值，未配置时退化为直通。"""

from __future__ import annotations

import asyncio

import pytest

from tenant.llm_limiter import ModelConcurrencyLimiter


@pytest.mark.asyncio
async def test_peak_concurrency_does_not_exceed_limit() -> None:
    """并发发起超过上限的调用时，同时在执行的调用数不超过上限。"""
    limiter = ModelConcurrencyLimiter(limit=3)
    running = 0
    peak = 0
    lock = asyncio.Lock()

    async def call() -> None:
        nonlocal running, peak
        async with lock:
            running += 1
            peak = max(peak, running)
        await asyncio.sleep(0.01)
        async with lock:
            running -= 1

    await asyncio.gather(*(limiter.run(call) for _ in range(12)))

    assert peak <= 3
    assert limiter.peak_in_flight <= 3


@pytest.mark.asyncio
async def test_all_calls_complete() -> None:
    """限流不会丢调用：全部调用都执行完成。"""
    limiter = ModelConcurrencyLimiter(limit=2)
    completed: list[int] = []

    async def call(index: int) -> None:
        await asyncio.sleep(0.001)
        completed.append(index)

    await asyncio.gather(*(limiter.run(lambda i=i: call(i)) for i in range(10)))

    assert sorted(completed) == list(range(10))


@pytest.mark.asyncio
async def test_unlimited_limiter_is_passthrough() -> None:
    """未配置上限时不引入额外等待，全部调用成功。"""
    limiter = ModelConcurrencyLimiter(limit=None)
    results: list[int] = []

    async def call(index: int) -> int:
        results.append(index)
        return index

    output = await asyncio.gather(*(limiter.run(lambda i=i: call(i)) for i in range(8)))

    assert limiter.limit is None
    assert sorted(output) == list(range(8))
    assert len(results) == 8


@pytest.mark.asyncio
async def test_exception_releases_slot() -> None:
    """调用抛异常也要归还名额，否则后续调用会被永久阻塞。"""
    limiter = ModelConcurrencyLimiter(limit=1)

    async def boom() -> None:
        raise ValueError("模型调用失败")

    with pytest.raises(ValueError, match="模型调用失败"):
        await limiter.run(boom)

    assert limiter.in_flight == 0
    assert await limiter.run(lambda: asyncio.sleep(0, result="ok")) == "ok"


@pytest.mark.asyncio
async def test_wrap_preserves_arguments() -> None:
    """包装后的函数正确透传参数与返回值。"""
    limiter = ModelConcurrencyLimiter(limit=2)

    async def echo(text: str, suffix: str = "") -> str:
        return f"{text}{suffix}"

    wrapped = limiter.wrap(echo)

    assert await wrapped("hello", suffix="!") == "hello!"
