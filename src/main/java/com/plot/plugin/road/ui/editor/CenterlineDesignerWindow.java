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
    /** 窗口内草稿分段选择；确认成功前不写入全局选中。 */
    private String selectedWorkingEdgeId = "";

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
        selectedWorkingEdgeId = resolveInitialWorkingEdgeId(ctx, live.getRoad(roadId));
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));
    }

    @Override
    public void discard(RoadUiContext ctx) {
        selectedWorkingEdgeId = "";
        super.discard(ctx);
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
        selectedWorkingEdgeId = resolveWorkingEdgeId(working, road, selectedWorkingEdgeId);
        RoadEdge primaryEdge = working.getEdge(selectedWorkingEdgeId);
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
        float segmentListHeight = Math.clamp(ImGui.getContentRegionAvailY() * 0.22f, 72f,
                140f);
        ImGui.beginChild(
            "##centerline_segment_list",
            0f,
            segmentListHeight,
            false,
            ImGuiWindowFlags.AlwaysVerticalScrollbar);
        segmentEditor.renderWorkingSegmentList(
            ctx,
            working,
            road,
            selectedWorkingEdgeId,
            edgeId -> selectedWorkingEdgeId = edgeId);
        ImGui.endChild();
        segmentEditor.renderSegmentSummary(ctx, working, road, primaryEdge, chainageDisplay);
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
        // 拓扑可能已变：先按 road 刷新选择集，再落到草稿中选中的 live Edge。
        if (live.getRoad(roadId) != null) {
            ctx.networkManager().selectRoad(roadId, false);
        }
        if (selectedWorkingEdgeId != null
            && !selectedWorkingEdgeId.isBlank()
            && live.getEdge(selectedWorkingEdgeId) != null) {
            ctx.networkManager().setPrimarySelectedEdge(selectedWorkingEdgeId);
        }
    }

    @Override
    public FinishResult finishOrPrompt(RoadUiContext ctx) {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        return super.finishOrPrompt(ctx);
    }

    /** 包内测试：当前草稿选中的分段。 */
    String selectedWorkingEdgeId() {
        return selectedWorkingEdgeId;
    }

    /** 包内测试：切换草稿分段选择。 */
    void selectWorkingEdge(String edgeId) {
        selectedWorkingEdgeId = edgeId != null ? edgeId : "";
    }

    private static String resolveInitialWorkingEdgeId(RoadUiContext ctx, Road liveRoad) {
        if (liveRoad == null) {
            return "";
        }
        String selected = ctx.networkManager().getPrimarySelectedEdgeId();
        if (selected != null
            && !selected.isBlank()
            && liveRoad.getOrderedSegmentIds().contains(selected)) {
            return selected;
        }
        return liveRoad.getOrderedSegmentIds().isEmpty()
            ? ""
            : liveRoad.getOrderedSegmentIds().getFirst();
    }

    private static String resolveWorkingEdgeId(RoadNetwork working, Road road, String preferred) {
        if (preferred != null && !preferred.isBlank() && working.getEdge(preferred) != null) {
            String roadIdForEdge = working.getEdge(preferred).getRoadId();
            if (road.getId().equals(roadIdForEdge)) {
                return preferred;
            }
        }
        return road.getOrderedSegmentIds().isEmpty()
            ? ""
            : road.getOrderedSegmentIds().getFirst();
    }
}
