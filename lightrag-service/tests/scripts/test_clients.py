"""真实客户端的降级行为与口令唯一性单测。

本机没有 psycopg / neo4j，测的正是「缺依赖时给可读提示」这条路径——
这是 `--real` 模式最容易踩的坑：原始 ImportError 堆栈无法告诉用户该装什么。
"""

from __future__ import annotations

import importlib.util

import pytest

from isolation.clients import MissingDependencyError, PostgresInspector
from isolation.scenarios import generate_token


def test_missing_dependency_error_mentions_package() -> None:
    """缺依赖错误必须包含包名与安装命令。"""
    error = MissingDependencyError("psycopg[binary]", "PostgreSQL 巡检")

    message = str(error)
    assert "psycopg[binary]" in message
    assert "pip install" in message
    assert "PostgreSQL 巡检" in message


@pytest.mark.asyncio
async def test_postgres_inspector_reports_missing_driver() -> None:
    """未安装 psycopg 时给出可读提示，而不是原始 ImportError。"""
    if importlib.util.find_spec("psycopg") is not None:
        pytest.skip("本机已安装 psycopg，跳过降级路径")

    inspector = PostgresInspector(dsn="postgresql://ignored")

    with pytest.raises(MissingDependencyError) as captured:
        await inspector.distinct_workspaces("LIGHTRAG_DOC_STATUS")

    assert "psycopg" in str(captured.value)


def test_tokens_are_prefixed_and_random() -> None:
    """口令带固定前缀且随机部分不重复。"""
    first, second = generate_token(), generate_token()

    assert first.startswith("ISOLATION-")
    assert first != second
    assert len(first) > len("ISOLATION-")


def test_token_has_no_whitespace() -> None:
    """口令不能含空白，否则检索时会被分词破坏。"""
    token = generate_token()

    assert not any(character.isspace() for character in token)
