"""隔离验证所需的外部能力协议。

场景判定逻辑只依赖这里的协议，因此本机可以用替身完整跑通全部分支，
包括「故意喂串数据时能否判出 fail」这类关键测试。

协议只有三个：租户 RAG 的 HTTP 接口、PostgreSQL 巡检、Neo4j 巡检。
真实实现在 clients.py，替身实现在 scripts/fakes.py。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Protocol, Sequence


class ScenarioStatus(str, Enum):
    """场景结论的三值状态。

    blocked 表示环境不具备（连不上、缺依赖、索引超时），
    它绝不是「通过」，也不等同于「失败」。
    """

    PASS = "pass"
    FAIL = "fail"
    BLOCKED = "blocked"


@dataclass(frozen=True)
class UploadedDocument:
    """一次上传的结果。"""

    doc_id: str
    file_name: str
    status: str
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class DocumentSummary:
    """文档列表中的单条记录，只保留判定需要的字段。"""

    doc_id: str
    file_name: str
    status: str


@dataclass(frozen=True)
class QueryAnswer:
    """一次查询的观察结果。

    content 是回答正文；contexts 是检索命中的上下文，两者都要检查，
    因为口令可能只出现在上下文而没进入最终答案。
    """

    content: str
    contexts: Sequence[str] = ()

    def mentions(self, token: str) -> bool:
        """判断回答或上下文中是否出现指定口令。"""
        if token in self.content:
            return True
        return any(token in context for context in self.contexts)


@dataclass
class ScenarioResult:
    """单个场景的判定结果。

    evidence 记录「实际观察到了什么」，供报告与人工复核使用；
    它不能是空话，要能让读者自己判断结论是否成立。
    """

    scenario_id: str
    status: ScenarioStatus
    detail: str
    evidence: dict[str, Any] = field(default_factory=dict)

    @property
    def passed(self) -> bool:
        """是否判定为通过。"""
        return self.status is ScenarioStatus.PASS


class RagClient(Protocol):
    """租户 RAG 服务的访问接口。

    所有方法都以 user_id 区分租户，但**不允许**由调用方直接拼 URL 决定租户，
    租户到前缀的映射属于服务端行为。
    """

    async def upload_text(
        self, user_id: str, file_name: str, content: str
    ) -> UploadedDocument:
        """上传一段文本作为文档。"""

    async def list_documents(self, user_id: str) -> Sequence[DocumentSummary]:
        """列出该租户的全部文档。"""

    async def delete_document(self, user_id: str, doc_id: str) -> None:
        """删除该租户的指定文档。"""

    async def wait_until_indexed(
        self,
        user_id: str,
        doc_id: str,
        timeout_seconds: float,
        poll_interval: float,
    ) -> str:
        """轮询直到文档处理完成，返回最终状态；超时抛 TimeoutError。"""

    async def query(
        self, user_id: str, question: str, mode: str = "naive"
    ) -> QueryAnswer:
        """以指定模式提问。"""


class PgInspector(Protocol):
    """PostgreSQL 侧的巡检接口。"""

    async def workspace_counts(self, table: str) -> dict[str, int]:
        """按 workspace 统计指定表的行数。"""

    async def distinct_workspaces(self, table: str) -> Sequence[str]:
        """列出该表出现过的全部 workspace 值。"""

    async def rows_for_workspace(self, table: str, workspace: str) -> int:
        """统计指定 workspace 的行数。"""


class Neo4jInspector(Protocol):
    """Neo4j 侧的巡检接口。"""

    async def labels(self) -> Sequence[str]:
        """列出库中全部节点 Label。"""

    async def entity_ids(self, label: str) -> Sequence[str]:
        """列出指定 Label 下的全部实体标识。"""
