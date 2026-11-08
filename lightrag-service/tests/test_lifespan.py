"""生命周期单测：每实例各初始化与释放一次，共享状态清理一次且顺序正确。"""

from __future__ import annotations

from typing import Any

import pytest

from tenant import lifespan as lifespan_module


class RecordingRag:
    """记录生命周期调用的替身实例。"""

    def __init__(self, name: str, events: list[str], fail_on_init: bool = False) -> None:
        self.name = name
        self.events = events
        self.fail_on_init = fail_on_init

    async def initialize_storages(self) -> None:
        """记录初始化；可按需抛出异常以验证回滚。"""
        self.events.append(f"init:{self.name}")
        if self.fail_on_init:
            raise RuntimeError(f"{self.name} 初始化失败")

    async def check_and_migrate_data(self) -> None:
        """记录迁移调用。"""
        self.events.append(f"migrate:{self.name}")

    async def finalize_storages(self) -> None:
        """记录释放调用。"""
        self.events.append(f"finalize:{self.name}")


class Context:
    """最小租户上下文替身。"""

    def __init__(self, user_id: str, rag: RecordingRag) -> None:
        self.user_id = user_id
        self.rag = rag


@pytest.fixture
def patched_shared_state(monkeypatch: pytest.MonkeyPatch) -> list[str]:
    """替换共享状态清理，避免触碰真实存储，并记录调用顺序。"""
    events: list[str] = []
    monkeypatch.setattr(
        lifespan_module, "finalize_shared_state", lambda: events.append("shared")
    )
    return events


def _contexts(events: list[str], failing: str | None = None) -> list[Context]:
    """构造两个租户上下文，可按 user_id 指定初始化失败的实例。"""
    return [
        Context(
            "user_1",
            RecordingRag("user_1", events, fail_on_init=failing == "user_1"),
        ),
        Context(
            "user_2",
            RecordingRag("user_2", events, fail_on_init=failing == "user_2"),
        ),
    ]


@pytest.mark.asyncio
async def test_each_instance_initialized_and_released_once(
    patched_shared_state: list[str],
) -> None:
    """启动再关闭后，每实例初始化与释放各一次，共享状态清理一次。"""
    events = patched_shared_state
    contexts = _contexts(events)

    async with lifespan_module.build_lifespan(contexts)(_FakeApp()):
        pass

    assert events.count("init:user_1") == 1
    assert events.count("init:user_2") == 1
    assert events.count("finalize:user_1") == 1
    assert events.count("finalize:user_2") == 1
    assert events.count("shared") == 1


@pytest.mark.asyncio
async def test_shared_state_is_cleaned_last(patched_shared_state: list[str]) -> None:
    """共享状态清理必须发生在所有实例释放之后。"""
    events = patched_shared_state
    contexts = _contexts(events)

    async with lifespan_module.build_lifespan(contexts)(_FakeApp()):
        pass

    assert events[-1] == "shared"


@pytest.mark.asyncio
async def test_release_happens_after_background_drain(
    monkeypatch: pytest.MonkeyPatch, patched_shared_state: list[str]
) -> None:
    """实例释放发生在等待后台任务结束之后。"""
    events = patched_shared_state

    async def fake_drain(background_tasks: set, timeout: float = 30.0) -> None:
        events.append("drain")

    monkeypatch.setattr(lifespan_module, "drain_background_tasks", fake_drain)
    contexts = _contexts(events)

    async with lifespan_module.build_lifespan(contexts)(_FakeApp()):
        pass

    assert events.index("drain") < events.index("finalize:user_1")
    assert events.index("drain") < events.index("finalize:user_2")


@pytest.mark.asyncio
async def test_startup_failure_rolls_back_initialized_instances(
    patched_shared_state: list[str],
) -> None:
    """第二个实例启动失败时，第一个实例被回滚释放，异常继续抛出。"""
    events = patched_shared_state
    contexts = _contexts(events, failing="user_2")

    with pytest.raises(RuntimeError, match="初始化失败"):
        async with lifespan_module.build_lifespan(contexts)(_FakeApp()):
            pass

    assert "finalize:user_1" in events
    assert "shared" not in events


class _FakeApp:
    """最小应用替身：lifespan 只用到 ``state``。"""

    class _State:
        background_tasks: set = set()

    def __init__(self) -> None:
        self.state = _FakeApp._State()
