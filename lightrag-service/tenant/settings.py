"""多租户服务的运行期配置。

模型、存储与目录等外部依赖全部通过环境变量注入，vendored 源码里不写死任何接入参数，
同一份源码即可在不同环境复用。模型参数允许缺省：P1 阶段只做配置与装配自检，
真实模型接入留到后续阶段。
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

DEFAULT_INPUT_DIR = "./data/inputs"
DEFAULT_WORKING_DIR = "./data/rag_storage"

DEFAULT_KV_STORAGE = "PGKVStorage"
DEFAULT_VECTOR_STORAGE = "PGVectorStorage"
DEFAULT_DOC_STATUS_STORAGE = "PGDocStatusStorage"
DEFAULT_GRAPH_STORAGE = "Neo4JStorage"

# 服务目录：lightrag-service/，用于把相对路径解析成绝对路径。
SERVICE_ROOT = Path(__file__).resolve().parents[1]


def _read_text(environ: Mapping[str, str], name: str, default: str) -> str:
    """读取字符串配置；空白值按未设置处理，避免出现"看起来配了其实是空"的情况。"""
    value = (environ.get(name) or "").strip()
    return value or default


def _read_int(environ: Mapping[str, str], name: str, default: int) -> int:
    """读取整数配置，非数字时直接报错，避免带着错误配置进入后续阶段。"""
    raw_value = (environ.get(name) or "").strip()
    if not raw_value:
        return default
    try:
        return int(raw_value)
    except ValueError as error:
        raise ValueError(f"环境变量 {name} 必须是整数，当前值：{raw_value!r}") from error


def _read_path(environ: Mapping[str, str], name: str, default: str) -> Path:
    """读取目录配置并解析为绝对路径，相对路径以服务目录为基准。"""
    candidate = Path(_read_text(environ, name, default)).expanduser()
    if not candidate.is_absolute():
        candidate = SERVICE_ROOT / candidate
    return candidate.resolve()


@dataclass(frozen=True)
class ServiceSettings:
    """多租户服务的全部可配置项。

    input_base_dir 与 working_base_dir 是各租户共享的基目录，实际目录按
    ``<base_dir>/<workspace>`` 推导，与官方 DocumentManager 的规则保持一致。
    """

    input_base_dir: Path
    working_base_dir: Path
    kv_storage: str
    vector_storage: str
    graph_storage: str
    doc_status_storage: str
    llm_model: str
    llm_base_url: str | None
    llm_api_key: str | None
    llm_model_max_async: int
    embedding_model: str
    embedding_base_url: str | None
    embedding_api_key: str | None
    embedding_dim: int | None
    embedding_max_token_size: int
    top_k: int
    max_pending_documents: int
    max_parallel_insert: int

    @classmethod
    def from_env(cls, environ: Mapping[str, str] | None = None) -> "ServiceSettings":
        """从环境变量构造配置；缺省值面向本地开发，不包含任何密钥。"""
        source = os.environ if environ is None else environ
        embedding_dim = _read_int(source, "LR_EMBEDDING_DIM", 0)
        return cls(
            input_base_dir=_read_path(source, "LR_INPUT_DIR", DEFAULT_INPUT_DIR),
            working_base_dir=_read_path(
                source, "LR_WORKING_DIR", DEFAULT_WORKING_DIR
            ),
            kv_storage=_read_text(source, "LR_KV_STORAGE", DEFAULT_KV_STORAGE),
            vector_storage=_read_text(
                source, "LR_VECTOR_STORAGE", DEFAULT_VECTOR_STORAGE
            ),
            graph_storage=_read_text(
                source, "LR_GRAPH_STORAGE", DEFAULT_GRAPH_STORAGE
            ),
            doc_status_storage=_read_text(
                source, "LR_DOC_STATUS_STORAGE", DEFAULT_DOC_STATUS_STORAGE
            ),
            llm_model=_read_text(source, "LR_LLM_MODEL", ""),
            llm_base_url=_read_text(source, "LR_LLM_BASE_URL", "") or None,
            llm_api_key=_read_text(source, "LR_LLM_API_KEY", "") or None,
            llm_model_max_async=_read_int(source, "LR_LLM_MAX_ASYNC", 4),
            embedding_model=_read_text(source, "LR_EMBEDDING_MODEL", ""),
            embedding_base_url=_read_text(source, "LR_EMBEDDING_BASE_URL", "") or None,
            embedding_api_key=_read_text(source, "LR_EMBEDDING_API_KEY", "") or None,
            embedding_dim=embedding_dim or None,
            embedding_max_token_size=_read_int(
                source, "LR_EMBEDDING_MAX_TOKEN_SIZE", 8192
            ),
            top_k=_read_int(source, "LR_TOP_K", 60),
            max_pending_documents=_read_int(source, "LR_MAX_PENDING_DOCUMENTS", 0),
            max_parallel_insert=_read_int(source, "LR_MAX_PARALLEL_INSERT", 2),
        )

    def missing_model_settings(self) -> list[str]:
        """返回尚未配置的模型参数，供自检输出提醒；空列表表示模型接齐了。"""
        missing: list[str] = []
        if not self.llm_model:
            missing.append("LR_LLM_MODEL")
        if not self.embedding_model:
            missing.append("LR_EMBEDDING_MODEL")
        if self.embedding_dim is None:
            missing.append("LR_EMBEDDING_DIM")
        return missing
