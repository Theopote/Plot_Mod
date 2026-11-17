package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.RoadEdgeListHelper;
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
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.LinkedHashSet;
import java.util.Set;

/** 中心线 / 平面线形浮动编辑器（多选时可切换当前编辑道路）。 */
public final class CenterlineDesignerWindow extends AbstractRoadFloatingEditor {

    /** 下半区左轨（分段列表）宽度占比，对齐设施/变截面主从。 */
    private static final float SEGMENT_RAIL_WIDTH_FRACTION = 0.28f;

    private final HorizontalAlignmentSummaryEditor horizontalAlignmentEditor =
        new HorizontalAlignmentSummaryEditor();
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;
    /** 窗口内草稿分段选择；确认成功前不写入全局选中。 */
    private String selectedWorkingEdgeId = "";
    /** 打开时的多选范围（保序）；用于作用域守卫与目标路切换。 */
    private java.util.List<String> siblingRoadIds = java.util.List.of();

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
        LinkedHashSet<String> selected = new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());
        if (selected.isEmpty() && roadId != null && !roadId.isBlank()) {
            selected.add(roadId);
        }
        siblingRoadIds = java.util.List.copyOf(selected);
        var live = ctx.networkManager().getNetwork();
        var openSnapshot = live.snapshot();
        selectedWorkingEdgeId = resolveInitialWorkingEdgeId(ctx, live.getRoad(roadId));
        horizontalAlignmentEditor.clearMessages();
        centerlineEditPanel.clearMessages();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));
    }

    @Override
    public void discard(RoadUiContext ctx) {
        selectedWorkingEdgeId = "";
        siblingRoadIds = java.util.List.of();
        horizontalAlignmentEditor.clearMessages();
        centerlineEditPanel.clearMessages();
        super.discard(ctx);
    }

    @Override
    public boolean matchesScope(Set<String> selectedRoadIds) {
        return new LinkedHashSet<>(siblingRoadIds).equals(selectedRoadIds);
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
        renderMultiRoadSwitcher(ctx, working);
        selectedWorkingEdgeId = resolveWorkingEdgeId(working, road, selectedWorkingEdgeId);
        RoadEdge primaryEdge = working.getEdge(selectedWorkingEdgeId);

        ChainageDisplayContext chainageDisplay = null;
        if (RoadStationing.isStationable(working, road)) {
            chainageDisplay = new ChainageDisplayContext(
                RoadStationing.canonicalLength(working, road),
                chainageDisplayMode,
                RoadStationFormat.DISTANCE_METERS);
        }

        // 上路级平面线形：仅在已定义 HA 时展示；空状态（折线中心线）对拾取建路是常态，不占版面。
        if (road.getHorizontalAlignment() != null && !road.getHorizontalAlignment().isEmpty()) {
            RoadUiSections.section("plugin.road.floating_editor.centerline.tab.alignment");
            horizontalAlignmentEditor.render(ctx, working, road, chainageDisplay);
            ImGui.spacing();
        }

        RoadUiSections.section("plugin.road.floating_editor.centerline.tab.segments");
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.floating_editor.centerline.segments_hint"));
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
            RoadEdge selected = working.getEdge(selectedWorkingEdgeId);
            if (selected == null) {
                selected = primaryEdge;
            }
            centerlineEditPanel.renderPinnedPreview(ctx, selected);
            ImGui.spacing();
            float scrollH = Math.max(80f, ImGui.getContentRegionAvailY());
            ImGui.beginChild(
                "##centerline_detail_scroll",
                0f,
                scrollH,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar);
            try {
                segmentEditor.renderSegmentSummary(ctx, working, road, selected, chainageDisplay);
                ImGui.separator();
                ImGui.spacing();
                centerlineEditPanel.renderWorkingNetwork(ctx, working, road, selected);
                ImGui.spacing();
                if (isDirty()) {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.elevation_hint_after_confirm"));
                } else {
                    segmentEditor.renderElevationHint(ctx, selected);
                }
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

    private void renderMultiRoadSwitcher(RoadUiContext ctx, RoadNetwork working) {
        if (siblingRoadIds.size() <= 1) {
            return;
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.floating_editor.centerline.batch_scope",
                siblingRoadIds.size()));
        String[] labels = new String[siblingRoadIds.size()];
        int current = 0;
        for (int i = 0; i < siblingRoadIds.size(); i++) {
            String id = siblingRoadIds.get(i);
            Road candidate = working.getRoad(id);
            labels[i] = candidate != null
                ? RoadEdgeListHelper.formatRoadLabel(working, candidate)
                : id;
            if (id.equals(roadId)) {
                current = i;
            }
        }
        ImInt index = new ImInt(current);
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.floating_editor.centerline.switch_road")
                    + "##centerline_switch_road",
                index,
                labels)
            && index.get() != current) {
            open(ctx, siblingRoadIds.get(index.get()));
        }
        ImGui.spacing();
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
