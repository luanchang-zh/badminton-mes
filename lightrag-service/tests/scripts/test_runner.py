"""编排单测：退出码、场景挑选、报告字段。"""

from __future__ import annotations

import json
import pathlib

import pytest

from fakes import build_healthy_neo4j, build_healthy_pg, build_healthy_rag, build_leaking_rag
from isolation.protocols import ScenarioResult, ScenarioStatus
from isolation.report import build_report
from isolation.runner import (
    ALL_SCENARIOS,
    EXIT_BLOCKED,
    EXIT_FAILED,
    EXIT_OK,
    decide_exit_code,
    parse_scenario_selection,
    run_selected,
    summarize,
)


def test_selection_defaults_to_all() -> None:
    """未指定时运行全部场景。"""
    assert parse_scenario_selection(None) == ALL_SCENARIOS


def test_selection_accepts_subset() -> None:
    """只运行指定场景。"""
    assert parse_scenario_selection("S1,S3") == ("S1", "S3")


def test_selection_rejects_unknown() -> None:
    """未知场景编号必须报错，不能静默跳过。"""
    with pytest.raises(ValueError, match="未知场景编号"):
        parse_scenario_selection("S1,S9")


def test_exit_code_ok_when_all_pass() -> None:
    """全部通过时退出码为 0。"""
    results = [ScenarioResult("S1", ScenarioStatus.PASS, "ok")]

    assert decide_exit_code(results) == EXIT_OK


def test_exit_code_failed_on_any_fail() -> None:
    """存在 fail 时退出码为失败码。"""
    results = [
        ScenarioResult("S1", ScenarioStatus.PASS, "ok"),
        ScenarioResult("S2", ScenarioStatus.FAIL, "leak"),
    ]

    assert decide_exit_code(results) == EXIT_FAILED


def test_exit_code_blocked_is_distinct() -> None:
    """只有 blocked 时用独立退出码，既不算通过也不算失败。"""
    results = [ScenarioResult("S5", ScenarioStatus.BLOCKED, "no db")]

    assert decide_exit_code(results) == EXIT_BLOCKED


@pytest.mark.asyncio
async def test_run_selected_only_runs_requested() -> None:
    """只执行指定场景时，结果条数与编号都受控。"""
    results = await run_selected(
        ("S1", "S6"), build_healthy_rag(), build_healthy_pg(), build_healthy_neo4j()
    )

    assert [r.scenario_id for r in results] == ["S1", "S6"]


@pytest.mark.asyncio
async def test_self_check_all_pass() -> None:
    """健康替身下六个场景全部通过。"""
    results = await run_selected(
        ALL_SCENARIOS, build_healthy_rag(), build_healthy_pg(), build_healthy_neo4j()
    )

    assert summarize(results)["pass"] == 6
    assert decide_exit_code(results) == EXIT_OK


@pytest.mark.asyncio
async def test_leaking_backend_produces_failures() -> None:
    """串数据替身下必须出现 fail，否则判定逻辑形同虚设。"""
    results = await run_selected(
        ("S1", "S2", "S3"), build_leaking_rag(), build_healthy_pg(), build_healthy_neo4j()
    )

    assert summarize(results)["fail"] == 3
    assert decide_exit_code(results) == EXIT_FAILED


@pytest.mark.asyncio
async def test_scenario_exception_becomes_blocked() -> None:
    """单场景抛异常时记 blocked，不中断其他场景。"""

    class Exploding:
        async def upload_text(self, *args, **kwargs):
            raise RuntimeError("boom")

        async def list_documents(self, *args, **kwargs):
            raise RuntimeError("boom")

    results = await run_selected(
        ("S1", "S6"), Exploding(), build_healthy_pg(), build_healthy_neo4j()
    )

    by_id = {r.scenario_id: r for r in results}
    assert by_id["S1"].status is ScenarioStatus.BLOCKED
    assert by_id["S6"].status is ScenarioStatus.PASS


def test_report_marks_self_check_as_non_conclusive() -> None:
    """自检模式的报告必须标注「不能作为隔离结论」。"""
    results = [ScenarioResult("S1", ScenarioStatus.PASS, "ok", {"k": "v"})]

    report = build_report(results, mode="self-check")

    assert report["conclusive"] is False
    assert report["mode"] == "self-check"
    assert report["scenarios"][0]["evidence"] == {"k": "v"}


def test_report_marks_real_run_as_conclusive() -> None:
    """真实执行的报告才标记为有结论。"""
    report = build_report([ScenarioResult("S1", ScenarioStatus.PASS, "ok")], mode="real")

    assert report["conclusive"] is True


def test_portable_entry_exists_and_is_thin() -> None:
    """入口文件只做转发，不含判定逻辑。"""
    entry = pathlib.Path(__file__).resolve().parents[2] / "scripts/verify_isolation.py"
    source = entry.read_text(encoding="utf-8")

    assert "isolation.runner" in source
    assert "run_s1_cross_query" not in source
