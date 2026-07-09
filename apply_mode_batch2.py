#!/usr/bin/env python3
"""Merge strategy mode, transform/scale mode, and batch-2 status lang keys."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # shared strategy modes (WithSelectionStrategy)
    "strategy.plot.mode.selection": ("Selection Mode", "选择模式"),
    "strategy.plot.mode.selection.desc": (
        "Left-click to select shapes; right-click to finish selection",
        "左键选择图形，右键完成选择",
    ),
    "strategy.plot.mode.move": ("Move Mode", "移动模式"),
    "strategy.plot.mode.move.desc": (
        "Drag or point-to-point move after selection",
        "选择完成后拖拽或点-移动-点",
    ),
    "strategy.plot.mode.rotate": ("Rotate Mode", "旋转模式"),
    "strategy.plot.mode.rotate.desc": (
        "Three-point rotate: center, reference, target",
        "三点旋转：中心点-参考点-目标点",
    ),
    "strategy.plot.mode.scale": ("Scale Mode", "缩放模式"),
    "strategy.plot.mode.scale.desc": (
        "Three-point scale: center, reference, target",
        "三点缩放：中心点-参考点-目标点",
    ),
    "strategy.plot.mode.mirror": ("Mirror Mode", "镜像模式"),
    "strategy.plot.mode.mirror.desc": (
        "Define mirror axis with two points",
        "两点定义镜像轴",
    ),
    "strategy.plot.mode.align": ("Align Mode", "对齐模式"),
    "strategy.plot.mode.align.desc": (
        "Four-point align: S1 -> T1 -> S2 -> T2",
        "四点对齐：S1 -> T1 -> S2 -> T2",
    ),
    "strategy.plot.mode.array": ("Array Mode", "阵列模式"),
    "strategy.plot.mode.array.desc": (
        "Set array parameters and preview",
        "设置阵列参数并预览",
    ),
    "strategy.plot.mode.transform.select": ("Selection Mode", "选择模式"),
    "strategy.plot.mode.transform.select.desc": (
        "Select shapes; right-click to enter transform mode",
        "选择图形，右键确认进入变换",
    ),
    "strategy.plot.mode.transform.transform": ("Transform Mode", "变换模式"),
    "strategy.plot.mode.transform.transform.desc": (
        "Drag control points to transform; right-click to return",
        "拖拽控制点进行变换，右键返回选择",
    ),
    # transform modes (TransformMode enum)
    "mode.plot.transform.free": ("Free Transform", "自由变换"),
    "mode.plot.transform.free.desc": (
        "Drag control points freely",
        "自由拖拽控制点变换",
    ),
    "mode.plot.transform.horizontal": ("Horizontal Scale", "水平缩放"),
    "mode.plot.transform.horizontal.desc": (
        "Scale horizontally only",
        "仅水平方向缩放",
    ),
    "mode.plot.transform.vertical": ("Vertical Scale", "垂直缩放"),
    "mode.plot.transform.vertical.desc": (
        "Scale vertically only",
        "仅垂直方向缩放",
    ),
    "mode.plot.transform.uniform": ("Uniform Scale", "等比缩放"),
    "mode.plot.transform.uniform.desc": (
        "Scale uniformly in all directions",
        "各方向等比缩放",
    ),
    "mode.plot.transform.rotation": ("Rotation", "旋转"),
    "mode.plot.transform.rotation.desc": (
        "Rotate around the bounding-box center",
        "绕包围盒中心旋转",
    ),
    # scale modes (ScaleStrategy.ScaleMode)
    "mode.plot.scale.uniform": ("Uniform Scale", "统一缩放"),
    "mode.plot.scale.uniform.desc": (
        "Scale uniformly from center and reference points",
        "从中心和参考点统一缩放",
    ),
    "mode.plot.scale.non_uniform": ("Non-uniform Scale", "非统一缩放"),
    "mode.plot.scale.non_uniform.desc": (
        "Scale X and Y independently",
        "X、Y 方向独立缩放",
    ),
    # common idle label
    "mode.plot.common.idle": ("Idle", "空闲"),
    # mirror state
    "mode.plot.mirror.state.setting_axis_end": ("Set End Point", "设置终点"),
    "mode.plot.mirror.state.idle.desc": (
        "Waiting to set mirror axis start point",
        "等待设置镜像轴起点",
    ),
    "mode.plot.mirror.state.setting_axis_end.desc": (
        "Click to set mirror axis end point",
        "点击设置镜像轴终点",
    ),
    # scale state
    "mode.plot.scale.state.scaling": ("Scaling", "缩放中"),
    "mode.plot.scale.state.idle.desc": (
        "Waiting to set scale center point",
        "等待设置缩放中心点",
    ),
    "mode.plot.scale.state.scaling.desc": (
        "Move mouse to scale shapes",
        "移动鼠标缩放图形",
    ),
    # rotate state
    "mode.plot.rotate.state.setting_center": ("Set Center", "设置中心"),
    "mode.plot.rotate.state.setting_reference": ("Set Reference", "设置参考"),
    "mode.plot.rotate.state.rotating": ("Rotating", "旋转中"),
    "mode.plot.rotate.state.idle.desc": (
        "Waiting to set rotation center point",
        "等待设置旋转中心点",
    ),
    "mode.plot.rotate.state.setting_center.desc": (
        "Click to set rotation center",
        "点击设置旋转中心点",
    ),
    "mode.plot.rotate.state.rotating.desc": (
        "Move mouse to rotate shapes",
        "移动鼠标旋转图形",
    ),
    # align state
    "mode.plot.align.state.await_source1": ("Await Source 1", "等待源点1"),
    "mode.plot.align.state.await_target1": ("Await Target 1", "等待目标点1"),
    "mode.plot.align.state.await_source2": ("Await Source 2", "等待源点2"),
    "mode.plot.align.state.await_target2": ("Await Target 2", "等待目标点2"),
    "mode.plot.align.state.idle.desc": (
        "Waiting to start alignment",
        "等待开始对齐",
    ),
    "mode.plot.align.state.await_source1.desc": (
        "Click source point 1 on selected shape",
        "在选中图形上点击源点1",
    ),
    "mode.plot.align.state.await_target1.desc": (
        "Click target point 1",
        "点击目标点1",
    ),
    "mode.plot.align.state.await_source2.desc": (
        "Click source point 2 on selected shape",
        "在选中图形上点击源点2",
    ),
    "mode.plot.align.state.await_target2.desc": (
        "Click target point 2 to finish alignment",
        "点击目标点2完成对齐",
    ),
    # array state
    "mode.plot.array.state.await_base_point": ("Await Base Point", "等待基准点"),
    "mode.plot.array.state.await_path": ("Await Path", "等待路径"),
    "mode.plot.array.state.previewing": ("Previewing", "预览中"),
    "mode.plot.array.state.idle.desc": (
        "Waiting to start array",
        "等待开始阵列",
    ),
    "mode.plot.array.state.await_path.desc": (
        "Select a path object",
        "选择路径对象",
    ),
    "mode.plot.array.state.previewing.desc": (
        "Adjust parameters and preview array",
        "调整参数并预览阵列效果",
    ),
    # extend state
    "mode.plot.extend.state.selecting_boundary": ("Select Boundary", "选择边界"),
    "mode.plot.extend.state.extending": ("Extend Mode", "延伸模式"),
    "mode.plot.extend.state.selecting_boundary.desc": (
        "Select boundary shapes; right-click to confirm",
        "选择边界图形，右键确认",
    ),
    "mode.plot.extend.state.extending.desc": (
        "Click shape endpoint to extend; ESC to reselect boundary",
        "点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    "mode.plot.extend.mode.standard": ("Standard Extend", "标准延伸"),
    "mode.plot.extend.mode.standard.desc": (
        "Extend shape to intercept line",
        "延伸图形到拦截线，确保能碰到拦截线",
    ),
    "mode.plot.extend.mode.project": ("Project Extend", "投影延伸"),
    "mode.plot.extend.mode.project.desc": (
        "Project-extend shape to intercept line extension",
        "投影延伸图形到拦截线的延长线位置",
    ),
    # eraser state
    "mode.plot.eraser.state.erasing": ("Erasing", "删除中"),
    "mode.plot.eraser.state.idle.desc": (
        "Waiting for user action",
        "等待用户操作",
    ),
    "mode.plot.eraser.state.erasing.desc": (
        "Deleting shapes",
        "正在删除图形",
    ),
    # scale live status
    "status.plot.scale.in_progress": ("Scaling...", "缩放中..."),
    "status.plot.scale.uniform": ("Uniform scale: %.2fx", "统一缩放: %.2fx"),
    "status.plot.scale.uniform_center": (
        "Uniform scale: %.2fx (center: %.1f, %.1f)",
        "统一缩放: %.2fx (中心: %.1f, %.1f)",
    ),
    "status.plot.scale.non_uniform": (
        "Non-uniform scale: X=%.2f, Y=%.2f",
        "非统一缩放: X=%.2f, Y=%.2f",
    ),
    "status.plot.scale.non_uniform_center": (
        "Non-uniform scale: X=%.2f, Y=%.2f (center: %.1f, %.1f)",
        "非统一缩放: X=%.2f, Y=%.2f (中心: %.1f, %.1f)",
    ),
    # move distance
    "status.plot.move.distance": ("Move distance: %.2f", "移动距离: %.2f"),
    # annotation initial hints
    "status.plot.annotation.distance_hint": (
        "Distance mode: click two points to annotate distance",
        "距离模式：点击两点进行距离标注",
    ),
    "status.plot.annotation.angle_hint": (
        "Angle mode: select two intersecting lines; right-click to annotate angle",
        "角度模式：选择两条有夹角的直线，右键完成选中时自动标注角度",
    ),
    "status.plot.annotation.radius_hint": (
        "Radius mode: select circles/arcs; right-click to annotate radius",
        "半径模式：点选或框选圆形、半圆、圆弧图形，右键完成选中时自动标注半径",
    ),
    "status.plot.annotation.area_hint": (
        "Area mode: select region; right-click to annotate area",
        "面积模式：选择区域，右键完成选中时自动标注面积",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-2 keys")
