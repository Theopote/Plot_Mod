package com.plot.ui.dialog;
import com.plot.core.shortcut.KeyboardShortcutConverter;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.core.snap.SnapManager;
import com.plot.core.snap.SnapPriorityEvaluator;
import com.plot.ui.tools.impl.modify.ControlPointEditTool;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.tools.snap.SnapVisualStyle;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// no-op

/**
 * 设置与帮助对话框（第一期：快捷键页）
 */
public class SettingsAndHelpDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/SettingsAndHelpDialog");
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
            int flags = ImGuiWindowFlags.NoCollapse
                    | ImGuiWindowFlags.NoSavedSettings
                    | ImGuiWindowFlags.NoScrollbar
                    | ImGuiWindowFlags.NoScrollWithMouse;
            if (!ImGui.begin(PlotI18n.tr("screen.plot.settings_help"), flags)) {
                ImGui.end();
                return;
            }
            ImGui.setScrollY(0.0f);

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
                        if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.shortcuts"))) {
                            renderShortcutsPage();
                            ImGui.endTabItem();
                        }
                        if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.feedback"))) {
                            renderDisplayPage();
                            ImGui.endTabItem();
                        }
                        if (ImGui.beginTabItem(PlotI18n.tr("settings.plot.help"))) {
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
                    DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.back"), PlotI18n.tr("button.plot.done"), DialogStyleManager.getContentWidth());
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

    private float getBottomHintReservedHeight(String text) {
        float wrapWidth = Math.max(180.0f, ImGui.getContentRegionAvailX());
        float textHeight = ImGui.calcTextSize(text, false, wrapWidth).y;
        return textHeight
                + DialogStyleManager.SUBSECTION_GAP
                + DialogStyleManager.ROW_GAP * 2.0f
                + ImGui.getStyle().getFramePaddingY() * 2.0f;
    }

    private void renderBottomHintText(String text) {
        //DialogLayoutHelper.rowGap();
        ImGui.separator();
        DialogLayoutHelper.rowGap();
        DialogLayoutHelper.helpText(text);
    }

    private void renderShortcutsPage() {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        final String shortcutHintText = PlotI18n.tr("settings.plot.shortcuts_hint");
        boolean captureActive = editingActionId != null;
        applyCaptureSuppression(captureActive);

        // Esc 在录制态仅取消录制，不应冒泡到对话框“返回/关闭”逻辑
        if (captureActive && DialogLayoutHelper.isCancelShortcutPressed()) {
            cancelCapture();
            captureActive = false;
        }

        float hintReservedHeight = getBottomHintReservedHeight(shortcutHintText);
        if (DialogLayoutHelper.beginRemainingChild("##shortcut_panel_region", 0.0f,
                true, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            if (DialogLayoutHelper.beginRemainingChild("##shortcut_scroll_region", hintReservedHeight,
                    false, ImGuiWindowFlags.NoScrollbar)) {
                // 顶部工具行：搜索与重置默认
                if (captureActive) ImGui.beginDisabled();
                ImGui.text(PlotI18n.tr("settings.plot.search"));
                ImGui.sameLine();
                ImGui.setNextItemWidth(240);
                ImGui.inputTextWithHint("##shortcut_search", PlotI18n.tr("settings.plot.shortcuts_search_hint"), searchText);

                ImGui.sameLine();
                if (ImGui.button(PlotI18n.tr("button.plot.reset"))) {
                    KeymapManager.getInstance().resetToDefault();
                }
                if (captureActive) ImGui.endDisabled();

                if (editingActionId != null) {
                    String actionName = KeymapManager.getInstance().getActionDisplayName(editingActionId);
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, withAlpha(theme.accent, 56));
                    if (ImGui.beginChild("##shortcut_capture_notice", 0, 40, true)) {
                        ImGui.textColored(theme.warningText, PlotI18n.tr("settings.plot.shortcuts_recording", actionName));
                    }
                    ImGui.endChild();
                    ImGui.popStyleColor();
                    ImGui.spacing();
                }

                String filter = searchText.get().trim().toLowerCase();
                List<KeymapManager.ActionDef> filteredActions = new ArrayList<>();
                for (KeymapManager.ActionDef def : KeymapManager.getInstance().getAllActions()) {
                    String display = def.displayName();
                    String actionId = def.actionId();
                    if (!filter.isEmpty()) {
                        String binding = KeymapManager.getInstance().getBindingDisplay(actionId);
                        if (!(display.toLowerCase().contains(filter)
                                || (binding != null && binding.toLowerCase().contains(filter)))) {
                            continue;
                        }
                    }
                    filteredActions.add(def);
                }

                if (filteredActions.isEmpty()) {
                    ImGui.textDisabled(PlotI18n.tr("settings.plot.shortcuts_no_results"));
                    ImGui.sameLine();
                    if (ImGui.smallButton(PlotI18n.tr("settings.plot.shortcuts_clear_search") + "##clear_shortcut_search")) {
                        searchText.set("");
                    }
                } else {
                    float tableHeight = Math.max(240.0f, ImGui.getContentRegionAvailY());
                    int tableFlags = ImGuiTableFlags.BordersInnerV
                            | ImGuiTableFlags.RowBg
                            | ImGuiTableFlags.Resizable
                            | ImGuiTableFlags.ScrollY
                            | ImGuiTableFlags.SizingStretchProp;
                    if (ImGui.beginTable("shortcut_table", 3, tableFlags, 0, tableHeight)) {
                        ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_action"), ImGuiTableColumnFlags.WidthStretch, 1.0f);
                        ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_current"), ImGuiTableColumnFlags.WidthFixed, 220.0f);
                        ImGui.tableSetupColumn(PlotI18n.tr("settings.plot.shortcuts_operations"), ImGuiTableColumnFlags.WidthFixed, 210.0f);
                        ImGui.tableHeadersRow();

                        String lastCategory = null;
                        for (KeymapManager.ActionDef def : filteredActions) {
                            String display = def.displayName();
                            String actionId = def.actionId();
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
                                ImGui.textColored(theme.warningText, PlotI18n.tr("settings.plot.shortcuts_press_combo"));
                                if (ImGui.isKeyPressed(ImGuiKey.Backspace)) {
                                    KeymapManager.getInstance().clearBinding(actionId);
                                    editingActionId = null;
                                } else {
                                    String captured = tryCaptureShortcutString();
                                    if (captured != null) {
                                        String conflicted = KeymapManager.getInstance().updateBindingAndGetConflict(actionId, captured);
                                        if (conflicted != null) {
                                            String conflictName = KeymapManager.getInstance().getActionDisplayName(conflicted);
                                            shortcutConflictMessage = PlotI18n.tr("settings.plot.shortcuts_conflict", captured, conflictName);
                                            ImGui.openPopup("##shortcut_conflict_popup");
                                        }
                                        editingActionId = null;
                                    }
                                }
                            } else {
                                ImGui.text(current == null || current.isEmpty() ? PlotI18n.tr("settings.plot.shortcuts_unbound") : current);
                                if (!captureActive && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                                    editingActionId = actionId;
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_double_click_record"));
                                }
                            }

                            ImGui.tableSetColumnIndex(2);
                            if (isEditing) {
                                if (ImGui.button(PlotI18n.tr("button.plot.cancel") + "##cancel_" + actionId)) {
                                    editingActionId = null;
                                }
                            } else {
                                if (captureActive) ImGui.beginDisabled();
                                if (ImGui.button(PlotI18n.tr("button.plot.edit") + "##edit_" + actionId)) {
                                    editingActionId = actionId;
                                }
                                ImGui.sameLine();
                                if (ImGui.button(PlotI18n.tr("button.plot.clear") + "##clear_" + actionId)) {
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
                                if (ImGui.smallButton(PlotI18n.tr("button.plot.reset") + "##reset_" + actionId)) {
                                    KeymapManager.getInstance().updateBinding(actionId, defaultKey);
                                }
                                if (canReset) {
                                    ImGui.popStyleColor(3);
                                }
                                if (ImGui.isItemHovered()) {
                                    if (canReset) {
                                        ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_reset_default", defaultKey));
                                    } else if (!hasDefault) {
                                        ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_no_default"));
                                    } else {
                                        ImGui.setTooltip(PlotI18n.tr("settings.plot.shortcuts_already_default"));
                                    }
                                }
                                if (!canReset) ImGui.endDisabled();
                                if (captureActive) ImGui.endDisabled();
                            }
                        }
                        ImGui.endTable();
                    }
                }

                if (ImGui.beginPopup("##shortcut_conflict_popup")) {
                    ImGui.textWrapped(shortcutConflictMessage == null ? PlotI18n.tr("settings.plot.shortcuts_conflict_generic") : shortcutConflictMessage);
                    if (ImGui.button(PlotI18n.tr("button.plot.got_it"), 90, 0)) {
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.endPopup();
                }
            }
            ImGui.endChild();

            renderBottomHintText(shortcutHintText);
        }
        ImGui.endChild();
    }

    private void renderHelpPage() {
        ImGui.textDisabled(PlotI18n.tr("settings.plot.help_select_topic"));

        if (DialogLayoutHelper.beginSettingsPageBody("##help_scroll_region", 0.0f)) {
            if (ImGui.beginChild("##help_nav", 180, 0, true)) {
                if (ImGui.selectable(PlotI18n.tr("settings.plot.basic_operations"), selectedHelpTopic == 0)) selectedHelpTopic = 0;
                if (ImGui.selectable(PlotI18n.tr("settings.plot.advanced_tips"), selectedHelpTopic == 1)) selectedHelpTopic = 1;
                if (ImGui.selectable(PlotI18n.tr("settings.plot.shortcuts_troubleshooting"), selectedHelpTopic == 2)) selectedHelpTopic = 2;
                if (ImGui.selectable(PlotI18n.tr("settings.plot.changelog"), selectedHelpTopic == 3)) selectedHelpTopic = 3;
            }
            ImGui.endChild();

            ImGui.sameLine();

            if (ImGui.beginChild("##help_content", 0, 0, true)) {
                switch (selectedHelpTopic) {
                    case 0 -> {
                        ImGui.text(PlotI18n.tr("settings.plot.basic_operations"));
                        ImGui.separator();
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_select"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_move"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_basic_zoom"));
                    }
                    case 1 -> {
                        ImGui.text(PlotI18n.tr("settings.plot.advanced_tips"));
                        ImGui.separator();
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_shift"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_snap"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_advanced_edit"));
                    }
                    case 2 -> {
                        ImGui.text(PlotI18n.tr("settings.plot.shortcuts_troubleshooting"));
                        ImGui.separator();
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_conflict"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_invalid"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.help_troubleshoot_esc"));
                    }
                    case 3 -> {
                        ImGui.text(PlotI18n.tr("settings.plot.changelog"));
                        ImGui.separator();
                        ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_table"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_recording"));
                        ImGui.bulletText(PlotI18n.tr("settings.plot.changelog_display"));
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
        final String displayHintText = PlotI18n.tr("settings.plot.display_marker_hint");

        DialogLayoutHelper.helpText("Object Snap（OSnap）与反馈设置：控制端点/中点/重心等吸附提示及显示样式。");

        if (DialogLayoutHelper.beginRemainingChild("##display_panel_region", 0.0f, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            float hintReservedHeight = getBottomHintReservedHeight(displayHintText);
            if (DialogLayoutHelper.beginRemainingChild("##display_scroll_region", hintReservedHeight, false,
                    ImGuiWindowFlags.NoScrollbar)) {

                if (ImGui.treeNodeEx(PlotI18n.tr("settings.plot.display_basic_settings") + "##display_basic", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.indent(10);

                    if (ImGui.beginTable("##osnap_toggle_grid", 2, ImGuiTableFlags.SizingStretchProp)) {
                        ImGui.tableNextRow();
                        ImGui.tableSetColumnIndex(0);
                        if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_snap_markers"), showMarkersState)) {
                            snapManager.setShowSnapMarkersEnabled(showMarkersState.get());
                        }
                        ImGui.tableSetColumnIndex(1);
                        if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_endpoint_feedback"), endPointState)) {
                            snapManager.setEndPointSnapEnabled(endPointState.get());
                        }

                        ImGui.tableNextRow();
                        ImGui.tableSetColumnIndex(0);
                        if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_midpoint_feedback"), midPointState)) {
                            snapManager.setMidPointSnapEnabled(midPointState.get());
                        }
                        ImGui.tableSetColumnIndex(1);
                        if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_center_feedback"), centerPointState)) {
                            snapManager.setCenterPointSnapEnabled(centerPointState.get());
                        }
                        renderHelpMarkerInline("center_point", PlotI18n.tr("settings.plot.snap_help_center_point"));

                        ImGui.tableNextRow();
                        ImGui.tableSetColumnIndex(0);
                        if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_centroid_feedback"), centroidState)) {
                            snapManager.setCentroidSnapEnabled(centroidState.get());
                        }
                        renderHelpMarkerInline("centroid", PlotI18n.tr("settings.plot.snap_help_centroid"));
                        ImGui.tableSetColumnIndex(1);
                        ImGui.textDisabled(" ");

                        ImGui.endTable();
                    }

                    DialogLayoutHelper.rowGap();
                    float[] markerSize = new float[] { snapManager.getMarkerSize() };
                    ImGui.setNextItemWidth(Math.min(220.0f, Math.max(160.0f, ImGui.getContentRegionAvailX() - 12.0f)));
                    if (ImGui.sliderFloat(PlotI18n.tr("snap.plot.marker_size"), markerSize, 2.0f, 10.0f, "%.1f px")) {
                        snapManager.setMarkerSize(markerSize[0]);
                    }

                    DialogLayoutHelper.rowGap();
                    if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_control_points"), showControlPointsState)) {
                        ControlPointEditTool.setDisplayEnabled(showControlPointsState.get());
                    }

                    DialogLayoutHelper.rowGap();
                    if (ImGui.checkbox(PlotI18n.tr("settings.plot.show_control_point_index"), showPointIndexState)) {
                        ControlPointEditTool.setShowPointIndex(showPointIndexState.get());
                    }

                    ImGui.unindent(10);
                    ImGui.treePop();
                }

                DialogLayoutHelper.subsectionGap();
                if (ImGui.treeNodeEx(PlotI18n.tr("settings.plot.display_color_custom") + "##display_color", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.indent(10);
                    ImGui.textDisabled(PlotI18n.tr("settings.plot.display_color_hint"));
                    DialogLayoutHelper.rowGap();

                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_end_point"), SnapPriorityEvaluator.SnapType.END_POINT);
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_nearest"), SnapPriorityEvaluator.SnapType.NEAREST_POINT);
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_mid_point"), SnapPriorityEvaluator.SnapType.MID_POINT);
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_center"), SnapPriorityEvaluator.SnapType.CENTER_POINT, PlotI18n.tr("settings.plot.snap_help_center_color"));
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_perpendicular"), SnapPriorityEvaluator.SnapType.PERPENDICULAR, PlotI18n.tr("settings.plot.snap_help_perpendicular"));
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_tangent"), SnapPriorityEvaluator.SnapType.TANGENT, PlotI18n.tr("settings.plot.snap_help_tangent"));
                    renderSnapColorEditor(PlotI18n.tr("settings.plot.snap_color_vertex"), SnapPriorityEvaluator.SnapType.VERTEX);

                    DialogLayoutHelper.rowGap();
                    if (ImGui.button(PlotI18n.tr("button.plot.reset_all_snap_colors"))) {
                        SnapVisualStyle.resetCustomColors();
                    }
                    ImGui.unindent(10);
                    ImGui.treePop();
                }
            }
            ImGui.endChild();

            renderBottomHintText(displayHintText);
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
        if (ImGui.smallButton(PlotI18n.tr("button.plot.reset") + "##snap_reset_" + type.name())) {
            SnapVisualStyle.clearCustomColor(type);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("settings.plot.snap_color_reset_tooltip"));
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
        } catch (Exception e) { ExceptionDebug.log("SettingsAndHelpDialog: read modifier key state", e); }

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


