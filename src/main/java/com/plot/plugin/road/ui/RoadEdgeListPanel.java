package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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
    private static final int MAX_VISIBLE_ROWS = 14;

    private final RoadUiContext ctx;
    /** 按道路分组时「显示分段」树的展开状态，用于估算列表高度 */
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
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();
        List<RoadEdge> edges = ctx.networkManager().filteredEdges(
            ctx.edgeSearchBuffer().get(),
            ctx.edgeSortMode(),
            ctx.currentCoordFilter());

        float lineHeight = ImGui.getTextLineHeightWithSpacing();
        float padding = ImGui.getStyle().getWindowPaddingY() * 2.0f;
        float estimatedHeight = computeContentHeight(network, edges, lineHeight, padding);
        float maxHeight = lineHeight * MAX_VISIBLE_ROWS + padding;
        float childHeight = Math.min(estimatedHeight, maxHeight);
        int windowFlags = estimatedHeight > maxHeight ? ImGuiWindowFlags.None : ImGuiWindowFlags.NoScrollbar;

        ImGui.beginChild(childId, 0, childHeight, true, windowFlags);
        if (edges.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_empty"));
        }
        if (ctx.edgeSortMode() == RoadEdgeListHelper.SortMode.ROAD_GROUP) {
            renderGroupedList(network, edges, showDelete);
        } else {
            renderFlatList(network, edges, showDelete);
        }
        ImGui.endChild();
    }

    private float computeContentHeight(
            RoadNetwork network,
            List<RoadEdge> edges,
            float lineHeight,
            float padding) {
        if (edges.isEmpty()) {
            return lineHeight * 2 + padding;
        }
        if (ctx.edgeSortMode() != RoadEdgeListHelper.SortMode.ROAD_GROUP) {
            return lineHeight * edges.size() + padding;
        }

        float total = padding;
        for (RoadEdgeListHelper.RoadGroup group : RoadEdgeListHelper.groupByRoad(network, edges)) {
            if (group.edges().size() == 1) {
                total += lineHeight * 2;
                continue;
            }
            total += lineHeight;
            total += lineHeight;
            if (expandedSegmentGroups.contains(group.roadId())) {
                total += lineHeight * group.edges().size();
            }
        }
        return total;
    }

    private void renderFlatList(RoadNetwork network, List<RoadEdge> edges, boolean showDelete) {
        for (RoadEdge edge : edges) {
            String roadId = edge.getRoadId();
            int segmentIndex = -1;
            if (roadId != null && !roadId.isBlank()) {
                Road road = network.getRoad(roadId);
                if (road != null) {
                    segmentIndex = RoadEdgeListHelper.orderedSegmentIds(road).indexOf(edge.getId());
                }
            }
            renderEdgeRow(network, edge, showDelete, null, segmentIndex, roadId);
        }
    }

    private void renderGroupedList(RoadNetwork network, List<RoadEdge> edges, boolean showDelete) {
        for (RoadEdgeListHelper.RoadGroup group : RoadEdgeListHelper.groupByRoad(network, edges)) {
            boolean hasRoadId = group.roadId() != null && !group.roadId().isBlank();
            if (group.edges().size() == 1) {
                renderSingleSegmentRoadRow(network, group, group.edges().getFirst(), showDelete, hasRoadId);
                continue;
            }

            if (showDelete && hasRoadId) {
                renderDeleteEntireRoadButton(group.roadId());
                ImGui.sameLine();
            }

            boolean roadSelected = group.edges().stream()
                .allMatch(edge -> ctx.networkManager().getSelectedEdgeIds().contains(edge.getId()));
            String header = group.label() + " (" + PlotI18n.tr(
                "plugin.road.segment_count", group.edges().size()) + ")";
            if (ImGui.selectable(header + "##road_group_" + group.roadId(), roadSelected)) {
                ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
            }
            if (ImGui.treeNode(PlotI18n.tr("plugin.road.show_segments") + "##segments_" + group.roadId())) {
                expandedSegmentGroups.add(group.roadId());
                Road road = hasRoadId ? network.getRoad(group.roadId()) : null;
                List<String> orderedIds = road != null
                    ? RoadEdgeListHelper.orderedSegmentIds(road)
                    : group.edges().stream().map(RoadEdge::getId).toList();
                for (String edgeId : orderedIds) {
                    RoadEdge edge = network.getEdge(edgeId);
                    if (edge == null || group.edges().stream().noneMatch(item -> item.getId().equals(edgeId))) {
                        continue;
                    }
                    int segmentIndex = orderedIds.indexOf(edgeId);
                    renderEdgeRow(network, edge, showDelete, "  ", segmentIndex, group.roadId());
                }
                ImGui.treePop();
            } else {
                expandedSegmentGroups.remove(group.roadId());
            }
        }
    }

    private void renderSingleSegmentRoadRow(
            RoadNetwork network,
            RoadEdgeListHelper.RoadGroup group,
            RoadEdge edge,
            boolean showDelete,
            boolean hasRoadId) {
        if (showDelete && hasRoadId) {
            renderDeleteEntireRoadButton(group.roadId());
            ImGui.sameLine();
        }

        boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(edge.getId());
        if (ImGui.selectable(group.label() + "##road_single_" + group.roadId(), selected)) {
            if (hasRoadId) {
                ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
            } else {
                ctx.networkManager().handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
            }
        }

        if (showDelete) {
            ImGui.indent();
            renderDeleteSegmentButton(edge.getId());
            ImGui.unindent();
        }
    }

    private void renderEdgeRow(
            RoadNetwork network,
            RoadEdge edge,
            boolean showDelete,
            String prefix,
            int segmentIndex,
            String roadId) {
        ImGui.pushID(edge.getId());
        String label = (prefix != null ? prefix : "") + RoadEdgeListHelper.formatEdgeLabel(network, edge);
        boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(edge.getId());

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
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.split_road_confirm_hint"));
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
