"""S5 / S6：存储层的物理隔离核对。

这两个场景直接查 PostgreSQL 与 Neo4j，因为「API 返回正常」不等于「数据分开存了」——
上游可能恰好做了查询过滤，数据其实混在一起；只有查表才能确认真实隔离。

LLM 缓存表必须包含在内：缓存串了等于答案串了，即使向量检索本身是隔离的也没用。
"""

from __future__ import annotations

from isolation.protocols import (
    Neo4jInspector,
    PgInspector,
    ScenarioResult,
    ScenarioStatus,
)
from isolation.scenarios import ISOLATED_TABLES


def _blocked(scenario_id: str, detail: str, **evidence: object) -> ScenarioResult:
    """构造环境不具备的结果。

    与 scenarios 里的同名辅助函数保持一致的语义：
    blocked 表示环境不具备，既不是通过也不是失败。
    """
    return ScenarioResult(
        scenario_id=scenario_id,
        status=ScenarioStatus.BLOCKED,
        detail=detail,
        evidence=dict(evidence),
    )


async def run_s5_workspace_isolation(pg: PgInspector) -> ScenarioResult:
    """S5：PostgreSQL 各表必须按 workspace 分开存放。

    只看 API 返回不够——上游可能恰好做了查询过滤，数据其实混在一起。
    直接查表才能确认物理隔离，尤其要检查 LLM 缓存表：
    缓存串了等于答案串了，即使向量检索是隔离的也没用。
    """
    scenario_id = "S5"
    expected = {"ws_001", "ws_002"}
    evidence: dict[str, object] = {}

    for table in ISOLATED_TABLES:
        try:
            workspaces = set(await pg.distinct_workspaces(table))
        except (OSError, ConnectionError) as error:
            return _blocked(scenario_id, f"无法连接 PostgreSQL：{error}", table=table)

        evidence[table] = sorted(workspaces)
        unexpected = workspaces - expected
        if unexpected:
            return ScenarioResult(
                scenario_id,
                ScenarioStatus.FAIL,
                f"表 {table} 出现了预期之外的 workspace",
                {**evidence, "unexpected_workspaces": sorted(unexpected)},
            )

    populated = [
        table
        for table, values in evidence.items()
        if isinstance(values, list) and values
    ]
    if not populated:
        return _blocked(
            scenario_id,
            "所有目标表都是空的，无法据此判断隔离",
            **evidence,
        )

    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "所有目标表只包含本租户的 workspace",
        evidence,
    )


async def run_s6_graph_isolation(neo4j: Neo4jInspector) -> ScenarioResult:
    """S6：Neo4j 图谱节点必须按 workspace 分开。

    上游把 workspace 用作节点 Label。隔离失败有两种表现：
    - 节点落到全局 Label（workspace 为空时的 base）；
    - 同一实体出现在两个租户的 Label 下。
    """
    scenario_id = "S6"
    expected = {"ws_001", "ws_002"}
    try:
        labels = set(await neo4j.labels())
    except (OSError, ConnectionError) as error:
        return _blocked(scenario_id, f"无法连接 Neo4j：{error}")

    evidence: dict[str, object] = {"labels": sorted(labels)}
    if "base" in labels:
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "存在全局 base Label，说明节点没有落到租户命名空间",
            evidence,
        )

    unexpected = labels - expected
    if unexpected:
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "出现了预期之外的 Label",
            {**evidence, "unexpected_labels": sorted(unexpected)},
        )

    if not labels:
        return _blocked(scenario_id, "图谱中没有任何 Label，无法判断隔离", **evidence)

    # 逐个 Label 取实体，确认同一实体没有跨 Label 重复出现。
    seen: dict[str, str] = {}
    for label in sorted(labels):
        for entity_id in await neo4j.entity_ids(label):
            owner = seen.get(entity_id)
            if owner is not None and owner != label:
                return ScenarioResult(
                    scenario_id,
                    ScenarioStatus.FAIL,
                    "同一实体出现在两个租户的 Label 下",
                    {**evidence, "entity_id": entity_id, "labels": [owner, label]},
                )
            seen[entity_id] = label

    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "图谱 Label 与实体均按租户分开",
        {**evidence, "entity_count": len(seen)},
    )
