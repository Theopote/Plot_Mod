package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.RoadCenterlineEditPanel;
import com.plot.plugin.road.ui.RoadSegmentEditor;
import com.plot.plugin.road.ui.RoadUiSections;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Segment list, centerline local edits, and slope overrides. */
public final class RoadSegmentSection {
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.segments"))) {
            return;
        }
        RoadUiSections.level("plugin.road.design_stack.segment_level_hint");
        segmentEditor.renderSegmentList(ctx.ui, ctx.network, ctx.road);
        segmentEditor.renderSegmentSummary(ctx.network, ctx.road, ctx.primaryEdge, ctx.chainageDisplay());
        centerlineEditPanel.render(ctx.ui, ctx.network, ctx.road, ctx.primaryEdge);
        segmentEditor.renderElevationHint(ctx.ui, ctx.primaryEdge);
    }
}
