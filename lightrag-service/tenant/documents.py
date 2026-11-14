"""Tenant-bound access to the exact text stored by LightRAG."""
import hmac
import re
from pathlib import PurePosixPath
from fastapi import APIRouter, Header, HTTPException


def create_content_router(rag, api_key: str | None) -> APIRouter:
    """Bind storage at registration time; never accept a workspace from callers."""
    router = APIRouter()

    @router.get('/documents/{doc_id}/content')
    async def content(doc_id: str, x_api_key: str | None = Header(default=None)):
        if api_key and (not x_api_key or not hmac.compare_digest(api_key, x_api_key)):
            raise HTTPException(status_code=401, detail='Invalid internal credential')
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,200}', doc_id):
            raise HTTPException(status_code=400, detail='Invalid document identifier')
        status = await rag.doc_status.get_by_id(doc_id)
        document = await rag.full_docs.get_by_id(doc_id)
        if not status or not document or not isinstance(document.get('content'), str):
            raise HTTPException(status_code=404, detail='Document content unavailable')
        filename = PurePosixPath(str(status.get('file_path') or 'document.txt').replace('\\', '/')).name
        return {'docId': doc_id, 'fileName': filename, 'content': document['content']}

    return router
