package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.RoadCenterlineEditPanel;
import com.plot.plugin.road.ui.RoadSegmentEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiSections;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Segment list, centerline local edits, and slope overrides. */
public final class RoadSegmentSection {
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private boolean segmentsHeaderOpen;

    public void finishPendingEdits(RoadUiContext ui) {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ui);
    }

    public void render(RoadDesignSectionContext ctx) {
        boolean open = ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.segments"));
        if (segmentsHeaderOpen && !open) {
            finishPendingEdits(ctx.ui);
        }
        segmentsHeaderOpen = open;
        if (!open) {
            return;
        }
        RoadUiSections.level("plugin.road.design_stack.segment_level_hint");
        segmentEditor.renderSegmentList(ctx.ui, ctx.network, ctx.road);
        segmentEditor.renderSegmentSummary(ctx.network, ctx.road, ctx.primaryEdge, ctx.chainageDisplay());
        centerlineEditPanel.render(ctx.ui, ctx.network, ctx.road, ctx.primaryEdge);
        segmentEditor.renderElevationHint(ctx.ui, ctx.primaryEdge);
    }
}
