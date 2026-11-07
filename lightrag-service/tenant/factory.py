"""租户实例的构造入口。

LightRAG 及其依赖体积较大，且 P1 的自检与单测不连接数据库、不调用模型，
因此这里统一做延迟导入：只有真正需要装配实例时才加载官方包，其余场景保持零副作用。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Callable

from tenant.settings import ServiceSettings

LlmFunc = Callable[..., Any]


def build_llm_func(settings: ServiceSettings) -> LlmFunc:
    """构造 OpenAI 兼容的 LLM 调用函数。

    未配置模型时直接报错：装配真实实例必须要有模型，静默使用默认值会让后续阶段
    把"配置缺失"误判成"模型不可用"。
    """
    if not settings.llm_model:
        raise ValueError("缺少 LR_LLM_MODEL，无法构造 LLM 调用函数；请先补齐模型配置")

    from functools import partial

    from lightrag.llm.openai import openai_complete_if_cache

    return partial(
        openai_complete_if_cache,
        settings.llm_model,
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
    )


def build_embedding_func(settings: ServiceSettings) -> Any:
    """构造 EmbeddingFunc；维度必须显式配置，否则向量表无法确定列宽。"""
    if not settings.embedding_model:
        raise ValueError(
            "缺少 LR_EMBEDDING_MODEL，无法构造 Embedding 调用函数；请先补齐模型配置"
        )
    if settings.embedding_dim is None:
        raise ValueError(
            "缺少 LR_EMBEDDING_DIM，无法确定向量维度；更换模型后该值不可随意修改"
        )

    from functools import partial

    from lightrag.llm.openai import openai_embed
    from lightrag.utils import EmbeddingFunc

    embedding_callable = partial(
        openai_embed,
        model=settings.embedding_model,
        base_url=settings.embedding_base_url,
        api_key=settings.embedding_api_key,
    )
    return EmbeddingFunc(
        embedding_dim=settings.embedding_dim,
        max_token_size=settings.embedding_max_token_size,
        func=embedding_callable,
    )


def build_doc_manager(input_base_dir: Path, workspace: str) -> Any:
    """构造绑定到指定 workspace 的 DocumentManager。

    目录按官方规则由 ``input_base_dir / workspace`` 推导，这里只负责把 workspace
    显式传入，不依赖任何环境变量。
    """
    from lightrag.api.routers.document_routes import DocumentManager

    return DocumentManager(str(input_base_dir), workspace=workspace)


def build_rag(workspace: str, settings: ServiceSettings) -> Any:
    """按租户 workspace 构造独立的 LightRAG 实例。

    workspace 一律显式传参：即使宿主机设置了 WORKSPACE，也不会改变这里的隔离边界。
    构造阶段不建立数据库连接，连接发生在 initialize_storages()。
    """
    from lightrag import LightRAG

    return LightRAG(
        working_dir=str(settings.working_base_dir / workspace),
        workspace=workspace,
        llm_model_func=build_llm_func(settings),
        llm_model_name=settings.llm_model,
        llm_model_max_async=settings.llm_model_max_async,
        embedding_func=build_embedding_func(settings),
        kv_storage=settings.kv_storage,
        vector_storage=settings.vector_storage,
        graph_storage=settings.graph_storage,
        doc_status_storage=settings.doc_status_storage,
        max_pending_documents=settings.max_pending_documents,
        max_parallel_insert=settings.max_parallel_insert,
    )
