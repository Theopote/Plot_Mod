#!/usr/bin/env python3
"""Merge batch-6 lang keys: array errors, scale center, extend validation, offset warnings."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "status.plot.array.error.invalid_source": ("Invalid source shape", "无效的源图形"),
    "status.plot.array.error.invalid_base_point": ("Invalid base point", "无效的基准点"),
    "status.plot.array.error.invalid_path_points": ("Invalid path points", "无效的路径点"),
    "status.plot.array.error.clone_failed": ("Shape clone failed", "图形克隆失败"),
    "status.plot.array.error.insufficient_path_points": ("Insufficient path points", "路径点不足"),
    "status.plot.array.error.invalid_parameters": ("Invalid array parameters", "无效的阵列参数"),
    "status.plot.array.error.preview_failed": ("Preview calculation failed", "预览计算失败"),
    "status.plot.array.error.combined": ("%s: %s", "%s: %s"),
    "status.plot.array.invalid_type": ("Invalid array type", "阵列类型无效"),
    "status.plot.array.invalid_base_point_detail": ("Array base point is invalid", "阵列基准点无效"),
    "status.plot.array.path_zero_length": ("Path length is zero; cannot array", "路径长度为0，无法阵列"),
    "status.plot.array.clone_null": ("Shape clone returned null", "图形克隆返回null"),
    "status.plot.array.clone_failed_detail": ("Failed to clone shape", "克隆图形失败"),
    "mode.plot.scale.center.shape": ("Shape Center", "图形中心"),
    "mode.plot.scale.center.selection": ("Selection Center", "选择中心"),
    "mode.plot.scale.center.custom": ("Custom Point", "自定义点"),
    "status.plot.extend.point_required": ("Extend point is required", "延伸点不能为空"),
    "status.plot.extend.boundary_required": ("Boundary shape list is required", "边界图形列表不能为空"),
    "status.plot.extend.tolerance_positive": ("Extend tolerance must be greater than 0", "延伸容差必须大于0"),
    "status.plot.extend.tolerance_positive_value": (
        "Extend tolerance must be greater than 0, current: %s",
        "延伸容差必须大于0，当前值: %s",
    ),
    "status.plot.extend.endpoint_tolerance_positive": (
        "Endpoint tolerance must be greater than 0",
        "端点容差必须大于0",
    ),
    "status.plot.extend.endpoint_tolerance_positive_value": (
        "Endpoint tolerance must be greater than 0, current: %s",
        "端点容差必须大于0，当前值: %s",
    ),
    "status.plot.offset.warn.bezier": (
        "Bezier/spline offset may be inaccurate; consider another method",
        "样条曲线偏移可能不够准确，建议使用其他方法",
    ),
    "status.plot.offset.warn.cable": (
        "Catenary offset may be inaccurate; consider another method",
        "悬链线偏移可能不够准确，建议使用其他方法",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-6 keys")
