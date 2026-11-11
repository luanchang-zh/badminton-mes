"""The content extension must not widen tenant access or return untrusted paths."""
from types import SimpleNamespace
from unittest.mock import AsyncMock
from fastapi import FastAPI
from fastapi.testclient import TestClient
from tenant.documents import create_content_router


def application():
    app = FastAPI()
    status = AsyncMock()
    status.get_by_id.side_effect = lambda key: {'file_path': '../资料.md'} if key == 'doc-a' else None
    docs = AsyncMock()
    docs.get_by_id.side_effect = lambda key: {'content': '工艺资料\n原文'} if key == 'doc-a' else None
    first = SimpleNamespace(doc_status=status, full_docs=docs)
    empty = SimpleNamespace(doc_status=AsyncMock(), full_docs=AsyncMock())
    empty.doc_status.get_by_id.return_value = None
    empty.full_docs.get_by_id.return_value = None
    app.include_router(create_content_router(first, 'test-internal'), prefix='/tenant/a')
    app.include_router(create_content_router(empty, 'test-internal'), prefix='/tenant/b')
    return TestClient(app), docs


def test_original_text_requires_internal_credential():
    client, docs = application()
    assert client.get('/tenant/a/documents/doc-a/content').status_code == 401
    docs.get_by_id.assert_not_awaited()


def test_content_is_exact_and_file_name_does_not_expose_storage_path():
    client, _ = application()
    result = client.get('/tenant/a/documents/doc-a/content', headers={'X-API-Key': 'test-internal'})
    assert result.status_code == 200
    assert result.json() == {'docId': 'doc-a', 'fileName': '资料.md', 'content': '工艺资料\n原文'}


def test_same_document_id_cannot_read_another_tenants_storage():
    client, _ = application()
    response = client.get('/tenant/b/documents/doc-a/content', headers={'X-API-Key': 'test-internal'})
    assert response.status_code == 404
