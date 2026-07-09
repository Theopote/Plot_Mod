#!/usr/bin/env python3
"""Merge batch-3 status/handler/overlay lang keys referenced in code but missing from lang files."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # --- extend (missing keys) ---
    "status.plot.common.added_to_selection": (
        "Added to selection: %d new, %d total",
        "已添加到选择，新增 %d 个图形，总计 %d 个",
    ),
    "status.plot.extend.box_boundary_done": (
        "Box select done! %d boundary shapes selected; extend mode active. Click endpoints to extend; ESC to reselect boundary",
        "框选完成！已选择 %d 个边界图形，自动进入延伸模式。点击要延伸的图形端点执行延伸，ESC重新选择边界",
    ),
    "status.plot.extend.boundary_confirmed": (
        "Boundary confirmed! %d shapes; click shape endpoints to extend; ESC to reselect boundary",
        "边界已确认！已选择 %d 个边界图形，点击要延伸的图形端点执行延伸，ESC重新选择边界",
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
        "Extend success! Shape %s extended; tool reset — reselect boundary shapes for next extend",
        "延伸成功！图形 %s 已延伸，工具已重置，请重新选择边界图形开始下一次延伸",
    ),
    "status.plot.extend.calc_failed": (
        "Extend failed: could not compute result; check boundaries (count: %d)",
        "延伸失败：无法计算延伸结果，请确保边界有效（当前边界数: %d）",
    ),
    "status.plot.extend.error_with_boundaries": (
        "Extend failed: %s (boundaries: %d); check boundary shapes",
        "延伸操作失败: %s，边界数: %d，请检查边界图形是否有效",
    ),
    "status.plot.extend.box_extend_done": (
        "Box extend done! Extended %d shapes; tool reset — reselect boundary shapes",
        "延伸框选完成！成功延伸 %d 个图形，工具已重置，请重新选择边界图形开始下一次延伸",
    ),
    "status.plot.extend.boundaries_selected": (
        "Selected %d boundary shapes; right-click to confirm and start extending",
        "已选择 %d 个边界图形，右键确认边界选择并开始延伸操作",
    ),
    "status.plot.extend.boundaries_cached": (
        "Cached %d boundary shapes",
        "已缓存 %d 个边界图形",
    ),
    "status.plot.extend.no_boundary_cache": (
        "No boundary shapes cached",
        "未缓存边界图形",
    ),
    # --- offset (missing keys) ---
    "status.plot.offset.distance_mode": (
        "Distance mode: offset distance %.2f; click object to offset, or Esc to cancel",
        "距离模式：偏移距离 %.2f，点击要偏移的对象，或按ESC取消",
    ),
    "status.plot.offset.mode_click": (
        "Switched to %s; click object to offset",
        "已切换到%s，点击要偏移的对象",
    ),
    "status.plot.offset.multiple_enabled": (
        "Multiple offset mode enabled",
        "多重偏移模式已启用",
    ),
    "status.plot.offset.multiple_disabled": (
        "Multiple offset mode disabled",
        "多重偏移模式已禁用",
    ),
    "status.plot.offset.complete_warning": (
        "Offset complete (warning: %s)",
        "偏移完成（警告: %s）",
    ),
    "status.plot.offset.complete": (
        "Offset complete",
        "偏移完成",
    ),
    "status.plot.offset.idle_hint": (
        "Click object to offset; press D to set distance (%.2f), T to toggle through-point mode, M for multiple mode, or Esc",
        "点击要偏移的对象，按D设置偏移距离(%.2f)，按T切换穿点模式，按M切换多重模式，或按ESC取消",
    ),
    "status.plot.offset.idle_hint_with_status": (
        "%s — click object to offset; distance %.2f, or Esc",
        "%s — 点击要偏移的对象，偏移距离 %.2f，或按ESC取消",
    ),
    "status.plot.offset.next_object": (
        "Offset complete; select next object",
        "偏移完成，请选择下一个要偏移的对象",
    ),
    "status.plot.offset.next_object_with_status": (
        "%s — select next object to offset",
        "%s — 请选择下一个要偏移的对象",
    ),
    "status.plot.offset.distance_preview": (
        "Offset distance: %.2f",
        "偏移距离: %.2f",
    ),
    # --- chamfer (missing keys) ---
    "status.plot.chamfer.select_first_with_distance": (
        "Select first shape (line/polyline/polygon); +/- to adjust distance (%.1f), or Esc",
        "选择第一个图形（线/折线/多边形），按+/-调整距离(%.1f)，或按ESC取消",
    ),
    "status.plot.chamfer.select_second_corner": (
        "Select second shape or adjacent corner on same polyline/polygon",
        "选择第二个图形或同一折线/多边形的邻边",
    ),
    "status.plot.chamfer.confirm_right": (
        "Right-click to confirm chamfer (distance %.1f); scroll/+/- to adjust, or Esc",
        "按鼠标右键确认倒角(距离%.1f)，滚轮/+/-调整距离，或ESC取消",
    ),
    "status.plot.chamfer.confirm_right_scroll": (
        "Right-click to confirm chamfer (distance %.1f); scroll to adjust, or Esc",
        "按鼠标右键确认倒角(距离:%.1f)，滚轮调整距离，或ESC取消",
    ),
    "status.plot.chamfer.ready": (
        "Ready to apply chamfer",
        "准备应用倒角",
    ),
    "status.plot.chamfer.complete_distance": (
        "Chamfer complete (distance: %.1f)",
        "倒角完成 (距离: %.1f)",
    ),
    "status.plot.chamfer.distance_min": (
        "Chamfer distance must be >= %.1f",
        "倒角距离必须 >= %.1f",
    ),
    "status.plot.chamfer.distance_too_large": (
        "Chamfer distance %.1f is too large for segment",
        "倒角距离 %.1f 过大，超出线段范围",
    ),
    # --- array (missing keys) ---
    "status.plot.array.rect_params": (
        "Rectangular array: %dx%d, row spacing: %.1f, column spacing: %.1f — click to finish",
        "矩形阵列: %dx%d, 行间距: %.1f, 列间距: %.1f，点完成",
    ),
    "status.plot.array.polar_params": (
        "Polar array: %d items, radius: %.1f — click to confirm",
        "环形阵列: %d个, 半径: %.1f, 点击确认",
    ),
    "status.plot.array.created_count": (
        "Array created: %d shapes",
        "阵列创建完成，共 %d 个图形",
    ),
    "status.plot.array.rect_preview_detail": (
        "Rectangular array preview: %dx%d, row spacing %.1f, column spacing %.1f",
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
    "status.plot.array.count_rectangular": (
        "Rectangular array: %d shapes",
        "矩形阵列: %d 个图形",
    ),
    "status.plot.array.count_polar": (
        "Polar array: %d shapes",
        "环形阵列: %d 个图形",
    ),
    "status.plot.array.count_path": (
        "Path array: %d shapes",
        "路径阵列: %d 个图形",
    ),
    "status.plot.array.path_points_insufficient": (
        "Insufficient path points: need at least 2, currently %d",
        "路径点不足，需要至少2个点，当前只有%d个",
    ),
    # --- break / selection ---
    "status.plot.break.select_hint": (
        "Select object to break; press T to switch mode (current: %s)",
        "选择要打断的对象，按T键切换模式（当前：%s）",
    ),
    "status.plot.select.box_left_to_right": (
        "Left-to-right: select fully contained shapes",
        "从左到右选择：只选择完全包含的图形",
    ),
    "status.plot.select.box_right_to_left": (
        "Right-to-left: select intersecting shapes",
        "从右到左选择：选择相交的图形",
    ),
    "status.plot.select.lasso_done": (
        "Lasso (fully contained) selection done; %d shapes selected",
        "套索（完全包含）选择完成，已选择 %d 个图形",
    ),
    "status.plot.select.select_all": (
        "Selected all %d shapes",
        "已全选 %d 个图形",
    ),
    # --- handler live status ---
    "status.plot.move.handler": (
        "Move: dx=%.1f, dy=%.1f, distance=%.1f, angle=%.1f°",
        "移动: dx=%.1f, dy=%.1f, 距离=%.1f, 角度=%.1f°",
    ),
    "status.plot.rotate.handler": (
        "Rotate: %.1f°",
        "旋转: %.1f°",
    ),
    "status.plot.rotate.handler_center": (
        "Rotate: %.1f° (center: %.1f, %.1f)",
        "旋转: %.1f° (中心: %.1f, %.1f)",
    ),
    "status.plot.mirror.central_handler": (
        "Central symmetry%s: center=(%.1f, %.1f)",
        "中心对称%s: 中心=(%.1f, %.1f)",
    ),
    "status.plot.mirror.central_handler_simple": (
        "Central symmetry%s",
        "中心对称%s",
    ),
    "status.plot.mirror.axis_handler": (
        "Axis symmetry%s: length=%.1f, angle=%.1f°%s",
        "轴对称%s: 轴长=%.1f, 角度=%.1f°%s",
    ),
    "status.plot.mirror.constraint_horizontal": (
        " (horizontal)",
        " (水平)",
    ),
    "status.plot.mirror.constraint_vertical": (
        " (vertical)",
        " (垂直)",
    ),
    "status.plot.offset.through_point_handler": (
        "Through-point offset: point(%.1f, %.1f)",
        "穿点偏移: 点(%.1f, %.1f)",
    ),
    "status.plot.offset.distance_handler": (
        "Distance offset: %.1f",
        "距离偏移: %.1f",
    ),
    # --- align validation ---
    "status.plot.align.no_selection": (
        "No shapes selected for alignment",
        "没有选择要对齐的图形",
    ),
    "status.plot.align.min_count": (
        "Select at least %d shapes",
        "至少需要选择 %d 个图形",
    ),
    # --- drawing overlays ---
    "status.plot.draw.sine.wavelength": (
        "Wavelength: %.1f  ",
        "波长: %.1f  ",
    ),
    "status.plot.draw.sine.length": (
        "Length: %.1f  ",
        "长度: %.1f  ",
    ),
    "status.plot.draw.sine.amplitude": (
        "Amplitude: %.1f  ",
        "振幅: %.1f  ",
    ),
    "status.plot.draw.sine.phase": (
        "Phase: %.1f°",
        "相位: %.1f°",
    ),
    "status.plot.draw.radius_label": (
        "Radius: %.1f",
        "半径: %.1f",
    ),
    # --- annotation units ---
    "annotation.plot.blocks_int": (
        "%d blocks",
        "%d 方块",
    ),
    "annotation.plot.blocks_float": (
        "%.2f blocks",
        "%.2f 方块",
    ),
    "annotation.plot.degrees": (
        "%.2f°",
        "%.2f°",
    ),
    "status.plot.draw.angle_label": (
        "%.1f°",
        "%.1f°",
    ),
    # --- boundary detection (area tool) ---
    "status.plot.boundary.enclosed": (
        "Enclosed region found; boundary coverage: %.1f%%",
        "找到封闭区域，边界覆盖率: %.1f%%",
    ),
    "status.plot.boundary.not_enclosed": (
        "Region not enclosed; coverage: %.1f%% (need at least %.1f%%)",
        "区域不封闭，边界覆盖率: %.1f%% (需要至少 %.1f%%)",
    ),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-3 keys")
