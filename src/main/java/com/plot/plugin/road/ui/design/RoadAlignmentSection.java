package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.HorizontalAlignmentSummaryEditor;
import com.plot.plugin.road.ui.RoadCrossSectionEditor;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Horizontal alignment and engineering cross-section parameters. */
public final class RoadAlignmentSection {
    private final HorizontalAlignmentSummaryEditor horizontalAlignmentEditor =
        new HorizontalAlignmentSummaryEditor();

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.alignment"))) {
            return;
        }
        horizontalAlignmentEditor.render(ctx.ui, ctx.network, ctx.road, ctx.chainageDisplay());
        RoadCrossSectionEditor.renderAdvancedCrossSection(ctx.ui, ctx.road, ctx.ui::pushRoadEditHistory);
    }
}
