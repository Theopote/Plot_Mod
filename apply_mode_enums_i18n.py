#!/usr/bin/env python3
"""Merge mode/break/extend/offset/mirror enum label keys."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "mode.plot.desc.arc.start_end_direction": (
        "Click start, end, and a point on the arc",
        "点击设置起点、终点、圆弧上的点",
    ),
    "mode.plot.desc.arc.through_point": (
        "Click start, through point, and end",
        "点击设置起点、经过点、终点",
    ),
    "mode.plot.desc.arc.center_start_end": (
        "Click center, start, and end",
        "点击设置圆心、起点、终点",
    ),
    "mode.plot.desc.rect.two_points": (
        "Click two opposite corners",
        "点击两个对角点创建矩形",
    ),
    "mode.plot.desc.rect.three_points": (
        "Two base points plus height point",
        "底边两点+高度点创建矩形",
    ),
    "mode.plot.desc.rect.center_point": (
        "Center point plus corner point",
        "中心点+角点创建矩形",
    ),
    "mode.plot.desc.rect.rounded": (
        "First two points set rectangle; third sets corner radius",
        "前两点确定矩形，第三点确定圆角大小",
    ),
    "mode.plot.desc.polygon.center_radius": (
        "First point is center; second sets radius and start vertex",
        "第一点为中心，第二点确定半径和起始顶点",
    ),
    "mode.plot.desc.polygon.center_vertex": (
        "First point is center; drag to set circumradius",
        "第一点为中心，拖动确定外接圆半径",
    ),
    "mode.plot.desc.ellipse.three_point_axis": (
        "First two points define major axis; third sets minor axis",
        "前两点确定长轴，第三点确定短半轴",
    ),
    "mode.plot.desc.ellipse.three_point_center": (
        "First point is center; second and third set semi-axes",
        "第一点确定中心点，第二点和第三点分别确定长半轴和短半轴",
    ),
    "mode.plot.desc.ellipse.two_points": (
        "Draw inscribed ellipse in bounding box",
        "绘制矩形内切椭圆",
    ),
    "mode.plot.desc.semicircle.two_points": (
        "First point is center; second sets radius and direction",
        "第一点确定圆心，第二点确定半径和方向",
    ),
    "mode.plot.desc.semicircle.three_points": (
        "Two points define diameter; third sets direction",
        "两点确定直径，第三点确定半圆方向",
    ),
    "break.plot.mode.single": ("Single-Point Break", "单点打断"),
    "break.plot.mode.single.desc": ("Break shape at clicked point", "在指定点打断图形"),
    "break.plot.mode.two_point": ("Two-Point Break", "两点打断"),
    "break.plot.mode.two_point.desc": (
        "Remove segment between two points",
        "在两点间移除图形部分",
    ),
    "break.plot.state.select_shape": ("Select Shape", "选择图形"),
    "break.plot.state.select_shape.desc": (
        "Click shape to break",
        "点击选择要打断的图形",
    ),
    "break.plot.state.setting_second": ("Set Second Point", "设置第二点"),
    "break.plot.state.processing": ("Processing", "处理中"),
    "break.plot.state.processing.desc": (
        "Executing break operation",
        "正在执行打断操作",
    ),
    "extend.plot.state.selecting_boundary": ("Selecting Boundaries", "选择边界"),
    "extend.plot.state.selecting_boundary.desc": (
        "Select boundary shapes; right-click to confirm",
        "选择边界图形，右键确认",
    ),
    "extend.plot.state.extending": ("Extend Mode", "延伸模式"),
    "extend.plot.state.extending.desc": (
        "Click endpoint to extend; ESC to reselect boundaries",
        "点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    "offset.plot.mode.distance": ("Distance Offset", "距离偏移"),
    "offset.plot.mode.distance.desc": ("Specify offset distance", "指定偏移距离"),
    "offset.plot.mode.through_point": ("Through-Point Offset", "穿点偏移"),
    "offset.plot.mode.through_point.desc": (
        "Click to set offset position",
        "通过点击确定偏移位置",
    ),
    "offset.plot.state.idle": ("Idle", "空闲"),
    "offset.plot.state.idle.desc": (
        "Waiting to select shape to offset",
        "等待选择要偏移的图形",
    ),
    "offset.plot.state.selecting": ("Selecting", "选择中"),
    "offset.plot.state.selecting.desc": (
        "Shape selected; confirm offset parameters",
        "已选择图形，等待确定偏移参数",
    ),
    "mirror.plot.state.idle": ("Idle", "空闲"),
    "mirror.plot.state.idle.desc": (
        "Waiting to set mirror axis start",
        "等待设置镜像轴起点",
    ),
    "mirror.plot.state.setting_end": ("Set End Point", "设置终点"),
    "mirror.plot.state.setting_end.desc": (
        "Click to set mirror axis end",
        "点击设置镜像轴终点",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG_DIR / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, (en, zh) in KEYS.items():
        data[key] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} keys")
