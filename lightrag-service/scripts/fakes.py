"""替身实现：内存版 RAG 后端、假 PostgreSQL 表、假 Neo4j 图。

这份替身同时供 `--self-check` 与 pytest 使用。共用同一份实现是刻意的：
否则「测试用的替身」与「自检用的替身」会各自演化，自检就失去了意义。

`leaking=True` 用来模拟串数据：此时每个租户都能看到全部文档，
判定逻辑必须能把它判成 fail。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Sequence

from isolation.protocols import (
    DocumentSummary,
    Neo4jInspector,
    PgInspector,
    QueryAnswer,
    RagClient,
    UploadedDocument,
)

# 替身里模拟的「已索引完成」状态。
INDEXED_STATUS = "processed"


@dataclass
class FakeRagBackend:
    """内存版的 RAG 后端。

    Args:
        leaking: 为 True 时模拟串数据——所有租户共享同一份文档存储。
        stuck_documents: 这些文件名上传后永远停在 pending，用于测超时。
        indexed_immediately: 上传即可用，不经过等待（S1/S2 默认走这条）。
    """

    leaking: bool = False
    stuck_documents: set[str] = field(default_factory=set)
    indexed_immediately: bool = True

    _documents: dict[str, list[UploadedDocument]] = field(default_factory=dict)
    _contents: dict[str, str] = field(default_factory=dict)
    _statuses: dict[str, str] = field(default_factory=dict)

    def _bucket(self, user_id: str) -> list[UploadedDocument]:
        """取该租户的文档桶；串数据模式下所有租户共用一个桶。"""
        key = "__shared__" if self.leaking else user_id
        return self._documents.setdefault(key, [])

    def _visible_user_ids(self, user_id: str) -> list[str]:
        """该租户能查到内容的来源；串数据模式下能看到全部。"""
        if self.leaking:
            return ["__shared__"]
        return [user_id]

    async def upload_text(
        self, user_id: str, file_name: str, content: str
    ) -> UploadedDocument:
        """记录一次上传；文件名相同视为覆盖（用于模拟同名文件冲突）。"""
        doc_id = f"doc-{uuid.uuid4().hex[:12]}"
        bucket = self._bucket(user_id)
        bucket[:] = [doc for doc in bucket if doc.file_name != file_name]
        status = "pending" if file_name in self.stuck_documents else INDEXED_STATUS
        document = UploadedDocument(doc_id=doc_id, file_name=file_name, status=status)
        bucket.append(document)
        self._contents[doc_id] = content
        self._statuses[doc_id] = status
        return document

    async def list_documents(self, user_id: str) -> Sequence[DocumentSummary]:
        """列出该租户可见的文档。"""
        return [
            DocumentSummary(doc_id=d.doc_id, file_name=d.file_name, status=self._statuses[d.doc_id])
            for d in self._bucket(user_id)
        ]

    async def delete_document(self, user_id: str, doc_id: str) -> None:
        """从该租户的桶里移除文档。"""
        bucket = self._bucket(user_id)
        bucket[:] = [doc for doc in bucket if doc.doc_id != doc_id]
        self._contents.pop(doc_id, None)
        self._statuses.pop(doc_id, None)

    async def wait_until_indexed(
        self,
        user_id: str,
        doc_id: str,
        timeout_seconds: float,
        poll_interval: float,
    ) -> str:
        """返回最终状态；文档卡在 pending 时抛超时。"""
        status = self._statuses.get(doc_id, "missing")
        if status == "pending":
            raise TimeoutError(f"文档 {doc_id} 在 {timeout_seconds} 秒内未完成索引")
        return status

    async def query(
        self, user_id: str, question: str, mode: str = "naive"
    ) -> QueryAnswer:
        """把可见文档的正文拼成回答。

        替身不做真实检索：把可见内容全部塞进上下文，
        这样「串数据」会立刻体现在回答里，判定逻辑很容易抓到。
        """
        visible: list[str] = []
        for key in self._visible_user_ids(user_id):
            for doc in self._documents.get(key, []):
                content = self._contents.get(doc.doc_id)
                if content:
                    visible.append(content)
        joined = "\n".join(visible)
        return QueryAnswer(content=joined, contexts=tuple(visible))


@dataclass
class FakePgInspector:
    """假的 PostgreSQL 巡检器，用字典模拟「表 → workspace → 行数」。"""

    tables: dict[str, dict[str, int]] = field(default_factory=dict)

    async def workspace_counts(self, table: str) -> dict[str, int]:
        """返回该表的 workspace 行数分布。"""
        return dict(self.tables.get(table, {}))

    async def distinct_workspaces(self, table: str) -> Sequence[str]:
        """返回该表出现过的全部 workspace。"""
        return sorted(self.tables.get(table, {}))

    async def rows_for_workspace(self, table: str, workspace: str) -> int:
        """返回该 workspace 在该表的行数。"""
        return self.tables.get(table, {}).get(workspace, 0)


@dataclass
class FakeNeo4jInspector:
    """假的 Neo4j 巡检器，用字典模拟「Label → 实体集合」。"""

    graph: dict[str, list[str]] = field(default_factory=dict)

    async def labels(self) -> Sequence[str]:
        """返回全部 Label。"""
        return sorted(self.graph)

    async def entity_ids(self, label: str) -> Sequence[str]:
        """返回该 Label 下的实体。"""
        return list(self.graph.get(label, ()))


def build_healthy_rag() -> FakeRagBackend:
    """构造隔离正常的 RAG 替身。"""
    return FakeRagBackend(leaking=False)


def build_leaking_rag() -> FakeRagBackend:
    """构造会串数据的 RAG 替身，用于验证判定逻辑抓得住问题。"""
    return FakeRagBackend(leaking=True)


def build_healthy_pg() -> FakePgInspector:
    """构造隔离正常的 PG 替身：所有表都只有两个租户的 workspace。"""
    from isolation.scenarios import ISOLATED_TABLES

    tables = {name: {"ws_001": 3, "ws_002": 2} for name in ISOLATED_TABLES}
    return FakePgInspector(tables=tables)


def build_leaking_pg() -> FakePgInspector:
    """构造存在错位行的 PG 替身。"""
    from isolation.scenarios import ISOLATED_TABLES

    tables = {name: {"ws_001": 3, "ws_002": 2} for name in ISOLATED_TABLES}
    tables["LIGHTRAG_LLM_CACHE"] = {"ws_001": 3, "ws_002": 2, "default": 1}
    return FakePgInspector(tables=tables)


def build_healthy_neo4j() -> FakeNeo4jInspector:
    """构造隔离正常的 Neo4j 替身。"""
    return FakeNeo4jInspector(graph={"ws_001": ["e-a1", "e-a2"], "ws_002": ["e-b1"]})


def build_leaking_neo4j() -> FakeNeo4jInspector:
    """构造存在跨租户实体的 Neo4j 替身。"""
    return FakeNeo4jInspector(
        graph={"ws_001": ["e-a1", "e-shared"], "ws_002": ["e-b1", "e-shared"]}
    )


def build_base_label_neo4j() -> FakeNeo4jInspector:
    """构造节点落到全局 base Label 的 Neo4j 替身。"""
    return FakeNeo4jInspector(graph={"base": ["e-x"], "ws_001": ["e-a1"]})
