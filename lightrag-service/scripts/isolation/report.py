"""把验证结果写成 JSON 证据报告。

报告要能被课设直接引用，因此必须记录「实际观察到了什么」，
而不是只给一个结论。判定依据、口令、双方的观察结果都保留下来。
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

from isolation.protocols import ScenarioResult

REPORT_SCHEMA = "lightrag.isolation-report.v1"


def build_report(
    results: Sequence[ScenarioResult], mode: str
) -> dict[str, Any]:
    """构造报告字典。

    mode 标注是自检还是真实执行：这两者的证据强度完全不同，
    混淆会让读者误以为隔离已经被证明。
    """
    return {
        "schema": REPORT_SCHEMA,
        "mode": mode,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "conclusive": mode == "real",
        "summary": {
            "total": len(results),
            "pass": sum(1 for r in results if r.status.value == "pass"),
            "fail": sum(1 for r in results if r.status.value == "fail"),
            "blocked": sum(1 for r in results if r.status.value == "blocked"),
        },
        "scenarios": [
            {
                "id": result.scenario_id,
                "status": result.status.value,
                "detail": result.detail,
                "evidence": result.evidence,
            }
            for result in results
        ],
    }


def write_report(
    path: Path, results: Sequence[ScenarioResult], mode: str
) -> None:
    """把报告写入文件，父目录不存在时自动创建。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = build_report(results, mode)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
