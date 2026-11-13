"""协议的真实实现：HTTP 调用、PostgreSQL 巡检、Neo4j 巡检。

这些是 `--real` 模式用的代码，本机不执行。三个驱动都在**函数内部**延迟导入：
模块级导入会让「只想跑自检」的人也被迫安装数据库驱动，
而缺依赖时的原始 ImportError 堆栈无法告诉用户该装什么。
"""

from __future__ import annotations

import asyncio
import os
from typing import Any, Sequence

from isolation.protocols import DocumentSummary, QueryAnswer, UploadedDocument


class MissingDependencyError(RuntimeError):
    """缺少可选依赖，消息里带上需要安装的包名。"""

    def __init__(self, package: str, purpose: str) -> None:
        super().__init__(
            f"使用 {purpose} 需要安装 {package}；请执行 pip install {package}"
        )
        self.package = package
        self.purpose = purpose


# 索引完成后可能出现的状态值；不同上游版本命名略有差异，这里一并接受。
INDEXED_STATES = frozenset({"processed", "completed", "success", "done"})
FAILED_STATES = frozenset({"failed", "error"})


class HttpRagClient:
    """通过 HTTP 访问多租户 RAG 服务的实现。

    租户前缀由服务端决定，这里只按 user_id 拼路径，
    不接受调用方传入任意前缀，避免把隔离责任交给客户端。
    """

    def __init__(self, base_url: str, api_key: str, timeout: float = 60.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._timeout = timeout

    def _headers(self) -> dict[str, str]:
        """构造带内部密钥的请求头。"""
        return {"X-API-Key": self._api_key}

    def _tenant_url(self, user_id: str, suffix: str) -> str:
        """拼出租户路由地址。"""
        return f"{self._base_url}/tenant/{user_id}/{suffix.lstrip('/')}"

    async def _request(self, method: str, url: str, **kwargs: Any) -> Any:
        """发起一次 HTTP 请求；缺 httpx 时给可读提示。"""
        try:
            import httpx
        except ImportError as error:
            raise MissingDependencyError("httpx", "HTTP 调用") from error

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            response = await client.request(
                method, url, headers=self._headers(), **kwargs
            )
            response.raise_for_status()
            return response.json()

    async def upload_text(
        self, user_id: str, file_name: str, content: str
    ) -> UploadedDocument:
        """以纯文本方式上传文档。"""
        payload = await self._request(
            "POST",
            self._tenant_url(user_id, "documents/text"),
            json={"text": content, "description": file_name},
        )
        return UploadedDocument(
            doc_id=str(payload.get("track_id") or payload.get("doc_id") or ""),
            file_name=file_name,
            status=str(payload.get("status", "pending")),
            raw=payload,
        )

    async def list_documents(self, user_id: str) -> Sequence[DocumentSummary]:
        """拉取文档列表。"""
        payload = await self._request(
            "GET",
            self._tenant_url(user_id, "documents/paginated"),
            params={"page": 1, "page_size": 200},
        )
        items = payload.get("items") or payload.get("documents") or []
        return [
            DocumentSummary(
                doc_id=str(item.get("id") or item.get("doc_id") or ""),
                file_name=str(item.get("file_path") or item.get("file_name") or ""),
                status=str(item.get("status", "")),
            )
            for item in items
        ]

    async def delete_document(self, user_id: str, doc_id: str) -> None:
        """删除指定文档。"""
        await self._request(
            "DELETE",
            self._tenant_url(user_id, "documents/delete_document"),
            json={"doc_ids": [doc_id]},
        )

    async def wait_until_indexed(
        self,
        user_id: str,
        doc_id: str,
        timeout_seconds: float,
        poll_interval: float,
    ) -> str:
        """轮询文档状态直到完成。

        用轮询加超时而不是固定 sleep：索引耗时随文件大小变化，
        写死等待时间要么浪费要么不够。
        """
        deadline = asyncio.get_running_loop().time() + timeout_seconds
        while True:
            documents = await self.list_documents(user_id)
            current = next((d for d in documents if d.doc_id == doc_id), None)
            if current is not None:
                lowered = current.status.lower()
                if lowered in INDEXED_STATES:
                    return current.status
                if lowered in FAILED_STATES:
                    raise RuntimeError(
                        f"文档 {doc_id} 处理失败，状态 {current.status}"
                    )
            if asyncio.get_running_loop().time() >= deadline:
                raise TimeoutError(
                    f"文档 {doc_id} 在 {timeout_seconds} 秒内未完成索引"
                )
            await asyncio.sleep(poll_interval)

    async def query(
        self, user_id: str, question: str, mode: str = "naive"
    ) -> QueryAnswer:
        """以指定模式提问，同时收集回答与参考上下文。"""
        payload = await self._request(
            "POST",
            self._tenant_url(user_id, "query"),
            json={"query": question, "mode": mode, "include_references": True},
        )
        contexts: list[str] = []
        for reference in payload.get("references") or []:
            if isinstance(reference, dict):
                for key in ("content", "text", "snippet"):
                    value = reference.get(key)
                    if isinstance(value, str):
                        contexts.append(value)
                        break
        return QueryAnswer(
            content=str(payload.get("response") or ""), contexts=tuple(contexts)
        )


class PostgresInspector:
    """直连 PostgreSQL 核对 workspace 隔离。"""

    def __init__(self, dsn: str | None = None) -> None:
        self._dsn = dsn or os.environ.get("LR_PG_DSN") or ""

    async def _fetch(self, sql: str, *params: Any) -> list[tuple]:
        """执行查询并返回全部行；缺 psycopg 时给可读提示。"""
        try:
            import psycopg
        except ImportError as error:
            raise MissingDependencyError("psycopg[binary]", "PostgreSQL 巡检") from error

        async with await psycopg.AsyncConnection.connect(self._dsn) as connection:
            async with connection.cursor() as cursor:
                await cursor.execute(sql, params)
                return await cursor.fetchall()

    async def workspace_counts(self, table: str) -> dict[str, int]:
        """按 workspace 统计行数。"""
        rows = await self._fetch(
            f"SELECT workspace, COUNT(*) FROM {table} GROUP BY workspace"
        )
        return {str(row[0]): int(row[1]) for row in rows}

    async def distinct_workspaces(self, table: str) -> Sequence[str]:
        """列出出现过的 workspace。"""
        rows = await self._fetch(f"SELECT DISTINCT workspace FROM {table}")
        return [str(row[0]) for row in rows]

    async def rows_for_workspace(self, table: str, workspace: str) -> int:
        """统计指定 workspace 的行数。"""
        rows = await self._fetch(
            f"SELECT COUNT(*) FROM {table} WHERE workspace = %s", workspace
        )
        return int(rows[0][0]) if rows else 0


class Neo4jLabelInspector:
    """直连 Neo4j 核对图谱 Label 隔离。"""

    def __init__(
        self,
        uri: str | None = None,
        user: str | None = None,
        password: str | None = None,
    ) -> None:
        self._uri = uri or os.environ.get("NEO4J_URI", "")
        self._user = user or os.environ.get("NEO4J_USERNAME", "")
        self._password = password or os.environ.get("NEO4J_PASSWORD", "")

    async def _run(self, cypher: str) -> list[Any]:
        """执行一条 Cypher；缺 neo4j 时给可读提示。"""
        try:
            from neo4j import AsyncGraphDatabase
        except ImportError as error:
            raise MissingDependencyError("neo4j", "Neo4j 巡检") from error

        driver = AsyncGraphDatabase.driver(
            self._uri, auth=(self._user, self._password)
        )
        try:
            async with driver.session() as session:
                result = await session.run(cypher)
                return [record async for record in result]
        finally:
            await driver.close()

    async def labels(self) -> Sequence[str]:
        """列出全部节点 Label。"""
        records = await self._run("CALL db.labels() YIELD label RETURN label")
        return [str(record["label"]) for record in records]

    async def entity_ids(self, label: str) -> Sequence[str]:
        """列出指定 Label 下的实体标识。"""
        records = await self._run(
            f"MATCH (n:`{label}`) RETURN n.entity_id AS entity_id"
        )
        return [
            str(record["entity_id"]) for record in records if record["entity_id"]
        ]


def build_real_clients() -> tuple[HttpRagClient, PostgresInspector, Neo4jLabelInspector]:
    """按环境变量构造三个真实客户端。

    刻意不在这里连接：连接发生在第一次调用时，
    这样参数错误能立刻看到，而网络问题在对应场景里以 blocked 呈现。
    """
    base_url = os.environ.get("LR_BASE_URL", "http://127.0.0.1:9621")
    api_key = os.environ.get("LR_INTERNAL_API_KEY", "")
    return HttpRagClient(base_url, api_key), PostgresInspector(), Neo4jLabelInspector()
