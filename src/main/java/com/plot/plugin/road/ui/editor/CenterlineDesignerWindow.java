package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.HorizontalAlignmentSummaryEditor;
import com.plot.plugin.road.ui.RoadCenterlineEditPanel;
import com.plot.plugin.road.ui.RoadSegmentEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiSections;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 中心线 / 平面线形浮动编辑器。 */
public final class CenterlineDesignerWindow extends AbstractRoadFloatingEditor {

    private final HorizontalAlignmentSummaryEditor horizontalAlignmentEditor =
        new HorizontalAlignmentSummaryEditor();
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.CENTERLINE;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.centerline.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_centerline_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_centerline_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.GEOMETRY;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        var live = ctx.networkManager().getNetwork();
        var openSnapshot = live.snapshot();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        if (session == null) {
            return;
        }
        RoadNetwork working = session.workingNetwork();
        Road road = working.getRoad(roadId);
        if (road == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            return;
        }
        String primaryEdgeId = resolvePrimaryEdgeId(ctx, working, road);
        RoadEdge primaryEdge = working.getEdge(primaryEdgeId);
        if (primaryEdge == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.edge_missing"));
            return;
        }

        ChainageDisplayContext chainageDisplay = null;
        if (RoadStationing.isStationable(working, road)) {
            chainageDisplay = new ChainageDisplayContext(
                RoadStationing.canonicalLength(working, road),
                chainageDisplayMode,
                RoadStationFormat.DISTANCE_METERS);
        }

        RoadUiSections.section("plugin.road.floating_editor.centerline.tab.alignment");
        horizontalAlignmentEditor.render(ctx, working, road, chainageDisplay);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.floating_editor.centerline.tab.segments");
        float segmentListHeight = Math.min(
            140f,
            Math.max(72f, ImGui.getContentRegionAvailY() * 0.22f));
        ImGui.beginChild(
            "##centerline_segment_list",
            0f,
            segmentListHeight,
            false,
            ImGuiWindowFlags.AlwaysVerticalScrollbar);
        segmentEditor.renderSegmentList(ctx, working, road);
        ImGui.endChild();
        segmentEditor.renderSegmentSummary(working, road, primaryEdge, chainageDisplay);
        centerlineEditPanel.renderWorkingNetwork(ctx, working, road, primaryEdge);
        segmentEditor.renderElevationHint(ctx, primaryEdge);
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        var live = ctx.networkManager().getNetwork();
        CenterlineEditorApplier.applyToLive(live, session.workingNetwork(), roadId);
        com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossings(live);
        com.plot.plugin.road.crossing.RoadStackCrossingReconciler.reconcileStackCrossings(live);
        ctx.networkManager().invalidateEditedProfilesIfGeometryChanged();
    }

    @Override
    public FinishResult finishOrPrompt(RoadUiContext ctx) {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        return super.finishOrPrompt(ctx);
    }

    private static String resolvePrimaryEdgeId(RoadUiContext ctx, RoadNetwork working, Road road) {
        String selected = ctx.networkManager().getPrimarySelectedEdgeId();
        if (selected != null && !selected.isBlank() && working.getEdge(selected) != null) {
            String roadIdForEdge = working.getEdge(selected).getRoadId();
            if (road.getId().equals(roadIdForEdge)) {
                return selected;
            }
        }
        return road.getOrderedSegmentIds().isEmpty()
            ? ""
            : road.getOrderedSegmentIds().getFirst();
    }
}
