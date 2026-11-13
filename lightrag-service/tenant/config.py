"""租户配置：把 config/tenants.yaml 解析为可装配的租户列表。

配置只描述"哪个业务用户对应哪个 workspace"，其余全部推导：
上传目录与工作目录一律为 ``<base_dir>/<workspace>``，与官方 DocumentManager
的目录规则保持一致，避免两套命名并存导致隔离失效。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import yaml

from tenant.settings import ServiceSettings

# 服务目录（lightrag-service/），默认配置文件位于其下的 config/。
SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TENANTS_FILE = SERVICE_ROOT / "config" / "tenants.yaml"


class TenantConfigError(ValueError):
    """配置无法读取或内容不合法；调用方应终止启动而不是带着错误配置继续。"""


@dataclass(frozen=True)
class TenantConfig:
    """单个租户的不可变配置。

    user_id 是业务系统的可信用户标识，workspace 是 LightRAG 的数据隔离键；
    两者都在启动阶段固定，不接受运行期由前端指定。
    """

    user_id: str
    workspace: str
    input_dir: Path
    working_dir: Path


def _require_text(entry: Mapping[str, Any], field_name: str, index: int) -> str:
    """取出必填字符串字段；缺失、空白或非字符串都视为配置错误。"""
    raw_value = entry.get(field_name)
    if not isinstance(raw_value, str) or not raw_value.strip():
        raise TenantConfigError(
            f"第 {index + 1} 个租户缺少有效的 {field_name}（当前值：{raw_value!r}）"
        )
    return raw_value.strip()


def _require_workspace(entry: Mapping[str, Any], index: int) -> str:
    """取出 workspace 原始值。

    这里只校验类型：空值与命名规则属于隔离语义，统一交给校验器判定，
    这样"为什么不能为空"的解释只存在于一个地方。
    """
    raw_value = entry.get("workspace")
    if not isinstance(raw_value, str):
        raise TenantConfigError(
            f"第 {index + 1} 个租户缺少字符串类型的 workspace（当前值：{raw_value!r}）"
        )
    return raw_value.strip()


def _read_entries(document: Any) -> list[Any]:
    """取出 tenants 列表；顶层结构与类型不对时给出定位明确的原因。"""
    if not isinstance(document, dict):
        raise TenantConfigError("配置根节点必须是映射，且包含 tenants 列表")
    entries = document.get("tenants")
    if not isinstance(entries, list):
        raise TenantConfigError("配置缺少 tenants 列表，或 tenants 不是列表")
    return entries


def _build_tenant(
    entry: Any, index: int, settings: ServiceSettings
) -> TenantConfig:
    """把单条配置转换为 TenantConfig，并按 workspace 推导目录。"""
    if not isinstance(entry, dict):
        raise TenantConfigError(f"第 {index + 1} 个租户必须是映射，当前值：{entry!r}")

    user_id = _require_text(entry, "user_id", index)
    workspace = _require_workspace(entry, index)
    return TenantConfig(
        user_id=user_id,
        workspace=workspace,
        input_dir=settings.input_base_dir / workspace,
        working_dir=settings.working_base_dir / workspace,
    )


def load_tenants(
    config_path: Path | str | None = None,
    settings: ServiceSettings | None = None,
) -> list[TenantConfig]:
    """读取租户配置并推导各租户目录。

    Args:
        config_path: 配置文件路径；缺省使用 ``config/tenants.yaml``。
        settings: 提供基目录的运行期配置；缺省从环境变量读取。

    Returns:
        按配置文件顺序排列的租户配置列表，可能为空（是否允许为空由校验器决定）。

    Raises:
        TenantConfigError: 文件不存在、YAML 解析失败或字段缺失。
    """
    resolved_settings = settings or ServiceSettings.from_env()
    resolved_path = Path(config_path) if config_path else DEFAULT_TENANTS_FILE

    if not resolved_path.is_file():
        raise TenantConfigError(f"租户配置文件不存在：{resolved_path}")
    try:
        document = yaml.safe_load(resolved_path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise TenantConfigError(
            f"租户配置文件解析失败：{resolved_path}：{error}"
        ) from error

    return [
        _build_tenant(entry, index, resolved_settings)
        for index, entry in enumerate(_read_entries(document))
    ]
