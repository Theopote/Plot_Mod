import re
from pathlib import Path

BASE = Path('src/main/java/com/plot')
IMPORT = 'import com.plot.utils.PlotI18n;'

def ensure_import(content):
    if 'import com.plot.utils.PlotI18n' in content:
        return content
    m = re.search(r'(package [^;]+;\n\n)', content)
    if m:
        return content[:m.end()] + IMPORT + '\n' + content[m.end():]
    m = re.search(r'(package [^;]+;\n)', content)
    if m:
        return content[:m.end()] + '\n' + IMPORT + '\n' + content[m.end():]
    return content

def apply_replacements(path, replacements):
    p = BASE / path
    if not p.exists():
        print(f'MISSING: {path}')
        return False
    content = p.read_text(encoding='utf-8')
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
    content = ensure_import(content)
    if content != original:
        p.write_text(content, encoding='utf-8')
        print(f'UPDATED: {path}')
        return True
    print(f'NOCHANGE: {path}')
    return False

draw_mode_files = [
    'ui/panel/tool/renderer/ArcToolOptionRenderer.java',
    'ui/panel/tool/renderer/EllipseToolOptionRenderer.java',
    'ui/panel/tool/renderer/CircleToolOptionRenderer.java',
    'ui/panel/tool/renderer/SplineToolOptionRenderer.java',
    'ui/panel/tool/renderer/RectangleToolOptionRenderer.java',
    'ui/panel/tool/renderer/CatenaryLineToolOptionRenderer.java',
    'ui/panel/tool/renderer/SemicircleToolOptionRenderer.java',
    'ui/panel/tool/renderer/PolylineToolOptionRenderer.java',
    'ui/panel/tool/renderer/PolygonToolOptionRenderer.java',
]
for f in draw_mode_files:
    apply_replacements(f, [('ImGui.text("绘制模式")', 'ImGui.text(PlotI18n.tr("option.plot.draw_mode"))')])

apply_replacements('ui/panel/tool/renderer/FreeDrawToolOptionRenderer.java', [
    ('ImGui.text("平滑度")', 'ImGui.text(PlotI18n.tr("option.plot.smoothing"))'),
])
apply_replacements('ui/panel/tool/renderer/SelectionToolOptionRenderer.java', [
    ('ImGui.text("选择模式")', 'ImGui.text(PlotI18n.tr("option.plot.selection_mode"))'),
])
apply_replacements('ui/panel/tool/renderer/OffsetToolOptionRenderer.java', [
    ('ImGui.text("多重偏移")', 'ImGui.text(PlotI18n.tr("option.plot.multiple_offset"))'),
])
apply_replacements('ui/panel/tool/renderer/EraserToolOptionRenderer.java', [
    ('ImGui.text("橡皮擦大小")', 'ImGui.text(PlotI18n.tr("option.plot.eraser_size"))'),
])
apply_replacements('ui/panel/tool/renderer/MirrorToolOptionRenderer.java', [
    ('ImGui.text("对称模式")', 'ImGui.text(PlotI18n.tr("option.plot.symmetry_mode"))'),
])
apply_replacements('ui/panel/tool/renderer/SineToolOptionRenderer.java', [
    ('ImGui.text("相位")', 'ImGui.text(PlotI18n.tr("option.plot.phase"))'),
])
apply_replacements('ui/panel/tool/renderer/BreakToolOptionRenderer.java', [
    ('ImGui.text("打断模式")', 'ImGui.text(PlotI18n.tr("option.plot.break_mode"))'),
])
apply_replacements('ui/panel/tool/renderer/FilletToolOptionRenderer.java', [
    ('ImGui.text("半径")', 'ImGui.text(PlotI18n.tr("option.plot.radius"))'),
])
apply_replacements('ui/panel/tool/renderer/ChamferToolOptionRenderer.java', [
    ('ImGui.text("倒角距离")', 'ImGui.text(PlotI18n.tr("option.plot.chamfer_distance"))'),
])
apply_replacements('ui/panel/tool/renderer/StarToolOptionRenderer.java', [
    ('ImGui.text("顶点数量")', 'ImGui.text(PlotI18n.tr("option.plot.vertex_count"))'),
    ('ImGui.text("内扭转")', 'ImGui.text(PlotI18n.tr("option.plot.inner_twist"))'),
    ('ImGui.text("外扭转")', 'ImGui.text(PlotI18n.tr("option.plot.outer_twist"))'),
])
apply_replacements('ui/panel/tool/renderer/LineToolOptionRenderer.java', [
    ('ImGui.text("线型")', 'ImGui.text(PlotI18n.tr("option.plot.line_type"))'),
    ('ImGui.text("线条数量")', 'ImGui.text(PlotI18n.tr("option.plot.line_count"))'),
    ('ImGui.text("线条间距")', 'ImGui.text(PlotI18n.tr("option.plot.line_spacing"))'),
])
apply_replacements('ui/panel/tool/renderer/RotateToolOptionRenderer.java', [
    ('ImGui.text("请选择旋转工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_rotate_tool"))'),
    ('ImGui.text("角度步长")', 'ImGui.text(PlotI18n.tr("option.plot.angle_step"))'),
    ('ImGui.text("预设值")', 'ImGui.text(PlotI18n.tr("option.plot.preset_values"))'),
    ('ImGui.text("角度吸附")', 'ImGui.text(PlotI18n.tr("option.plot.angle_snap"))'),
])
apply_replacements('ui/panel/tool/renderer/TransformToolOptionRenderer.java', [
    ('ImGui.text("请选择变换工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_transform_tool"))'),
    ('ImGui.text("旋转功能")', 'ImGui.text(PlotI18n.tr("option.plot.rotation_feature"))'),
])
apply_replacements('ui/panel/tool/renderer/AlignToolOptionRenderer.java', [
    ('ImGui.text("对齐模式(已简化)")', 'ImGui.text(PlotI18n.tr("option.plot.align_mode"))'),
    ('ImGui.text("参考模式(已简化)")', 'ImGui.text(PlotI18n.tr("option.plot.reference_mode"))'),
    ('ImGui.text("缩放设置")', 'ImGui.text(PlotI18n.tr("option.plot.scale_settings"))'),
    ('if (ImGui.checkbox("允许缩放", scaleEnabled))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.allow_scale"), scaleEnabled))'),
])
apply_replacements('ui/panel/tool/renderer/ScaleToolOptionRenderer.java', [
    ('if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen))', 'if (ImGui.collapsingHeader(PlotI18n.tr("option.plot.usage_instructions"), ImGuiTreeNodeFlags.DefaultOpen))'),
])
apply_replacements('ui/panel/tool/renderer/AnnotationToolOptionRenderer.java', [
    ('ImGui.text("模式")', 'ImGui.text(PlotI18n.tr("option.plot.annotation_mode"))'),
    ('if (ImGui.button("距离", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_distance"), BUTTON_SIZE, BUTTON_SIZE))'),
    ('if (ImGui.button("角度", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_angle"), BUTTON_SIZE, BUTTON_SIZE))'),
    ('if (ImGui.button("半径", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_radius"), BUTTON_SIZE, BUTTON_SIZE))'),
    ('if (ImGui.button("面积", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_area"), BUTTON_SIZE, BUTTON_SIZE))'),
])
apply_replacements('ui/panel/tool/renderer/SpiralToolOptionRenderer.java', [
    ('ImGui.text("尖角样式")', 'ImGui.text(PlotI18n.tr("option.plot.sharp_style"))'),
    ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
    ('ImGui.text("逆时针")', 'ImGui.text(PlotI18n.tr("option.plot.clockwise"))'),
    ('ImGui.text("请选择螺旋线工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_spiral_tool"))'),
    ('ImGui.text("螺旋类型")', 'ImGui.text(PlotI18n.tr("option.plot.spiral_type"))'),
    ('ImGui.text("当前状态")', 'ImGui.text(PlotI18n.tr("option.plot.current_state"))'),
])
apply_replacements('ui/panel/tool/renderer/PolygonToolOptionRenderer.java', [
    ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
])
apply_replacements('ui/panel/tool/renderer/ArrayToolOptionRenderer.java', [
    ('ImGui.text("阵列类型")', 'ImGui.text(PlotI18n.tr("option.plot.array_type"))'),
    ('if (ImGui.button("矩形阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.rectangular"), 80, 30))'),
    ('if (ImGui.button("环形阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.polar"), 80, 30))'),
    ('if (ImGui.button("路径阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.path"), 80, 30))'),
    ('ImGui.text("行数")', 'ImGui.text(PlotI18n.tr("option.plot.row_count"))'),
    ('ImGui.text("列数")', 'ImGui.text(PlotI18n.tr("option.plot.column_count"))'),
    ('ImGui.text("行间距")', 'ImGui.text(PlotI18n.tr("option.plot.row_spacing"))'),
    ('ImGui.text("列间距")', 'ImGui.text(PlotI18n.tr("option.plot.column_spacing"))'),
    ('if (ImGui.button("完成", 80, 24))', 'if (ImGui.button(PlotI18n.tr("button.plot.done"), 80, 24))'),
    ('ImGui.text("数量")', 'ImGui.text(PlotI18n.tr("option.plot.count"))'),
    ('ImGui.text("半径")', 'ImGui.text(PlotI18n.tr("option.plot.radius"))'),
    ('ImGui.text("角度间隔")', 'ImGui.text(PlotI18n.tr("option.plot.angle_interval"))'),
    ('ImGui.text("点位数")', 'ImGui.text(PlotI18n.tr("option.plot.point_count"))'),
    ('ImGui.text("路径长度")', 'ImGui.text(PlotI18n.tr("option.plot.path_length"))'),
    ('ImGui.text("等距步长")', 'ImGui.text(PlotI18n.tr("option.plot.step_distance"))'),
    ('if (ImGui.button("完成", 90, 26))', 'if (ImGui.button(PlotI18n.tr("button.plot.done"), 90, 26))'),
])
apply_replacements('ui/panel/tool/renderer/TrimToolOptionRenderer.java', [
    ('ImGui.text("当前状态")', 'ImGui.text(PlotI18n.tr("option.plot.current_state"))'),
    ('ImGui.text("选择模式")', 'ImGui.text(PlotI18n.tr("option.plot.selection_mode"))'),
    ('ImGui.text("修剪模式")', 'ImGui.text(PlotI18n.tr("option.plot.trim_mode"))'),
    ('ImGui.text("处理中")', 'ImGui.text(PlotI18n.tr("option.plot.processing"))'),
    ('ImGui.text("未知状态")', 'ImGui.text(PlotI18n.tr("option.plot.unknown_state"))'),
    ('if (ImGui.button("边界修剪", 80, 30))', 'if (ImGui.button(PlotI18n.tr("trim.plot.boundary"), 80, 30))'),
    ('if (ImGui.button("栅栏修剪", 80, 30))', 'if (ImGui.button(PlotI18n.tr("option.plot.fence_trim"), 80, 30))'),
    ('ImGui.text("栅栏类型")', 'ImGui.text(PlotI18n.tr("option.plot.fence_type"))'),
    ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
    ('ImGui.text("修剪容差")', 'ImGui.text(PlotI18n.tr("option.plot.trim_tolerance"))'),
])
apply_replacements('ui/panel/tool/renderer/TextToolOptionRenderer.java', [
    ('ImGui.text("说明")', 'ImGui.text(PlotI18n.tr("option.plot.text_description"))'),
    ('ImGui.text("字体大小")', 'ImGui.text(PlotI18n.tr("option.plot.font_size"))'),
    ('ImGui.text("样式")', 'ImGui.text(PlotI18n.tr("option.plot.style"))'),
    ('if (ImGui.checkbox("粗体##bold", tempBold))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_bold") + "##bold", tempBold))'),
    ('if (ImGui.checkbox("斜体##italic", tempItalic))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_italic") + "##italic", tempItalic))'),
    ('ImGui.text("对齐")', 'ImGui.text(PlotI18n.tr("option.plot.text_align"))'),
    ('ImGui.text("行高")', 'ImGui.text(PlotI18n.tr("option.plot.text_line_height"))'),
    ('ImGui.text("输入方式")', 'ImGui.text(PlotI18n.tr("option.plot.input_method"))'),
    ('if (ImGui.checkbox("使用对话框##use_dialog", tempUseDialog))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_use_dialog") + "##use_dialog", tempUseDialog))'),
    ('ImGui.text("操作")', 'ImGui.text(PlotI18n.tr("option.plot.text_operations"))'),
    ('if (ImGui.button("重置大小", 80, 20))', 'if (ImGui.button(PlotI18n.tr("button.plot.reset_size"), 80, 20))'),
    ('if (ImGui.button("转换图形", 80, 20))', 'if (ImGui.button(PlotI18n.tr("button.plot.convert_shape"), 80, 20))'),
])

apply_replacements('ui/panel/layer/LayerContextMenuRenderer.java', [
    ('if (ImGui.menuItem("设为活动图层", "", isActive))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.set_active"), "", isActive))'),
    ('ImGui.menuItem("设为活动图层", "图层已锁定", false, false)', 'ImGui.menuItem(PlotI18n.tr("layer.plot.set_active"), PlotI18n.tr("layer.plot.locked"), false, false)'),
    ('if (ImGui.menuItem("重命名", "双击图层名称"))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.rename"), PlotI18n.tr("layer.plot.rename_hint")))'),
    ('ImGui.menuItem("重命名", "图层已锁定", false, false)', 'ImGui.menuItem(PlotI18n.tr("layer.plot.rename"), PlotI18n.tr("layer.plot.locked"), false, false)'),
    ('if (ImGui.menuItem("复制图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.copy"), ""))'),
    ('if (ImGui.menuItem("删除图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.delete"), ""))'),
    ('ImGui.menuItem("删除图层", "图层已锁定", false, false)', 'ImGui.menuItem(PlotI18n.tr("layer.plot.delete"), PlotI18n.tr("layer.plot.locked"), false, false)'),
    ('if (ImGui.menuItem("解锁图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.unlock"), ""))'),
    ('if (ImGui.menuItem("锁定图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.lock"), ""))'),
    ('if (ImGui.menuItem("隐藏图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.hide"), ""))'),
    ('if (ImGui.menuItem("显示图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.show"), ""))'),
    ('if (ImGui.menuItem("合并选中图层", ""))', 'if (ImGui.menuItem(PlotI18n.tr("layer.plot.merge_selected"), ""))'),
    ('ImGui.menuItem("合并选中图层", disabledReason, false, false)', 'ImGui.menuItem(PlotI18n.tr("layer.plot.merge_selected"), disabledReason, false, false)'),
])
apply_replacements('ui/panel/layer/NewLayerDialog.java', [
    ('if (ImGui.button("重新编辑名称", 0, 0))', 'if (ImGui.button(PlotI18n.tr("button.plot.reedit_name"), 0, 0))'),
])

apply_replacements('core/snap/SnapManager.java', [
    ('if (ImGui.collapsingHeader("几何特征吸附"))', 'if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.geom_features")))'),
    ('settingsChanged |= ImGui.checkbox("端点吸附", settings.endPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.end_point"), settings.endPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("中点吸附", settings.midPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.mid_point"), settings.midPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("圆心吸附", settings.centerPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.circle_center"), settings.centerPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("中心点吸附", settings.centroidSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.center_point"), settings.centroidSnap)'),
    ('settingsChanged |= ImGui.checkbox("角点吸附", settings.vertexSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.vertex"), settings.vertexSnap)'),
    ('settingsChanged |= ImGui.checkbox("象限点吸附", settings.quadrantSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.quadrant"), settings.quadrantSnap)'),
    ('settingsChanged |= ImGui.checkbox("网格点吸附", settings.gridPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.grid_point"), settings.gridPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("垂足吸附", settings.perpendicularSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.perpendicular"), settings.perpendicularSnap)'),
    ('settingsChanged |= ImGui.checkbox("交点吸附", settings.intersectionSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.intersection"), settings.intersectionSnap)'),
    ('settingsChanged |= ImGui.checkbox("最近点吸附", settings.nearestPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.nearest_point"), settings.nearestPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("控制点吸附", settings.controlPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.control_point"), settings.controlPointSnap)'),
    ('settingsChanged |= ImGui.checkbox("切点吸附", settings.tangentPointSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.tangent_point"), settings.tangentPointSnap)'),
    ('if (ImGui.collapsingHeader("几何关系约束"))', 'if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.geom_relations")))'),
    ('settingsChanged |= ImGui.checkbox("水平约束", settings.horizontalSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.horizontal"), settings.horizontalSnap)'),
    ('settingsChanged |= ImGui.checkbox("竖直约束", settings.verticalSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.vertical"), settings.verticalSnap)'),
    ('settingsChanged |= ImGui.checkbox("平行约束", settings.parallelSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.parallel"), settings.parallelSnap)'),
    ('settingsChanged |= ImGui.checkbox("延长线约束", settings.extensionSnap)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.extension"), settings.extensionSnap)'),
    ('if (ImGui.collapsingHeader("吸附设置"))', 'if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.constraint_settings")))'),
    ('settingsChanged |= ImGui.checkbox("排除隐藏图层", settings.excludeHiddenLayers)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.exclude_hidden_layers"), settings.excludeHiddenLayers)'),
    ('settingsChanged |= ImGui.checkbox("临时禁用 (Shift)", settings.tempDisableWithShift)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.temp_disable_shift"), settings.tempDisableWithShift)'),
    ('settingsChanged |= ImGui.checkbox("吸附标记预览", settings.showSnapMarkers)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.show_markers_preview"), settings.showSnapMarkers)'),
    ('settingsChanged |= ImGui.checkbox("标记动画", settings.enableMarkerPulse)', 'settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.enable_marker_pulse"), settings.enableMarkerPulse)'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("重置默认", "确定", DialogStyleManager.getContentWidth())', 'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.reset"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

apply_replacements('ui/dialog/SettingsAndHelpDialog.java', [
    ('if (ImGui.beginTabItem("快捷键"))', 'if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.shortcuts")))'),
    ('if (ImGui.beginTabItem("吸附与反馈"))', 'if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.feedback")))'),
    ('if (ImGui.beginTabItem("帮助与教程"))', 'if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.help")))'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("返回", "完成", DialogStyleManager.getContentWidth())', 'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.done"), DialogStyleManager.getContentWidth())'),
    ('ImGui.text("搜索：")', 'ImGui.text(PlotI18n.tr("settings.plot.search"))'),
    ('if (ImGui.button("重置为默认"))', 'if (ImGui.button(PlotI18n.tr("button.plot.reset")))'),
    ('if (ImGui.button("知道了", 90, 0))', 'if (ImGui.button(PlotI18n.tr("button.plot.got_it"), 90, 0))'),
    ('if (ImGui.selectable("基础操作", selectedHelpTopic == 0))', 'if (ImGui.selectable(PlotI18n.tr("settings.plot.basic_operations"), selectedHelpTopic == 0))'),
    ('if (ImGui.selectable("高级技巧", selectedHelpTopic == 1))', 'if (ImGui.selectable(PlotI18n.tr("settings.plot.advanced_tips"), selectedHelpTopic == 1))'),
    ('if (ImGui.selectable("快捷键与排障", selectedHelpTopic == 2))', 'if (ImGui.selectable(PlotI18n.tr("settings.plot.shortcuts_troubleshooting"), selectedHelpTopic == 2))'),
    ('if (ImGui.selectable("更新日志", selectedHelpTopic == 3))', 'if (ImGui.selectable(PlotI18n.tr("settings.plot.changelog"), selectedHelpTopic == 3))'),
    ('ImGui.text("基础操作")', 'ImGui.text(PlotI18n.tr("settings.plot.basic_operations"))'),
    ('ImGui.text("高级技巧")', 'ImGui.text(PlotI18n.tr("settings.plot.advanced_tips"))'),
    ('ImGui.text("快捷键与排障")', 'ImGui.text(PlotI18n.tr("settings.plot.shortcuts_troubleshooting"))'),
    ('ImGui.text("更新日志")', 'ImGui.text(PlotI18n.tr("settings.plot.changelog"))'),
    ('if (ImGui.checkbox("显示吸附标记", showMarkersState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_snap_markers"), showMarkersState))'),
    ('if (ImGui.checkbox("显示端点反馈", endPointState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_endpoint_feedback"), endPointState))'),
    ('if (ImGui.checkbox("显示中点反馈", midPointState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_midpoint_feedback"), midPointState))'),
    ('if (ImGui.checkbox("显示圆心反馈", centerPointState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_center_feedback"), centerPointState))'),
    ('if (ImGui.checkbox("显示中心点反馈", centroidState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_centroid_feedback"), centroidState))'),
    ('if (ImGui.checkbox("显示控制点", showControlPointsState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_control_points"), showControlPointsState))'),
    ('if (ImGui.checkbox("显示控制点编号", showPointIndexState))', 'if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_control_point_index"), showPointIndexState))'),
    ('if (ImGui.button("重置全部吸附颜色"))', 'if (ImGui.button(PlotI18n.tr("button.plot.reset_all_snap_colors")))'),
])

apply_replacements('ui/dialog/TextInputDialog.java', [
    ('if (ImGui.button("重新编辑", 0, 0))', 'if (ImGui.button(PlotI18n.tr("button.plot.reedit"), 0, 0))'),
])

apply_replacements('ui/toolbar/group/ControlSlidersGroup.java', [
    ('float zoomLabelWidth = ImGui.calcTextSize("视图范围:").x', 'float zoomLabelWidth = ImGui.calcTextSize(PlotI18n.tr("panel.plot.view_range") + ":").x'),
    ('float opacityLabelWidth = ImGui.calcTextSize("画布透明度:").x', 'float opacityLabelWidth = ImGui.calcTextSize(PlotI18n.tr("panel.plot.canvas_opacity") + ":").x'),
    ('ImGui.text("视图范围")', 'ImGui.text(PlotI18n.tr("panel.plot.view_range"))'),
    ('ImGui.text("画布透明度")', 'ImGui.text(PlotI18n.tr("panel.plot.canvas_opacity"))'),
    ('ImGui.text("视图范围:")', 'ImGui.text(PlotI18n.tr("panel.plot.view_range") + ":")'),
    ('ImGui.text("画布透明度:")', 'ImGui.text(PlotI18n.tr("panel.plot.canvas_opacity") + ":")'),
    ('"视图范围:", UILayout.Toolbar.SLIDER_WIDTH,', 'PlotI18n.tr("panel.plot.view_range") + ":", UILayout.Toolbar.SLIDER_WIDTH,'),
    ('"画布透明度:", UILayout.Toolbar.SLIDER_WIDTH,', 'PlotI18n.tr("panel.plot.canvas_opacity") + ":", UILayout.Toolbar.SLIDER_WIDTH,'),
])

apply_replacements('ui/panel/extension/ExtensionPanel.java', [
    ('ImGui.text("已安装插件")', 'ImGui.text(PlotI18n.tr("panel.plot.extension_installed"))'),
    ('ImGui.text("请先启用插件 \'" + currentActivePlugin.getName() + "\' 以使用其功能")', 'ImGui.text(PlotI18n.tr("panel.plot.extension_enable_first", currentActivePlugin.getName()))'),
])

apply_replacements('ui/panel/gallery/GalleryPanel.java', [
    ('ImGui.text("删除类别")', 'ImGui.text(PlotI18n.tr("panel.plot.gallery_delete_category"))'),
    ('float addButtonWidth = getCompactCategoryButtonWidth("+ 添加类别", baseButtonWidth, availableWidth, false)', 'float addButtonWidth = getCompactCategoryButtonWidth(PlotI18n.tr("panel.plot.gallery_add_category"), baseButtonWidth, availableWidth, false)'),
    ('if (ImGui.button("+ 添加类别##add_category", buttonWidth, 24))', 'if (ImGui.button(PlotI18n.tr("panel.plot.gallery_add_category") + "##add_category", buttonWidth, 24))'),
    ('ImGui.text("没有找到符合条件的图库项目")', 'ImGui.text(PlotI18n.tr("panel.plot.gallery_no_items"))'),
    ('ImGui.text("放置")', 'ImGui.text(PlotI18n.tr("button.plot.place"))'),
    ('ImGui.text("编辑")', 'ImGui.text(PlotI18n.tr("button.plot.edit"))'),
    ('ImGui.text("导入")', 'ImGui.text(PlotI18n.tr("button.plot.import"))'),
])

print('Done')
