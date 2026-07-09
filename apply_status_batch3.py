#!/usr/bin/env python3
"""Merge status.plot.* lang keys for batch-3 strategy/handler migration."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # --- common / selection ---
    "status.plot.common.added_to_selection": (
        "Added to selection: %d new, %d total",
        "已添加到选择，新增 %d 个图形，总计 %d 个",
    ),
    "status.plot.select.lasso_done": (
        "Lasso (fully enclosed) selection done; selected %d shapes",
        "套索（完全包含）选择完成，已选择 %d 个图形",
    ),
    "status.plot.select.select_all": (
        "Selected all %d shapes",
        "已全选 %d 个图形",
    ),
    # --- extend ---
    "status.plot.extend.box_boundary_done": (
        "Box select done! Selected %d boundary shapes; auto-entered extend mode. "
        "Click endpoints to extend; ESC to reselect boundary",
        "框选完成！已选择 %d 个边界图形，自动进入延伸模式。"
        "点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    "status.plot.extend.boundary_confirmed": (
        "Boundary confirmed! Selected %d boundary shapes. "
        "Click endpoints to extend; ESC to reselect boundary",
        "边界已确认！已选择 %d 个边界图形，点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    "status.plot.extend.box_extend_done": (
        "Extend box select done! Extended %d shapes; tool reset. "
        "Reselect boundaries for next extend",
        "延伸框选完成！成功延伸 %d 个图形，工具已重置，请重新选择边界图形开始下一次延伸",
    ),
    "status.plot.extend.boundary_added": (
        "Added boundary shape %s; %d total; right-click to confirm",
        "已添加边界图形 %s，总计 %d 个边界图形，右键确认选择",
    ),
    "status.plot.extend.boundary_selected": (
        "Selected boundary shape %s; %d total; right-click to confirm",
        "已选择边界图形 %s，总计 %d 个边界图形，右键确认选择",
    ),
    "status.plot.extend.boundary_deselected": (
        "Deselected boundary shape %s; %d total",
        "已取消选择边界图形 %s，总计 %d 个边界图形",
    ),
    "status.plot.extend.success_reset": (
        "Extend success! Shape %s extended; tool reset. Reselect boundaries for next extend",
        "延伸成功！图形 %s 已延伸，工具已重置，请重新选择边界图形开始下一次延伸",
    ),
    "status.plot.extend.calc_failed": (
        "Extend failed: could not compute result; ensure boundaries are valid (count: %d)",
        "延伸失败：无法计算延伸结果，请确保边界有效（当前边界数: %d）",
    ),
    "status.plot.extend.error_with_boundaries": (
        "Extend failed: %s; boundary count: %d; check boundary shapes",
        "延伸操作失败: %s，边界数: %d，请检查边界图形是否有效",
    ),
    "status.plot.extend.boundaries_selected": (
        "Selected %d boundary shapes; right-click to confirm and start extend",
        "已选择 %d 个边界图形，右键确认边界选择并开始延伸操作",
    ),
    "status.plot.extend.boundaries_cached": (
        "Cached %d boundary shapes",
        "已缓存 %d 个边界图形",
    ),
    "status.plot.extend.no_boundaries_cached": (
        "No boundary shapes cached",
        "未缓存边界图形",
    ),
    # --- offset ---
    "status.plot.offset.distance_mode": (
        "Distance mode: offset distance %.2f; click object to offset, or ESC to cancel",
        "距离模式：偏移距离 %.2f，点击要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.mode_click": (
        "%s mode: click object to offset, or ESC to cancel",
        "%s模式：点击要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.multiple_enabled": (
        "Multiple mode enabled; click object to offset, or ESC to cancel",
        "多重模式已开启，点击要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.multiple_disabled": (
        "Multiple mode disabled; click object to offset, or ESC to cancel",
        "多重模式已关闭，点击要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.complete_warning": (
        "Offset complete (warning: %s)",
        "偏移完成（警告: %s）",
    ),
    "status.plot.offset.complete": ("Offset complete", "偏移完成"),
    "status.plot.offset.idle_hint": (
        "Click object to offset; D sets distance (%.2f); T toggles through-point; "
        "M toggles multiple; ESC cancels",
        "点击要偏移的对象，按D设置偏移距离(%.2f)，按T切换穿点模式，按M切换多重模式，或按ESC取消",
    ),
    "status.plot.offset.idle_hint_with_status": (
        "%s - click object to offset; D sets distance (%.2f); T toggles through-point; "
        "M toggles multiple; ESC cancels",
        "%s - 点击要偏移的对象，按D设置偏移距离(%.2f)，按T切换穿点模式，按M切换多重模式，或按ESC取消",
    ),
    "status.plot.offset.next_object": (
        "Click next object to offset, or ESC to cancel",
        "点击下一个要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.next_object_with_status": (
        "%s - click next object to offset, or ESC to cancel",
        "%s - 点击下一个要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.distance_preview": (
        "Offset distance: %.2f",
        "偏移距离: %.2f",
    ),
    "status.plot.offset.through_point_value": (
        "Through-point offset: point(%.1f, %.1f)",
        "穿点偏移: 点(%.1f, %.1f)",
    ),
    "status.plot.offset.distance_value": (
        "Distance offset: %.1f",
        "距离偏移: %.1f",
    ),
    "mode.plot.offset.state.idle.desc": (
        "Waiting to select shape to offset",
        "等待选择要偏移的图形",
    ),
    "mode.plot.offset.state.selecting": ("Selecting", "选择中"),
    "mode.plot.offset.state.selecting.desc": (
        "Shape selected; waiting for offset parameters",
        "已选择图形，等待确定偏移参数",
    ),
    "mode.plot.offset.state.offsetting": ("Offsetting", "偏移中"),
    "mode.plot.offset.state.offsetting.desc": (
        "Performing offset operation",
        "正在执行偏移操作",
    ),
    # --- array ---
    "status.plot.array.rect_params": (
        "Rectangular array: %dx%d, row spacing: %.1f, col spacing: %.1f; click to finish",
        "矩形阵列: %dx%d, 行间距: %.1f, 列间距: %.1f，点完成",
    ),
    "status.plot.array.polar_params": (
        "Polar array: %d items, radius: %.1f; click to confirm",
        "环形阵列: %d个, 半径: %.1f, 点击确认",
    ),
    "status.plot.array.created_count": (
        "Array created; %d shapes total",
        "阵列创建完成，共 %d 个图形",
    ),
    "status.plot.array.rect_preview_detail": (
        "Rectangular array preview: %dx%d, row spacing %.1f, col spacing %.1f",
        "矩形阵列预览中：%dx%d，行间距%.1f，列间距%.1f",
    ),
    "status.plot.array.polar_preview_detail": (
        "Polar array preview: %d items, radius %.1f",
        "环形阵列预览中：%d个，半径%.1f",
    ),
    "status.plot.array.path_preview_detail": (
        "Path array preview: %d points (incl. endpoints, equidistant), path length %.1f",
        "路径阵列预览中：点位数%d（含起终点，沿路径等距），路径长度%.1f",
    ),
    # --- chamfer ---
    "status.plot.chamfer.select_first_with_distance": (
        "Select first shape (line/polyline/polygon); +/- adjusts distance (%.1f); ESC cancels",
        "选择第一个图形（线/折线/多边形），按+/-调整距离(%.1f)，或按ESC取消",
    ),
    "status.plot.chamfer.select_second_corner": (
        "Select second shape (click same polyline/polygon again for corner chamfer)",
        "选择第二个图形（可再次点同一折线/多边形以倒角拐角）",
    ),
    "status.plot.chamfer.confirm_right": (
        "Right-click to confirm chamfer (distance %.1f); scroll/+/- adjust; ESC cancels",
        "按鼠标右键确认倒角(距离%.1f)，滚轮/+/-调整距离，或ESC取消",
    ),
    "status.plot.chamfer.confirm_right_scroll": (
        "Right-click to confirm chamfer (distance:%.1f); scroll adjusts distance; ESC cancels",
        "按鼠标右键确认倒角(距离:%.1f)，滚轮调整距离，或ESC取消",
    ),
    "status.plot.chamfer.complete_distance": (
        "Chamfer complete (distance: %.1f)",
        "倒角完成 (距离: %.1f)",
    ),
    "status.plot.chamfer.ready": ("Ready", "准备就绪"),
    # --- break ---
    "status.plot.break.select_hint": (
        "Select object to break; press T to switch mode (current: %s)",
        "选择要打断的对象，按T键切换模式（当前：%s）",
    ),
    # --- handlers ---
    "status.plot.move.vector": (
        "Move: dx=%.1f, dy=%.1f, distance=%.1f, angle=%.1f°",
        "移动: dx=%.1f, dy=%.1f, 距离=%.1f, 角度=%.1f°",
    ),
    "status.plot.rotate.angle": ("Rotate: %.1f°", "旋转: %.1f°"),
    "status.plot.rotate.angle_center": (
        "Rotate: %.1f° (center: %.1f, %.1f)",
        "旋转: %.1f° (中心: %.1f, %.1f)",
    ),
    "status.plot.mirror.central_status": (
        "Central symmetry%s: center=(%.1f, %.1f)",
        "中心对称%s: 中心=(%.1f, %.1f)",
    ),
    "status.plot.mirror.central_only": ("Central symmetry%s", "中心对称%s"),
    "status.plot.mirror.axis_status": (
        "Axis symmetry%s: length=%.1f, angle=%.1f°%s",
        "轴对称%s: 轴长=%.1f, 角度=%.1f°%s",
    ),
    "status.plot.mirror.axis_only": ("Axis symmetry%s", "轴对称%s"),
    "status.plot.mirror.constraint_horizontal": (" (horizontal)", " (水平)"),
    "status.plot.mirror.constraint_vertical": (" (vertical)", " (垂直)"),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-3 status keys")
