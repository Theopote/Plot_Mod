#!/usr/bin/env python3
"""Batch 3: drawing tool status hints, trim/array enums, fillet validation."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"
JAVA_ROOT = ROOT / "src/main/java/com/plot"
PLOT_I18N = "import com.plot.utils.PlotI18n;"

# (en, zh)
KEYS = {
    # ── circle ──
    "status.plot.draw.click_center": ("Click to set center", "点击设置圆心"),
    "status.plot.draw.click_or_drag_radius": ("Click or drag to set radius", "点击或拖动设置半径"),
    "status.plot.draw.click_first_point": ("Click to set first point", "点击设置第一个点"),
    "status.plot.draw.click_second_for_diameter": (
        "Click second point to set diameter",
        "点击设置第二个点以确定直径",
    ),
    "status.plot.draw.click_second_point": ("Click to set second point", "点击设置第二个点"),
    "status.plot.draw.click_third_for_circle": (
        "Click third point to define circle",
        "点击设置第三个点以确定圆",
    ),
    # ── arc ──
    "status.plot.draw.click_start": ("Click to set start point", "点击设置起点"),
    "status.plot.draw.click_end": ("Click to set end point", "点击设置终点"),
    "status.plot.draw.click_arc_point": ("Click a point on the arc", "点击设置圆弧上的点"),
    "status.plot.draw.click_through": ("Click to set through point", "点击设置经过点"),
    # ── semicircle ──
    "status.plot.draw.semicircle.two_center": (
        "Two-point mode: click semicircle center",
        "两点模式：点击确定半圆中心点",
    ),
    "status.plot.draw.semicircle.radius_direction": (
        "Click to set radius and direction",
        "点击确定半径和方向",
    ),
    "status.plot.draw.semicircle.three_first": (
        "Three-point mode: click first diameter point",
        "三点模式：点击确定直径的第一个点",
    ),
    "status.plot.draw.semicircle.three_second": (
        "Click second diameter point",
        "点击确定直径的第二个点",
    ),
    "status.plot.draw.semicircle.three_direction": (
        "Click to set semicircle direction",
        "点击确定半圆的方向",
    ),
    # ── rectangle ──
    "status.plot.draw.rect.click_first_corner": ("Click first corner", "点击第一个角点"),
    "status.plot.draw.rect.click_second_corner": ("Click second corner", "点击第二个角点"),
    "status.plot.draw.rect.click_base_first": ("Click first base point", "点击底边第一个点"),
    "status.plot.draw.rect.click_base_second": ("Click second base point", "点击底边第二个点"),
    "status.plot.draw.rect.click_height": ("Click height point", "点击高度点"),
    "status.plot.draw.rect.click_center": ("Click center point", "点击中心点"),
    "status.plot.draw.rect.click_corner": ("Click corner point", "点击角点"),
    "status.plot.draw.rect.drag_rounded": (
        "Move mouse to adjust corner radius, click to confirm",
        "移动鼠标调整圆角大小，点击确定",
    ),
    # ── ellipse ──
    "status.plot.draw.ellipse.axis_p1": (
        "Three-point axis: click first major-axis point",
        "三点-轴模式：点击设置长轴第一点",
    ),
    "status.plot.draw.ellipse.axis_p2": (
        "Three-point axis: click second major-axis point",
        "三点-轴模式：点击设置长轴第二点",
    ),
    "status.plot.draw.ellipse.axis_minor": (
        "Three-point axis: click minor-axis length",
        "三点-轴模式：点击设置短轴长度",
    ),
    "status.plot.draw.ellipse.center_p1": (
        "Three-point center: click ellipse center",
        "三点-中心点模式：点击设置椭圆中心点",
    ),
    "status.plot.draw.ellipse.center_major": (
        "Three-point center: click major-axis endpoint",
        "三点-中心点模式：点击设置长半轴终点",
    ),
    "status.plot.draw.ellipse.center_minor": (
        "Three-point center: click minor-axis endpoint",
        "三点-中心点模式：点击设置短半轴终点",
    ),
    "status.plot.draw.ellipse.two_p1": (
        "Two-point mode: click first corner of bounding box",
        "两点模式：点击设置矩形第一个角点",
    ),
    "status.plot.draw.ellipse.two_p2": (
        "Two-point mode: click opposite corner",
        "两点模式：点击设置矩形对角点",
    ),
    # ── polygon ──
    "status.plot.draw.polygon.center_radius_p1": (
        "Center-radius: click polygon center",
        "中心-半径模式：点击设置多边形中心点",
    ),
    "status.plot.draw.polygon.center_radius_p2": (
        "Center-radius: click radius and start vertex",
        "中心-半径模式：点击设置半径和起始顶点位置",
    ),
    "status.plot.draw.polygon.center_vertex_p1": (
        "Center-vertex: click polygon center",
        "中心-顶点模式：点击设置多边形中心点",
    ),
    "status.plot.draw.polygon.center_vertex_p2": (
        "Center-vertex: drag to set circumradius",
        "中心-顶点模式：拖动确定外接圆半径",
    ),
    # ── spline ──
    "status.plot.draw.spline.add_point": ("Click to add spline control point", "点击添加样条控制点"),
    "status.plot.draw.spline.continue": (
        "Continue clicking to add points; right-click to finish",
        "继续点击添加控制点，右键完成绘制",
    ),
    "status.plot.draw.spline.continue_close": (
        "Continue clicking; press C to close, right-click to finish",
        "继续点击添加控制点，按C键封闭曲线，右键完成绘制",
    ),
    # ── polyline / edit ──
    "status.plot.draw.polyline_start": ("Click to start drawing polyline", "点击开始绘制折线"),
    "status.plot.draw.polyline_add_enter": (
        "Click next vertex; right-click/Enter to finish, Esc to cancel",
        "点击添加下一个顶点，右键或Enter键完成绘制，Esc键取消",
    ),
    "status.plot.draw.polyline_vertex_count": (
        "Added %d vertices; right-click/Enter to finish, Esc to cancel",
        "已添加 %d 个顶点，右键或Enter键完成绘制，Esc键取消",
    ),
    "status.plot.draw.edit.select_shape": ("Select shape to edit", "选择要编辑的图形"),
    "status.plot.draw.edit.drag_anchor": ("Drag to adjust anchor", "拖动调整锚点位置"),
    "status.plot.draw.edit.drag_control": ("Drag to adjust control point", "拖动调整控制点位置"),
    "status.plot.draw.edit.select_node": (
        "Click node to select; drag to move; Shift+click to convert segment type",
        "点击选择节点，拖动调整位置，Shift+点击转换段类型",
    ),
    # ── sine ──
    "status.plot.draw.sine.start": ("Click sine curve start point", "点击确定正弦曲线起点"),
    "status.plot.draw.sine.wavelength": (
        "Click to set wavelength (distance between first two points)",
        "点击确定波长（第一点到第二点的距离）",
    ),
    "status.plot.draw.sine.length": (
        "Click to set total length (start to third point)",
        "点击确定整体长度（从起点到第三点的总长度）",
    ),
    "status.plot.draw.sine.amplitude": ("Click to set amplitude", "点击确定振幅"),
    # ── spiral ──
    "status.plot.draw.spiral.center": ("Click spiral center", "点击确定螺旋中心"),
    "status.plot.draw.spiral.start": ("Click spiral start point", "点击确定螺旋起点"),
    "status.plot.draw.spiral.pitch_point": ("Click pitch point", "点击确定螺距点"),
    "status.plot.draw.spiral.start_radius": (
        "Click start radius (center to first point)",
        "点击确定起始半径（中心到第一点的距离）",
    ),
    "status.plot.draw.spiral.center_start": ("Click spiral center (start point)", "点击确定螺旋中心（起点）"),
    "status.plot.draw.spiral.start_radius_2": (
        "Click start radius (center to second point)",
        "点击确定起始半径（中心到第二点的距离）",
    ),
    "status.plot.draw.spiral.outer": ("Click outermost turn to finish", "点击确定最外圈，完成绘制"),
    "status.plot.draw.spiral.default": (
        "Click spiral center, then radius and direction",
        "点击确定螺旋中心，点击确定半径和方向",
    ),
    # ── multistep ──
    "status.plot.draw.multistep.max": (
        "Click to set control point (max %d steps)",
        "点击设置控制点，最多%d步",
    ),
    "status.plot.draw.multistep.finish": (
        "Click to set control point; right-click or double-click to finish",
        "点击设置控制点，右键或双击完成",
    ),
    # ── fillet validation ──
    "status.plot.fillet.no_edges": (
        "Shape has no edges that can be filleted",
        "图形没有可进行圆角操作的边",
    ),
    "status.plot.fillet.no_edge_pair": (
        "No suitable edge pair found for fillet",
        "没有找到合适的边组合进行圆角操作",
    ),
    "status.plot.fillet.edge_invalid": ("Edge invalid; cannot fillet", "边无效，无法进行圆角操作"),
    # ── trim state/mode (enum labels) ──
    "trim.plot.fence": ("Fence Trim", "栅栏修剪"),
    "trim.plot.mode.boundary.desc": (
        "Select boundary shapes, then click the side of the shape to trim",
        "选择边界图形，然后点击要修剪的图形一侧",
    ),
    "trim.plot.mode.fence.desc": (
        "Select shapes to trim, then draw a fence region",
        "选择要修剪的图形，然后绘制栅栏区域",
    ),
    "trim.plot.mode.fence_alt.desc": (
        "Define fence line for batch trim",
        "定义栅栏线进行批量修剪",
    ),
    "trim.plot.state.selecting_boundaries": ("Selecting boundaries", "选择边界"),
    "trim.plot.state.selecting_boundaries.desc": (
        "Left-click boundary shapes; right-click to finish selection",
        "左键选择用作修剪边界的图形，右键完成选择",
    ),
    "trim.plot.state.waiting_trim": ("Waiting to trim", "等待修剪"),
    "trim.plot.state.waiting_trim.desc": (
        "Left-click the side of the shape to trim",
        "左键点击要修剪的图形一侧",
    ),
    "trim.plot.state.boundary_ready": ("Boundary ready", "边界就绪"),
    "trim.plot.state.boundary_ready.desc": (
        "Boundaries selected; continue trimming other shapes",
        "边界已选择，可以继续修剪其他图形",
    ),
    "trim.plot.state.selecting_targets": ("Selecting targets", "选择目标"),
    "trim.plot.state.selecting_targets.desc": (
        "Left-click shapes to trim; right-click to finish selection",
        "左键选择要修剪的图形，右键完成选择",
    ),
    "trim.plot.state.drawing_fence": ("Drawing fence", "绘制栅栏"),
    "trim.plot.state.drawing_fence.desc": (
        "Left-click to draw fence; right-click to finish and trim",
        "左键绘制栅栏区域，右键完成并执行修剪",
    ),
    "trim.plot.state.fence_ready": ("Fence ready", "栅栏就绪"),
    "trim.plot.state.fence_ready.desc": (
        "Targets selected; continue using fence trim",
        "目标已选择，可以继续使用栅栏修剪",
    ),
    "trim.plot.state.processing": ("Processing", "处理中"),
    "trim.plot.state.processing.desc": ("Executing trim operation", "正在执行修剪操作"),
    # ── array type desc ──
    "array.plot.rectangular.desc": ("Arrange shapes in rows and columns", "按行列排列图形"),
    "array.plot.polar.desc": ("Arrange shapes in a circle", "按圆形排列图形"),
    "array.plot.path.desc": ("Arrange shapes along a path", "沿指定路径排列图形"),
}

EXACT_MAP = {zh: key for key, (_, zh) in KEYS.items() if key.startswith("status.plot.")}

REGEX = [
    (
        r'return String\.format\("已添加 %d 个顶点，右键或Enter键完成绘制，Esc键取消", points\.size\(\)\);',
        'return PlotI18n.status("status.plot.draw.polyline_vertex_count", points.size());',
    ),
    (
        r'return String\.format\("点击设置控制点，最多%d步", maxSteps\);',
        'return PlotI18n.status("status.plot.draw.multistep.max", maxSteps);',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("已切换到%s模式", newType\.getDisplayName\(\)\)\);',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.mode_switched", newType.getDisplayName()));',
    ),
]


def merge_lang():
    for name in ("en_us.json", "zh_cn.json"):
        path = LANG_DIR / name
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, (en, zh) in KEYS.items():
            data[key] = en if name.startswith("en") else zh
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Merged {len(KEYS)} keys")


def add_import(text: str) -> str:
    if PLOT_I18N in text or "PlotI18n." not in text:
        return text
    idx = text.rfind("import ")
    end = text.find(";", idx) + 1
    return text[:end] + "\n" + PLOT_I18N + text[end:]


def patch_java():
    n = 0
    for path in JAVA_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, key in EXACT_MAP.items():
            text = text.replace(f'"{old}"', f'"{key}"')
        for pat, repl in REGEX:
            text = re.sub(pat, repl, text)
        if text != orig:
            text = add_import(text)
            path.write_text(text, encoding="utf-8")
            n += 1
            print(f"  {path.relative_to(ROOT)}")
    print(f"Patched {n} files")


if __name__ == "__main__":
    merge_lang()
    patch_java()
