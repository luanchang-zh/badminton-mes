"""应用构建：把每个租户的官方路由挂到自己的前缀下。

这里只负责「构建」，不负责启动。把构建与启动分开的好处是：``--print-routes``
可以在完全不连接数据库、不调用模型的前提下打印出真实路由表，便于验收与排错。

路由只在构建阶段注册一次。上游的三个路由工厂每次调用都会返回全新的 router，
因此可以安全地为每个租户各调用一次并挂到不同前缀下。
"""

from __future__ import annotations

from typing import Any, Iterator, Sequence

from tenant.lifespan import build_lifespan

# 三个工厂产生的路由条数，用于自检与回归断言；与 v1.5.7 对齐。
DOCUMENT_ROUTE_COUNT = 18
QUERY_ROUTE_COUNT = 3
GRAPH_ROUTE_COUNT = 12
ROUTES_PER_TENANT = DOCUMENT_ROUTE_COUNT + QUERY_ROUTE_COUNT + GRAPH_ROUTE_COUNT

# 不允许以「未加租户前缀」形式出现的路由：一旦出现就说明注册方式错了。
UNPREFIXED_PATHS = ("/documents/upload", "/query", "/graphs")

TENANT_PREFIX_TEMPLATE = "/tenant/{user_id}"


def _import_route_factories() -> tuple[Any, Any, Any]:
    """延迟导入官方路由工厂。

    这些模块会触发上游 argparse 解析，因此调用方必须先固定 ``sys.argv``
    并完成上游配置初始化，再执行本函数。
    """
    from lightrag.api.routers.document_routes import create_document_routes
    from lightrag.api.routers.graph_routes import create_graph_routes
    from lightrag.api.routers.query_routes import create_query_routes

    return create_document_routes, create_query_routes, create_graph_routes


def register_tenant_routes(
    app: Any,
    contexts: Sequence[Any],
    api_key: str | None,
    top_k: int,
) -> None:
    """把每个租户的三组路由注册到 ``/tenant/{user_id}`` 前缀下。

    每个租户各调用一次工厂，因此三组 router 天然互不共享；
    标签前置 user_id，使 Swagger 中能按租户分组查看。
    """
    create_document_routes, create_query_routes, create_graph_routes = (
        _import_route_factories()
    )

    for context in contexts:
        prefix = f"/tenant/{context.user_id}"
        app.include_router(
            create_document_routes(context.rag, context.doc_manager, api_key),
            prefix=prefix,
            tags=[f"{context.user_id}-documents"],
        )
        app.include_router(
            create_query_routes(context.rag, api_key, top_k),
            prefix=prefix,
            tags=[f"{context.user_id}-query"],
        )
        app.include_router(
            create_graph_routes(context.rag, api_key),
            prefix=prefix,
            tags=[f"{context.user_id}-graph"],
        )


def add_health_route(app: Any) -> None:
    """注册不参与租户隔离的健康检查。

    健康检查只回答「进程是否活着」，不触及任何租户数据，
    因此放在前缀之外，也不要求内部密钥。
    """
    @app.get("/health", include_in_schema=False)
    async def health() -> dict[str, str]:
        return {"status": "healthy"}


def build_app(
    contexts: Sequence[Any],
    settings: Any,
    title: str = "LightRAG Multi-Tenant Server",
) -> Any:
    """构建 FastAPI 应用。

    Args:
        contexts: 已装配的租户上下文列表。
        settings: 运行期配置，提供密钥、top_k、文档开关与监听信息。
        title: OpenAPI 标题。

    Returns:
        已注册全部租户路由的 FastAPI 实例；构建过程不建立任何外部连接。
    """
    from fastapi import FastAPI

    app = FastAPI(
        title=title,
        lifespan=build_lifespan(list(contexts)),
        docs_url="/docs" if settings.enable_api_docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if settings.enable_api_docs else None,
    )

    register_tenant_routes(app, contexts, settings.internal_api_key, settings.top_k)
    add_health_route(app)
    return app


def collect_tenant_routes(app: Any) -> dict[str, list[dict[str, Any]]]:
    """按租户前缀归集路由，供 ``--print-routes`` 与单测使用。

    Returns:
        ``{user_id: [{"path": ..., "methods": [...]}, ...]}``，按路由表顺序。
    """
    grouped: dict[str, list[dict[str, Any]]] = {}
    for path, methods in _iter_route_entries(app.routes):
        if not path.startswith("/tenant/"):
            continue
        # /tenant/<user_id>/... → user_id
        segments = path.split("/")
        if len(segments) < 3:
            continue
        user_id = segments[2]
        grouped.setdefault(user_id, []).append(
            {"path": path, "methods": sorted(methods)}
        )
    return grouped


def find_unprefixed_routes(app: Any, targets: Sequence[str] = UNPREFIXED_PATHS) -> list[str]:
    """返回以未加租户前缀形式出现的目标路由路径。"""
    return [path for path, _ in _iter_route_entries(app.routes) if path in targets]


def _iter_route_entries(routes: Sequence[Any]) -> Iterator[tuple[str, set[str]]]:
    """递归展开路由表，产出 ``(完整路径, HTTP 方法集合)``。

    新版 FastAPI 会把 ``include_router`` 的结果保留为包装节点，
    真实路径与已拼接的前缀要到 ``effective_route_contexts()`` 里才可见，
    因此这里必须递归展开，不能只读顶层 ``route.path``。
    """
    for route in routes:
        inner = getattr(route, "effective_route_contexts", None)
        if callable(inner):
            for context in inner():
                yield from _iter_route_entries([_context_route(context)])
            continue

        path = getattr(route, "path", None)
        if path:
            yield path, set(getattr(route, "methods", []) or [])


def _context_route(context: Any) -> Any:
    """从路由上下文中取出实际的路由对象，兼容不同字段名。"""
    for attribute in ("route", "child_route", "context_route"):
        candidate = getattr(context, attribute, None)
        if candidate is not None:
            return candidate
    return context
