#!/usr/bin/env python3
"""Merge batch-7 lang keys: strategy names/descriptions and control-point status."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # modify strategy names
    "strategy.plot.name.offset": ("Offset Strategy", "偏移策略"),
    "strategy.plot.name.mirror": ("Mirror Strategy", "镜像策略"),
    "strategy.plot.name.scale": ("Scale Strategy", "缩放策略"),
    "strategy.plot.name.rotate": ("Rotate Strategy", "旋转策略"),
    "strategy.plot.name.selection": ("Selection Strategy", "选择策略"),
    "strategy.plot.name.break": ("Break Strategy", "打断策略"),
    "strategy.plot.name.move_with_selection": ("Move with Selection Strategy", "移动选择结合策略"),
    "strategy.plot.name.rotate_with_selection": ("Rotate with Selection Strategy", "旋转选择结合策略"),
    "strategy.plot.name.scale_with_selection": ("Scale with Selection Strategy", "缩放选择结合策略"),
    "strategy.plot.name.mirror_with_selection": ("Mirror with Selection Strategy", "镜像选择结合策略"),
    "strategy.plot.name.extend_with_selection": ("Extend with Selection Strategy", "延伸选择结合策略"),
    "strategy.plot.name.array_with_selection": ("Array with Selection Strategy", "阵列选择结合策略"),
    "strategy.plot.name.align_with_selection": ("Align with Selection Strategy", "对齐选择结合策略"),
    "strategy.plot.name.trim_with_selection": ("Trim with Selection Strategy", "修剪选择结合策略"),
    "strategy.plot.name.align": ("Align Strategy", "对齐策略"),
    "strategy.plot.name.array": ("Array Strategy", "阵列策略"),
    "strategy.plot.name.eraser": ("Eraser Strategy", "橡皮擦策略"),
    "strategy.plot.name.transform": ("Transform Strategy", "变换策略"),
    "strategy.plot.name.simple_offset": ("Simple Offset Strategy", "简化偏移"),
    "strategy.plot.name.control_point_edit": ("Control Point Edit Strategy", "控制点编辑策略"),
    "strategy.plot.name.chamfer": ("Chamfer Strategy", "倒角策略"),
    "strategy.plot.name.fillet": ("Fillet Strategy", "圆角策略"),
    "strategy.plot.name.annotation": ("Annotation Strategy", "标注策略"),
    # modify strategy descriptions
    "strategy.plot.desc.align": (
        "Align shapes with multiple modes and reference options",
        "用于对齐图形的策略，支持多种对齐模式和参考方式",
    ),
    "strategy.plot.desc.array": (
        "Create array copies with multiple array types",
        "用于创建图形阵列复制的策略，支持多种阵列类型",
    ),
    "strategy.plot.desc.eraser": (
        "Delete shapes via click or drag erase modes",
        "用于删除图形的策略，支持点击删除和拖拽删除模式",
    ),
    "strategy.plot.desc.transform": (
        "Dual-mode transform strategy supporting selection and transform interaction",
        "双模式变换策略 - 支持选择和变换两种交互模式",
    ),
    "strategy.plot.desc.simple_offset": (
        "On linear shapes: pick reference point, then second point on target side to offset-copy",
        "在线类图形上：先选图形参考点，再点击目标侧第二点，按目标侧生成偏移副本",
    ),
    "strategy.plot.desc.control_point_edit": (
        "Drag control points to adjust shape geometry",
        "拖拽控制点来调整图形形状",
    ),
    "strategy.plot.desc.chamfer": (
        "Create chamfers between two lines at their intersection",
        "倒角策略 - 在两条直线之间创建斜面",
    ),
    "strategy.plot.desc.fillet": (
        "Create fillets between two lines at their intersection",
        "圆角策略 - 在两条直线之间创建圆角",
    ),
    "strategy.plot.desc.annotation": (
        "Annotation strategy supporting distance, angle, radius, and area labels",
        "标注工具策略，支持距离、角度、半径和面积标注",
    ),
    "strategy.plot.desc.with_persistent": ("%s (continuous mode)", "%s（持续模式）"),
    # drawing strategy descriptions
    "strategy.plot.draw.spline": (
        "Spline tool multi-point click interaction strategy",
        "样条工具多点点击交互策略",
    ),
    "strategy.plot.draw.polygon": (
        "Polygon tool multi-point strategy supporting three draw modes",
        "多边形工具多点点击交互策略，支持三种绘制模式",
    ),
    "strategy.plot.draw.sine": (
        "Sine curve tool three-point click interaction strategy",
        "正弦曲线工具三点点击交互策略",
    ),
    "strategy.plot.draw.rectangle": (
        "Rectangle tool interaction strategy for all draw modes",
        "处理所有矩形绘制模式的交互策略",
    ),
    "strategy.plot.draw.semicircle": (
        "Semicircle tool multi-point strategy with two draw modes",
        "半圆工具多点点击交互策略，支持两种绘制模式",
    ),
    "strategy.plot.draw.ellipse": (
        "Ellipse tool multi-point strategy with three draw modes",
        "椭圆工具多点点击交互策略，支持三种绘制模式",
    ),
    "strategy.plot.draw.arc": (
        "Arc tool multi-point strategy with three draw modes",
        "弧形工具多点点击交互策略，支持三种绘制模式",
    ),
    "strategy.plot.draw.circle": (
        "Circle tool multi-point strategy with three draw modes",
        "圆形工具多点点击交互策略，支持三种绘制模式",
    ),
    "strategy.plot.draw.free_draw.name": ("Free Draw Mode", "自由绘制模式"),
    "strategy.plot.draw.free_draw": (
        "Free draw tool drag-and-drop interaction strategy",
        "自由绘制工具拖放交互策略",
    ),
    "strategy.plot.draw.catenary": (
        "Catenary tool three-point draw interaction strategy",
        "悬链线工具三点绘制交互策略",
    ),
    "strategy.plot.draw.polyline": (
        "Polyline tool strategy delegating to the current mode handler",
        "多段线工具交互策略，委托给当前模式处理器",
    ),
    "strategy.plot.draw.spiral": (
        "Spiral tool collaborative interaction strategy via tool context",
        "螺旋线工具协作式交互策略 - 通过上下文调用工具方法",
    ),
    # control point edit status
    "status.plot.control_point.inactive": ("Control point edit inactive", "控制点编辑未激活"),
    "status.plot.control_point.dragging": ("Dragging control point", "正在拖拽控制点"),
    "status.plot.control_point.hover": (
        "Hovering control point; click and drag to edit",
        "悬停在控制点上，点击拖拽进行编辑",
    ),
    "status.plot.control_point.select": ("Select a control point to edit", "选择一个控制点进行编辑"),
    "status.plot.strategy.unknown_state": ("Strategy state unknown", "策略状态未知"),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-7 keys")
