"""Configured dimensions must replace the upstream decorator's 1536 default."""
from types import SimpleNamespace
import numpy as np
import pytest
from tenant.factory import build_embedding_func, reset_shared_limiter
from tenant.settings import ServiceSettings


@pytest.mark.asyncio
async def test_configured_embedding_dimensions_replace_nested_default_validation(monkeypatch):
    import lightrag.llm.openai as provider
    observed = {}

    async def embedding(texts, **kwargs):
        observed.update(kwargs)
        return np.zeros((len(texts), 1024))

    monkeypatch.setattr(provider, 'openai_embed', SimpleNamespace(func=embedding))
    reset_shared_limiter()
    settings = ServiceSettings.from_env({'LR_EMBEDDING_MODEL': 'configured-model', 'LR_EMBEDDING_DIM': '1024'})
    result = await build_embedding_func(settings)(['测试正文'])
    assert result.shape == (1, 1024)
    assert observed['model'] == 'configured-model'
    assert observed['max_token_size'] == settings.embedding_max_token_size
    reset_shared_limiter()
