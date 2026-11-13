"""S1～S6 的隔离判定逻辑。

这些函数只依赖 protocols 里的协议，不关心数据来自真实服务还是替身，
因此本机可以用替身覆盖「正常」与「串数据」两条分支。

每轮验证都生成随机口令写入文档正文。口令是随机串，模型不可能自行编造，
所以它一旦出现在另一个租户的回答里，就是确凿的串数据证据。
"""

from __future__ import annotations

import asyncio
import secrets
from typing import Sequence

from isolation.protocols import (
    Neo4jInspector,
    PgInspector,
    RagClient,
    ScenarioResult,
    ScenarioStatus,
)

# 等待索引完成的默认上限：真实环境里大文件可能较慢，但必须有上限。
DEFAULT_INDEX_TIMEOUT_SECONDS = 120.0
DEFAULT_POLL_INTERVAL_SECONDS = 2.0

# 这些表按 workspace 隔离；LLM 缓存也在其中——缓存串了等于答案串了。
ISOLATED_TABLES = (
    "LIGHTRAG_DOC_FULL",
    "LIGHTRAG_DOC_CHUNKS",
    "LIGHTRAG_DOC_STATUS",
    "LIGHTRAG_VDB_CHUNKS",
    "LIGHTRAG_VDB_ENTITY",
    "LIGHTRAG_VDB_RELATION",
    "LIGHTRAG_FULL_ENTITIES",
    "LIGHTRAG_FULL_RELATIONS",
    "LIGHTRAG_LLM_CACHE",
)

# 两个固定租户：口令法只需要两个隔离主体。
TENANT_A = "user_1"
TENANT_B = "user_2"


def generate_token() -> str:
    """生成一次性口令。

    使用 secrets 而不是 random：口令要能作为「不可能被模型猜中」的标记，
    因此必须来自密码学安全的随机源。
    """
    return f"ISOLATION-{secrets.token_hex(8)}"


def _blocked(scenario_id: str, detail: str, **evidence: object) -> ScenarioResult:
    """构造环境不具备的结果。"""
    return ScenarioResult(
        scenario_id=scenario_id,
        status=ScenarioStatus.BLOCKED,
        detail=detail,
        evidence=dict(evidence),
    )


async def run_s1_cross_query(client: RagClient, token: str) -> ScenarioResult:
    """S1：A 的内容不得出现在 B 的查询结果里。

    判定要求两边都成立：
    - A 自己能查到该口令（否则是索引没建好，属于环境问题而非隔离问题）；
    - B 查不到该口令（回答与检索上下文都不能出现）。
    """
    scenario_id = "S1"
    try:
        uploaded = await client.upload_text(
            TENANT_A, "s1-secret.md", f"机密编号是 {token}，请勿外传。"
        )
        await client.wait_until_indexed(
            TENANT_A,
            uploaded.doc_id,
            DEFAULT_INDEX_TIMEOUT_SECONDS,
            DEFAULT_POLL_INTERVAL_SECONDS,
        )
    except TimeoutError as error:
        return _blocked(scenario_id, f"等待 A 的文档索引超时：{error}", token=token)
    except (OSError, ConnectionError) as error:
        return _blocked(scenario_id, f"无法访问 RAG 服务：{error}", token=token)

    question = "机密编号是什么？"
    answer_a = await client.query(TENANT_A, question, "naive")
    answer_b = await client.query(TENANT_B, question, "naive")
    answer_b_mix = await client.query(TENANT_B, question, "mix")

    a_sees = answer_a.mentions(token)
    b_sees = answer_b.mentions(token) or answer_b_mix.mentions(token)

    evidence = {
        "token": token,
        "tenant_a_found_token": a_sees,
        "tenant_b_found_token": b_sees,
    }
    if not a_sees:
        return _blocked(
            scenario_id,
            "A 自己查不到口令，说明索引未生效，无法据此判断隔离",
            **evidence,
        )
    if b_sees:
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "B 的回答或检索上下文中出现了 A 的口令",
            evidence,
        )
    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "A 能查到自己的口令，B 查不到",
        evidence,
    )


async def run_s2_same_filename(
    client: RagClient, token_a: str, token_b: str
) -> ScenarioResult:
    """S2：同名文件不得互相覆盖。

    两个租户上传同名但内容不同的文件；只要任一方查到了对方的口令，
    就说明上传目录或索引存在共享。
    """
    scenario_id = "S2"
    file_name = "shared-report.md"
    try:
        doc_a = await client.upload_text(TENANT_A, file_name, f"A 的专属口令 {token_a}")
        doc_b = await client.upload_text(TENANT_B, file_name, f"B 的专属口令 {token_b}")
        await client.wait_until_indexed(
            TENANT_A,
            doc_a.doc_id,
            DEFAULT_INDEX_TIMEOUT_SECONDS,
            DEFAULT_POLL_INTERVAL_SECONDS,
        )
        await client.wait_until_indexed(
            TENANT_B,
            doc_b.doc_id,
            DEFAULT_INDEX_TIMEOUT_SECONDS,
            DEFAULT_POLL_INTERVAL_SECONDS,
        )
    except TimeoutError as error:
        return _blocked(scenario_id, f"等待索引超时：{error}", file_name=file_name)
    except (OSError, ConnectionError) as error:
        return _blocked(scenario_id, f"无法访问 RAG 服务：{error}")

    answer_a = await client.query(TENANT_A, "你的专属口令是什么？", "naive")
    answer_b = await client.query(TENANT_B, "你的专属口令是什么？", "naive")

    a_has_own = answer_a.mentions(token_a)
    b_has_own = answer_b.mentions(token_b)
    a_leaked = answer_a.mentions(token_b)
    b_leaked = answer_b.mentions(token_a)

    evidence = {
        "file_name": file_name,
        "tenant_a_token": token_a,
        "tenant_b_token": token_b,
        "tenant_a_sees_own": a_has_own,
        "tenant_b_sees_own": b_has_own,
        "tenant_a_sees_other": a_leaked,
        "tenant_b_sees_other": b_leaked,
    }
    if a_leaked or b_leaked:
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "同名文件导致内容互相可见",
            evidence,
        )
    if not (a_has_own and b_has_own):
        return _blocked(
            scenario_id,
            "至少一方查不到自己的口令，索引可能未生效",
            **evidence,
        )
    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "两边都只查到自己的口令，同名文件未互相覆盖",
        evidence,
    )


async def run_s3_concurrent(client: RagClient, token: str) -> ScenarioResult:
    """S3：并发上传与删除不得串号。

    并发是隔离最容易失效的场景：后台任务若共享队列或状态，就会出现
    A 的任务影响 B 的文档。判定看两件事：
    - 双方文档列表里不出现对方的 doc_id；
    - 删除后双方各自的文档都被正确移除。
    """
    scenario_id = "S3"
    try:
        doc_a, doc_b = await asyncio.gather(
            client.upload_text(TENANT_A, "concurrent-a.md", f"A 的口令 {token}"),
            client.upload_text(TENANT_B, "concurrent-b.md", f"B 的口令 {token}"),
        )
        await asyncio.gather(
            client.wait_until_indexed(
                TENANT_A,
                doc_a.doc_id,
                DEFAULT_INDEX_TIMEOUT_SECONDS,
                DEFAULT_POLL_INTERVAL_SECONDS,
            ),
            client.wait_until_indexed(
                TENANT_B,
                doc_b.doc_id,
                DEFAULT_INDEX_TIMEOUT_SECONDS,
                DEFAULT_POLL_INTERVAL_SECONDS,
            ),
        )
    except TimeoutError as error:
        return _blocked(scenario_id, f"并发上传后等待索引超时：{error}")
    except (OSError, ConnectionError) as error:
        return _blocked(scenario_id, f"无法访问 RAG 服务：{error}")

    docs_a = {doc.doc_id for doc in await client.list_documents(TENANT_A)}
    docs_b = {doc.doc_id for doc in await client.list_documents(TENANT_B)}

    cross_a = doc_b.doc_id in docs_a
    cross_b = doc_a.doc_id in docs_b

    # 并发删除：各自删掉自己刚上传的文档，观察对方是否受影响。
    await asyncio.gather(
        client.delete_document(TENANT_A, doc_a.doc_id),
        client.delete_document(TENANT_B, doc_b.doc_id),
    )
    after_a = {doc.doc_id for doc in await client.list_documents(TENANT_A)}
    after_b = {doc.doc_id for doc in await client.list_documents(TENANT_B)}

    evidence = {
        "tenant_a_doc": doc_a.doc_id,
        "tenant_b_doc": doc_b.doc_id,
        "tenant_a_listed_tenant_b_doc": cross_a,
        "tenant_b_listed_tenant_a_doc": cross_b,
        "tenant_a_doc_removed": doc_a.doc_id not in after_a,
        "tenant_b_doc_removed": doc_b.doc_id not in after_b,
    }
    if cross_a or cross_b:
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "并发场景下双方文档列表互相出现了对方的文档标识",
            evidence,
        )
    if not (evidence["tenant_a_doc_removed"] and evidence["tenant_b_doc_removed"]):
        return ScenarioResult(
            scenario_id,
            ScenarioStatus.FAIL,
            "并发删除后至少一方仍保留了自己的文档",
            evidence,
        )
    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "并发上传与删除后双方文档互不干扰",
        evidence,
    )


async def run_s4_restart_consistency(
    before: dict[str, Sequence[str]] | None,
    after: dict[str, Sequence[str]] | None,
) -> ScenarioResult:
    """S4：重启前后的文档集合必须一致。

    这个场景本身不发起请求，只比对两次采样。before 为 None 表示还没采样，
    属于调用顺序问题；after 缺失表示重启后采集失败，都记为 blocked。
    """
    scenario_id = "S4"
    if before is None or after is None:
        return _blocked(
            scenario_id,
            "缺少重启前或重启后的采样结果",
            has_before=before is not None,
            has_after=after is not None,
        )

    evidence: dict[str, object] = {}
    for user_id in (TENANT_A, TENANT_B):
        before_ids = sorted(before.get(user_id, ()))
        after_ids = sorted(after.get(user_id, ()))
        evidence[f"{user_id}_before_count"] = len(before_ids)
        evidence[f"{user_id}_after_count"] = len(after_ids)
        if before_ids != after_ids:
            evidence[f"{user_id}_changed"] = True
            return ScenarioResult(
                scenario_id,
                ScenarioStatus.FAIL,
                f"重启后 {user_id} 的文档集合发生变化",
                evidence,
            )
        evidence[f"{user_id}_changed"] = False

    return ScenarioResult(
        scenario_id,
        ScenarioStatus.PASS,
        "重启前后双方文档集合一致",
        evidence,
    )
