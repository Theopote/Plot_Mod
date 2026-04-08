package com.plot.ui.dialog;
import com.plot.core.shortcut.KeyboardShortcutConverter;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.core.snap.SnapManager;
import com.plot.core.snap.SnapPriorityEvaluator;
import com.plot.ui.tools.impl.modify.ControlPointEditTool;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.tools.snap.SnapVisualStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

// no-op

/**
 * 设置与帮助对话框（第一期：快捷键页）
 */
public class SettingsAndHelpDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/SettingsAndHelpDialog");
    private static final float DISPLAY_HINT_RESERVED_LINES = 3.0f;
    private static final SettingsAndHelpDialog INSTANCE = new SettingsAndHelpDialog();

    private boolean isOpen = false;
    private final ImString searchText = new ImString(256);
    private String editingActionId = null; // 当前正在录制快捷键的动作
    private String shortcutConflictMessage = null;
    private int selectedHelpTopic = 0;
    private boolean captureSuppressionApplied = false;
    private boolean suppressCloseHotkeysThisFrame = false;

    // 复用状态对象，避免在 renderDisplayPage() 中每帧分配新的 ImBoolean
    private final ImBoolean showMarkersState = new ImBoolean();
    private final ImBoolean endPointState = new ImBoolean();
    private final ImBoolean midPointState = new ImBoolean();
    private final ImBoolean centerPointState = new ImBoolean();
    private final ImBoolean centroidState = new ImBoolean();
    private final ImBoolean showControlPointsState = new ImBoolean();
    private final ImBoolean showPointIndexState = new ImBoolean();

    private SettingsAndHelpDialog() {}

    public static SettingsAndHelpDialog getInstance() {
        return INSTANCE;
    }

    public void open() {
        isOpen = true;
        LOGGER.debug("打开 设置与帮助 对话框");
    }

    public void close() {
        isOpen = false;
        editingActionId = null;
        applyCaptureSuppression(false);
        LOGGER.debug("关闭 设置与帮助 对话框");
    }

    public void render() {
        if (!isOpen) return;
        suppressCloseHotkeysThisFrame = false;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        try {
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.LARGE.value, 550.0f, ImGuiCond.Appearing);
            int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoScrollbar;
            if (!ImGui.begin("设置与帮助", flags)) {
                ImGui.end();
                return;
            }

            boolean captureActive = editingActionId != null;
            boolean closeClicked = DialogStyleManager.renderTopRightCloseButton("settings_help", captureActive);
            if (closeClicked) {
                close();
                ImGui.end();
                return;
            }

            float footerReservedHeight = DialogLayoutHelper.getStandardFooterReservedHeight();
            if (DialogLayoutHelper.beginSettingsPageBody("##settings_body_region", footerReservedHeight)) {
                try {
                    if (ImGui.beginTabBar("##settings_tabs", ImGuiTabBarFlags.None)) {
                        if (ImGui.beginTabItem("快捷键")) {
                            renderShortcutsPage();
                            ImGui.endTabItem();
                        }
                        if (ImGui.beginTabItem("吸附与反馈")) {
                            renderDisplayPage();
                            ImGui.endTabItem();
                        }
                        if (ImGui.beginTabItem("帮助与教程")) {
                            renderHelpPage();
                            ImGui.endTabItem();
                        }
                        ImGui.endTabBar();
                    }
                } finally {
                    ImGui.endChild();
                }
            }

            // 底部操作区：设置即时生效，仅用于完成/返回。
            DialogLayoutHelper.beginFooter();
            boolean captureActiveNow = editingActionId != null;
            if (captureActiveNow) ImGui.beginDisabled();
            DialogLayoutHelper.FooterResult footerAction =
                    DialogLayoutHelper.footerConfirmCancelCentered("返回", "完成", DialogStyleManager.getContentWidth());
            boolean suppressDialogHotkeys = DialogLayoutHelper.shouldSuppressDialogHotkeys(
                    captureActiveNow, suppressCloseHotkeysThisFrame);
            if (footerAction.confirmClicked()
                    || (!suppressDialogHotkeys && DialogLayoutHelper.isConfirmShortcutPressed())) {
                close();
            }
            if (footerAction.cancelClicked()
                    || (!suppressDialogHotkeys && DialogLayoutHelper.isCancelShortcutPressed())) {
                close();
            }
            if (captureActiveNow) ImGui.endDisabled();

            ImGui.end();
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void renderShortcutsPage() {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        boolean captureActive = editingActionId != null;
        applyCaptureSuppression(captureActive);

        // Esc 在录制态仅取消录制，不应冒泡到对话框“返回/关闭”逻辑
        if (captureActive && DialogLayoutHelper.isCancelShortcutPressed()) {
            cancelCapture();
            captureActive = false;
        }

        // 顶部工具行：搜索、重置默认、导出、导入
        if (captureActive) ImGui.beginDisabled();
        ImGui.text("搜索：");
        ImGui.sameLine();
        ImGui.setNextItemWidth(240);
        ImGui.inputTextWithHint("##shortcut_search", "搜索动作或按键...", searchText);

        ImGui.sameLine();
        if (ImGui.button("重置为默认")) {
            KeymapManager.getInstance().resetToDefault();
        }
        if (captureActive) ImGui.endDisabled();


        if (DialogLayoutHelper.beginRemainingChild("##shortcut_scroll_region", 0.0f,
            true, ImGuiWindowFlags.NoScrollbar)) {
            if (editingActionId != null) {
                String actionName = KeymapManager.getInstance().getActionDisplayName(editingActionId);
                ImGui.pushStyleColor(ImGuiCol.ChildBg, withAlpha(theme.accent, 56));
                if (ImGui.beginChild("##shortcut_capture_notice", 0, 40, true)) {
                    ImGui.textColored(theme.warningText, "正在录制快捷键：" + actionName + "（Backspace 清除，Esc 取消）");
                }
                ImGui.endChild();
                ImGui.popStyleColor();
                ImGui.spacing();
            }

            float tableHeight = Math.max(240.0f, ImGui.getContentRegionAvailY() - 36.0f);
            int tableFlags = ImGuiTableFlags.BordersInnerV
                    | ImGuiTableFlags.RowBg
                    | ImGuiTableFlags.Resizable
                    | ImGuiTableFlags.ScrollY
                    | ImGuiTableFlags.SizingStretchProp;
            if (ImGui.beginTable("shortcut_table", 3, tableFlags, 0, tableHeight)) {
                ImGui.tableSetupColumn("动作", ImGuiTableColumnFlags.WidthStretch, 1.0f);
                ImGui.tableSetupColumn("当前快捷键", ImGuiTableColumnFlags.WidthFixed, 220.0f);
                ImGui.tableSetupColumn("操作", ImGuiTableColumnFlags.WidthFixed, 210.0f);
                ImGui.tableHeadersRow();

                String filter = searchText.get().trim().toLowerCase();
                String lastCategory = null;
                int matchedActions = 0;
                for (KeymapManager.ActionDef def : KeymapManager.getInstance().getAllActions()) {
                    String display = def.displayName();
                    String actionId = def.actionId();
                    if (!filter.isEmpty()) {
                        String binding = KeymapManager.getInstance().getBindingDisplay(actionId);
                        if (!(display.toLowerCase().contains(filter) || (binding != null && binding.toLowerCase().contains(filter)))) {
                            continue;
                        }
                    }

                    String category = def.category();
                    if (!Objects.equals(category, lastCategory)) {
                        ImGui.tableNextRow();
                        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, withAlpha(theme.panelBackground, 180));
                        ImGui.tableSetColumnIndex(0);
                        ImGui.textColored(theme.infoText, "[" + category + "]");
                        ImGui.tableSetColumnIndex(1);
                        ImGui.text("");
                        ImGui.tableSetColumnIndex(2);
                        ImGui.text("");
                        lastCategory = category;
                    }
                    matchedActions++;

                    boolean isEditing = editingActionId != null && editingActionId.equals(actionId);
                    ImGui.tableNextRow();
                    if (isEditing) {
                        float anim = (float) (Math.sin(ImGui.getTime() * 6.0f) * 0.15f + 0.2f);
                        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, withAlpha(theme.accent, (int) (anim * 255.0f)));
                    }

                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(display);

                    ImGui.tableSetColumnIndex(1);
                    String current = KeymapManager.getInstance().getBindingDisplay(actionId);
                    if (isEditing) {
                        ImGui.textColored(theme.warningText, "按下组合键...（Esc取消）");
                        if (ImGui.isKeyPressed(ImGuiKey.Backspace)) {
                            KeymapManager.getInstance().clearBinding(actionId);
                            editingActionId = null;
                        } else {
                            String captured = tryCaptureShortcutString();
                            if (captured != null) {
                                String conflicted = KeymapManager.getInstance().updateBindingAndGetConflict(actionId, captured);
                                if (conflicted != null) {
                                    String conflictName = KeymapManager.getInstance().getActionDisplayName(conflicted);
                                    shortcutConflictMessage = "快捷键 " + captured + " 与动作【" + conflictName + "】冲突，旧绑定已移除。";
                                    ImGui.openPopup("##shortcut_conflict_popup");
                                }
                                editingActionId = null;
                            }
                        }
                    } else {
                        ImGui.text(current == null || current.isEmpty() ? "未绑定" : current);
                        if (!captureActive && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                            editingActionId = actionId;
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("双击可进入快捷键录制");
                        }
                    }

                    ImGui.tableSetColumnIndex(2);
                    if (isEditing) {
                        if (ImGui.button("取消##cancel_" + actionId)) {
                            editingActionId = null;
                        }
                    } else {
                        if (captureActive) ImGui.beginDisabled();
                        if (ImGui.button("编辑##edit_" + actionId)) {
                            editingActionId = actionId;
                        }
                        ImGui.sameLine();
                        if (ImGui.button("清除##clear_" + actionId)) {
                            KeymapManager.getInstance().clearBinding(actionId);
                        }
                        ImGui.sameLine();
                        String defaultKey = KeymapManager.getInstance().getDefaultBinding(actionId);
                        boolean hasDefault = defaultKey != null && !defaultKey.isEmpty();
                        boolean isAtDefault = hasDefault && Objects.equals(current, defaultKey);
                        boolean canReset = hasDefault && !isAtDefault;

                        if (!canReset) ImGui.beginDisabled();
                        if (canReset) {
                            ImGui.pushStyleColor(ImGuiCol.Button, withAlpha(theme.accent, 120));
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, withAlpha(theme.accent, 180));
                            ImGui.pushStyleColor(ImGuiCol.ButtonActive, withAlpha(theme.accent, 220));
                        }
                        if (ImGui.smallButton("重置##reset_" + actionId)) {
                            KeymapManager.getInstance().updateBinding(actionId, defaultKey);
                        }
                        if (canReset) {
                            ImGui.popStyleColor(3);
                        }
                        if (ImGui.isItemHovered()) {
                            if (canReset) {
                                ImGui.setTooltip("恢复默认：" + defaultKey);
                            } else if (!hasDefault) {
                                ImGui.setTooltip("该动作暂无预设默认快捷键");
                            } else {
                                ImGui.setTooltip("当前已是默认快捷键");
                            }
                        }
                        if (!canReset) ImGui.endDisabled();
                        if (captureActive) ImGui.endDisabled();
                    }
                }
                ImGui.endTable();

                if (!filter.isEmpty() && matchedActions == 0) {
                    ImGui.spacing();
                    ImGui.textDisabled("未找到相关动作");
                    ImGui.sameLine();
                    if (ImGui.smallButton("清除搜索##clear_shortcut_search")) {
                        searchText.set("");
                    }
                }
            }

            if (ImGui.beginPopup("##shortcut_conflict_popup")) {
                ImGui.textWrapped(shortcutConflictMessage == null ? "检测到快捷键冲突。" : shortcutConflictMessage);
                if (ImGui.button("知道了", 90, 0)) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            ImGui.separator();
            DialogLayoutHelper.helpText("说明：单键（如 L、P、C、R、E、S、A、Space）用于快速切换工具；组合键（如 Ctrl+Z/Y、Ctrl+N）用于全局操作。按住 Shift 在绘制或修改时启用正交/角度约束。");
        }
        ImGui.endChild();
    }

    private void renderHelpPage() {
        ImGui.textDisabled("点击左侧条目查看对应教程");

        if (DialogLayoutHelper.beginSettingsPageBody("##help_scroll_region", 0.0f)) {
            if (ImGui.beginChild("##help_nav", 180, 0, true)) {
                if (ImGui.selectable("基础操作", selectedHelpTopic == 0)) selectedHelpTopic = 0;
                if (ImGui.selectable("高级技巧", selectedHelpTopic == 1)) selectedHelpTopic = 1;
                if (ImGui.selectable("快捷键与排障", selectedHelpTopic == 2)) selectedHelpTopic = 2;
                if (ImGui.selectable("更新日志", selectedHelpTopic == 3)) selectedHelpTopic = 3;
            }
            ImGui.endChild();

            ImGui.sameLine();

            if (ImGui.beginChild("##help_content", 0, 0, true)) {
                switch (selectedHelpTopic) {
                    case 0 -> {
                        ImGui.text("基础操作");
                        ImGui.separator();
                        ImGui.bulletText("选择：Space 切换到选择工具。拖拽框选可一次选中多个图元。");
                        ImGui.bulletText("移动：选中对象后直接拖拽，或输入精确位移值进行调整。");
                        ImGui.bulletText("缩放视图：使用滚轮缩放，按住中键可平移画布。");
                    }
                    case 1 -> {
                        ImGui.text("高级技巧");
                        ImGui.separator();
                        ImGui.bulletText("按住 Shift：绘制或修改时启用正交/角度约束，快速得到规整图形。");
                        ImGui.bulletText("吸附配合：开启端点/中点/垂足吸附可显著提高定位效率。");
                        ImGui.bulletText("修改建议：先用选择工具定位，再切换编辑工具，减少误操作。");
                    }
                    case 2 -> {
                        ImGui.text("快捷键与排障");
                        ImGui.separator();
                        ImGui.bulletText("快捷键冲突时，系统会提示被占用动作并自动移除旧绑定。");
                        ImGui.bulletText("若快捷键无效：先确认没有输入框焦点，再检查是否被其它模组拦截。");
                        ImGui.bulletText("录制快捷键时按 Esc 可立即取消当前录制。");
                    }
                    case 3 -> {
                        ImGui.text("更新日志");
                        ImGui.separator();
                        ImGui.bulletText("设置页已迁移至 Table API，列表列宽更稳定，支持分组显示。");
                        ImGui.bulletText("快捷键录制增强：支持冲突提示、录制状态高亮和清除按钮。");
                        ImGui.bulletText("显示反馈页已拆分为基础设置与颜色自定义两个折叠区。");
                    }
                    default -> selectedHelpTopic = 0;
                }
            }
            ImGui.endChild();
        }
        ImGui.endChild();
    }

    private void renderDisplayPage() {
        SnapManager snapManager = SnapManager.getInstance();
        syncDisplayToggleStates(snapManager);
        final String displayHintText = "提示：标记大小与颜色会同时影响绘制和修改工具中的吸附反馈。";

        DialogLayoutHelper.helpText("Object Snap（OSnap）与反馈设置：控制端点/中点/重心等吸附提示及显示样式。");
        DialogLayoutHelper.subsectionGap();

        float hintReservedHeight = DialogLayoutHelper.getReservedTextHeight(DISPLAY_HINT_RESERVED_LINES);
        if (DialogLayoutHelper.beginRemainingChild("##display_scroll_region", hintReservedHeight, true,
                ImGuiWindowFlags.NoScrollbar)) {

            if (ImGui.treeNodeEx("基础设置##display_basic", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent(10);

                if (ImGui.beginTable("##osnap_toggle_grid", 2, ImGuiTableFlags.SizingStretchProp)) {
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    if (ImGui.checkbox("显示吸附标记", showMarkersState)) {
                        snapManager.setShowSnapMarkersEnabled(showMarkersState.get());
                    }
                    ImGui.tableSetColumnIndex(1);
                    if (ImGui.checkbox("显示端点反馈", endPointState)) {
                        snapManager.setEndPointSnapEnabled(endPointState.get());
                    }

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    if (ImGui.checkbox("显示中点反馈", midPointState)) {
                        snapManager.setMidPointSnapEnabled(midPointState.get());
                    }
                    ImGui.tableSetColumnIndex(1);
                    if (ImGui.checkbox("显示圆心反馈", centerPointState)) {
                        snapManager.setCenterPointSnapEnabled(centerPointState.get());
                    }
                    renderHelpMarkerInline("center_point", "圆心吸附：吸附到圆或圆弧的几何中心点。");

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    if (ImGui.checkbox("显示中心点反馈", centroidState)) {
                        snapManager.setCentroidSnapEnabled(centroidState.get());
                    }
                    renderHelpMarkerInline("centroid", "重心吸附（Centroid）：吸附到闭合多边形的几何中心。\n对复杂图形可用于快速定位整体中心。");
                    ImGui.tableSetColumnIndex(1);
                    ImGui.textDisabled(" ");

                    ImGui.endTable();
                }

                float[] markerSize = new float[] { snapManager.getMarkerSize() };
                ImGui.setNextItemWidth(180);
                if (ImGui.sliderFloat("标记大小", markerSize, 2.0f, 10.0f, "%.1f px")) {
                    snapManager.setMarkerSize(markerSize[0]);
                }

                if (ImGui.checkbox("显示控制点", showControlPointsState)) {
                    ControlPointEditTool.setDisplayEnabled(showControlPointsState.get());
                }

                if (ImGui.checkbox("显示控制点编号", showPointIndexState)) {
                    ControlPointEditTool.setShowPointIndex(showPointIndexState.get());
                }

                ImGui.unindent(10);
                ImGui.treePop();
            }

            if (ImGui.treeNodeEx("颜色自定义##display_color", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent(10);
                ImGui.textDisabled("不同吸附点可设置不同颜色，实时生效");

                renderSnapColorEditor("端点", SnapPriorityEvaluator.SnapType.END_POINT);
                renderSnapColorEditor("最近点", SnapPriorityEvaluator.SnapType.NEAREST_POINT);
                renderSnapColorEditor("中点", SnapPriorityEvaluator.SnapType.MID_POINT);
                renderSnapColorEditor("中心点", SnapPriorityEvaluator.SnapType.CENTER_POINT, "中心点颜色：用于圆心/中心点吸附提示。");
                renderSnapColorEditor("垂足", SnapPriorityEvaluator.SnapType.PERPENDICULAR, "垂足吸附：从当前点向目标线作垂线，吸附到垂足位置。");
                renderSnapColorEditor("切点", SnapPriorityEvaluator.SnapType.TANGENT, "切点吸附：吸附到与目标曲线相切的接触点。");
                renderSnapColorEditor("角点", SnapPriorityEvaluator.SnapType.VERTEX);

                if (ImGui.button("重置全部吸附颜色")) {
                    SnapVisualStyle.resetCustomColors();
                }
                ImGui.unindent(10);
                ImGui.treePop();
            }
        }
        ImGui.endChild();

        DialogLayoutHelper.subsectionGap();
        if (DialogLayoutHelper.beginPinnedBottomRegion("##display_hint_region")) {
            DialogLayoutHelper.helpText(displayHintText);
        }
        ImGui.endChild();
    }

    private void syncDisplayToggleStates(SnapManager snapManager) {
        showMarkersState.set(snapManager.isShowSnapMarkersEnabled());
        endPointState.set(snapManager.isEndPointSnapEnabled());
        midPointState.set(snapManager.isMidPointSnapEnabled());
        centerPointState.set(snapManager.isCenterPointSnapEnabled());
        centroidState.set(snapManager.isCentroidSnapEnabled());
        showControlPointsState.set(ControlPointEditTool.isDisplayEnabled());
        showPointIndexState.set(ControlPointEditTool.isShowPointIndex());
    }

    private void renderSnapColorEditor(String label, SnapPriorityEvaluator.SnapType type) {
        renderSnapColorEditor(label, type, null);
    }

    private void renderSnapColorEditor(String label, SnapPriorityEvaluator.SnapType type, String tooltip) {
        int argb = SnapVisualStyle.getEffectiveColorArgb(type);
        float[] rgba = argbToFloat4(argb);

        ImGui.pushStyleColor(ImGuiCol.Button, argb);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, argb);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, argb);
        ImGui.button("##snap_preview_" + type.name(), 16, 16);
        ImGui.popStyleColor(3);

        ImGui.sameLine();
        float colorEditorWidth = Math.min(200.0f, Math.max(120.0f, ImGui.getContentRegionAvailX() - 70.0f));
        ImGui.setNextItemWidth(colorEditorWidth);
        if (ImGui.colorEdit4(label + "##snap_color_" + type.name(), rgba)) {
            SnapVisualStyle.setCustomColor(type, float4ToArgb(rgba));
        }
        if (tooltip != null && !tooltip.isEmpty() && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("重置##snap_reset_" + type.name())) {
            SnapVisualStyle.clearCustomColor(type);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("恢复该项默认颜色");
        }
    }

    private static float[] argbToFloat4(int argb) {
        return new float[] {
            ((argb >> 16) & 0xFF) / 255.0f,
            ((argb >> 8) & 0xFF) / 255.0f,
            (argb & 0xFF) / 255.0f,
            ((argb >>> 24) & 0xFF) / 255.0f
        };
    }

    private static int float4ToArgb(float[] rgba) {
        int r = Math.max(0, Math.min(255, Math.round(rgba[0] * 255.0f)));
        int g = Math.max(0, Math.min(255, Math.round(rgba[1] * 255.0f)));
        int b = Math.max(0, Math.min(255, Math.round(rgba[2] * 255.0f)));
        int a = Math.max(0, Math.min(255, Math.round(rgba[3] * 255.0f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String tryCaptureShortcutString() {
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            return cancelCapture();
        }

        boolean ctrl = false, shift = false, alt = false, superKey = false;
        try {
            ctrl = ImGui.getIO().getKeyCtrl();
            shift = ImGui.getIO().getKeyShift();
            alt = ImGui.getIO().getKeyAlt();
            superKey = ImGui.getIO().getKeySuper();
        } catch (Exception ignored) {}

        for (int key = KeyboardShortcutConverter.captureKeyMin(); key <= KeyboardShortcutConverter.captureKeyMax(); key++) {
            if (ImGui.isKeyPressed(key)) {
                if (KeyboardShortcutConverter.isModifierKey(key)) {
                    continue;
                }
                String s = KeyboardShortcutConverter.convertToShortcutString(key, composeModifiers(ctrl, shift, alt, superKey));
                if (s != null) return s;
            }
        }
        return null;
    }

    private String cancelCapture() {
        editingActionId = null;
        suppressCloseHotkeysThisFrame = true;
        return null;
    }

    private int composeModifiers(boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        int m = 0;
        if (shift) m |= 0x0001; // GLFW_MOD_SHIFT
        if (ctrl) m |= 0x0002;  // GLFW_MOD_CONTROL
        if (alt) m |= 0x0004;   // GLFW_MOD_ALT
        if (superKey) m |= 0x0008; // GLFW_MOD_SUPER
        return m;
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private void renderHelpMarkerInline(String id, String tooltip) {
        ImGui.sameLine();
        ImGui.textDisabled("(?)##" + id);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }

    private void applyCaptureSuppression(boolean captureActive) {
        if (captureSuppressionApplied == captureActive) {
            return;
        }
        ShortcutManager.getInstance().setDispatchSuppressed(captureActive);
        captureSuppressionApplied = captureActive;
    }
}


