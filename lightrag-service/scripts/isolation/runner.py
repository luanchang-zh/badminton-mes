"""隔离验证的命令行编排。

这里只负责「把场景跑起来并汇总」，判定逻辑都在 scenarios / storage_scenarios 里，
因此本机可以用替身完整验证编排行为（退出码、场景挑选、报告字段）。
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path
from typing import Any, Sequence

from isolation.protocols import ScenarioResult, ScenarioStatus
from isolation.scenarios import (
    TENANT_A,
    TENANT_B,
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

EXIT_OK = 0
EXIT_FAILED = 1
EXIT_BLOCKED = 2

ALL_SCENARIOS = ("S1", "S2", "S3", "S4", "S5", "S6")


def parse_scenario_selection(raw: str | None) -> tuple[str, ...]:
    """解析 --only 参数。

    未指定时返回全部场景；指定了但包含未知编号时直接报错，
    避免「写错编号却静默跳过」这种最危险的失败方式。
    """
    if not raw:
        return ALL_SCENARIOS
    requested = tuple(item.strip().upper() for item in raw.split(",") if item.strip())
    unknown = [item for item in requested if item not in ALL_SCENARIOS]
    if unknown:
        raise ValueError(f"未知场景编号：{', '.join(unknown)}；可选：{', '.join(ALL_SCENARIOS)}")
    return requested


async def run_selected(
    selection: Sequence[str],
    rag: Any,
    pg: Any,
    neo4j: Any,
    restart_samples: tuple[dict | None, dict | None] = (None, None),
) -> list[ScenarioResult]:
    """按选择执行场景，返回全部结果。

    场景之间互不影响：某个场景抛异常时只把该场景记为 blocked，
    其余场景继续，这样一次运行能看到全貌而不是中途终止。
    """
    token_a = generate_token()
    token_b = generate_token()
    results: list[ScenarioResult] = []

    runners = {
        "S1": lambda: run_s1_cross_query(rag, token_a),
        "S2": lambda: run_s2_same_filename(rag, token_a, token_b),
        "S3": lambda: run_s3_concurrent(rag, token_a),
        "S4": lambda: run_s4_restart_consistency(*restart_samples),
        "S5": lambda: run_s5_workspace_isolation(pg),
        "S6": lambda: run_s6_graph_isolation(neo4j),
    }

    for scenario_id in selection:
        try:
            if scenario_id == "S4" and restart_samples == (None, None):
                # 没提供采样时自行采集：先取一次，再用同一后端重新读取一次。
                # 自检模式下没有真实重启，这样至少能验证「比对逻辑」本身可用；
                # 真实模式下由外部在服务重启前后各调用一次并传入样本。
                before = {user: [d.doc_id for d in await rag.list_documents(user)]
                          for user in (TENANT_A, TENANT_B)}
                after = {user: [d.doc_id for d in await rag.list_documents(user)]
                         for user in (TENANT_A, TENANT_B)}
                result = await run_s4_restart_consistency(before, after)
            else:
                result = await runners[scenario_id]()
        except Exception as error:  # noqa: BLE001 - 单场景异常不应中断整体
            result = ScenarioResult(
                scenario_id=scenario_id,
                status=ScenarioStatus.BLOCKED,
                detail=f"场景执行异常：{type(error).__name__}: {error}",
                evidence={"error_type": type(error).__name__},
            )
        results.append(result)

    return results


def summarize(results: Sequence[ScenarioResult]) -> dict[str, Any]:
    """汇总结果，供退出码判断与报告使用。"""
    counts = {status.value: 0 for status in ScenarioStatus}
    for result in results:
        counts[result.status.value] += 1
    return {
        "total": len(results),
        "pass": counts["pass"],
        "fail": counts["fail"],
        "blocked": counts["blocked"],
    }


def decide_exit_code(results: Sequence[ScenarioResult]) -> int:
    """决定进程退出码。

    只要存在 fail 就返回失败码；全部 pass 才返回成功码；
    只有 blocked 时返回独立的码，提示「环境不具备」而不是「通过」或「失败」。
    """
    statuses = {result.status for result in results}
    if ScenarioStatus.FAIL in statuses:
        return EXIT_FAILED
    if ScenarioStatus.BLOCKED in statuses:
        return EXIT_BLOCKED
    return EXIT_OK


def format_results(results: Sequence[ScenarioResult]) -> list[str]:
    """把结果格式化为可读文本行。"""
    lines: list[str] = []
    for result in results:
        lines.append(f"[{result.status.value.upper():7s}] {result.scenario_id}  {result.detail}")
    summary = summarize(results)
    lines.append(
        f"合计 {summary['total']} 项：pass={summary['pass']} "
        f"fail={summary['fail']} blocked={summary['blocked']}"
    )
    return lines


def build_parser() -> argparse.ArgumentParser:
    """构造命令行解析器。"""
    parser = argparse.ArgumentParser(
        prog="verify_isolation", description="LightRAG 跨租户隔离验证"
    )
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="使用替身跑完全部场景，不连接任何外部服务",
    )
    parser.add_argument(
        "--real", action="store_true", help="对真实服务执行验证（需要数据库与模型）"
    )
    parser.add_argument("--only", default=None, help="只运行指定场景，例如 S1,S3")
    parser.add_argument("--report", default=None, help="把 JSON 证据报告写到该路径")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """命令行入口。"""
    arguments = build_parser().parse_args(argv)
    try:
        selection = parse_scenario_selection(arguments.only)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return EXIT_FAILED

    if arguments.real:
        from isolation.clients import build_real_clients

        rag, pg, neo4j = build_real_clients()
    else:
        # 默认走自检：没有外部服务时也必须能跑起来，避免误连生产库。
        from fakes import build_healthy_neo4j, build_healthy_pg, build_healthy_rag

        rag, pg, neo4j = build_healthy_rag(), build_healthy_pg(), build_healthy_neo4j()

    results = asyncio.run(run_selected(selection, rag, pg, neo4j))

    for line in format_results(results):
        print(line)

    if arguments.report:
        from isolation.report import write_report

        write_report(Path(arguments.report), results, mode="real" if arguments.real else "self-check")
        print(f"报告已写入 {arguments.report}")

    return decide_exit_code(results)
