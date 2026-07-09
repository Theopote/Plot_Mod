#!/usr/bin/env python3
"""Batch 2: remaining tool status strings -> status.plot.* keys."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"
JAVA_ROOT = ROOT / "src/main/java/com/plot"
PLOT_I18N_IMPORT = "import com.plot.utils.PlotI18n;"

STATUS_KEYS = {
    # common
    "status.plot.common.no_app_state": ("Cannot get application state", "无法获取应用状态"),
    "status.plot.common.no_selection_generic": ("No shapes selected", "没有选中的图形"),
    "status.plot.common.selection_cleared": ("All selections cleared", "已取消所有选择"),
    "status.plot.common.select_all_failed": ("Select all failed: %s", "全选失败: %s"),
    "status.plot.common.delete_failed": ("Delete failed: %s", "删除失败: %s"),
    "status.plot.common.selected_right_click_op": (
        "Selected %d shapes; right-click to start operation",
        "已选择 %d 个图形，右键开始操作",
    ),
    "status.plot.common.box_select_done": (
        "Box select done; %d shapes selected; right-click to start operation",
        "框选完成，已选择 %d 个图形，右键开始操作",
    ),
    "status.plot.common.continuous_cancelled": ("Continuous mode cancelled", "持续模式已取消"),
    "status.plot.common.deleted_count": ("Deleted %d shapes", "删除了 %d 个图形"),
    "status.plot.common.drag_delete_count": (
        "Deleting while dragging... %d shapes selected",
        "拖拽删除中... 已选中 %d 个图形",
    ),

    # move / align selection
    "status.plot.move.initial_base": (
        "Selected %d shapes; click to set move base point",
        "已选择 %d 个图形，点击设置移动基点",
    ),
    "status.plot.move.initial_select": ("Select shapes to move first", "请先选择要移动的图形"),
    "status.plot.align.initial_source1": (
        "Selected %d shapes; click source point 1",
        "已选择 %d 个图形，点击源点1",
    ),
    "status.plot.align.source1_short": ("Source point 1 set; click target point 1", "已选源点1，点击目标点1"),
    "status.plot.align.source2_short": (
        "Source point 2 set; click target point 2 to finish",
        "已选源点2，点击目标点2以完成",
    ),

    # mirror / rotate
    "status.plot.mirror.failed_missing": (
        "Mirror failed: missing required parameters",
        "镜像操作失败：缺少必要参数",
    ),
    "status.plot.mirror.failed_invalid_state": ("Mirror failed: invalid state", "镜像操作失败：无效状态"),
    "status.plot.mirror.failed_invalid_params": ("Mirror failed: invalid parameters", "镜像操作失败：参数无效"),
    "status.plot.mirror.failed": ("Mirror failed: %s", "镜像失败: %s"),
    "status.plot.rotate.process_failed": (
        "Cannot process selected shapes; please retry",
        "无法处理选中的图形，请重试",
    ),
    "status.plot.rotate.retry": ("Rotate operation failed; please retry", "旋转操作失败，请重试"),
    "status.plot.rotate.copy_complete": ("Copy-rotate complete", "复制旋转完成"),
    "status.plot.rotate.copy_command_failed": (
        "Failed to create copy-rotate command",
        "创建复制旋转命令失败",
    ),

    # break
    "status.plot.break.not_found": ("No breakable object found", "未找到可打断对象"),
    "status.plot.break.invalid_click": ("Invalid click position; please select again", "点击位置无效，请重新选择"),
    "status.plot.break.click_second": ("Click to set second break point", "点击设置第二个打断点"),
    "status.plot.break.invalid_first": (
        "Invalid first click position; please select again",
        "第一个点击位置无效，请重新选择",
    ),
    "status.plot.break.click_closer": (
        "Click closer to the line for the second break point",
        "请点击更靠近线条的位置设置第二个打断点",
    ),
    "status.plot.break.same_shape": (
        "Click second break point on the same shape",
        "请在同一图形上点击设置第二个打断点",
    ),
    "status.plot.break.too_close": (
        "Break points too close; pick a farther second point",
        "两个打断点太近，请选择更远的第二个点",
    ),
    "status.plot.break.incomplete_params": ("Break parameters incomplete", "打断操作参数不完整"),
    "status.plot.break.need_second_point": (
        "Two-point break mode requires a second break point",
        "两点打断模式需要设置第二个打断点",
    ),
    "status.plot.break.command_failed": (
        "Failed to create break command; please retry",
        "创建打断命令失败，请重试",
    ),
    "status.plot.break.exec_failed": ("Break operation failed: %s", "打断操作执行失败: %s"),
    "status.plot.break.system_error": ("Break failed: system state error", "打断操作失败：系统状态错误"),
    "status.plot.break.generate_failed": (
        "Break failed: could not generate new shapes",
        "打断操作失败：无法生成新图形",
    ),
    "status.plot.break.create_failed": ("Failed to create break command: %s", "打断命令创建失败: %s"),

    # trim
    "status.plot.trim.select_boundary_first": ("Select boundary shapes first", "请先选择边界图形"),
    "status.plot.trim.boundary_click_side": (
        "Selected %d boundary shapes; click the side of the shape to trim",
        "已选择 %d 个边界图形，点击要修剪的图形一侧",
    ),
    "status.plot.trim.boundary_click_side_continuous": (
        "Selected %d boundary shapes; click side to trim (continuous mode)",
        "已选择 %d 个边界图形，点击要修剪的图形一侧（持续模式）",
    ),
    "status.plot.trim.select_target_first": ("Select shapes to trim first", "请先选择要修剪的图形"),
    "status.plot.trim.fence_continue": (
        "Selected %d shapes; continue fence trim (continuous mode)",
        "已选择 %d 个图形，可以继续使用栅栏修剪（持续模式）",
    ),
    "status.plot.trim.fence_start": (
        "Selected %d shapes; start drawing fence",
        "已选择 %d 个图形，开始绘制栅栏",
    ),
    "status.plot.trim.fence_min_points": ("Fence needs at least 3 points", "栅栏至少需要3个点"),
    "status.plot.trim.fence_invalid_points": ("Invalid fence points; redraw", "栅栏点无效，请重新绘制"),
    "status.plot.trim.fence_continuous_cancelled": (
        "Fence continuous mode cancelled",
        "栅栏持续模式已取消",
    ),
    "status.plot.trim.cancelled_reselect_boundary": (
        "Trim cancelled; reselect boundary shapes",
        "修剪已取消，重新选择边界图形",
    ),
    "status.plot.trim.boundary_continuous_cancelled": (
        "Boundary continuous mode cancelled",
        "边界持续模式已取消",
    ),
    "status.plot.trim.fence_draw_start": (
        "Drawing fence region; left-click to add points, right-click to finish",
        "开始绘制栅栏区域，左键添加点，右键完成",
    ),
    "status.plot.trim.fence_point_count": (
        "%d fence points; right-click to finish drawing",
        "栅栏点 %d 个，右键完成栅栏绘制",
    ),
    "status.plot.trim.fence_base_set": (
        "Fence base set; left-click second point, right-click to finish",
        "已设置栅栏基点，左键设置第二点，右键完成",
    ),
    "status.plot.trim.fence_too_small": (
        "Fence range too small; reset second point",
        "栅栏范围过小，请重新设置第二点",
    ),
    "status.plot.trim.fence_generated": (
        "%s fence generated; right-click to trim (left-click to reset)",
        "已生成%s栅栏，右键执行修剪（左键可重设）",
    ),
    "status.plot.trim.boundary_deselected": (
        "Removed 1 boundary shape; %d remaining",
        "已取消 1 个边界图形，当前 %d 个",
    ),
    "status.plot.trim.target_deselected": (
        "Removed 1 target shape; %d remaining",
        "已取消 1 个目标图形，当前 %d 个",
    ),
    "status.plot.trim.mode_boundary": ("Switched to boundary trim mode", "切换到边界修剪模式"),
    "status.plot.trim.mode_fence": ("Switched to fence trim mode", "切换到栅栏修剪模式"),
    "status.plot.trim.not_found": ("No shape found to trim", "未找到要修剪的图形"),
    "status.plot.trim.non_boundary_only": (
        "Select a non-boundary shape to trim",
        "请选择非边界图形进行修剪",
    ),
    "status.plot.trim.generate_failed": (
        "Trim failed: could not generate trimmed shape",
        "修剪失败：无法生成修剪图形",
    ),
    "status.plot.trim.no_change": (
        "Trim unchanged; click the side to remove",
        "修剪结果无变化，请点击要删除的一侧",
    ),
    "status.plot.trim.complete": ("Trim complete", "修剪完成"),
    "status.plot.trim.complete_continuous": (
        "Trim complete; continue trimming other shapes (continuous mode)",
        "修剪完成，可以继续修剪其他图形（持续模式）",
    ),
    "status.plot.trim.command_failed": ("Failed to create trim command", "创建修剪命令失败"),
    "status.plot.trim.failed": ("Trim failed: %s", "修剪失败: %s"),
    "status.plot.trim.fence_generate_failed": (
        "Fence trim failed: could not generate trimmed shape",
        "栅栏修剪失败：无法生成修剪图形",
    ),
    "status.plot.trim.fence_complete": ("Fence trim complete", "栅栏修剪完成"),
    "status.plot.trim.fence_complete_continuous": (
        "Fence trim complete; continue fence trim (continuous mode)",
        "栅栏修剪完成，可以继续使用栅栏修剪（持续模式）",
    ),
    "status.plot.trim.fence_command_failed": (
        "Failed to create fence trim command",
        "创建栅栏修剪命令失败",
    ),
    "status.plot.trim.fence_failed": ("Fence trim failed: %s", "栅栏修剪失败: %s"),
    "status.plot.trim.box_select_done": (
        "Box select done; %d shapes selected",
        "框选完成，已选择 %d 个图形",
    ),
    "status.plot.trim.boundary_selected": (
        "Selected %d boundary shapes; right-click to confirm or continue",
        "已选择 %d 个边界图形，右键确认或继续选择",
    ),
    "status.plot.trim.target_selected": (
        "Selected %d target shapes; right-click to confirm or continue",
        "已选择 %d 个目标图形，右键确认或继续选择",
    ),
    "status.plot.trim.click_position": ("Click the position on the shape to trim", "请点击要修剪的图形位置"),

    # extend
    "status.plot.extend.select_boundary_empty": (
        "Select boundary shapes first (none selected)",
        "请先选择边界图形（当前未选择任何图形）",
    ),
    "status.plot.extend.select_boundary": ("Select boundary shapes first", "请先选择边界图形"),
    "status.plot.extend.hover_shape": ("Move mouse over the shape to extend", "请将鼠标移动到要延伸的图形上"),
    "status.plot.extend.hover_and_click": (
        "Move mouse over shape to extend and click",
        "请将鼠标移动到要延伸的图形上并点击",
    ),
    "status.plot.extend.failed": ("Extend operation failed: %s", "延伸操作失败: %s"),
    "status.plot.extend.box_no_shapes": (
        "No extendable shapes found in box selection",
        "框选区域内没有找到可延伸的图形",
    ),
    "status.plot.extend.box_no_endpoints": (
        "No extendable endpoints found in box selection",
        "框选区域内没有找到可延伸的图形端点",
    ),
    "status.plot.extend.box_failed": ("Extend box selection failed: %s", "延伸框选操作失败: %s"),
    "status.plot.extend.shape_not_found": (
        "Shape not found; select boundary shapes",
        "未找到图形，请选择边界图形",
    ),
    "status.plot.extend.command_failed": (
        "Extend failed: could not create modify command",
        "延伸操作失败：无法创建修改命令",
    ),
    "status.plot.extend.select_boundary_required": (
        "Select boundary shapes (none selected)",
        "请选择边界图形（当前未选择任何图形）",
    ),

    # array
    "status.plot.array.rect_preview_auto": (
        "Rectangular array preview: auto 3×3; drag spacing anchors or adjust panel; click Finish",
        "矩形阵列预览：已自动生成 3×3，可拖拽间距锚点或在面板调整，点击“完成”确认",
    ),
    "status.plot.array.polar_center": (
        "Selected %d shapes; click polar array center (default 6 items)",
        "已选择 %d 个图形，点击设置环形阵列中心（默认 6 个）",
    ),
    "status.plot.array.path_pick": (
        "Selected %d shapes; left-click path (count = equidistant points incl. endpoints)",
        "已选择 %d 个图形，左键点击选择路径（数量=路径等距点位数，含起终点）",
    ),
    "status.plot.array.params_confirmed": (
        "Parameters confirmed; drag other anchors or click Finish",
        "参数已确认：可继续拖拽其他锚点，或点击“完成”确认阵列",
    ),
    "status.plot.array.drag_col_spacing": (
        "Dragging column spacing: move mouse to adjust, click to confirm",
        "正在拖拽列间距：移动鼠标调整，单击确认",
    ),
    "status.plot.array.drag_row_spacing": (
        "Dragging row spacing: move mouse to adjust, click to confirm",
        "正在拖拽行间距：移动鼠标调整，单击确认",
    ),
    "status.plot.array.drag_radius": (
        "Dragging radius: move mouse to adjust, click to confirm",
        "正在拖拽半径：移动鼠标调整，单击确认",
    ),
    "status.plot.array.rect_preview": (
        "Rectangular array preview: drag spacing anchors or adjust panel; click Finish",
        "矩形阵列预览中：可拖拽间距锚点或在面板调整，点击“完成”确认",
    ),
    "status.plot.array.polar_preview": (
        "Polar array preview: default 6; adjust count/radius in panel; click Finish",
        "环形阵列预览中：默认 6 个，可在面板调整数量/半径，点击“完成”确认",
    ),
    "status.plot.array.pick_path_object": ("Click to select path object", "点击选择路径对象"),
    "status.plot.array.path_selected": (
        "Path selected; adjust point count in panel (incl. endpoints); click Finish",
        "已选择路径：可在面板调整点位数（含起终点，沿路径等距），点击“完成”确认",
    ),
    "status.plot.array.path_invalid": (
        "Selected object cannot be used as path (not enough points)",
        "所选对象无法作为路径（点数不足）",
    ),
    "status.plot.array.path_points_failed": ("Cannot get path points", "无法获取路径点"),
    "status.plot.array.path_not_selected": ("No path object selected", "未选中路径对象"),
    "status.plot.array.mode_rect": ("Switched to rectangular array mode", "切换到矩形阵列模式"),
    "status.plot.array.mode_polar": ("Switched to polar array mode", "切换到环形阵列模式"),
    "status.plot.array.mode_path": ("Switched to path array mode", "切换到路径阵列模式"),
    "status.plot.array.row_spacing": ("Row spacing: %s", "行间距: %s"),
    "status.plot.array.col_spacing": ("Column spacing: %s", "列间距: %s"),
    "status.plot.array.path_count": ("Count: %d", "数量: %d"),
    "status.plot.array.path_picked": (
        "Path picked; adjust point count (incl. endpoints); click Finish",
        "已拾取路径，调整点位数（含起终点，沿路径等距）后点击完成",
    ),
    "status.plot.array.objects_picked": (
        "Objects picked; adjust point count (incl. endpoints); click Finish",
        "已拾取物件，调整点位数（含起终点，沿路径等距）后点击完成",
    ),
    "status.plot.array.rows_increased": ("Row count increased", "行数已增加"),
    "status.plot.array.rows_decreased": ("Row count decreased", "行数已减少"),
    "status.plot.array.cols_decreased": ("Column count decreased", "列数已减少"),
    "status.plot.array.cols_increased": ("Column count increased", "列数已增加"),
    "status.plot.array.click_shapes": ("Click shapes to create array", "请点击要创建阵列的图形"),
    "status.plot.array.set_base": (
        "Click anywhere to set array base/center point",
        "点击任意位置设置阵列基准/中心点",
    ),
    "status.plot.array.use_panel_path": (
        "Use panel to pick path and objects, then click Finish by path point count",
        "请使用面板按钮拾取路径与物件，然后按路径等距点位数量点击完成",
    ),
    "status.plot.array.confirm_failed": (
        "Cannot confirm array: missing required conditions",
        "无法确认阵列：缺少必要条件",
    ),
    "status.plot.array.command_failed": ("Failed to create array command", "创建阵列命令失败"),
    "status.plot.array.click_select": ("Click to select shapes to array", "点击选择要阵列的图形"),
    "status.plot.array.set_reference": ("Click to set array reference point", "点击设置阵列基准点"),
    "status.plot.array.use_panel_pick_path": (
        "Use panel to pick path object",
        "请使用面板拾取路径对象",
    ),

    # offset / chamfer / control point / selection / annotation / draw
    "status.plot.offset.no_shape_at_click": (
        "No offsettable shape at click position",
        "点击位置没有找到可偏移的图形",
    ),
    "status.plot.offset.click_point": (
        "Click to specify offset point, or Esc to cancel",
        "点击指定偏移点，或按ESC取消",
    ),
    "status.plot.offset.click_direction": (
        "Click to specify offset direction and distance, or Esc to cancel",
        "点击指定偏移方向和距离，或按ESC取消",
    ),
    "status.plot.offset.complete_continue": (
        "Offset complete (distance %.2f); select next line",
        "偏移完成（距离 %.2f），请继续选择下一条线",
    ),
    "status.plot.chamfer.select_valid": ("Select a chamferable shape", "请选择一个可倒角的图形"),
    "status.plot.chamfer.select_second_scroll": (
        "Click second shape; scroll to adjust distance, or Esc",
        "点击选择第二个图形，滚轮调整距离，或按ESC取消",
    ),
    "status.plot.chamfer.select_second_valid": (
        "Select a second chamferable shape",
        "请选择第二个可倒角图形",
    ),
    "status.plot.control_point.drag": ("Drag control points to adjust shape", "拖拽控制点调整图形形状"),
    "status.plot.control_point.exited": ("Exited control point edit mode", "已退出控制点编辑模式"),
    "status.plot.control_point.click_drag": (
        "Click and drag control points to adjust shape",
        "点击并拖拽控制点来调整图形",
    ),
    "status.plot.control_point.drag_exit": (
        "Drag control points to adjust; click empty area to exit",
        "拖拽控制点来调整图形形状，点击空白处退出编辑",
    ),
    "status.plot.control_point.complete": ("Control point edit complete", "控制点编辑完成"),
    "status.plot.control_point.entered": (
        "Entered control point edit mode; drag control points",
        "已进入控制点编辑模式，拖拽控制点调整图形",
    ),
    "status.plot.select.click_second_distance": (
        "Click second point to finish distance annotation",
        "点击第二点完成距离标注",
    ),
    "status.plot.select.distance_created": ("Distance annotation created", "距离标注已创建"),
    "status.plot.select.distance_cancelled": ("Distance measurement cancelled", "已取消距离测量"),
    "status.plot.select.select_first": ("Select shapes first", "请先选择图形"),
    "status.plot.select.angle_created": ("Angle annotation created", "角度标注已创建"),
    "status.plot.select.inner_angle_created": ("Inner angle annotation created", "内角标注已创建"),
    "status.plot.select.angle_hint": (
        "Angle annotation: pick two lines, or one polyline/polygon",
        "角度标注：可选两条直线，或单条折线/多边形",
    ),
    "status.plot.select.radius_created": ("Radius annotation created", "半径标注已创建"),
    "status.plot.select.area_created": ("Area annotation created", "面积标注已创建"),
    "status.plot.select.area_closed_required": (
        "Area annotation requires closed shape (polygon, rectangle, circle, ellipse, or closed polyline)",
        "面积标注需要闭合图形（多边形、矩形、圆形、椭圆或闭合折线）",
    ),
    "status.plot.draw.circle_ready": ("Circle tool ready", "圆形工具就绪"),
}

EXACT_MAP = {zh: key for key, (_, zh) in STATUS_KEYS.items()}

REGEX_REPLACEMENTS = [
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，" \+ hint\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.selected_suffix", selectedShapeIds.size(), PlotI18n.localizeStatus(hint)))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，点击设置移动基点"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.move.initial_base", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，点击源点1"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.align.initial_source1", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，右键开始操作"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.selected_right_click_op", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("框选完成，已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，右键开始操作"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.box_select_done", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\(statusMessage \+ " - 点击完成移动"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix", PlotI18n.localizeStatus(statusMessage)))',
    ),
    (
        r'context\.setStatusMessage\(statusMessage \+ " - 点击完成缩放"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix", PlotI18n.localizeStatus(statusMessage)))',
    ),
    (
        r'context\.setStatusMessage\(statusMessage \+ " - 点击完成旋转"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix", PlotI18n.localizeStatus(statusMessage)))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ boundaryShapes\.size\(\) \+ " 个边界图形，点击要修剪的图形一侧（持续模式）"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.boundary_click_side_continuous", boundaryShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ boundaryShapes\.size\(\) \+ " 个边界图形，点击要修剪的图形一侧"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.boundary_click_side", boundaryShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ targetShapes\.size\(\) \+ " 个图形，可以继续使用栅栏修剪（持续模式）"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.fence_continue", targetShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ targetShapes\.size\(\) \+ " 个图形，开始绘制栅栏"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.fence_start", targetShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("栅栏点 " \+ fencePoints\.size\(\) \+ " 个，右键完成栅栏绘制"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.fence_point_count", fencePoints.size()))',
    ),
    (
        r'context\.setStatusMessage\("已生成" \+ fenceType\.getDisplayName\(\) \+ "栅栏，右键执行修剪（左键可重设）"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.fence_generated", fenceType.getDisplayName()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("已取消 1 个边界图形，当前 %d 个", boundaryShapes\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.boundary_deselected", boundaryShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("已取消 1 个目标图形，当前 %d 个", targetShapes\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.target_deselected", targetShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("修剪失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.failed", e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\("栅栏修剪失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.fence_failed", e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("框选完成，已选择 %d 个图形", boxSelectedShapes\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.box_select_done", boxSelectedShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("已选择 %d 个边界图形，右键确认或继续选择", boundaryShapes\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.boundary_selected", boundaryShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("已选择 %d 个目标图形，右键确认或继续选择", targetShapes\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.trim.target_selected", targetShapes.size()))',
    ),
    (
        r'context\.setStatusMessage\("延伸操作失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.extend.failed", e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\("延伸框选操作失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.extend.box_failed", e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，点击设置环形阵列中心（默认 6 个）"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.polar_center", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，左键点击选择路径（数量=路径等距点位数，含起终点）"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.path_pick", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("行间距: " \+ String\.format\("%.1f", rowSpacing\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.row_spacing", String.format("%.1f", rowSpacing)))',
    ),
    (
        r'context\.setStatusMessage\("列间距: " \+ String\.format\("%.1f", spacing\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.col_spacing", String.format("%.1f", spacing)))',
    ),
    (
        r'context\.setStatusMessage\("列间距: " \+ String\.format\("%.1f", colSpacing\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.col_spacing", String.format("%.1f", colSpacing)))',
    ),
    (
        r'context\.setStatusMessage\("数量: " \+ pathCount\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.array.path_count", pathCount))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("删除了 %d 个图形", shapesToDelete\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.deleted_count", shapesToDelete.size()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("拖拽删除中\.\.\. 已选中 %d 个图形", shapesToDelete\.size\(\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.drag_delete_count", shapesToDelete.size()))',
    ),
    (
        r'context\.setStatusMessage\(String\.format\("偏移完成（距离 %.2f），请继续选择下一条线", Math\.abs\(signedDistance\)\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.offset.complete_continue", Math.abs(signedDistance)))',
    ),
    (
        r'context\.setStatusMessage\("全选失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.select_all_failed", e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\("删除失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.delete_failed", e.getMessage()))',
    ),
    (
        r'\(\(ModifyToolContext\) context\)\.setStatusMessage\("打断命令创建失败: " \+ e\.getMessage\(\)\)',
        '((ModifyToolContext) context).setStatusMessage(PlotI18n.status("status.plot.break.create_failed", e.getMessage()))',
    ),
    (
        r'setStatusMessage\("圆形工具就绪"\)',
        'setStatusMessage("status.plot.draw.circle_ready")',
    ),
]


def merge_lang():
    for name in ("en_us.json", "zh_cn.json"):
        path = LANG_DIR / name
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, (en_val, zh_val) in STATUS_KEYS.items():
            data[key] = en_val if name.startswith("en") else zh_val
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Merged {len(STATUS_KEYS)} status keys")


def add_import(content: str) -> str:
    if PLOT_I18N_IMPORT in content or "PlotI18n." not in content:
        return content
    idx = content.rfind("import ")
    if idx == -1:
        return PLOT_I18N_IMPORT + "\n" + content
    end = content.find(";", idx) + 1
    return content[:end] + "\n" + PLOT_I18N_IMPORT + content[end:]


def patch_java_files():
    changed = 0
    for path in JAVA_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        original = text
        for old, key in EXACT_MAP.items():
            text = text.replace(f'"{old}"', f'"{key}"')
        for pattern, repl in REGEX_REPLACEMENTS:
            text = re.sub(pattern, repl, text)
        if text != original:
            text = add_import(text)
            path.write_text(text, encoding="utf-8")
            changed += 1
            print(f"  patched {path.relative_to(ROOT)}")
    print(f"Patched {changed} Java files")


if __name__ == "__main__":
    merge_lang()
    patch_java_files()
