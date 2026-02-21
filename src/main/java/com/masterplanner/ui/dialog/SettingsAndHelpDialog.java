package com.masterplanner.ui.dialog;
import com.masterplanner.core.snap.SnapManager;
import com.masterplanner.core.snap.SnapPriorityEvaluator;
import com.masterplanner.ui.tools.impl.modify.ControlPointEditTool;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.tools.snap.SnapVisualStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// no-op

/**
 * 设置与帮助对话框（第一期：快捷键页）
 */
public class SettingsAndHelpDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/SettingsAndHelpDialog");
    private static SettingsAndHelpDialog INSTANCE;

    private boolean isOpen = false;
    private final ImString searchText = new ImString(64);
    private String editingActionId = null; // 当前正在录制快捷键的动作

    private SettingsAndHelpDialog() {}

    public static SettingsAndHelpDialog getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SettingsAndHelpDialog();
        }
        return INSTANCE;
    }

    public void open() {
        isOpen = true;
        LOGGER.debug("打开 设置与帮助 对话框");
    }

    public void close() {
        isOpen = false;
        editingActionId = null;
        LOGGER.debug("关闭 设置与帮助 对话框");
    }

    public void render() {
        if (!isOpen) return;

        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Separator, theme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, theme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, theme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, theme.mutedText);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Header, theme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.tabActive);
        ImGui.pushStyleColor(ImGuiCol.Tab, theme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.TabHovered, theme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.TabActive, theme.tabActive);
        ImGui.pushStyleColor(ImGuiCol.TabUnfocused, theme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.TabUnfocusedActive, theme.tabActive);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.TabRounding, 0.0f);

        try {
            ImGui.setNextWindowSize(680, 520);
            int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;
            if (!ImGui.begin("设置与帮助", flags)) {
                ImGui.end();
                return;
            }

            if (ImGui.beginTabBar("##settings_tabs", ImGuiTabBarFlags.None)) {
                if (ImGui.beginTabItem("快捷键")) {
                    renderShortcutsPage();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("显示反馈")) {
                    renderDisplayPage();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("帮助与教程")) {
                    renderHelpPage();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }

            // 底部操作区：确定 / 关闭（设置已即时生效，这里仅关闭窗口）
            ImGui.separator();
            if (ImGui.button("确定", 80, 0) || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                close();
            }
            ImGui.sameLine();
            if (ImGui.button("关闭", 80, 0) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                close();
            }

            ImGui.end();
        } finally {
            ImGui.popStyleVar(7);
            ImGui.popStyleColor(25);
        }
    }

    private void renderShortcutsPage() {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        // 顶部工具行：搜索、重置默认、导出、导入
        ImGui.text("搜索：");
        ImGui.sameLine();
        ImGui.setNextItemWidth(240);
        ImGui.inputText("##shortcut_search", searchText);

        ImGui.sameLine();
        if (ImGui.button("重置为默认")) {
            KeymapManager.getInstance().resetToDefault();
        }
        ImGui.sameLine();
        if (ImGui.button("导出配置")) {
            KeymapManager.getInstance().exportBindings();
        }
        ImGui.sameLine();
        if (ImGui.button("导入配置")) {
            KeymapManager.getInstance().importBindings();
        }

        ImGui.separator();

        // 表头
        ImGui.columns(3, "shortcut_table");
        ImGui.text("动作"); ImGui.nextColumn();
        ImGui.text("当前快捷键"); ImGui.nextColumn();
        ImGui.text("操作"); ImGui.nextColumn();
        ImGui.separator();

        String filter = searchText.get().trim().toLowerCase();
        for (KeymapManager.ActionDef def : KeymapManager.getInstance().getAllActions()) {
            String display = def.displayName();
            String actionId = def.actionId();
            if (!filter.isEmpty()) {
                String binding = KeymapManager.getInstance().getBindingDisplay(actionId);
                if (!(display.toLowerCase().contains(filter) || (binding != null && binding.toLowerCase().contains(filter)))) {
                    continue;
                }
            }

            ImGui.text(display);
            ImGui.nextColumn();

            // 显示与编辑状态
            String current = KeymapManager.getInstance().getBindingDisplay(actionId);
            if (editingActionId != null && editingActionId.equals(actionId)) {
                ImGui.textColored(theme.warningText, "按下组合键...（Esc取消）");
                // 录制：遍历GLFW keyCode范围，检测是否有按键按下
                String captured = tryCaptureShortcutString();
                if (captured != null) {
                    boolean ok = KeymapManager.getInstance().updateBinding(actionId, captured);
                    if (!ok) {
                        // 冲突提示（简单版）：显示一条提示文本
                        ImGui.textColored(theme.infoText, "存在冲突，已覆盖旧绑定。");
                    }
                    editingActionId = null;
                }
            } else {
                ImGui.text(current == null || current.isEmpty() ? "未绑定" : current);
            }
            ImGui.nextColumn();

            // 操作按钮
            if (editingActionId != null && editingActionId.equals(actionId)) {
                if (ImGui.button("取消##cancel_" + actionId)) {
                    editingActionId = null;
                }
            } else {
                if (ImGui.button("编辑##edit_" + actionId)) {
                    editingActionId = actionId;
                }
                ImGui.sameLine();
                if (ImGui.button("解绑##unbind_" + actionId)) {
                    KeymapManager.getInstance().clearBinding(actionId);
                }
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);

        ImGui.separator();
        ImGui.textDisabled("说明：单键（如 L、P、C、R、E、S、A、Space）用于快速切换工具；组合键（如 Ctrl+Z/Y、Ctrl+N）用于全局操作。按住 Shift 在绘制或修改时启用正交/角度约束。");
    }

    private void renderHelpPage() {
        ImGui.textWrapped("MasterPlanner 快速上手：");
        ImGui.bulletText("F1：打开快捷键速查（预留）");
        ImGui.bulletText("按住 Shift：绘制/修改时正交或角度约束");
        ImGui.separator();
        ImGui.textWrapped("如果快捷键不生效，请检查是否有文本框获得输入焦点，或是否与其他模组快捷键冲突。");
    }

    private void renderDisplayPage() {
        SnapManager snapManager = SnapManager.getInstance();

        ImGui.textWrapped("线图形选择与吸附时的视觉反馈设置（端点/中点/中心点等）。");
        ImGui.separator();

        ImGui.text("吸附点显示");
        ImGui.indent(10);

        ImBoolean showMarkers = new ImBoolean(snapManager.isShowSnapMarkersEnabled());
        if (ImGui.checkbox("显示吸附标记", showMarkers)) {
            snapManager.setShowSnapMarkersEnabled(showMarkers.get());
        }

        ImBoolean endPoint = new ImBoolean(snapManager.isEndPointSnapEnabled());
        if (ImGui.checkbox("显示端点反馈", endPoint)) {
            snapManager.setEndPointSnapEnabled(endPoint.get());
        }

        ImBoolean midPoint = new ImBoolean(snapManager.isMidPointSnapEnabled());
        if (ImGui.checkbox("显示中点反馈", midPoint)) {
            snapManager.setMidPointSnapEnabled(midPoint.get());
        }

        ImBoolean centerPoint = new ImBoolean(snapManager.isCenterPointSnapEnabled());
        if (ImGui.checkbox("显示圆心反馈", centerPoint)) {
            snapManager.setCenterPointSnapEnabled(centerPoint.get());
        }

        ImBoolean centroid = new ImBoolean(snapManager.isCentroidSnapEnabled());
        if (ImGui.checkbox("显示中心点反馈", centroid)) {
            snapManager.setCentroidSnapEnabled(centroid.get());
        }

        float[] markerSize = new float[] { snapManager.getMarkerSize() };
        ImGui.setNextItemWidth(180);
        if (ImGui.sliderFloat("标记大小", markerSize, 2.0f, 10.0f, "%.1f px")) {
            snapManager.setMarkerSize(markerSize[0]);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("吸附颜色（自定义）");
        ImGui.textDisabled("不同吸附点可设置不同颜色，实时生效");

        renderSnapColorEditor("端点", SnapPriorityEvaluator.SnapType.END_POINT);
        renderSnapColorEditor("最近点", SnapPriorityEvaluator.SnapType.NEAREST_POINT);
        renderSnapColorEditor("中点", SnapPriorityEvaluator.SnapType.MID_POINT);
        renderSnapColorEditor("中心点", SnapPriorityEvaluator.SnapType.CENTER_POINT);
        renderSnapColorEditor("垂足", SnapPriorityEvaluator.SnapType.PERPENDICULAR);
        renderSnapColorEditor("切点", SnapPriorityEvaluator.SnapType.TANGENT);
        renderSnapColorEditor("角点", SnapPriorityEvaluator.SnapType.VERTEX);

        if (ImGui.button("重置全部吸附颜色")) {
            SnapVisualStyle.resetCustomColors();
        }

        ImGui.unindent(10);
        ImGui.separator();

        ImGui.text("控制点显示");
        ImGui.indent(10);

        ImBoolean showControlPoints = new ImBoolean(ControlPointEditTool.isDisplayEnabled());
        if (ImGui.checkbox("显示控制点", showControlPoints)) {
            ControlPointEditTool.setDisplayEnabled(showControlPoints.get());
        }

        ImBoolean showPointIndex = new ImBoolean(ControlPointEditTool.isShowPointIndex());
        if (ImGui.checkbox("显示控制点编号", showPointIndex)) {
            ControlPointEditTool.setShowPointIndex(showPointIndex.get());
        }

        ImGui.unindent(10);
        ImGui.separator();
        ImGui.textDisabled("提示：标记大小与颜色会同时影响绘制和修改工具中的吸附反馈。\n");
    }

    private void renderSnapColorEditor(String label, SnapPriorityEvaluator.SnapType type) {
        int argb = SnapVisualStyle.getEffectiveColorArgb(type);
        float[] rgba = argbToFloat4(argb);
        if (ImGui.colorEdit4(label + "##snap_color_" + type.name(), rgba)) {
            SnapVisualStyle.setCustomColor(type, float4ToArgb(rgba));
        }
        ImGui.sameLine();
        if (ImGui.button("重置##snap_reset_" + type.name())) {
            SnapVisualStyle.clearCustomColor(type);
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
        // Esc 取消
        if (ImGui.isKeyPressed(256)) { // GLFW_KEY_ESCAPE
            return cancelCapture();
        }

        // 组合键状态
        boolean ctrl = false, shift = false, alt = false, superKey = false;
        try {
            ctrl = ImGui.getIO().getKeyCtrl();
            shift = ImGui.getIO().getKeyShift();
            alt = ImGui.getIO().getKeyAlt();
            superKey = ImGui.getIO().getKeySuper();
        } catch (Exception ignored) {}

        // 遍历常用键范围（空格到F12/修饰键区）
        for (int key = 32; key <= 348; key++) {
            if (ImGui.isKeyPressed(key)) {
                String s = com.masterplanner.core.shortcut.KeyboardShortcutConverter.convertToShortcutString(key, composeModifiers(ctrl, shift, alt, superKey));
                if (s != null) return s;
            }
        }
        return null;
    }

    private String cancelCapture() {
        editingActionId = null;
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
}


