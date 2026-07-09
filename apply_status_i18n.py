#!/usr/bin/env python3
"""Migrate hardcoded Chinese tool status strings to status.plot.* i18n keys."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"
JAVA_ROOT = ROOT / "src/main/java/com/plot"

# (en, zh) — keys added to lang files
STATUS_KEYS = {
    # ── common ──
    "status.plot.common.select_modify_first": (
        "Select shapes to modify first",
        "请先选择要修改的图形",
    ),
    "status.plot.common.operation_cancelled": ("Operation cancelled", "操作已取消"),
    "status.plot.common.command_failed": ("Failed to create command", "创建命令失败"),
    "status.plot.common.invalid": ("Invalid: %s", "无效: %s"),
    "status.plot.common.complete": ("%s complete", "%s完成"),
    "status.plot.common.failed": ("%s failed: %s", "%s失败: %s"),
    "status.plot.common.selected_count": ("Selected %d shapes", "已选择 %d 个图形"),
    "status.plot.common.selected_suffix": ("Selected %1$d shapes, %2$s", "已选择 %1$d 个图形，%2$s"),
    "status.plot.common.selected_right_click": (
        "Selected %d shapes; right-click to start %s",
        "已选择 %d 个图形，右键开始%s",
    ),
    "status.plot.common.no_selection": ("No shapes selected for %s", "没有选中的图形可以%s"),
    "status.plot.common.select_first": ("Select shapes for %s first", "请先选择要%s的图形"),
    "status.plot.common.click_finish_suffix": ("%s - click to finish", "%s - 点击完成"),
    "status.plot.common.click_reference_preview": (
        "Click to set reference point; move mouse to preview",
        "点击设置参考点，移动鼠标查看预览",
    ),
    "status.plot.common.click_reference": ("Click to set reference point", "点击设置参考点"),
    "status.plot.common.draw_cancelled": ("Drawing cancelled", "绘制已取消"),
    "status.plot.common.tool_ready": ("%s ready", "%s工具就绪"),

    # ── modify tool initial hints ──
    "status.plot.move.initial": (
        "Select shapes with left-click; right-click to finish selection and enter move mode",
        "左键选择图形，右键完成选择并进入移动模式",
    ),
    "status.plot.rotate.initial_center": ("Click to set rotation center", "点击设置旋转中心点"),
    "status.plot.rotate.initial_select": ("Select shapes to rotate first", "请先选择要旋转的图形"),
    "status.plot.rotate.move_finish": (
        "Move mouse to rotate; click to finish",
        "移动鼠标旋转图形，点击完成",
    ),
    "status.plot.mirror.initial_center": ("Click to set symmetry center", "点击设置对称中心"),
    "status.plot.mirror.initial_axis": ("Click to set mirror axis start", "点击设置镜像轴起点"),
    "status.plot.mirror.initial_select": ("Select shapes to mirror first", "请先选择要镜像的图形"),
    "status.plot.mirror.center_confirm": (
        "Symmetry center set; click to confirm (hold Ctrl to copy)",
        "已设置对称中心，点击确认完成 (按住Ctrl可复制)",
    ),
    "status.plot.mirror.axis_drag": (
        "Move mouse to set mirror axis end; click to finish (Shift: orthogonal, Ctrl: copy)",
        "移动鼠标设置镜像轴终点，点击完成 (按住Shift正交，按住Ctrl复制)",
    ),
    "status.plot.scale.initial_select": ("Select shapes to scale first", "请先选择要缩放的图形"),
    "status.plot.scale.initial_center": (
        "Selected %d shapes; click to set scale center",
        "已选择 %d 个图形，点击设置缩放中心点",
    ),
    "status.plot.scale.move_finish": (
        "Move mouse to scale; click to finish",
        "移动鼠标缩放图形，点击完成",
    ),
    "status.plot.align.initial_modes": (
        "Choose align mode: Left(L), Right(R), Center(C), Top(T), Bottom(B), Middle(M)",
        "选择对齐方式：左对齐(L)、右对齐(R)、中心对齐(C)、顶部对齐(T)、底部对齐(B)、中间对齐(M)",
    ),
    "status.plot.align.initial_select": ("Select shapes to align first", "请先选择要对齐的图形"),
    "status.plot.align.source1": (
        "Source point 1 set; click target point 1 (anywhere)",
        "已选源点1，点击目标点1（可以是任意位置）",
    ),
    "status.plot.align.target1_source2": ("Target point 1 set; click source point 2", "已选目标点1，点击源点2"),
    "status.plot.align.source2_target2": (
        "Source point 2 set; click target point 2 to finish (anywhere)",
        "已选源点2，点击目标点2以完成（可以是任意位置）",
    ),
    "status.plot.align.four_point": (
        "Selected %d shapes; right-click to start four-point align",
        "已选择 %d 个图形，右键开始四点对齐",
    ),
    "status.plot.align.select_shapes": ("Select shapes to align", "请选择要对齐的图形"),
    "status.plot.offset.initial": (
        "Click a linear shape for reference, then click second point on target side to offset-copy",
        "点击线类图形确定参考点 → 在目标侧点击第二点，完成偏移复制",
    ),
    "status.plot.fillet.initial": ("Select the first edge to fillet", "选择要圆角的第一条边"),
    "status.plot.chamfer.initial": ("Select the first edge to chamfer", "选择要倒角的第一条边"),
    "status.plot.break.initial": (
        "Select object to break; press T to switch mode",
        "选择要打断的对象，按T键切换模式",
    ),
    "status.plot.extend.initial": (
        "Left-click boundary shapes, right-click to confirm, then left-click endpoints to extend",
        "请左键选择边界图形，右键确认选择，然后左键点击要延伸的图形端点",
    ),
    "status.plot.array.initial": (
        "Click shapes to array → choose type (R/C/P) → set parameters",
        "点击选择要阵列的图形 → 选择阵列类型(R/C/P) → 设置参数",
    ),
    "status.plot.array.pick_path": ("Click a path object on canvas", "在画布上点击路径对象进行拾取"),
    "status.plot.array.pick_objects": ("Click objects to array on canvas", "在画布上点击要阵列的物件"),
    "status.plot.array.mode_switched": ("Switched to %s mode", "已切换到%s模式"),
    "status.plot.transform.initial": (
        "Select shapes to transform; right-click to start",
        "选择要变换的图形，右键开始变换",
    ),
    "status.plot.eraser.initial": (
        "Click or drag to delete shapes; Esc to cancel",
        "点击或拖拽删除图形，ESC键取消",
    ),
    "status.plot.trim.boundary_select": (
        "Select boundary shapes; right-click to confirm",
        "选择边界图形，右键完成选择",
    ),
    "status.plot.trim.target_select": (
        "Select shapes to trim; right-click to confirm",
        "选择要修剪的图形，右键完成选择",
    ),
    "status.plot.trim.initial": ("Select shapes to start trimming", "选择图形开始修剪"),
    "status.plot.control_point.inactive": (
        "Select a shape to edit control points",
        "选择一个图形来编辑其控制点",
    ),
    "status.plot.control_point.active": (
        "Drag control points to adjust shape; Esc to exit",
        "拖拽控制点来调整图形形状，ESC键退出编辑模式",
    ),

    # ── per-tool cancel / complete ──
    "status.plot.move.cancelled": ("Move cancelled", "移动已取消"),
    "status.plot.rotate.cancelled": ("Rotate cancelled", "旋转已取消"),
    "status.plot.mirror.cancelled": ("Mirror cancelled", "镜像已取消"),
    "status.plot.scale.cancelled": ("Scale cancelled", "缩放已取消"),
    "status.plot.align.cancelled": ("Align cancelled", "对齐已取消"),
    "status.plot.array.cancelled": ("Array cancelled", "阵列已取消"),
    "status.plot.trim.cancelled": ("Trim cancelled", "修剪已取消"),
    "status.plot.offset.cancelled": ("Offset cancelled", "偏移操作已取消"),
    "status.plot.eraser.cancelled": ("Delete cancelled", "删除已取消"),
    "status.plot.move.complete": ("Move complete", "移动完成"),
    "status.plot.rotate.complete": ("Rotate complete", "旋转完成"),
    "status.plot.mirror.complete": ("Mirror complete", "镜像完成"),
    "status.plot.scale.complete": ("Scale complete", "缩放完成"),
    "status.plot.align.complete": ("Align complete", "对齐完成"),
    "status.plot.mirror.command_failed": ("Failed to create mirror command", "创建镜像命令失败"),
    "status.plot.rotate.command_failed": ("Failed to create rotate command", "创建旋转命令失败"),
    "status.plot.scale.command_failed": ("Failed to create scale command", "创建缩放命令失败"),
    "status.plot.align.command_failed": ("Failed to create align command", "创建对齐命令失败"),
    "status.plot.mirror.invalid": ("Mirror invalid: %s", "镜像无效: %s"),
    "status.plot.rotate.invalid": ("Rotate invalid: %s", "旋转无效: %s"),
    "status.plot.scale.invalid": ("Scale invalid: %s", "缩放无效: %s"),
    "status.plot.move.too_small": ("Move distance too small", "移动距离太小"),
    "status.plot.move.command_failed": ("Failed to create move command", "创建移动命令失败"),
    "status.plot.move.invalid": ("Move invalid: %s", "移动无效: %s"),
    "status.plot.move.base_set": (
        "Base point set; move to target and click to finish",
        "已设置基点，移动鼠标到目标位置，点击完成移动",
    ),
    "status.plot.move.select_shapes": ("Select shapes to move", "请选择要移动的图形"),

    # ── drawing ──
    "status.plot.draw.arc_ready": ("Arc tool ready", "弧形工具就绪"),
    "status.plot.draw.semicircle_ready": ("Semicircle tool ready", "半圆工具就绪"),
    "status.plot.draw.polyline_add_vertex": (
        "Click to add next vertex; right-click or Esc to finish",
        "点击添加下一个顶点，右键或Esc键结束绘制",
    ),
    "status.plot.draw.circle_collinear": (
        "Three points are collinear; pick three non-collinear points",
        "三点共线，无法绘制圆形。请重新选择三个不共线的点。",
    ),

    # ── fillet / chamfer ──
    "status.plot.fillet.select_first": (
        "Click first shape, or Esc to cancel",
        "点击选择第一个图形，或按ESC取消",
    ),
    "status.plot.fillet.select_valid": ("Select a filletable shape", "请选择一个可圆角的图形"),
    "status.plot.fillet.select_second": (
        "Click second shape; scroll to adjust radius, or Esc",
        "点击选择第二个图形，滚轮调整半径，或按ESC取消",
    ),
    "status.plot.fillet.select_different": ("Select a different shape", "请选择不同的图形"),
    "status.plot.fillet.select_second_valid": ("Select a second filletable shape", "请选择第二个可圆角的图形"),
    "status.plot.fillet.invalid_mode": ("Invalid mode", "无效模式"),
    "status.plot.chamfer.select_second": ("Select second shape", "选择第二个图形"),
    "status.plot.chamfer.select_different_edge": (
        "Pick different shapes (polyline/polygon: different adjacent edges on same object)",
        "请选择不同图形（折线/多边形可点同一对象不同邻边）",
    ),
    "status.plot.chamfer.command_failed": ("Failed to create chamfer command", "创建倒角命令失败"),

    # ── offset ──
    "status.plot.offset.click_inside": ("Click inside or on shape outline", "请点击图形轮廓或内部"),
    "status.plot.offset.no_linear": ("No offsettable linear shape found", "未找到可偏移的线类图形"),
    "status.plot.offset.reference_set": (
        "Reference point set; click second point on target side",
        "已选择图形并记录参考点，请在目标侧点击第二点",
    ),
    "status.plot.offset.generate_failed": (
        "Offset failed: could not generate offset shape",
        "偏移失败：无法生成偏移图形",
    ),
    "status.plot.offset.invalid": ("Offset invalid: %s", "偏移无效: %s"),
    "status.plot.offset.retry": ("Offset failed; please retry", "偏移操作失败，请重试"),
    "status.plot.offset.command_failed": ("Failed to create offset command", "创建偏移命令失败"),
    "status.plot.offset.failed": ("Offset operation failed", "偏移操作失败"),

    # ── eraser ──
    "status.plot.eraser.not_found": ("No shapes found to delete", "没有找到要删除的图形"),
    "status.plot.eraser.dragging": ("Deleting while dragging...", "拖拽删除中..."),

    # ── transform ──
    "status.plot.transform.back_selection": ("Returned to selection mode", "已返回选择模式"),
    "status.plot.transform.selection_cancelled": ("Selection cancelled", "选择已取消"),
    "status.plot.transform.enter_mode": (
        "Transform mode: drag control points; right-click to return to selection",
        "进入变换模式，拖拽控制点进行操作，右键返回选择。",
    ),
    "status.plot.transform.invalid_selection": (
        "Invalid selection; please select again",
        "选中的图形无效，请重新选择。",
    ),
    "status.plot.transform.select_then_confirm": (
        "Select shapes first, then right-click to confirm",
        "请先选择图形，然后右键确认。",
    ),
    "status.plot.transform.back_with_count": (
        "Back to selection; %d shapes selected",
        "返回选择模式，当前已选择 %d 个图形。",
    ),
    "status.plot.transform.drag_control": ("Drag control points to transform", "拖拽控制点进行变换"),
    "status.plot.transform.drag_move": ("Drag to move selection", "拖拽移动选择集"),
    "status.plot.transform.preview": ("Transform preview...", "变换预览中..."),
    "status.plot.transform.done_keep_box": (
        "Transform complete; transform box remains visible",
        "变换完成，变换框继续显示",
    ),
    "status.plot.transform.exec_failed": ("Transform failed: %s", "变换执行失败: %s"),

    # ── spiral dynamic (scroll adjust) ──
    "status.plot.spiral.coefficient": (
        "Spiral coefficient: %.2f (scroll to adjust)",
        "螺旋系数: %.2f (滚轮调整)",
    ),
    "status.plot.spiral.growth": (
        "Growth factor: %.2f (scroll to adjust)",
        "生长因子: %.2f (滚轮调整)",
    ),
    "status.plot.spiral.expansion": (
        "Expansion rate: %.2f (scroll to adjust)",
        "扩张率: %.2f (滚轮调整)",
    ),
    "status.plot.mirror.no_selection": ("No shapes selected to mirror", "没有选中的图形可以镜像"),
    "status.plot.rotate.no_selection": ("No shapes selected to rotate", "没有选中的图形可以旋转"),
    "status.plot.scale.no_selection": ("No shapes selected to scale", "没有选中的图形可以缩放"),
    "status.plot.align.no_selection": ("No shapes selected to align", "没有选中的图形可以对齐"),
    "status.plot.array.no_selection": ("No shapes selected to array", "没有选中的图形可以阵列"),
    "status.plot.move.no_selection": ("No shapes selected to move", "没有选中的图形可以移动"),
    "status.plot.array.select_first": ("Select shapes to array first", "请先选择要阵列的图形"),
    "status.plot.array.select_shapes": ("Select shapes to array", "请选择要阵列的图形"),
    "status.plot.spiral.pitch": (
        "Pitch: %.1f (scroll to adjust)",
        "螺距: %.1f (滚轮调整)",
    ),
}

# Exact string -> i18n key (used inside quotes for setStatusMessage / return / constants)
EXACT_MAP = {
    "请先选择要修改的图形": "status.plot.common.select_modify_first",
    "操作已取消": "status.plot.common.operation_cancelled",
    "左键选择图形，右键完成选择并进入移动模式": "status.plot.move.initial",
    "点击设置旋转中心点": "status.plot.rotate.initial_center",
    "请先选择要旋转的图形": "status.plot.rotate.initial_select",
    "移动鼠标旋转图形，点击完成": "status.plot.rotate.move_finish",
    "点击设置对称中心": "status.plot.mirror.initial_center",
    "点击设置镜像轴起点": "status.plot.mirror.initial_axis",
    "请先选择要镜像的图形": "status.plot.mirror.initial_select",
    "已设置对称中心，点击确认完成 (按住Ctrl可复制)": "status.plot.mirror.center_confirm",
    "移动鼠标设置镜像轴终点，点击完成 (按住Shift正交，按住Ctrl复制)": "status.plot.mirror.axis_drag",
    "请先选择要缩放的图形": "status.plot.scale.initial_select",
    "移动鼠标缩放图形，点击完成": "status.plot.scale.move_finish",
    "点击设置参考点，移动鼠标查看预览": "status.plot.common.click_reference_preview",
    "点击设置参考点": "status.plot.common.click_reference",
    "选择对齐方式：左对齐(L)、右对齐(R)、中心对齐(C)、顶部对齐(T)、底部对齐(B)、中间对齐(M)": "status.plot.align.initial_modes",
    "请先选择要对齐的图形": "status.plot.align.initial_select",
    "已选源点1，点击目标点1（可以是任意位置）": "status.plot.align.source1",
    "已选目标点1，点击源点2": "status.plot.align.target1_source2",
    "已选源点2，点击目标点2以完成（可以是任意位置）": "status.plot.align.source2_target2",
    "请选择要对齐的图形": "status.plot.align.select_shapes",
    "点击线类图形确定参考点 → 在目标侧点击第二点，完成偏移复制": "status.plot.offset.initial",
    "选择要圆角的第一条边": "status.plot.fillet.initial",
    "选择要倒角的第一条边": "status.plot.chamfer.initial",
    "选择要打断的对象，按T键切换模式": "status.plot.break.initial",
    "请左键选择边界图形，右键确认选择，然后左键点击要延伸的图形端点": "status.plot.extend.initial",
    "点击选择要阵列的图形 → 选择阵列类型(R/C/P) → 设置参数": "status.plot.array.initial",
    "在画布上点击路径对象进行拾取": "status.plot.array.pick_path",
    "在画布上点击要阵列的物件": "status.plot.array.pick_objects",
    "选择要变换的图形，右键开始变换": "status.plot.transform.initial",
    "点击或拖拽删除图形，ESC键取消": "status.plot.eraser.initial",
    "选择边界图形，右键完成选择": "status.plot.trim.boundary_select",
    "选择要修剪的图形，右键完成选择": "status.plot.trim.target_select",
    "选择图形开始修剪": "status.plot.trim.initial",
    "选择一个图形来编辑其控制点": "status.plot.control_point.inactive",
    "拖拽控制点来调整图形形状，ESC键退出编辑模式": "status.plot.control_point.active",
    "移动已取消": "status.plot.move.cancelled",
    "旋转已取消": "status.plot.rotate.cancelled",
    "镜像已取消": "status.plot.mirror.cancelled",
    "缩放已取消": "status.plot.scale.cancelled",
    "对齐已取消": "status.plot.align.cancelled",
    "阵列已取消": "status.plot.array.cancelled",
    "偏移操作已取消": "status.plot.offset.cancelled",
    "删除已取消": "status.plot.eraser.cancelled",
    "移动完成": "status.plot.move.complete",
    "旋转完成": "status.plot.rotate.complete",
    "镜像完成": "status.plot.mirror.complete",
    "缩放完成": "status.plot.scale.complete",
    "对齐完成": "status.plot.align.complete",
    "创建镜像命令失败": "status.plot.mirror.command_failed",
    "创建旋转命令失败": "status.plot.rotate.command_failed",
    "创建缩放命令失败": "status.plot.scale.command_failed",
    "创建对齐命令失败": "status.plot.align.command_failed",
    "创建移动命令失败": "status.plot.move.command_failed",
    "移动距离太小": "status.plot.move.too_small",
    "已设置基点，移动鼠标到目标位置，点击完成移动": "status.plot.move.base_set",
    "请选择要移动的图形": "status.plot.move.select_shapes",
    "绘制已取消": "status.plot.common.draw_cancelled",
    "弧形工具就绪": "status.plot.draw.arc_ready",
    "半圆工具就绪": "status.plot.draw.semicircle_ready",
    "点击添加下一个顶点，右键或Esc键结束绘制": "status.plot.draw.polyline_add_vertex",
    "三点共线，无法绘制圆形。请重新选择三个不共线的点。": "status.plot.draw.circle_collinear",
    "点击选择第一个图形，或按ESC取消": "status.plot.fillet.select_first",
    "请选择一个可圆角的图形": "status.plot.fillet.select_valid",
    "点击选择第二个图形，滚轮调整半径，或按ESC取消": "status.plot.fillet.select_second",
    "请选择不同的图形": "status.plot.fillet.select_different",
    "请选择第二个可圆角的图形": "status.plot.fillet.select_second_valid",
    "无效模式": "status.plot.fillet.invalid_mode",
    "选择第二个图形": "status.plot.chamfer.select_second",
    "请选择不同图形（折线/多边形可点同一对象不同邻边）": "status.plot.chamfer.select_different_edge",
    "创建倒角命令失败": "status.plot.chamfer.command_failed",
    "请点击图形轮廓或内部": "status.plot.offset.click_inside",
    "未找到可偏移的线类图形": "status.plot.offset.no_linear",
    "已选择图形并记录参考点，请在目标侧点击第二点": "status.plot.offset.reference_set",
    "偏移失败：无法生成偏移图形": "status.plot.offset.generate_failed",
    "偏移操作失败，请重试": "status.plot.offset.retry",
    "创建偏移命令失败": "status.plot.offset.command_failed",
    "偏移操作失败": "status.plot.offset.failed",
    "没有找到要删除的图形": "status.plot.eraser.not_found",
    "拖拽删除中...": "status.plot.eraser.dragging",
    "已返回选择模式": "status.plot.transform.back_selection",
    "选择已取消": "status.plot.transform.selection_cancelled",
    "进入变换模式，拖拽控制点进行操作，右键返回选择。": "status.plot.transform.enter_mode",
    "选中的图形无效，请重新选择。": "status.plot.transform.invalid_selection",
    "请先选择图形，然后右键确认。": "status.plot.transform.select_then_confirm",
    "拖拽控制点进行变换": "status.plot.transform.drag_control",
    "拖拽移动选择集": "status.plot.transform.drag_move",
    "变换预览中...": "status.plot.transform.preview",
    "变换完成，变换框继续显示": "status.plot.transform.done_keep_box",
    "没有选中的图形可以镜像": "status.plot.mirror.no_selection",
    "没有选中的图形可以旋转": "status.plot.rotate.no_selection",
    "没有选中的图形可以缩放": "status.plot.scale.no_selection",
    "没有选中的图形可以对齐": "status.plot.align.no_selection",
    "没有选中的图形可以阵列": "status.plot.array.no_selection",
    "没有选中的图形可以移动": "status.plot.move.no_selection",
    "请先选择要阵列的图形": "status.plot.array.select_first",
    "请选择要阵列的图形": "status.plot.array.select_shapes",
}

# Parameterized pattern replacements (regex -> replacement template)
REGEX_REPLACEMENTS = [
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，点击设置缩放中心点"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.scale.initial_center", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，点击设置旋转中心点"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.selected_suffix", selectedShapeIds.size(), PlotI18n.status("status.plot.rotate.initial_center")))',
    ),
    (
        r'String hint = \(mirrorMode == MirrorMode\.CENTRAL_SYMMETRY\) \? "点击设置对称中心" : "点击设置镜像轴起点";\s*\n\s*context\.setStatusMessage\("已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形，" \+ hint\);',
        'String hint = (mirrorMode == MirrorMode.CENTRAL_SYMMETRY)\n                ? "status.plot.mirror.initial_center"\n                : "status.plot.mirror.initial_axis";\n                context.setStatusMessage(PlotI18n.status("status.plot.common.selected_suffix", selectedShapeIds.size(), PlotI18n.localizeStatus(hint)));',
    ),
    (
        r'context\.setStatusMessage\("镜像无效: " \+ validation\.getErrorMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.mirror.invalid", validation.getErrorMessage()))',
    ),
    (
        r'context\.setStatusMessage\("旋转无效: " \+ validation\.getErrorMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.rotate.invalid", validation.getErrorMessage()))',
    ),
    (
        r'context\.setStatusMessage\("缩放无效: " \+ validation\.getErrorMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.scale.invalid", validation.getErrorMessage()))',
    ),
    (
        r'context\.setStatusMessage\("移动无效: " \+ validation\.getErrorMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.move.invalid", validation.getErrorMessage()))',
    ),
    (
        r'context\.setStatusMessage\("偏移无效: " \+ validation\.getErrorMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.offset.invalid", validation.getErrorMessage()))',
    ),
    (
        r'context\.setStatusMessage\("对齐失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.failed", PlotI18n.tr("tool.plot.align"), e.getMessage()))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ count \+ " 个图形，右键开始四点对齐"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.align.four_point", count))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ count \+ " 个图形，右键开始移动"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.selected_right_click", count, PlotI18n.tr("tool.plot.move")))',
    ),
    (
        r'context\.setStatusMessage\("已选择 " \+ count \+ " 个图形，右键开始阵列操作"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.common.selected_right_click", count, PlotI18n.tr("tool.plot.array")))',
    ),
    (
        r'context\.setStatusMessage\("返回选择模式，当前已选择 " \+ selectedShapeIds\.size\(\) \+ " 个图形。"\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.transform.back_with_count", selectedShapeIds.size()))',
    ),
    (
        r'context\.setStatusMessage\("变换执行失败: " \+ e\.getMessage\(\)\)',
        'context.setStatusMessage(PlotI18n.status("status.plot.transform.exec_failed", e.getMessage()))',
    ),
    (
        r'updateStatusMessage\("已切换到" \+ newType\.getDisplayName\(\) \+ "模式"\)',
        'updateStatusMessage(PlotI18n.status("status.plot.array.mode_switched", newType.getDisplayName()))',
    ),
    (
        r'setStatusMessage\("弧形工具就绪"\)',
        'setStatusMessage("status.plot.draw.arc_ready")',
    ),
    (
        r'setStatusMessage\("半圆工具就绪"\)',
        'setStatusMessage("status.plot.draw.semicircle_ready")',
    ),
    (
        r'setStatusMessage\("绘制已取消"\)',
        'setStatusMessage("status.plot.common.draw_cancelled")',
    ),
    (
        r'updateStatusMessage\("选择要打断的对象，按T键切换模式"\)',
        'updateStatusMessage("status.plot.break.initial")',
    ),
    (
        r'updateStatusMessage\("请左键选择边界图形，右键确认选择，然后左键点击要延伸的图形端点"\)',
        'updateStatusMessage("status.plot.extend.initial")',
    ),
    (
        r'context\.updateStatusMessage\("点击添加下一个顶点，右键或Esc键结束绘制"\)',
        'context.updateStatusMessage("status.plot.draw.polyline_add_vertex")',
    ),
    (
        r'setStatusMessage\(String\.format\("螺旋系数: %.2f \(滚轮调整\)", newSpiralCoefficient\)\)',
        'setStatusMessage(PlotI18n.status("status.plot.spiral.coefficient", newSpiralCoefficient))',
    ),
    (
        r'setStatusMessage\(String\.format\("生长因子: %.2f \(滚轮调整\)", newGrowthFactor\)\)',
        'setStatusMessage(PlotI18n.status("status.plot.spiral.growth", newGrowthFactor))',
    ),
    (
        r'setStatusMessage\(String\.format\("扩张率: %.2f \(滚轮调整\)", newExpansionRate\)\)',
        'setStatusMessage(PlotI18n.status("status.plot.spiral.expansion", newExpansionRate))',
    ),
    (
        r'setStatusMessage\(String\.format\("螺距: %.1f \(滚轮调整\)", newSpacing\)\)',
        'setStatusMessage(PlotI18n.status("status.plot.spiral.pitch", newSpacing))',
    ),
    (
        r'setStatusMessage\("三点共线，无法绘制圆形。请重新选择三个不共线的点。"\)',
        'setStatusMessage("status.plot.draw.circle_collinear")',
    ),
]

IMPORT_LINE = "import com.plot.utils.PlotI18n;\n"
PLOT_I18N_IMPORT = "import com.plot.utils.PlotI18n;"


def merge_lang():
    for name in ("en_us.json", "zh_cn.json"):
        path = LANG_DIR / name
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, (en_val, zh_val) in STATUS_KEYS.items():
            data[key] = en_val if name.startswith("en") else zh_val
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Merged {len(STATUS_KEYS)} status keys")


def needs_plot_i18n(content: str) -> bool:
    return "PlotI18n." in content and PLOT_I18N_IMPORT not in content


def add_import(content: str) -> str:
    if PLOT_I18N_IMPORT in content:
        return content
    if "PlotI18n." not in content:
        return content
    # insert after last import
    idx = content.rfind("import ")
    if idx == -1:
        return IMPORT_LINE + content
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
