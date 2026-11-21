"""按已绑定的租户访问提取文本和上传原件。"""
import hmac
import re
from pathlib import Path, PurePosixPath
from urllib.parse import quote
from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import FileResponse, Response
from lightrag.constants import PARSED_DIR_NAME
from lightrag.utils_pipeline import read_source_file_basename


def create_content_router(rag, api_key: str | None, doc_manager) -> APIRouter:
    """注册时固定租户目录和存储，文件路径由文档状态推导。"""
    router = APIRouter()

    def authorize(x_api_key: str | None = Header(default=None)) -> None:
        if api_key and (not x_api_key or not hmac.compare_digest(api_key, x_api_key)):
            raise HTTPException(status_code=401, detail='Invalid internal credential')
    async def find_status(doc_id: str):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,200}', doc_id):
            raise HTTPException(status_code=400, detail='Invalid document identifier')
        status = await rag.doc_status.get_by_id(doc_id)
        if not status:
            raise HTTPException(status_code=404, detail='Document not found')
        return status

    async def read_text(doc_id: str) -> str:
        document = await rag.full_docs.get_by_id(doc_id)
        if not document or not isinstance(document.get('content'), str):
            raise HTTPException(status_code=404, detail='Document content unavailable')
        return document['content']

    @router.get('/documents/{doc_id}/content', dependencies=[Depends(authorize)])
    async def content(doc_id: str):
        status = await find_status(doc_id)
        filename = PurePosixPath(str(status.get('file_path') or 'document.txt').replace('\\', '/')).name
        return {'docId': doc_id, 'fileName': filename, 'content': await read_text(doc_id)}

    @router.get('/documents/{doc_id}/original', dependencies=[Depends(authorize)])
    async def original(doc_id: str):
        status = await find_status(doc_id)
        filename = PurePosixPath(str(status.get('file_path') or 'document.txt').replace('\\', '/')).name
        source_name = read_source_file_basename(status.get('metadata') or {})
        if source_name:
            source = find_original(Path(doc_manager.input_dir), source_name)
            return FileResponse(source, filename=filename, media_type='application/octet-stream')

        # 直接录入的文本没有物理文件，存储中的 UTF-8 内容就是原件。
        if Path(filename).suffix.lower() in {'.pdf', '.docx', '.xlsx', '.pptx'}:
            raise HTTPException(status_code=404, detail='Original file unavailable')
        text = await read_text(doc_id)
        return Response(text.encode('utf-8'), media_type='application/octet-stream', headers={
            'Content-Disposition': f"attachment; filename*=UTF-8''{quote(filename, safe='')}",
        })

    return router


def find_original(input_dir: Path, filename: str) -> Path:
    """查找上游保存的原件，处理完成后原件会移入解析归档目录。"""
    root = input_dir.resolve()
    for directory in (root, root / PARSED_DIR_NAME):
        # 解析工作线程可能在请求期间归档原件，先查输入目录再查归档目录。
        candidate = (directory / filename).resolve()
        if candidate.is_relative_to(root) and candidate.is_file():
            return candidate
    raise HTTPException(status_code=404, detail='Original file unavailable')
