#!/usr/bin/env python3
"""Merge mode.plot.* and preview suffix lang keys."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # arc mode descriptions
    "mode.plot.start_end_arc.desc": (
        "Click start, end, and a point on the arc",
        "点击设置起点、终点、圆弧上的点",
    ),
    "mode.plot.through_point.desc": (
        "Click start, through point, and end",
        "点击设置起点、经过点、终点",
    ),
    "mode.plot.center_start_end.desc": (
        "Click center, start, and end",
        "点击设置圆心、起点、终点",
    ),
    # rectangle mode descriptions
    "mode.plot.rect.two_points.desc": (
        "Click two diagonal corners to create a rectangle",
        "点击两个对角点创建矩形",
    ),
    "mode.plot.rect.three_points.desc": (
        "Two base points plus a height point",
        "底边两点+高度点创建矩形",
    ),
    "mode.plot.rect.center_point.desc": (
        "Center point plus corner point",
        "中心点+角点创建矩形",
    ),
    "mode.plot.rect.rounded.desc": (
        "First two points define the rectangle; third sets corner radius",
        "前两点确定矩形，第三点确定圆角大小",
    ),
    # semicircle / ellipse / polygon descriptions
    "mode.plot.semicircle.two_points.desc": (
        "First point sets center; second sets radius and direction",
        "第一点确定圆心，第二点确定半径和方向",
    ),
    "mode.plot.semicircle.three_points.desc": (
        "Two points define diameter; third sets semicircle direction",
        "两点确定直径，第三点确定半圆方向",
    ),
    "mode.plot.ellipse.three_axis.desc": (
        "First two points define major axis; third sets minor axis length",
        "前两点确定长轴，第三点确定短半轴",
    ),
    "mode.plot.ellipse.three_center.desc": (
        "First point is center; second and third set semi-axes",
        "第一点确定中心点，第二点和第三点分别确定长半轴和短半轴",
    ),
    "mode.plot.ellipse.two_points.desc": (
        "Draw an ellipse inscribed in a bounding rectangle",
        "绘制矩形内切椭圆",
    ),
    "mode.plot.polygon.center_radius.desc": (
        "First point is center; second sets radius and start vertex",
        "第一点为中心，第二点确定半径和起始顶点",
    ),
    "mode.plot.polygon.center_vertex.desc": (
        "First point is center; drag to set circumradius",
        "第一点为中心，拖动确定外接圆半径",
    ),
    # modify tool modes
    "mode.plot.mirror.axis_symmetry": ("Axis Symmetry", "轴对称"),
    "mode.plot.mirror.axis_symmetry.desc": (
        "Mirror across an axis defined by two points",
        "关于一条轴线做对称（两点定义轴）",
    ),
    "mode.plot.mirror.central_symmetry": ("Central Symmetry", "中心对称"),
    "mode.plot.mirror.central_symmetry.desc": (
        "Mirror about a center point (equivalent to 180° rotation)",
        "关于一个中心点做对称（等价于绕该点旋转180°）",
    ),
    "mode.plot.break.single": ("Single-point Break", "单点打断"),
    "mode.plot.break.single.desc": ("Break a shape at the clicked point", "在指定点打断图形"),
    "mode.plot.break.two_point": ("Two-point Break", "两点打断"),
    "mode.plot.break.two_point.desc": (
        "Remove the segment between two break points",
        "在两点间移除图形部分",
    ),
    "mode.plot.break.select_shape": ("Select Shape", "选择图形"),
    "mode.plot.break.select_shape.desc": ("Click to select a shape to break", "点击选择要打断的图形"),
    "mode.plot.break.processing": ("Processing", "处理中"),
    "mode.plot.break.set_second": ("Set Second Point", "设置第二点"),
    "status.plot.draw.preview.rotation": (", Rotation: %.1f°", ", 旋转: %.1f°"),
    "status.plot.draw.preview.radius_value": ("Radius: %.2f", "半径: %.2f"),
    "mode.plot.select.normal": ("Normal Selection", "普通选择"),
    "mode.plot.select.normal.desc": (
        "Click to select; drag to box-select multiple shapes",
        "点击选择图形，拖动框选多个图形",
    ),
    "mode.plot.select.lasso": ("Lasso Selection", "套索选择"),
    "mode.plot.select.lasso.desc": (
        "Hold and drag to draw a freeform selection region",
        "按住鼠标绘制自由选择区域",
    ),
    "mode.plot.offset.distance": ("Distance Offset", "距离偏移"),
    "mode.plot.offset.distance.desc": ("Specify offset distance", "指定偏移距离"),
    "mode.plot.offset.through_point": ("Through-point Offset", "穿点偏移"),
    "mode.plot.offset.through_point.desc": (
        "Click to determine offset position",
        "通过点击确定偏移位置",
    ),
    "mode.plot.rotate.three_point": ("Three-point Rotate", "三点旋转"),
    "mode.plot.rotate.three_point.desc": (
        "Set center, reference point, then rotate to target angle",
        "设置中心点、参考点，然后旋转到目标角度",
    ),
    "mode.plot.rotate.two_point": ("Two-point Rotate", "两点旋转"),
    "mode.plot.rotate.two_point.desc": (
        "Set center, then rotate directly to target angle",
        "设置中心点，然后直接旋转到目标角度",
    ),
    "mode.plot.rotate.center.selection": ("Selection Center", "选择中心"),
    "mode.plot.rotate.center.selection.desc": (
        "Use selection bounding-box center as rotation center",
        "使用选择框的中心作为旋转中心",
    ),
    "mode.plot.rotate.center.shape": ("Shape Center", "图形中心"),
    "mode.plot.rotate.center.shape.desc": (
        "Use each shape's center as rotation center",
        "使用每个图形的中心作为旋转中心",
    ),
    "mode.plot.rotate.center.custom": ("Custom Point", "自定义点"),
    "mode.plot.rotate.center.custom.desc": (
        "User-specified point as rotation center",
        "用户指定的点作为旋转中心",
    ),
    "mode.plot.eraser.click": ("Click Delete", "点击删除"),
    "mode.plot.eraser.click.desc": ("Click a shape to delete it", "点击图形直接删除"),
    "mode.plot.eraser.drag": ("Drag Delete", "拖拽删除"),
    "mode.plot.eraser.drag.desc": ("Drag to delete shapes along the path", "拖拽删除路径上的图形"),
    # live preview suffixes
    "status.plot.draw.preview.radius": ("%s | Radius: %.2f", "%s | 半径: %.2f"),
    "status.plot.draw.preview.distance": ("%s | Distance: %.2f", "%s | 距离: %.2f"),
    "status.plot.draw.preview.major_minor": (
        "Major: %.2f, Minor: %.2f",
        "长轴: %.2f, 短轴: %.2f",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} mode keys")
