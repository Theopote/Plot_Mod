package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 道路系统 UI 分区标题，统一 Overview / Adopt / Edit / Build 内部层级。
 */
public final class RoadUiSections {

    private RoadUiSections() {
    }

    /** 大区块标题（Network Health、Road List 等）。 */
    public static void section(String labelKey) {
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(labelKey).toUpperCase());
        ImGui.separator();
        ImGui.spacing();
    }

    /** 向导步骤标题（Step 1 — Centerline 等）。 */
    public static void step(String labelKey) {
        ImGui.spacing();
        RoadUiWidgets.textWrapped(PlotI18n.tr(labelKey));
        ImGui.spacing();
    }

    /** 分组标题（材质 / 结构等）。 */
    public static void group(String labelKey) {
        ImGui.spacing();
        RoadUiWidgets.textWrapped(PlotI18n.tr(labelKey));
        ImGui.spacing();
    }

    /** 一级工作域（快速设计 / 专业工具等）。 */
    public static void domain(String labelKey) {
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(PluginUiColors.ACCENT_BLUE, PlotI18n.tr(labelKey));
        ImGui.spacing();
    }

    /** 二级折叠区标题（较 domain 降权）。 */
    public static void secondaryHeader(String labelKey) {
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(labelKey));
    }

    /** 诊断/警告区背景容器开始；必须与 {@link #endDiagnosticsZone()} 配对。 */
    public static void beginDiagnosticsZone(String id, float height) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, PluginUiColors.PANEL_BG_DARK);
        ImGui.pushStyleColor(ImGuiCol.Border, PluginUiColors.WARNING & 0x00FFFFFF | 0x80000000);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ChildBorderSize, 1f);
        ImGui.beginChild(id, 0f, height, true);
    }

    public static void endDiagnosticsZone() {
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);
    }
}
