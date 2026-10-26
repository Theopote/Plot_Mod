package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.VariableCrossSectionEditor;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Station-based variable cross sections. */
public final class RoadVariableSectionSection {
    private final VariableCrossSectionEditor variableCrossSectionEditor = new VariableCrossSectionEditor();

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.variable_cross_section"))) {
            return;
        }
        variableCrossSectionEditor.render(
            ctx.ui, ctx.network, ctx.road, ctx.chainageDisplay(), ctx.ui::pushRoadEditHistory);
    }
}
