#!/usr/bin/env python3
"""Merge mode/state/strategy lang keys for batch 2 enum i18n."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # transform modes
    "mode.plot.transform.free": ("Free Transform", "自由变换"),
    "mode.plot.transform.free.desc": (
        "Transform in all directions",
        "允许在所有方向进行变换",
    ),
    "mode.plot.transform.horizontal": ("Horizontal Transform", "水平变换"),
    "mode.plot.transform.horizontal.desc": (
        "Transform horizontally only",
        "只允许水平方向变换",
    ),
    "mode.plot.transform.vertical": ("Vertical Transform", "垂直变换"),
    "mode.plot.transform.vertical.desc": (
        "Transform vertically only",
        "只允许垂直方向变换",
    ),
    "mode.plot.transform.uniform": ("Uniform Transform", "等比变换"),
    "mode.plot.transform.uniform.desc": (
        "Keep aspect ratio while transforming",
        "保持宽高比例进行变换",
    ),
    "mode.plot.transform.rotation": ("Rotation Mode", "旋转模式"),
    "mode.plot.transform.rotation.desc": (
        "Rotate selected shapes",
        "旋转选中的图形",
    ),
    # transform states
    "state.plot.transform.idle": ("Idle", "空闲"),
    "state.plot.transform.idle.desc": ("Waiting to start transform", "等待开始变换"),
    "state.plot.transform.bounding_box": ("Bounding Box", "显示包围盒"),
    "state.plot.transform.bounding_box.desc": (
        "Show bounding box and control points",
        "显示包围盒和控制点",
    ),
    "state.plot.transform.drag_control": ("Drag Control Point", "拖拽控制点"),
    "state.plot.transform.drag_control.desc": (
        "Dragging a control point to transform",
        "正在拖拽控制点进行变换",
    ),
    # scale modes
    "mode.plot.scale.uniform": ("Uniform Scale", "统一缩放"),
    "mode.plot.scale.uniform.desc": (
        "Scale uniformly while keeping aspect ratio",
        "保持宽高比的统一缩放",
    ),
    "mode.plot.scale.non_uniform": ("Non-uniform Scale", "非统一缩放"),
    "mode.plot.scale.non_uniform.desc": (
        "Scale X and Y independently",
        "可以分别调整X和Y方向的缩放比例",
    ),
    # spline mode short labels
    "mode.plot.spline.fit_short": ("Fit", "拟合"),
    "mode.plot.spline.control_short": ("Control", "控制"),
    # shared strategy modes
    "strategy.plot.mode.selection": ("Selection Mode", "选择模式"),
    "strategy.plot.mode.selection.desc": (
        "Left-click to select shapes; right-click to finish selection",
        "左键选择图形，右键完成选择",
    ),
    "strategy.plot.mode.move": ("Move Mode", "移动模式"),
    "strategy.plot.mode.move.desc": (
        "Drag to move or click-move-click",
        "拖拽移动或点击-移动-点击模式",
    ),
    "strategy.plot.mode.rotate": ("Rotate Mode", "旋转模式"),
    "strategy.plot.mode.rotate.desc": (
        "Three-point rotate: center, reference, target",
        "三点旋转：中心点-参考点-目标点",
    ),
    "strategy.plot.mode.mirror": ("Mirror Mode", "镜像模式"),
    "strategy.plot.mode.mirror.desc": (
        "Define mirror axis with two points",
        "两点定义镜像轴",
    ),
    "strategy.plot.mode.scale": ("Scale Mode", "缩放模式"),
    "strategy.plot.mode.scale.desc": (
        "Three-point scale: center, reference, target",
        "三点缩放：中心点-参考点-目标点",
    ),
    "strategy.plot.mode.align": ("Align Mode", "对齐模式"),
    "strategy.plot.mode.align.desc": (
        "Four-point align: S1 → T1 → S2 → T2",
        "四点对齐：S1 -> T1 -> S2 -> T2",
    ),
    "strategy.plot.mode.array": ("Array Mode", "阵列模式"),
    "strategy.plot.mode.array.desc": (
        "Set array parameters and preview",
        "设置阵列参数并预览",
    ),
    "strategy.plot.mode.transform": ("Transform Mode", "变换模式"),
    "strategy.plot.mode.transform.desc": (
        "Drag control points to transform; right-click to return",
        "拖拽控制点进行变换，右键返回选择",
    ),
    # shared / tool states
    "state.plot.idle": ("Idle", "空闲"),
    "state.plot.idle.desc": ("Waiting for user action", "等待用户操作"),
    "state.plot.tool.drawing": ("Drawing", "绘制中"),
    "state.plot.tool.drawing.desc": (
        "Drawing in progress with live preview",
        "正在进行绘制操作，显示实时预览",
    ),
    "state.plot.tool.selecting": ("Selecting", "选择中"),
    "state.plot.tool.selecting.desc": (
        "Selecting shapes to modify",
        "正在选择要修改的图形",
    ),
    "state.plot.tool.modifying": ("Modifying", "修改中"),
    "state.plot.tool.modifying.desc": (
        "Modifying selected shapes",
        "正在修改选中的图形",
    ),
    "state.plot.tool.previewing": ("Previewing", "预览中"),
    "state.plot.tool.previewing.desc": (
        "Showing modification preview",
        "显示修改预览效果",
    ),
    # rotate states
    "state.plot.rotate.setting_center": ("Set Center", "设置中心"),
    "state.plot.rotate.rotating": ("Rotating", "旋转中"),
    "state.plot.rotate.rotating.desc": (
        "Move mouse to rotate shapes",
        "移动鼠标旋转图形",
    ),
    "state.plot.rotate.await_center.desc": (
        "Waiting to set rotation center",
        "等待设置旋转中心点",
    ),
    "state.plot.rotate.setting_reference": ("Set Reference", "设置参考"),
    # scale states
    "state.plot.scale.await_center.desc": (
        "Waiting to set scale center",
        "等待设置缩放中心点",
    ),
    "state.plot.scale.await_reference": ("Await Reference", "等待参考点"),
    "state.plot.scale.scaling": ("Scaling", "缩放中"),
    "state.plot.scale.scaling.desc": (
        "Move mouse to scale shapes",
        "移动鼠标缩放图形",
    ),
    # mirror states
    "state.plot.mirror.await_axis_start.desc": (
        "Waiting to set mirror axis start",
        "等待设置镜像轴起点",
    ),
    "state.plot.mirror.setting_axis_end": ("Set End Point", "设置终点"),
    "state.plot.mirror.setting_axis_end.desc": (
        "Click to set mirror axis end point",
        "点击设置镜像轴终点",
    ),
    # align states
    "state.plot.align.await_start.desc": (
        "Waiting to start alignment",
        "等待开始对齐",
    ),
    "state.plot.align.await_source1": ("Await Source 1", "等待源点1"),
    "state.plot.align.await_source1.desc": (
        "Click source point 1 on selected shape",
        "在选中图形上点击源点1",
    ),
    "state.plot.align.await_target1": ("Await Target 1", "等待目标点1"),
    "state.plot.align.await_target1.desc": ("Click target point 1", "点击目标点1"),
    "state.plot.align.await_source2": ("Await Source 2", "等待源点2"),
    "state.plot.align.await_source2.desc": (
        "Click source point 2 on selected shape",
        "在选中图形上点击源点2",
    ),
    "state.plot.align.await_target2": ("Await Target 2", "等待目标点2"),
    "state.plot.align.await_target2.desc": (
        "Click target point 2 to finish alignment",
        "点击目标点2完成对齐",
    ),
    # array states
    "state.plot.array.await_start.desc": (
        "Waiting to start array",
        "等待开始阵列",
    ),
    "state.plot.array.await_base": ("Await Base Point", "等待基准点"),
    "state.plot.array.await_path": ("Await Path", "等待路径"),
    "state.plot.array.previewing": ("Previewing", "预览中"),
    "state.plot.array.previewing.desc": (
        "Adjust parameters and preview array",
        "调整参数并预览阵列效果",
    ),
    # extend
    "mode.plot.extend.standard": ("Standard Extend", "标准延伸"),
    "mode.plot.extend.standard.desc": (
        "Extend shape to intersect boundary",
        "延伸图形到拦截线，确保能碰到拦截线",
    ),
    "mode.plot.extend.project": ("Project Extend", "投影延伸"),
    "mode.plot.extend.project.desc": (
        "Project extend to boundary extension line",
        "投影延伸图形到拦截线的延长线位置",
    ),
    "state.plot.extend.selecting_boundary": ("Select Boundary", "选择边界"),
    "state.plot.extend.selecting_boundary.desc": (
        "Select boundary shapes; right-click to confirm",
        "选择边界图形，右键确认",
    ),
    "state.plot.extend.extending": ("Extend Mode", "延伸模式"),
    "state.plot.extend.extending.desc": (
        "Click shape endpoint to extend; ESC to reselect boundary",
        "点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    # status suffixes
    "status.plot.common.copy_mode_suffix": (" (Copy mode)", " (复制模式)"),
    "status.plot.rotate.previewing": ("Rotation preview...", "旋转预览中..."),
    # modify result (internal labels)
    "result.plot.continue": ("Continue", "继续"),
    "result.plot.continue.desc": (
        "Modification in progress; awaiting more input",
        "修改正在进行中，等待更多用户输入",
    ),
    "result.plot.complete": ("Complete", "完成"),
    "result.plot.complete.desc": (
        "Modification complete; ready to execute command",
        "修改已完成，可以执行最终修改命令",
    ),
    "result.plot.cancel": ("Cancel", "取消"),
    "result.plot.cancel.desc": (
        "Modification cancelled; reset to initial state",
        "修改被取消，需要重置到初始状态",
    ),
    "result.plot.ignored": ("Ignored", "忽略"),
    "result.plot.ignored.desc": (
        "Event not applicable in current strategy state",
        "当前事件不适用于此策略状态",
    ),
    "result.plot.need_selection": ("Need Selection", "需要选择"),
    "result.plot.need_selection.desc": (
        "Select shapes before modifying",
        "需要先选择图形才能进行修改",
    ),
}


def merge_lang(path: Path, lang: str) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    idx = 0 if lang == "en_us" else 1
    for key, pair in KEYS.items():
        data[key] = pair[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {path.name}: +{len(KEYS)} keys")


if __name__ == "__main__":
    merge_lang(LANG / "en_us.json", "en_us")
    merge_lang(LANG / "zh_cn.json", "zh_cn")
