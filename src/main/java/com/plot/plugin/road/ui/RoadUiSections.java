package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

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

    /** 编辑层级标签（ROAD-LEVEL / SEGMENT-LEVEL）。 */
    public static void level(String labelKey) {
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(PluginUiColors.ACCENT_BLUE, PlotI18n.tr(labelKey));
        ImGui.spacing();
    }
}
