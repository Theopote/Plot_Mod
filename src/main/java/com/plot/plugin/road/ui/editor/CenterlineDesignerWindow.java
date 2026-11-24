package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadCenterlineEditPanel;
import com.plot.plugin.road.ui.RoadSegmentEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.Set;

/**
 * 中心线浮动编辑器（分段折线编辑）。
 * <p>
 * 仅编辑当前一条道路：打开时收敛为单选，确认写回后保持该路选中并落到草稿分段。
 * 平面线形入口暂不在此展示（底层 HA 仍可由生成/同步使用）。
 */
public final class CenterlineDesignerWindow extends AbstractRoadFloatingEditor {

    /** 下半区左轨（分段列表）宽度占比，对齐设施/变截面主从。 */
    private static final float SEGMENT_RAIL_WIDTH_FRACTION = 0.28f;

    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    /** 窗口内草稿分段选择；确认成功前不写入全局选中。 */
    private String selectedWorkingEdgeId = "";

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.CENTERLINE;
    }

    @Override
    protected float fixedWidthOfScreen() {
        return RoadFloatingEditorShell.CENTERLINE_WIDTH_OF_SCREEN;
    }

    @Override
    protected float fixedHeightOfScreen() {
        return RoadFloatingEditorShell.CENTERLINE_HEIGHT_OF_SCREEN;
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
        // 中心线一次只改一条路：打开时收敛选择，避免多选作用域与确认写回冲突。
        if (roadId != null && !roadId.isBlank()) {
            ctx.networkManager().selectRoad(roadId, false);
        }
        var live = ctx.networkManager().getNetwork();
        selectedWorkingEdgeId = resolveInitialWorkingEdgeId(ctx, live.getRoad(roadId));
        centerlineEditPanel.clearMessages();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            (open, working) -> CenterlineEditorApplier.hasChanges(open, working, roadId));
    }

    @Override
    public void discard(RoadUiContext ctx) {
        selectedWorkingEdgeId = "";
        centerlineEditPanel.clearMessages();
        super.discard(ctx);
    }

    @Override
    protected void onWorkingNetworkReplaced() {
        centerlineEditPanel.clearMessages();
    }

    @Override
    public boolean matchesScope(Set<String> selectedRoadIds) {
        return selectedRoadIds != null
            && selectedRoadIds.size() == 1
            && roadId != null
            && !roadId.isBlank()
            && selectedRoadIds.contains(roadId);
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

        // 中心线设计器仅只读展示桩号，固定自起点递增（无切换 UI）。
        final ChainageDisplayContext chainageDisplay = RoadStationing.isStationable(working, road)
            ? ChainageDisplayContext.fromStart(RoadStationing.canonicalLength(working, road))
            : null;

        if (primaryEdge == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.edge_missing"));
            return;
        }
        renderSegmentMasterDetail(ctx, working, road, primaryEdge, chainageDisplay);
    }

    private void renderSegmentMasterDetail(
            RoadUiContext ctx,
            RoadNetwork working,
            Road road,
            RoadEdge primaryEdge,
            ChainageDisplayContext chainageDisplay) {
        float totalWidth = ImGui.getContentRegionAvailX();
        float bodyHeight = Math.max(120f, ImGui.getContentRegionAvailY());
        float spacing = ImGui.getStyle().getItemSpacingX();
        float railWidth = Math.max(120f, (totalWidth - spacing) * SEGMENT_RAIL_WIDTH_FRACTION);
        float detailWidth = Math.max(160f, totalWidth - spacing - railWidth);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        ImGui.setCursorPos(startX, startY);
        ImGui.beginChild(
            "##centerline_segment_rail",
            railWidth,
            bodyHeight,
            true,
            ImGuiWindowFlags.None);
        try {
            segmentEditor.renderWorkingSegmentList(
                ctx,
                working,
                road,
                selectedWorkingEdgeId,
                edgeId -> selectedWorkingEdgeId = edgeId);
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX + railWidth + spacing, startY);
        // 外层不滚动：预览钉顶；下方参数单独滚动，便于对照示意调参。
        ImGui.beginChild(
            "##centerline_segment_detail",
            detailWidth,
            bodyHeight,
            true,
            ImGuiWindowFlags.NoScrollbar);
        try {
            RoadEdge resolved = working.getEdge(selectedWorkingEdgeId);
            final RoadEdge selected = resolved != null ? resolved : primaryEdge;
            centerlineEditPanel.renderPinnedPreview(ctx, working, road, selected);
            // 桩号/起终点一行说明，紧贴预览下方即可，不必再套分区卡片。
            ImGui.spacing();
            segmentEditor.renderSegmentSummary(ctx, working, road, selected, chainageDisplay);
            ImGui.spacing();
            float scrollH = Math.max(80f, ImGui.getContentRegionAvailY());
            ImGui.beginChild(
                "##centerline_detail_scroll",
                0f,
                scrollH,
                false,
                0);
            try {
                centerlineEditPanel.renderWorkingNetwork(
                    ctx, working, road, selected, session);
            } finally {
                ImGui.endChild();
            }
        } finally {
            ImGui.endChild();
        }
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        var live = ctx.networkManager().getNetwork();
        CenterlineEditorApplier.applyToLive(live, session.workingNetwork(), roadId);
        com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossings(live);
        com.plot.plugin.road.crossing.RoadStackCrossingReconciler.reconcileStackCrossings(live);
        ctx.networkManager().invalidateEditedProfilesIfGeometryChanged();
        // 拓扑可能已变：保持单选当前路，再落到草稿中选中的 live Edge。
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
