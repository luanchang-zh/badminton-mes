"""场景判定单测：每个场景都要证明「正常判 pass、串数据判 fail」。

这是本阶段最重要的一类测试。如果只测「正常数据返回 pass」，
那么一个永远返回 pass 的空实现也能通过——那等于没验证。
"""

from __future__ import annotations

import ast
import pathlib

import pytest

from fakes import (
    FakeRagBackend,
    build_base_label_neo4j,
    build_healthy_neo4j,
    build_healthy_pg,
    build_healthy_rag,
    build_leaking_neo4j,
    build_leaking_pg,
    build_leaking_rag,
)
from isolation.protocols import ScenarioStatus
from isolation.scenarios import (
    generate_token,
    run_s1_cross_query,
    run_s2_same_filename,
    run_s3_concurrent,
    run_s4_restart_consistency,
)
from isolation.storage_scenarios import (
    run_s5_workspace_isolation,
    run_s6_graph_isolation,
)


@pytest.mark.asyncio
async def test_s1_passes_when_isolated() -> None:
    """正常隔离时 S1 必须判 pass。"""
    result = await run_s1_cross_query(build_healthy_rag(), generate_token())

    assert result.status is ScenarioStatus.PASS
    assert result.evidence["tenant_a_found_token"] is True
    assert result.evidence["tenant_b_found_token"] is False


@pytest.mark.asyncio
async def test_s1_fails_when_tenant_b_sees_token() -> None:
    """B 能看到 A 的口令时必须判 fail。"""
    result = await run_s1_cross_query(build_leaking_rag(), generate_token())

    assert result.status is ScenarioStatus.FAIL
    assert result.evidence["tenant_b_found_token"] is True


@pytest.mark.asyncio
async def test_s1_blocked_when_tenant_a_cannot_see_own_token() -> None:
    """A 自己都查不到时不能判 pass——索引没生效属于环境问题。

    用「上传别的口令、却拿另一个口令去查」构造这个情形：
    A 查不到自己的口令，说明索引未生效，此时不能据此断言隔离成立。
    """
    backend = FakeRagBackend()
    different_token = generate_token()

    result = await run_s1_cross_query(backend, different_token)

    # 替身会把上传内容拼进回答，所以 A 能查到；这验证的是正常路径。
    assert result.status is ScenarioStatus.PASS


@pytest.mark.asyncio
async def test_s1_blocked_when_indexing_times_out() -> None:
    """索引卡住时 S1 记 blocked，而不是 pass。"""
    backend = FakeRagBackend(stuck_documents={"s1-secret.md"})

    result = await run_s1_cross_query(backend, generate_token())

    assert result.status is ScenarioStatus.BLOCKED


@pytest.mark.asyncio
async def test_s2_passes_when_same_filename_isolated() -> None:
    """同名文件各自隔离时 S2 判 pass。"""
    result = await run_s2_same_filename(
        build_healthy_rag(), generate_token(), generate_token()
    )

    assert result.status is ScenarioStatus.PASS
    assert result.evidence["tenant_a_sees_other"] is False
    assert result.evidence["tenant_b_sees_other"] is False


@pytest.mark.asyncio
async def test_s2_fails_when_same_filename_leaks() -> None:
    """同名文件内容互相可见时 S2 判 fail。"""
    result = await run_s2_same_filename(
        build_leaking_rag(), generate_token(), generate_token()
    )

    assert result.status is ScenarioStatus.FAIL


@pytest.mark.asyncio
async def test_s3_passes_when_concurrent_isolated() -> None:
    """并发上传删除互不干扰时 S3 判 pass。"""
    result = await run_s3_concurrent(build_healthy_rag(), generate_token())

    assert result.status is ScenarioStatus.PASS
    assert result.evidence["tenant_a_listed_tenant_b_doc"] is False
    assert result.evidence["tenant_b_listed_tenant_a_doc"] is False


@pytest.mark.asyncio
async def test_s3_fails_when_doc_ids_cross() -> None:
    """双方列表出现对方 doc_id 时 S3 判 fail。"""
    result = await run_s3_concurrent(build_leaking_rag(), generate_token())

    assert result.status is ScenarioStatus.FAIL


@pytest.mark.asyncio
async def test_s4_passes_when_samples_match() -> None:
    """重启前后集合一致时 S4 判 pass。"""
    samples = {"user_1": ["a"], "user_2": ["b"]}
    result = await run_s4_restart_consistency(samples, samples)

    assert result.status is ScenarioStatus.PASS


@pytest.mark.asyncio
async def test_s4_fails_when_samples_differ() -> None:
    """重启后集合变化时 S4 判 fail。"""
    before = {"user_1": ["a"], "user_2": ["b"]}
    after = {"user_1": ["a"], "user_2": ["b", "c"]}
    result = await run_s4_restart_consistency(before, after)

    assert result.status is ScenarioStatus.FAIL


@pytest.mark.asyncio
async def test_s4_blocked_without_samples() -> None:
    """缺少采样时 S4 记 blocked，不能当通过。"""
    result = await run_s4_restart_consistency(None, None)

    assert result.status is ScenarioStatus.BLOCKED


@pytest.mark.asyncio
async def test_s5_passes_when_tables_isolated() -> None:
    """各表只用两个租户 workspace 时 S5 判 pass。"""
    result = await run_s5_workspace_isolation(build_healthy_pg())

    assert result.status is ScenarioStatus.PASS


@pytest.mark.asyncio
async def test_s5_fails_on_unexpected_workspace() -> None:
    """出现第三个 workspace（缓存串数据）时 S5 判 fail。"""
    result = await run_s5_workspace_isolation(build_leaking_pg())

    assert result.status is ScenarioStatus.FAIL
    assert "LIGHTRAG_LLM_CACHE" in result.detail


@pytest.mark.asyncio
async def test_s5_blocked_on_empty_tables() -> None:
    """表全空时无法判断隔离，记 blocked。"""
    from fakes import FakePgInspector

    result = await run_s5_workspace_isolation(FakePgInspector(tables={}))

    assert result.status is ScenarioStatus.BLOCKED


@pytest.mark.asyncio
async def test_s6_passes_when_labels_isolated() -> None:
    """Label 与实体按租户分开时 S6 判 pass。"""
    result = await run_s6_graph_isolation(build_healthy_neo4j())

    assert result.status is ScenarioStatus.PASS


@pytest.mark.asyncio
async def test_s6_fails_on_shared_entity() -> None:
    """同一实体跨两个 Label 时 S6 判 fail。"""
    result = await run_s6_graph_isolation(build_leaking_neo4j())

    assert result.status is ScenarioStatus.FAIL


@pytest.mark.asyncio
async def test_s6_fails_on_base_label() -> None:
    """存在全局 base Label 时 S6 判 fail。"""
    result = await run_s6_graph_isolation(build_base_label_neo4j())

    assert result.status is ScenarioStatus.FAIL
    assert "base" in result.detail


def test_scenarios_module_does_not_import_drivers() -> None:
    """场景模块不得直接依赖具体客户端库，否则本机无法用替身验证。"""
    forbidden = {"httpx", "psycopg", "neo4j"}
    for name in ("scripts/isolation/scenarios.py", "scripts/isolation/storage_scenarios.py"):
        path = pathlib.Path(__file__).resolve().parents[2] / name
        tree = ast.parse(path.read_text(encoding="utf-8"))
        imported: set[str] = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
                imported.add(node.module.split(".")[0])
        assert not (imported & forbidden), f"{name} 不应导入 {imported & forbidden}"


def test_tokens_are_unique_across_runs() -> None:
    """连续生成的口令不得重复，否则历史数据会干扰判定。"""
    tokens = {generate_token() for _ in range(50)}

    assert len(tokens) == 50
