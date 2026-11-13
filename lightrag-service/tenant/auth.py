"""内部鉴权与启动期安全校验。

租户路由只允许可信业务服务访问，因此整组路由共用一个内部密钥（对应官方
``X-API-Key``）。这里除了把密钥交给路由工厂，还负责在启动阶段拦下会让鉴权失效的配置。
"""

from __future__ import annotations

from typing import Iterable, Sequence

from tenant.settings import ServiceSettings


class SecurityConfigError(ValueError):
    """安全相关配置不可接受；必须拒绝启动，而不是降级运行。"""


def parse_whitelist(raw_value: str) -> list[tuple[str, bool]]:
    """把上游格式的白名单字符串解析成 ``(前缀, 是否前缀匹配)`` 列表。

    与上游编译规则保持一致：以 ``/*`` 结尾表示前缀匹配，其余为精确匹配。
    """
    patterns: list[tuple[str, bool]] = []
    for entry in raw_value.split(","):
        candidate = entry.strip()
        if not candidate:
            continue
        if candidate.endswith("/*"):
            patterns.append((candidate[:-2], True))
        else:
            patterns.append((candidate, False))
    return patterns


def _is_catch_all_pattern(prefix: str) -> bool:
    """判断上游白名单条目是否退化成全通配。

    上游把 ``/*`` 这类写法编译成空前缀加前缀匹配，空前缀对所有路径成立，
    等于关闭了所有路由的鉴权。
    """
    return prefix == ""


def validate_security_settings(
    settings: ServiceSettings,
    whitelist_patterns: Sequence[tuple[str, bool]] | None = None,
) -> None:
    """校验启动期安全前提，任一条不满足即抛异常。

    Args:
        settings: 运行期配置。
        whitelist_patterns: 已编译的白名单条目 ``(prefix, is_prefix_match)``；
            ``None`` 表示按 ``settings.whitelist_paths`` 自行解析。

    Raises:
        SecurityConfigError: 监听非回环且缺少内部密钥，或白名单会绕过租户鉴权。
    """
    problems: list[str] = []

    if not settings.binds_loopback_only and not settings.internal_api_key:
        problems.append(
            f"监听地址 {settings.host!r} 不是回环地址，必须先配置 LR_INTERNAL_API_KEY，"
            "否则租户路由没有可用的访问凭证"
        )

    patterns = (
        parse_whitelist(settings.whitelist_paths)
        if whitelist_patterns is None
        else whitelist_patterns
    )
    for prefix, is_prefix_match in patterns:
        if is_prefix_match and _is_catch_all_pattern(prefix):
            problems.append(
                "WHITELIST_PATHS 配置为全通配（如 /*），会让 /tenant/* 路由绕过租户鉴权"
            )
            break

    if problems:
        raise SecurityConfigError(_format(problems))


def _format(problems: Iterable[str]) -> str:
    """把多条安全问题格式化为多行信息。"""
    lines = ["服务安全配置校验失败："]
    lines.extend(f"  - {problem}" for problem in problems)
    return "\n".join(lines)
