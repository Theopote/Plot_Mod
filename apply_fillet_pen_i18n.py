#!/usr/bin/env python3
"""Merge fillet/pen status keys and patch constants + strategy files."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "status.plot.fillet.select_second_param": (
        "Select second shape; scroll to adjust parameters, or Esc",
        "选择第二个图形，滚轮调整参数，或按ESC取消",
    ),
    "status.plot.fillet.ready_chamfer_template": (
        "Right-click to confirm fillet (radius: %.1f); scroll to adjust; Esc",
        "按鼠标右键确认倒圆角(半径:%.1f)，滚轮调整半径，或按ESC取消",
    ),
    "status.plot.fillet.complete_template": (
        "Fillet complete (radius: %.1f)",
        "倒圆角完成 (半径: %.1f)",
    ),
    "status.plot.fillet.lines_parallel": (
        "Shapes are parallel; cannot fillet",
        "图形平行，无法进行倒角操作",
    ),
    "status.plot.fillet.angle_too_small": (
        "Angle between shapes too small; cannot fillet",
        "图形夹角太小，无法进行倒角操作",
    ),
    "status.plot.fillet.angle_too_large": (
        "Angle between shapes too large; cannot fillet",
        "图形夹角太大，无法进行倒角操作",
    ),
    "status.plot.fillet.radius_too_large": (
        "Fillet radius too large; cannot fillet",
        "倒角半径太大，无法进行倒角操作",
    ),
    "status.plot.fillet.error_invalid_shapes": (
        "Fillet requires two shapes",
        "倒角操作需要选择两个图形",
    ),
    "status.plot.fillet.error_invalid_radius": (
        "Fillet radius must be between %.1f and %.1f",
        "倒角半径必须在 %.1f 到 %.1f 之间",
    ),
    "status.plot.fillet.select_first_line": (
        "Select first line; +/- adjust radius (%.1f); Esc to cancel",
        "选择第一条直线，按+/-调整半径(%.1f)，或按ESC取消",
    ),
    "status.plot.fillet.ready_confirm": (
        "Right-click to confirm fillet (radius %.1f); +/- adjust; Esc",
        "按鼠标右键确认圆角(半径%.1f)，+/-调整半径，或ESC取消",
    ),
    "status.plot.fillet.radius_scroll": (
        "Fillet radius: %.1f (scroll to adjust)",
        "圆角半径: %.1f (滚轮调整)",
    ),
    "status.plot.fillet.radius_value": ("Fillet radius: %.1f", "圆角半径: %.1f"),
    "status.plot.fillet.confirm_scroll": (
        "Right-click to confirm fillet (radius: %.1f); scroll to adjust; Esc",
        "按鼠标右键确认圆角(半径:%.1f)，滚轮调整半径，或按ESC取消",
    ),
    "status.plot.fillet.error_same_shape_only": (
        "Same shape supports polyline/polygon corners only",
        "同一图形仅支持折线或多边形拐角圆角",
    ),
    "status.plot.fillet.error_corner_failed": (
        "Cannot create fillet at this corner",
        "无法在该图形拐角创建圆角",
    ),
    "status.plot.pen.start": ("Click to start drawing Bezier curve", "点击开始绘制贝塞尔曲线"),
    "status.plot.pen.first_point": (
        "Drag to create curve control point, or click for straight segment; right-click/Enter to finish, Esc to cancel",
        "拖动创建曲线控制点，或直接点击添加直线段，右键或Enter键完成绘制，Esc键取消",
    ),
    "status.plot.pen.adding_points": (
        "Added %d points; right-click/Enter to finish, Esc to cancel",
        "已添加 %d 个点，右键或Enter键完成绘制，Esc键取消",
    ),
    "status.plot.pen.drag_curve": (
        "Drag to create curve control point, or click to add anchor; right-click/Esc to finish",
        "拖动创建曲线控制点，或直接点击添加锚点，右键或Esc键结束绘制",
    ),
    "status.plot.pen.insufficient_points": (
        "Not enough points to complete drawing",
        "点数不足，无法完成绘制",
    ),
    "status.plot.pen.completed": ("Drawing complete", "绘制完成"),
    "status.plot.common.unknown_state": ("Unknown state", "未知状态"),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG_DIR / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, (en_val, zh_val) in KEYS.items():
        data[key] = en_val if name.startswith("en") else zh_val
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} keys")
