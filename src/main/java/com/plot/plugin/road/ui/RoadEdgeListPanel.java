package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 道路边列表工具栏与列表（编辑 Tab 专用）。
 */
public final class RoadEdgeListPanel {
    private static final float EDGE_LIST_HEIGHT = 220f;
    private static final float PATH_LIST_HEIGHT = 360f;
    private static final float EDGE_ROW_HEIGHT_LINES = 1.0f;

    private final RoadUiContext ctx;
    /** 按道路分组时展开分段列表的道路 id */
    private final Set<String> expandedSegmentGroups = new HashSet<>();

    public RoadEdgeListPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderToolbar(String idPrefix) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.62f);
        ImGui.inputTextWithHint(
            idPrefix + "_edge_search",
            PlotI18n.tr("plugin.road.edge_search_hint"),
            ctx.edgeSearchBuffer());
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(idPrefix + "_edge_sort", ctx.edgeSortMode().label())) {
            for (RoadEdgeListHelper.SortMode mode : RoadEdgeListHelper.SortMode.values()) {
                boolean selected = mode == ctx.edgeSortMode();
                if (ImGui.selectable(mode.label(), selected)) {
                    ctx.setEdgeSortMode(mode);
                }
            }
            ImGui.endCombo();
        }

        ImBoolean coordFilterRef = new ImBoolean(ctx.coordFilterEnabled());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.coord_filter"), coordFilterRef)) {
            ctx.setCoordFilterEnabled(coordFilterRef.get());
        }
        if (ctx.coordFilterEnabled()) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_min_x", ctx.coordMinX(), 1f, -100000f, 100000f, "X>=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_max_x", ctx.coordMaxX(), 1f, -100000f, 100000f, "X<=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_min_y", ctx.coordMinY(), 1f, -100000f, 100000f, "Y>=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.dragFloat(idPrefix + "_max_y", ctx.coordMaxY(), 1f, -100000f, 100000f, "Y<=%.0f");
        }

        if (ImGui.smallButton(PlotI18n.tr("plugin.road.select_all_edges") + idPrefix)) {
            ctx.networkManager().selectAllEdges();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.clear_selection") + idPrefix)) {
            ctx.networkManager().clearEdgeSelection();
        }
        ImGui.sameLine();
        int selectedRoads = ctx.networkManager().getSelectedRoadIds().size();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.selection_count",
                selectedRoads,
                RoadEdgeListHelper.groupByRoad(
                    ctx.networkManager().getNetwork(),
                    ctx.networkManager().filteredEdges(
                        ctx.edgeSearchBuffer().get(),
                        ctx.edgeSortMode(),
                        ctx.currentCoordFilter())).size()));
    }

    public void renderList(boolean showDelete, String childId) {
        renderList(showDelete, childId, false);
    }

    public void renderList(boolean showDelete, String childId, boolean showThumbnails) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();
        List<RoadEdge> edges = ctx.networkManager().filteredEdges(
            ctx.edgeSearchBuffer().get(),
            ctx.edgeSortMode(),
            ctx.currentCoordFilter());

        float listHeight = showThumbnails ? PATH_LIST_HEIGHT : EDGE_LIST_HEIGHT;
        ImGui.beginChild(childId, 0, listHeight, true);
        if (edges.isEmpty()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_empty"));
        } else {
            List<RoadEdgeListHelper.DisplayRow> rows = RoadEdgeListHelper.buildDisplayRows(
                network,
                edges,
                ctx.edgeSortMode(),
                expandedSegmentGroups,
                showDelete);
            renderVirtualEdgeList(network, rows, showDelete, showThumbnails);
        }
        ImGui.endChild();
    }

    private void renderVirtualEdgeList(
            RoadNetwork network,
            List<RoadEdgeListHelper.DisplayRow> rows,
            boolean showDelete,
            boolean showThumbnails) {
        int rowHeight = showThumbnails
            ? Math.round(RoadNetworkOverviewRenderer.thumbnailHeight() + ImGui.getStyle().getItemSpacingY())
            : Math.round(ImGui.getTextLineHeightWithSpacing() * EDGE_ROW_HEIGHT_LINES);
        ImGuiListClipper.forEach(rows.size(), rowHeight, new ImListClipperCallback() {
            @Override
            public void accept(int index) {
                RoadEdgeListHelper.DisplayRow row = rows.get(index);
                ImGui.pushID(index);
                renderDisplayRow(network, row, showDelete, showThumbnails);
                ImGui.popID();
            }
        });
    }

    private void renderDisplayRow(
            RoadNetwork network,
            RoadEdgeListHelper.DisplayRow row,
            boolean showDelete,
            boolean showThumbnails) {
        switch (row.kind()) {
            case FLAT -> {
                if (showThumbnails) {
                    Road road = row.roadId() != null && !row.roadId().isBlank()
                        ? network.getRoad(row.roadId()) : null;
                    boolean hasRoadId = road != null;
                    RoadEdgeListHelper.RoadGroup group = new RoadEdgeListHelper.RoadGroup(
                        hasRoadId ? row.roadId() : "",
                        RoadEdgeListHelper.formatRoadLabel(network, road),
                        List.of(row.edge()));
                    boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(row.edge().getId());
                    renderPathRoadRow(network, group, selected, hasRoadId);
                } else {
                    renderEdgeRow(
                        network,
                        row.edge(),
                        showDelete,
                        null,
                        row.segmentIndex(),
                        row.roadId(),
                        false);
                }
            }
            case SINGLE_ROAD -> renderSingleSegmentRoadRow(
                network,
                row.group(),
                row.edge(),
                showDelete,
                row.hasRoadId(),
                showThumbnails);
            case SINGLE_ROAD_DELETE -> {
                ImGui.indent();
                renderDeleteSegmentButton(row.edge().getId());
                ImGui.unindent();
            }
            case GROUP_HEADER -> renderGroupHeaderRow(
                network, row.group(), row.hasRoadId(), showDelete, showThumbnails);
            case GROUP_SEGMENT -> {
                if (showThumbnails) {
                    renderPathSegmentRow(network, row.edge(), row.segmentIndex());
                } else {
                    renderEdgeRow(
                        network,
                        row.edge(),
                        showDelete,
                        "  ",
                        row.segmentIndex(),
                        row.group().roadId(),
                        false);
                }
            }
        }
    }

    private void renderPathSegmentRow(RoadNetwork network, RoadEdge edge, int segmentIndex) {
        ImGui.indent(RoadNetworkOverviewRenderer.thumbnailWidth() * 0.35f);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.path.segment_row",
                segmentIndex + 1,
                RoadEdgeListHelper.formatEdgeLabel(network, edge)));
        ImGui.unindent();
    }

    private void renderGroupHeaderRow(
            RoadNetwork network,
            RoadEdgeListHelper.RoadGroup group,
            boolean hasRoadId,
            boolean showDelete,
            boolean showThumbnails) {
        ImGui.pushID(group.roadId());
        if (showDelete && hasRoadId && !showThumbnails) {
            renderDeleteEntireRoadButton(group.roadId());
            ImGui.sameLine();
        }

        boolean expanded = expandedSegmentGroups.contains(group.roadId());
        if (ImGui.arrowButton("##expand", expanded ? ImGuiDir.Down : ImGuiDir.Right)) {
            if (expanded) {
                expandedSegmentGroups.remove(group.roadId());
            } else {
                expandedSegmentGroups.add(group.roadId());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.show_segments"));
        }
        ImGui.sameLine();

        boolean roadSelected = group.edges().stream()
            .allMatch(edge -> ctx.networkManager().getSelectedEdgeIds().contains(edge.getId()));
        if (showThumbnails) {
            renderPathRoadRow(network, group, roadSelected, hasRoadId);
        } else {
            String header = group.label() + " (" + PlotI18n.tr(
                "plugin.road.segment_count", group.edges().size()) + ")";
            if (ImGui.selectable(header + "##road_group_" + group.roadId(), roadSelected)) {
                ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
                ctx.requestOverlayRefresh();
            }
        }
        ImGui.popID();
    }

    private void renderSingleSegmentRoadRow(
            RoadNetwork network,
            RoadEdgeListHelper.RoadGroup group,
            RoadEdge edge,
            boolean showDelete,
            boolean hasRoadId,
            boolean showThumbnails) {
        if (showDelete && hasRoadId && !showThumbnails) {
            renderDeleteEntireRoadButton(group.roadId());
            ImGui.sameLine();
        }

        boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(edge.getId());
        if (showThumbnails) {
            renderPathRoadRow(network, group, selected, hasRoadId);
            return;
        }

        if (ImGui.selectable(group.label() + "##road_single_" + group.roadId(), selected)) {
            selectRoadOrEdge(hasRoadId, group.roadId(), edge.getId());
        }
    }

    private void renderPathRoadRow(
            RoadNetwork network,
            RoadEdgeListHelper.RoadGroup group,
            boolean selected,
            boolean hasRoadId) {
        if (renderRoadThumbnail(network, group.edges(), selected, group.roadId())) {
            if (hasRoadId) {
                ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
                ctx.requestOverlayRefresh();
            } else if (!group.edges().isEmpty()) {
                ctx.networkManager().handleEdgeSelect(
                    group.edges().getFirst().getId(), ImGui.getIO().getKeyCtrl());
            }
        }
        ImGui.sameLine();

        Road road = hasRoadId ? network.getRoad(group.roadId()) : null;
        RoadSystemConfig config = ctx.networkManager().getConfig();
        float columnWidth = Math.max(120f, ImGui.getContentRegionAvail().x);

        ImGui.beginGroup();
        String name = RoadEdgeListHelper.formatRoadDisplayName(network, road);
        if (ImGui.selectable(name + "##name_" + group.roadId(), selected, 0, columnWidth, 0.0f)) {
            selectRoadOrEdge(hasRoadId, group.roadId(),
                group.edges().isEmpty() ? "" : group.edges().getFirst().getId());
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            RoadEdgeListHelper.formatRoadPathSummary(network, road, config));
        if (hasRoadId) {
            renderPathDeleteRoadButton(group.roadId());
        } else if (!group.edges().isEmpty()) {
            renderDeleteSegmentButton(group.edges().getFirst().getId());
        }
        ImGui.endGroup();
    }

    private void renderPathDeleteRoadButton(String roadId) {
        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete") + "##path_delete_" + roadId)) {
            ctx.requestDeleteRoad(roadId);
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.delete_entire_road"));
        }
    }

    private void renderEdgeRow(
            RoadNetwork network,
            RoadEdge edge,
            boolean showDelete,
            String prefix,
            int segmentIndex,
            String roadId,
            boolean showThumbnails) {
        ImGui.pushID(edge.getId());
        String label = (prefix != null ? prefix : "") + RoadEdgeListHelper.formatEdgeLabel(network, edge);
        boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(edge.getId());

        if (showThumbnails) {
            if (renderRoadThumbnail(network, List.of(edge), selected, edge.getId())) {
                ctx.networkManager().handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
            }
            ImGui.sameLine();
        }

        float actionsWidth = showDelete ? estimateSegmentActionsWidth(segmentIndex, roadId) : 0f;
        float rowWidth = ImGui.getContentRegionAvail().x;
        float selectableWidth = showDelete
            ? Math.max(0.0f, rowWidth - actionsWidth - ImGui.getStyle().getItemSpacingX())
            : rowWidth;
        if (ImGui.selectable(label + "##sel", selected, 0, selectableWidth, 0.0f)) {
            ctx.networkManager().handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
        }
        if (showDelete) {
            ImGui.sameLine(0.0f, ImGui.getStyle().getItemSpacingX());
            renderSegmentActions(edge.getId(), roadId, segmentIndex);
        }
        ImGui.popID();
    }

    private boolean renderRoadThumbnail(
            RoadNetwork network,
            List<RoadEdge> edges,
            boolean selected,
            String colorKey) {
        int colorIndex = colorKey != null ? colorKey.hashCode() : 0;
        return RoadNetworkOverviewRenderer.renderRoadThumbnail(network, edges, selected, colorIndex);
    }

    private void selectRoadOrEdge(boolean hasRoadId, String roadId, String edgeId) {
        if (hasRoadId) {
            ctx.networkManager().selectRoad(roadId, ImGui.getIO().getKeyCtrl());
            ctx.requestOverlayRefresh();
        } else {
            ctx.networkManager().handleEdgeSelect(edgeId, ImGui.getIO().getKeyCtrl());
        }
    }

    private float estimateSegmentActionsWidth(int segmentIndex, String roadId) {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float width = ImGui.calcTextSize(PlotI18n.tr("plugin.road.delete_segment")).x
            + ImGui.getStyle().getFramePaddingX() * 2.0f + 8.0f;
        boolean canSplit = roadId != null && !roadId.isBlank() && segmentIndex > 0;
        if (canSplit) {
            width += spacing + ImGui.calcTextSize(PlotI18n.tr("plugin.road.split_road")).x
                + ImGui.getStyle().getFramePaddingX() * 2.0f + 8.0f;
        }
        return width;
    }

    private void renderSegmentActions(String edgeId, String roadId, int segmentIndex) {
        renderDeleteSegmentButton(edgeId);
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        ImGui.sameLine();
        if (segmentIndex > 0) {
            renderSplitRoadButton(roadId, edgeId);
        } else {
            ImGui.beginDisabled();
            ImGui.smallButton(PlotI18n.tr("plugin.road.split_road") + "##split_disabled_" + edgeId);
            ImGui.endDisabled();
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.split_road_first_segment"));
            }
        }
    }

    private void renderDeleteEntireRoadButton(String roadId) {
        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete_road") + "##delete_road_" + roadId)) {
            ctx.requestDeleteRoad(roadId);
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.delete_entire_road"));
        }
    }

    private void renderDeleteSegmentButton(String edgeId) {
        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete_segment") + "##delete_segment_" + edgeId)) {
            ctx.requestDeleteSegment(edgeId);
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.delete_segment"));
        }
    }

    private void renderSplitRoadButton(String roadId, String edgeId) {
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.split_road") + "##split_" + edgeId)) {
            ctx.requestSplitRoad(roadId, edgeId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.split_road"));
        }
    }

    public void renderDeleteConfirmPopup() {
        if (ctx.deleteConfirmPending()) {
            ImGui.openPopup("##road_delete_confirm");
            ctx.clearDeleteConfirmPending();
        }

        if (ImGui.beginPopupModal("##road_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            RoadUiContext.RoadListAction action = ctx.pendingRoadListAction();
            if (action == null) {
                action = !ctx.pendingDeleteRoadId().isEmpty()
                    ? RoadUiContext.RoadListAction.DELETE_ENTIRE_ROAD
                    : RoadUiContext.RoadListAction.DELETE_SEGMENT;
            }
            String messageKey = switch (action) {
                case DELETE_ENTIRE_ROAD -> "plugin.road.delete_road_confirm";
                case SPLIT_ROAD -> "plugin.road.split_road_confirm";
                default -> "plugin.road.delete_segment_confirm";
            };
            ImGui.textWrapped(PlotI18n.tr(messageKey));
            if (action == RoadUiContext.RoadListAction.SPLIT_ROAD) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.split_road_confirm_hint"));
            }
            ImGui.separator();
            String confirmLabel = action == RoadUiContext.RoadListAction.SPLIT_ROAD
                ? PlotI18n.tr("plugin.road.split_road")
                : PlotI18n.tr("plugin.road.delete");
            if (ImGui.button(confirmLabel, 100, 0)) {
                switch (action) {
                    case DELETE_ENTIRE_ROAD -> ctx.networkManager().deleteRoad(ctx.pendingDeleteRoadId());
                    case SPLIT_ROAD -> ctx.networkManager().splitRoadBeforeSegment(
                        ctx.pendingDeleteRoadId(),
                        ctx.pendingDeleteEdgeId());
                    default -> ctx.networkManager().deleteSegment(ctx.pendingDeleteEdgeId());
                }
                ctx.clearPendingDeleteEdgeId();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                ctx.clearPendingDeleteEdgeId();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
