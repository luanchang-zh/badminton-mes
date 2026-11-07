"""启动前的隔离校验。

原则是"配置错误即拒绝启动"：跨用户串数据一旦发生，事后很难发现，也难以
反推是哪一层配置写错了。因此所有会破坏隔离前提的配置都在启动阶段被拦下，
并一次性给出可直接定位的原因。
"""

from __future__ import annotations

import os
import re
from collections.abc import Iterable, Mapping, Sequence
from pathlib import Path

from tenant.config import TenantConfig, TenantConfigError

# workspace 是 LightRAG 的唯一隔离键，同时会拼进文件目录。
# 空值等于全局命名空间，路径分隔符会造成越界写入，因此只允许白名单字符。
WORKSPACE_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")

# 这些环境变量会覆盖实例级 workspace：
# - POSTGRES_WORKSPACE 在 PostgreSQLDB 初始化时优先级最高，直接改写存储实例的 workspace；
# - NEO4J_WORKSPACE 在 Neo4j 存储构造函数里覆盖入参；
# - WORKSPACE 不覆盖显式传参，但会形成"两处配置同一个值"的隐患，一并禁止。
FORBIDDEN_WORKSPACE_ENV_VARS = ("POSTGRES_WORKSPACE", "NEO4J_WORKSPACE", "WORKSPACE")


def _normalize_path(path: Path) -> str:
    """把路径转成可比较的规范形式；Windows 下统一大小写与分隔符。"""
    return os.path.normcase(str(path))


def _describe(tenant: TenantConfig) -> str:
    """生成用于错误信息的租户标识，便于直接定位到配置条目。"""
    return f"{tenant.user_id}（workspace={tenant.workspace!r}）"


def _check_duplicates(
    values: Sequence[str], label: str, tenants: Sequence[TenantConfig]
) -> list[str]:
    """找出重复的取值，并在错误信息里带上对应租户，方便一次改完。"""
    seen: dict[str, TenantConfig] = {}
    problems: list[str] = []
    for value, tenant in zip(values, tenants):
        first = seen.get(value)
        if first is None:
            seen[value] = tenant
            continue
        problems.append(
            f"{label} 重复：{value!r} 同时被 {_describe(first)} 与 {_describe(tenant)} 使用"
        )
    return problems


def _check_workspace_names(tenants: Sequence[TenantConfig]) -> list[str]:
    """校验 workspace 的命名约束；空值与非法字符都会被拒绝。"""
    problems: list[str] = []
    for tenant in tenants:
        workspace = tenant.workspace
        if not workspace:
            problems.append(f"{_describe(tenant)} 的 workspace 为空；空值等于全局命名空间")
        elif "/" in workspace or "\\" in workspace:
            problems.append(f"{_describe(tenant)} 的 workspace 含路径分隔符：{workspace!r}")
        elif not WORKSPACE_PATTERN.match(workspace):
            problems.append(
                f"{_describe(tenant)} 的 workspace 含非法字符：{workspace!r}"
                "（只允许字母、数字与下划线）"
            )
    return problems


def _iter_tenant_dirs(
    tenants: Sequence[TenantConfig],
) -> Iterable[tuple[TenantConfig, str, Path]]:
    """展开每个租户的上传目录与工作目录，附带用途标签。"""
    for tenant in tenants:
        yield tenant, "上传目录", tenant.input_dir
        yield tenant, "工作目录", tenant.working_dir


def _looks_nested(candidate: str, other: str) -> bool:
    """判断 candidate 是否等于 other 或位于 other 之下（按路径段比较）。"""
    return candidate == other or candidate.startswith(other + os.sep)


def _check_directory_overlap(tenants: Sequence[TenantConfig]) -> list[str]:
    """确保所有推导目录互不重叠：目录相同或互为父子都会造成文件互相覆盖。"""
    entries = [
        (tenant, label, _normalize_path(path))
        for tenant, label, path in _iter_tenant_dirs(tenants)
    ]
    problems: list[str] = []
    for index, (first_tenant, first_label, first_path) in enumerate(entries):
        for second_tenant, second_label, second_path in entries[index + 1 :]:
            if _looks_nested(first_path, second_path) or _looks_nested(
                second_path, first_path
            ):
                problems.append(
                    f"目录冲突：{_describe(first_tenant)} 的{first_label} {first_path} "
                    f"与 {_describe(second_tenant)} 的{second_label} {second_path} "
                    "相同或互相包含"
                )
    return problems


def _check_forbidden_env(environ: Mapping[str, str]) -> list[str]:
    """拒绝会覆盖实例级 workspace 的全局环境变量。"""
    problems: list[str] = []
    for name in FORBIDDEN_WORKSPACE_ENV_VARS:
        if (environ.get(name) or "").strip():
            problems.append(
                f"禁止设置环境变量 {name}：它会覆盖实例级 workspace，破坏租户隔离"
            )
    return problems


def _format_problems(problems: Sequence[str]) -> str:
    """把多条问题格式化为多行信息，避免只暴露第一条导致反复试错。"""
    lines = ["租户隔离配置校验失败："]
    lines.extend(f"  - {problem}" for problem in problems)
    return "\n".join(lines)


def validate_tenants(
    tenants: Sequence[TenantConfig],
    environ: Mapping[str, str] | None = None,
) -> None:
    """执行全部隔离校验，任一项失败即抛异常，且一次性列出所有问题。

    Args:
        tenants: 已从配置文件解析出的租户列表。
        environ: 环境变量快照；缺省读取当前进程环境。

    Raises:
        TenantConfigError: 存在任意一项隔离风险；信息中包含全部原因。
    """
    problems: list[str] = []
    if not tenants:
        problems.append("租户列表为空：没有任何租户可装配")
    else:
        problems.extend(_check_duplicates([t.user_id for t in tenants], "user_id", tenants))
        problems.extend(
            _check_duplicates([t.workspace for t in tenants], "workspace", tenants)
        )
        problems.extend(_check_workspace_names(tenants))
        problems.extend(_check_directory_overlap(tenants))

    problems.extend(_check_forbidden_env(os.environ if environ is None else environ))

    if problems:
        raise TenantConfigError(_format_problems(problems))
