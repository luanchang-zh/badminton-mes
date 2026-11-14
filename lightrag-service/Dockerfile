FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 TIKTOKEN_CACHE_DIR=/opt/tiktoken
WORKDIR /app

COPY LightRAG ./LightRAG
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --timeout 120 --retries 8 './LightRAG[api]' 'asyncpg>=0.31,<1' 'pgvector>=0.4.2,<1' 'neo4j>=5,<7'

RUN python -c "import tiktoken; tiktoken.get_encoding('cl100k_base'); tiktoken.get_encoding('o200k_base')"

COPY tenant ./tenant
COPY config/tenants.yaml ./config/tenants.yaml

EXPOSE 9621
CMD ["python", "-m", "tenant.server", "--host", "0.0.0.0", "--port", "9621"]
