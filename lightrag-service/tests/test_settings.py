"""settings 模块单测：环境变量读取、缺省值与非法值处理。"""

from __future__ import annotations

from pathlib import Path

import pytest

from tenant.settings import DEFAULT_INPUT_DIR, DEFAULT_WORKING_DIR, ServiceSettings


def test_defaults_are_used_when_env_empty(tmp_path: Path) -> None:
    """未配置环境变量时使用默认存储组合，且模型参数为空。"""
    settings = ServiceSettings.from_env({"LR_INPUT_DIR": str(tmp_path / "in")})

    assert settings.input_base_dir == (tmp_path / "in")
    assert settings.kv_storage == "PGKVStorage"
    assert settings.vector_storage == "PGVectorStorage"
    assert settings.doc_status_storage == "PGDocStatusStorage"
    assert settings.graph_storage == "Neo4JStorage"
    assert settings.llm_model == ""
    assert settings.embedding_dim is None


def test_relative_dir_is_resolved_against_service_root() -> None:
    """相对目录以服务目录为基准解析，避免依赖启动时的工作目录。"""
    settings = ServiceSettings.from_env({"LR_INPUT_DIR": DEFAULT_INPUT_DIR})
    service_root = Path(__file__).resolve().parents[1]

    assert settings.input_base_dir == (service_root / DEFAULT_INPUT_DIR).resolve()
    assert settings.working_base_dir == (
        service_root / DEFAULT_WORKING_DIR
    ).resolve()


def test_blank_values_fall_back_to_defaults() -> None:
    """空白字符串按未配置处理，不会产生空 storage 名或空模型名。"""
    settings = ServiceSettings.from_env(
        {"LR_KV_STORAGE": "  ", "LR_LLM_MODEL": "   ", "LR_EMBEDDING_DIM": " "}
    )

    assert settings.kv_storage == "PGKVStorage"
    assert settings.llm_model == ""
    assert settings.embedding_dim is None


def test_non_numeric_value_raises() -> None:
    """非数字的容量配置必须立刻报错，而不是悄悄退化为默认值。"""
    with pytest.raises(ValueError, match="LR_MAX_PENDING_DOCUMENTS"):
        ServiceSettings.from_env({"LR_MAX_PENDING_DOCUMENTS": "many"})


def test_missing_model_settings_reports_all_gaps() -> None:
    """模型未配置时列出全部缺失项，供自检提示使用。"""
    settings = ServiceSettings.from_env({})

    assert settings.missing_model_settings() == [
        "LR_LLM_MODEL",
        "LR_EMBEDDING_MODEL",
        "LR_EMBEDDING_DIM",
    ]


def test_missing_model_settings_is_empty_when_configured() -> None:
    """模型参数齐全时不再报告缺失项。"""
    settings = ServiceSettings.from_env(
        {
            "LR_LLM_MODEL": "gpt-4o-mini",
            "LR_EMBEDDING_MODEL": "text-embedding-3-small",
            "LR_EMBEDDING_DIM": "1536",
        }
    )

    assert settings.missing_model_settings() == []
    assert settings.embedding_dim == 1536
