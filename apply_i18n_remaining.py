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

# Gallery delete tooltip
apply_replacements('ui/panel/gallery/GalleryPanel.java', [
    ('ImGui.text("删除")', 'ImGui.text(PlotI18n.tr("button.plot.delete"))'),
    ('DialogLayoutHelper.beginSection("新建类别")', 'DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.new_category"))'),
    ('DialogLayoutHelper.helpText("输入新的分类名称后确认即可添加。")', 'DialogLayoutHelper.helpText(PlotI18n.tr("dialog.plot.new_category_hint"))'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("取消", "确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

# Grid & Camera
apply_replacements('ui/grid/GridManager.java', [
    ('ImGui.begin("网格设置##GridSettings"', 'ImGui.begin(PlotI18n.tr("screen.plot.grid_settings") + "##GridSettings"'),
    ('ImGui.sliderFloat("网格大小"', 'ImGui.sliderFloat(PlotI18n.tr("grid.plot.grid_size")'),
    ('ImGui.sliderFloat("透明度"', 'ImGui.sliderFloat(PlotI18n.tr("grid.plot.opacity")'),
    ('ImGui.sliderFloat("线宽"', 'ImGui.sliderFloat(PlotI18n.tr("grid.plot.line_width")'),
    ('ImGui.colorEdit4("颜色"', 'ImGui.colorEdit4(PlotI18n.tr("grid.plot.color")'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("重置默认", "确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.reset"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

apply_replacements('ui/camera/CameraSettingsManager.java', [
    ('ImGui.begin("正交相机设置##CameraSettings"', 'ImGui.begin(PlotI18n.tr("screen.plot.camera_settings") + "##CameraSettings"'),
    ('ImGui.sliderFloat("缩放比例"', 'ImGui.sliderFloat(PlotI18n.tr("camera.plot.scale")'),
    ('ImGui.sliderFloat("视野范围"', 'ImGui.sliderFloat(PlotI18n.tr("camera.plot.view_distance")'),
    ('ImGui.sliderFloat("近平面"', 'ImGui.sliderFloat(PlotI18n.tr("camera.plot.near_plane")'),
    ('ImGui.sliderFloat("远平面"', 'ImGui.sliderFloat(PlotI18n.tr("camera.plot.far_plane")'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("重置默认", "确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.reset"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

# Projection settings
apply_replacements('ui/dialog/ProjectionSettingsDialog.java', [
    ('ImGui.begin("方块投影设置", windowFlags)', 'ImGui.begin(PlotI18n.tr("screen.plot.projection_settings"), windowFlags)'),
    ('DialogLayoutHelper.formRowLabel("模式")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.mode"))'),
    ('String[] modes = {"投影到地面", "投影到指定标高"};',
     'String[] modes = {PlotI18n.tr("dialog.plot.projection_mode_ground"), PlotI18n.tr("dialog.plot.projection_mode_elevation")};'),
    ('? "地面模式会按默认地表高度投影结果。"\n                                : "指定标高模式会将结果投影到手动设置的高度层。 ");',
     '? PlotI18n.tr("dialog.plot.projection_ground_mode_detail")\n                                : PlotI18n.tr("dialog.plot.projection_elevation_mode_detail"));'),
    ('DialogLayoutHelper.formRowLabel("标高")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.elevation"))'),
    ('DialogLayoutHelper.formRowHelp(String.format("当前投影到 Y=%d，可在 -64 到 320 之间调整。", elevation))',
     'DialogLayoutHelper.formRowHelp(PlotI18n.tr("dialog.plot.projection_elevation_help", elevation))'),
    ('DialogLayoutHelper.footerSingleCentered("关闭", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.close"), DialogStyleManager.getContentWidth())'),
])

# Line to block
apply_replacements('ui/dialog/LineToBlockSettingsDialog.java', [
    ('FULL("完整转换"),\n        SIMPLIFIED("精简转换");', 'FULL("mode.plot.full_conversion"),\n        SIMPLIFIED("mode.plot.simplified_conversion");'),
    ('return displayName;', 'return PlotI18n.tr(displayName);'),
    ('ImGui.begin("线转方块设置##LineToBlockSettings", windowFlags)',
     'ImGui.begin(PlotI18n.tr("screen.plot.line_to_block_settings") + "##LineToBlockSettings", windowFlags)'),
    ('DialogLayoutHelper.formRowLabel("转换模式")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.conversion_mode"))'),
    ('? "完整转换会尽量保留图形覆盖到的所有方块。"\n                                : "精简转换会按覆盖阈值过滤细碎区域：线框看覆盖长度，填充看单元覆盖率。");',
     '? PlotI18n.tr("dialog.plot.linetoblock_full_help")\n                                : PlotI18n.tr("dialog.plot.linetoblock_simplified_help"));'),
    ('DialogLayoutHelper.formRowLabel("封闭填充")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.closed_fill"))'),
    ('? "已启用封闭图形内部填充。"\n                                : "当前仅转换封闭图形的边缘轮廓。");',
     '? PlotI18n.tr("dialog.plot.linetoblock_fill_enabled_help")\n                                : PlotI18n.tr("dialog.plot.linetoblock_fill_disabled_help"));'),
    ('DialogLayoutHelper.formRowLabel("精简比率")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.simplify_ratio"))'),
    ('? String.format("线框按覆盖长度过滤，填充区域按单元覆盖率过滤；当前阈值为 %.2f。", simplificationRatio)\n                                    : String.format("仅当线条覆盖长度超过 %.2f 倍方块边长时才转换。", simplificationRatio));',
     '? PlotI18n.tr("dialog.plot.linetoblock_simplify_threshold_fill", simplificationRatio)\n                                    : PlotI18n.tr("dialog.plot.linetoblock_simplify_threshold_line", simplificationRatio));'),
    ('DialogLayoutHelper.footerSingleCentered("关闭", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.close"), DialogStyleManager.getContentWidth())'),
])

# Text input dialog
apply_replacements('ui/dialog/TextInputDialog.java', [
    ('private static final String DIALOG_TITLE = "添加文字";',
     'private static final String DIALOG_TITLE = PlotI18n.tr("screen.plot.add_text");'),
    ('DialogLayoutHelper.beginSection("编辑")', 'DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.edit_section"))'),
    ('DialogLayoutHelper.beginSection("文字样式")', 'DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.text_style"))'),
    ('DialogLayoutHelper.footerConfirmCancelRight("取消", "确定", contentWidth)',
     'DialogLayoutHelper.footerConfirmCancelRight(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.confirm"), contentWidth)'),
    ('DialogLayoutHelper.formRowLabel("字体大小")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("option.plot.font_size"))'),
    ('DialogLayoutHelper.formRowLabel("行高")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("option.plot.text_line_height"))'),
    ('DialogLayoutHelper.formRowLabel("字形")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.glyph"))'),
    ('DialogLayoutHelper.formRowCheckboxPair("粗体##bold", bold, "斜体##italic", italic)',
     'DialogLayoutHelper.formRowCheckboxPair(PlotI18n.tr("option.plot.text_bold") + "##bold", bold, PlotI18n.tr("option.plot.text_italic") + "##italic", italic)'),
    ('DialogLayoutHelper.formRowLabel("水平对齐")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.horizontal_align"))'),
    ('DialogLayoutHelper.formRowLabel("垂直对齐")', 'DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.vertical_align"))'),
    ('case LEFT -> "左对齐";', 'case LEFT -> PlotI18n.tr("text.align.left");'),
    ('case CENTER -> "居中";', 'case CENTER -> PlotI18n.tr("text.align.center");'),
    ('case RIGHT -> "右对齐";', 'case RIGHT -> PlotI18n.tr("text.align.right");'),
    ('case TOP -> "顶部";', 'case TOP -> PlotI18n.tr("text.align.top");'),
    ('case MIDDLE -> "居中";', 'case MIDDLE -> PlotI18n.tr("text.align.middle");'),
    ('case BOTTOM -> "底部";', 'case BOTTOM -> PlotI18n.tr("text.align.bottom");'),
])

# Rotate tool angle step popup
apply_replacements('ui/panel/tool/renderer/RotateToolOptionRenderer.java', [
    ('DialogLayoutHelper.beginSection("角度步长")', 'DialogLayoutHelper.beginSection(PlotI18n.tr("dialog.plot.angle_step_title"))'),
    ('DialogLayoutHelper.helpText("请输入 1 - 90 之间的整数，回车可直接确认，双击滑块也可再次打开此面板。")',
     'DialogLayoutHelper.helpText(PlotI18n.tr("dialog.plot.angle_step_help"))'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("取消", "确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

# Delete layer dialog
apply_replacements('ui/panel/layer/DeleteLayerDialog.java', [
    ('return "不能删除仅有的图层";', 'return PlotI18n.tr("layer.plot.cannot_delete_only");'),
    ('return "请先选择要删除的图层";', 'return PlotI18n.tr("layer.plot.select_to_delete");'),
    ('return "必须至少保留一个图层";', 'return PlotI18n.tr("layer.plot.must_keep_one");'),
    ('return "无法删除锁定的图层: " + layer.getName() + "，请先解锁";',
     'return PlotI18n.tr("layer.plot.cannot_delete_locked", layer.getName());'),
    ('ImGui.textWrapped("确定要删除图层 \\"" + layer.getName() + "\\" 吗？");',
     'ImGui.textWrapped(PlotI18n.tr("layer.plot.delete_confirm_single", layer.getName()));'),
    ('ImGui.textWrapped("确定要删除选中的 " + selectedLayers.size() + " 个图层吗？");',
     'ImGui.textWrapped(PlotI18n.tr("layer.plot.delete_confirm_multiple", selectedLayers.size()));'),
    ('DialogLayoutHelper.warningText("此操作不可撤销。")', 'DialogLayoutHelper.warningText(PlotI18n.tr("dialog.plot.delete_layer_warning"))'),
    ('DialogLayoutHelper.footerConfirmCancelCentered("取消", "删除", contentWidth)',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.delete"), contentWidth)'),
    ('showWarningDialog.accept("删除图层失败，请重试");', 'showWarningDialog.accept(PlotI18n.tr("layer.plot.delete_failed"));'),
])

apply_replacements('ui/panel/layer/NewLayerDialog.java', [
    ('private static final String DIALOG_TITLE = "新建图层";',
     'private static final String DIALOG_TITLE = PlotI18n.tr("screen.plot.new_layer");'),
])

apply_replacements('ui/toolbar/ToolbarUIUtils.java', [
    ('DialogLayoutHelper.footerConfirmCancelCentered("取消", "确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.cancel"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

apply_replacements('ui/toolbar/ControlPanel.java', [
    ('DialogLayoutHelper.footerSingleCentered("确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

apply_replacements('ui/panel/layer/LayerPanel.java', [
    ('DialogLayoutHelper.footerSingleCentered("确定", DialogStyleManager.getContentWidth())',
     'DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth())'),
])

# Settings dialog - remaining strings
apply_replacements('ui/dialog/SettingsAndHelpDialog.java', [
    ('if (!ImGui.begin("设置与帮助", flags))', 'if (!ImGui.begin(PlotI18n.tr("screen.plot.settings_help"), flags))'),
    ('final String shortcutHintText = "说明：单键（如 L、P、C、R、E、S、A、Space）用于快速切换工具；组合键（如 Ctrl+Z/Y、Ctrl+N）用于全局操作。按住 Shift 在绘制或修改时启用正交/角度约束。";',
     'final String shortcutHintText = PlotI18n.tr("settings.plot.shortcuts_hint");'),
    ('ImGui.inputTextWithHint("##shortcut_search", "搜索动作或按键...", searchText)',
     'ImGui.inputTextWithHint("##shortcut_search", PlotI18n.tr("settings.plot.shortcuts_search_hint"), searchText)'),
    ('ImGui.textColored(theme.warningText, "正在录制快捷键：" + actionName + "（Backspace 清除，Esc 取消）");',
     'ImGui.textColored(theme.warningText, PlotI18n.tr("settings.plot.shortcuts_recording", actionName));'),
    ('ImGui.textDisabled("未找到相关动作")', 'ImGui.textDisabled(PlotI18n.tr("settings.plot.shortcuts_no_results"))'),
    ('if (ImGui.smallButton("清除搜索##clear_shortcut_search"))', 'if (ImGui.smallButton(PlotI18n.tr("settings.plot.shortcuts_clear_search") + "##clear_shortcut_search"))'),
    ('ImGui.tableSetupColumn("动作", ImGuiTableColumnFlags.WidthStretch, 1.0f)', 'ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_action"), ImGuiTableColumnFlags.WidthStretch, 1.0f)'),
    ('ImGui.tableSetupColumn("当前快捷键", ImGuiTableColumnFlags.WidthFixed, 220.0f)', 'ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_current"), ImGuiTableColumnFlags.WidthFixed, 220.0f)'),
    ('ImGui.tableSetupColumn("操作", ImGuiTableColumnFlags.WidthFixed, 210.0f)', 'ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_operations"), ImGuiTableColumnFlags.WidthFixed, 210.0f)'),
    ('ImGui.textColored(theme.warningText, "按下组合键...（Esc取消）");', 'ImGui.textColored(theme.warningText, PlotI18n.tr("settings.plot.shortcuts_press_combo"));'),
    ('shortcutConflictMessage = "快捷键 " + captured + " 与动作【" + conflictName + "】冲突，旧绑定已移除。";',
     'shortcutConflictMessage = PlotI18n.tr("settings.plot.shortcuts_conflict", captured, conflictName);'),
    ('ImGui.text(current == null || current.isEmpty() ? "未绑定" : current);',
     'ImGui.text(current == null || current.isEmpty() ? PlotI18n.tr("settings.plot.shortcuts_unbound") : current);'),
    ('ImGui.setTooltip("双击可进入快捷键录制");', 'ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_double_click_record"));'),
    ('if (ImGui.button("取消##cancel_" + actionId))', 'if (ImGui.button(PlotI18n.tr("button.plot.cancel") + "##cancel_" + actionId))'),
    ('if (ImGui.button("编辑##edit_" + actionId))', 'if (ImGui.button(PlotI18n.tr("button.plot.edit") + "##edit_" + actionId))'),
    ('if (ImGui.button("清除##clear_" + actionId))', 'if (ImGui.button(PlotI18n.tr("button.plot.clear") + "##clear_" + actionId))'),
    ('if (ImGui.smallButton("重置##reset_" + actionId))', 'if (ImGui.smallButton(PlotI18n.tr("button.plot.reset") + "##reset_" + actionId))'),
    ('ImGui.setTooltip("恢复默认：" + defaultKey);', 'ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_reset_default", defaultKey));'),
    ('ImGui.setTooltip("该动作暂无预设默认快捷键");', 'ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_no_default"));'),
    ('ImGui.setTooltip("当前已是默认快捷键");', 'ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_already_default"));'),
    ('ImGui.textWrapped(shortcutConflictMessage == null ? "检测到快捷键冲突。" : shortcutConflictMessage);',
     'ImGui.textWrapped(shortcutConflictMessage == null ? PlotI18n.tr("settings.plot.shortcuts_conflict_generic") : shortcutConflictMessage);'),
    ('ImGui.textDisabled("点击左侧条目查看对应教程")', 'ImGui.textDisabled(PlotI18n.tr("settings.plot.help_select_topic"))'),
    ('if (ImGui.selectable("基础操作", selectedHelpTopic == 0)) selectedHelpTopic = 0;',
     'if (ImGui.selectable(PlotI18n.tr("settings.plot.basic_operations"), selectedHelpTopic == 0)) selectedHelpTopic = 0;'),
    ('if (ImGui.selectable("高级技巧", selectedHelpTopic == 1)) selectedHelpTopic = 1;',
     'if (ImGui.selectable(PlotI18n.tr("settings.plot.advanced_tips"), selectedHelpTopic == 1)) selectedHelpTopic = 1;'),
    ('if (ImGui.selectable("快捷键与排障", selectedHelpTopic == 2)) selectedHelpTopic = 2;',
     'if (ImGui.selectable(PlotI18n.tr("settings.plot.shortcuts_troubleshooting"), selectedHelpTopic == 2)) selectedHelpTopic = 2;'),
    ('if (ImGui.selectable("更新日志", selectedHelpTopic == 3)) selectedHelpTopic = 3;',
     'if (ImGui.selectable(PlotI18n.tr("settings.plot.changelog"), selectedHelpTopic == 3)) selectedHelpTopic = 3;'),
    ('ImGui.bulletText("选择：Space 切换到选择工具。拖拽框选可一次选中多个图元。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_select"));'),
    ('ImGui.bulletText("移动：选中对象后直接拖拽，或输入精确位移值进行调整。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_move"));'),
    ('ImGui.bulletText("缩放视图：使用滚轮缩放，按住中键可平移画布。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_zoom"));'),
    ('ImGui.bulletText("按住 Shift：绘制或修改时启用正交/角度约束，快速得到规整图形。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_shift"));'),
    ('ImGui.bulletText("吸附配合：开启端点/中点/垂足吸附可显著提高定位效率。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_snap"));'),
    ('ImGui.bulletText("修改建议：先用选择工具定位，再切换编辑工具，减少误操作。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_edit"));'),
    ('ImGui.bulletText("快捷键冲突时，系统会提示被占用动作并自动移除旧绑定。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_conflict"));'),
    ('ImGui.bulletText("若快捷键无效：先确认没有输入框焦点，再检查是否被其它模组拦截。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_invalid"));'),
    ('ImGui.bulletText("录制快捷键时按 Esc 可立即取消当前录制。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_esc"));'),
    ('ImGui.bulletText("设置页已迁移至 Table API，列表列宽更稳定，支持分组显示。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_table"));'),
    ('ImGui.bulletText("快捷键录制增强：支持冲突提示、录制状态高亮和清除按钮。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_recording"));'),
    ('ImGui.bulletText("显示反馈页已拆分为基础设置与颜色自定义两个折叠区。");',
     'ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_display"));'),
    ('final String displayHintText = "提示：标记大小与颜色会同时影响绘制和修改工具中的吸附反馈。";',
     'final String displayHintText = PlotI18n.tr("settings.plot.display_marker_hint");'),
    ('if (ImGui.treeNodeEx("基础设置##display_basic", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen))',
     'if (ImGui.treeNodeEx(PlotI18n.tr("settings.plot.display_basic_settings") + "##display_basic", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen))'),
    ('renderHelpMarkerInline("center_point", "圆心吸附：吸附到圆或圆弧的几何中心点。");',
     'renderHelpMarkerInline("center_point", PlotI18n.tr("settings.plot.snap_help_center_point"));'),
    ('renderHelpMarkerInline("centroid", "重心吸附（Centroid）：吸附到闭合多边形的几何中心。\\n对复杂图形可用于快速定位整体中心。");',
     'renderHelpMarkerInline("centroid", PlotI18n.tr("settings.plot.snap_help_centroid"));'),
    ('if (ImGui.sliderFloat("标记大小", markerSize, 2.0f, 10.0f, "%.1f px"))',
     'if (ImGui.sliderFloat(PlotI18n.tr("snap.plot.marker_size"), markerSize, 2.0f, 10.0f, "%.1f px"))'),
    ('if (ImGui.treeNodeEx("颜色自定义##display_color", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen))',
     'if (ImGui.treeNodeEx(PlotI18n.tr("settings.plot.display_color_custom") + "##display_color", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen))'),
    ('ImGui.textDisabled("不同吸附点可设置不同颜色，实时生效");',
     'ImGui.textDisabled(PlotI18n.tr("settings.plot.display_color_hint"));'),
    ('renderSnapColorEditor("端点", SnapPriorityEvaluator.SnapType.END_POINT)',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_end_point"), SnapPriorityEvaluator.SnapType.END_POINT)'),
    ('renderSnapColorEditor("最近点", SnapPriorityEvaluator.SnapType.NEAREST_POINT)',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_nearest"), SnapPriorityEvaluator.SnapType.NEAREST_POINT)'),
    ('renderSnapColorEditor("中点", SnapPriorityEvaluator.SnapType.MID_POINT)',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_mid_point"), SnapPriorityEvaluator.SnapType.MID_POINT)'),
    ('renderSnapColorEditor("中心点", SnapPriorityEvaluator.SnapType.CENTER_POINT, "中心点颜色：用于圆心/中心点吸附提示。")',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_center"), SnapPriorityEvaluator.SnapType.CENTER_POINT, PlotI18n.tr("settings.plot.snap_help_center_color"))'),
    ('renderSnapColorEditor("垂足", SnapPriorityEvaluator.SnapType.PERPENDICULAR, "垂足吸附：从当前点向目标线作垂线，吸附到垂足位置。")',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_perpendicular"), SnapPriorityEvaluator.SnapType.PERPENDICULAR, PlotI18n.tr("settings.plot.snap_help_perpendicular"))'),
    ('renderSnapColorEditor("切点", SnapPriorityEvaluator.SnapType.TANGENT, "切点吸附：吸附到与目标曲线相切的接触点。")',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_tangent"), SnapPriorityEvaluator.SnapType.TANGENT, PlotI18n.tr("settings.plot.snap_help_tangent"))'),
    ('renderSnapColorEditor("角点", SnapPriorityEvaluator.SnapType.VERTEX)',
     'renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_vertex"), SnapPriorityEvaluator.SnapType.VERTEX)'),
    ('if (ImGui.smallButton("重置##snap_reset_" + type.name()))',
     'if (ImGui.smallButton(PlotI18n.tr("button.plot.reset") + "##snap_reset_" + type.name()))'),
    ('ImGui.setTooltip("恢复该项默认颜色");', 'ImGui.setTooltip(PlotI18n.tr("settings.plot.snap_color_reset_tooltip"));'),
])

print('Done remaining')
