#!/usr/bin/env python3
"""Merge batch-4 lang keys: handlers, history ops, tool enums, draw overlays."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # history operation names (ModifyCommand)
    "history.plot.op.move": ("Move", "移动"),
    "history.plot.op.rotate": ("Rotate", "旋转"),
    "history.plot.op.scale": ("Scale", "缩放"),
    "history.plot.op.mirror": ("Mirror", "镜像"),
    "history.plot.op.offset": ("Offset", "偏移"),
    "history.plot.op.trim": ("Trim", "修剪"),
    "history.plot.op.break": ("Break", "打断"),
    "history.plot.op.extend": ("Extend", "延伸"),
    "history.plot.op.chamfer": ("Chamfer", "倒角"),
    "history.plot.op.fillet": ("Fillet", "圆角"),
    "history.plot.op.align": ("Align", "对齐"),
    "history.plot.op.annotation": ("Add Annotation", "添加标注"),
    # chamfer validation
    "status.plot.chamfer.need_two_objects": (
        "Chamfer requires exactly two selected objects",
        "倒角操作需要选择两个对象",
    ),
    "status.plot.chamfer.polyline_corner_invalid": (
        "Cannot create chamfer at this polyline corner",
        "无法在该折线拐角创建倒角",
    ),
    "status.plot.chamfer.polygon_corner_invalid": (
        "Cannot create chamfer at this polygon corner",
        "无法在该多边形拐角创建倒角",
    ),
    "status.plot.chamfer.same_shape_unsupported": (
        "Same-object chamfer supports polylines and polygons only",
        "同一对象倒角仅支持折线与多边形",
    ),
    "status.plot.chamfer.diff_lines_only": (
        "Different-object chamfer supports two lines only; use same object twice for polyline/polygon",
        "不同对象倒角目前仅支持两条直线；折线/多边形请对同一对象选择两次",
    ),
    "status.plot.chamfer.lines_must_intersect": (
        "Lines must intersect to chamfer",
        "两条直线必须相交才能进行倒角操作",
    ),
    # transform validation
    "status.plot.transform.invalid_drag_vector": (
        "Drag vector contains invalid values: (%f, %f)",
        "拖拽向量包含无效值: (%f, %f)",
    ),
    "status.plot.transform.invalid_control_index": (
        "Control point index out of range: %d (valid: 0-%d)",
        "控制点索引超出范围: %d (有效范围: 0-%d)",
    ),
    "status.plot.transform.empty_shapes": (
        "Transform command requires at least one shape",
        "变换命令需要至少一个图形，但提供了空列表",
    ),
    "status.plot.transform.params_invalid": (
        "Transform parameters validation failed",
        "变换参数验证失败",
    ),
    "status.plot.transform.control_type_required": (
        "Control point type is required",
        "控制点类型不能为空",
    ),
    "status.plot.transform.invalid_mode": (
        "Invalid transform mode: %s",
        "无效的变换模式: %s",
    ),
    # drawing tool interaction / state enums
    "mode.plot.interaction.drag_drop": ("Drag Mode", "拖放模式"),
    "mode.plot.interaction.drag_drop.desc": (
        "Press and drag to draw, release to finish",
        "按下鼠标拖动绘制，松开完成",
    ),
    "mode.plot.interaction.click_click": ("Click Mode", "点击模式"),
    "mode.plot.interaction.click_click.desc": (
        "Click to set control points; move to preview; click again to finish",
        "点击设置控制点，移动显示预览，再次点击完成",
    ),
    "mode.plot.draw.state.drawing": ("Drawing", "绘制中"),
    "mode.plot.draw.state.drawing.desc": (
        "Drawing in progress with live preview",
        "正在进行绘制操作，显示实时预览",
    ),
    "mode.plot.tool.state.selecting": ("Selecting", "选择中"),
    "mode.plot.tool.state.selecting.desc": (
        "Selecting shapes to modify",
        "正在选择要修改的图形",
    ),
    "mode.plot.tool.state.modifying": ("Modifying", "修改中"),
    "mode.plot.tool.state.modifying.desc": (
        "Modifying selected shapes",
        "正在修改选中的图形",
    ),
    # polygon overlay
    "status.plot.draw.polygon.sides": (
        "%d-gon",
        "%d边形",
    ),
    "status.plot.draw.polygon.sides_radius": (
        "%d-gon, radius: %.2f",
        "%d边形, 半径: %.2f",
    ),
    # spline status / tooltip
    "status.plot.draw.spline.with_mode": (
        "%s (%s)",
        "%s (%s)",
    ),
    "status.plot.draw.spline.with_mode_points": (
        "%s (%s) - added %d points",
        "%s (%s) - 已添加 %d 个点",
    ),
    "status.plot.draw.spline.tooltip": (
        "%s\nControl points: %d\nType: %s\nTension: %.2f\nSegments: %d",
        "%s\n控制点数: %d\n类型: %s\n张力: %.2f\n采样段数: %d",
    ),
    # config errors
    "status.plot.config.convert_failed": (
        "Config key %s: value '%s' cannot convert to %s",
        "配置键 %s 的值 '%s' 无法转换为 %s 类型",
    ),
    "status.plot.config.type_mismatch": (
        "Config key %s expects %s but got %s",
        "配置键 %s 期望类型 %s，但收到类型 %s",
    ),
    # module startup (rare)
    "status.plot.module.register_tool_failed": (
        "Failed to register tool '%s'; application cannot start",
        "注册工具 '%s' 失败，应用无法正常启动",
    ),
    "status.plot.module.register_modify_tool_failed": (
        "Failed to register modify tool '%s'; application cannot start",
        "注册修改工具 '%s' 失败，应用无法正常启动",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-4 keys")
