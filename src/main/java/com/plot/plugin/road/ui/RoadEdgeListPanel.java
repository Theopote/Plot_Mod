package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.selection_count",
                ctx.networkManager().getSelectedEdgeIds().size(),
                ctx.networkManager().filteredEdges(
                    ctx.edgeSearchBuffer().get(),
                    ctx.edgeSortMode(),
                    ctx.currentCoordFilter()).size()));
    }

    public void renderList(boolean showDelete, String childId) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();
        List<RoadEdge> edges = ctx.networkManager().filteredEdges(
            ctx.edgeSearchBuffer().get(),
            ctx.edgeSortMode(),
            ctx.currentCoordFilter());
        String deleteLabel = PlotI18n.tr("plugin.road.delete");
        float deleteButtonWidth = showDelete
            ? ImGui.calcTextSize(deleteLabel).x + ImGui.getStyle().getFramePaddingX() * 2.0f + 8.0f
            : 0.0f;

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
            renderGroupedList(network, edges, showDelete, deleteButtonWidth);
        } else {
            renderFlatList(network, edges, showDelete, deleteButtonWidth, deleteLabel);
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
                total += lineHeight;
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

    private void renderFlatList(
            RoadNetwork network,
            List<RoadEdge> edges,
            boolean showDelete,
            float deleteButtonWidth,
            String deleteLabel) {
        for (RoadEdge edge : edges) {
            renderEdgeRow(network, edge, showDelete, deleteButtonWidth, deleteLabel, null);
        }
    }

    private void renderGroupedList(
            RoadNetwork network,
            List<RoadEdge> edges,
            boolean showDelete,
            float deleteButtonWidth) {
        String deleteLabel = PlotI18n.tr("plugin.road.delete");
        for (RoadEdgeListHelper.RoadGroup group : RoadEdgeListHelper.groupByRoad(network, edges)) {
            boolean hasRoadId = group.roadId() != null && !group.roadId().isBlank();
            if (group.edges().size() == 1) {
                RoadEdge edge = group.edges().getFirst();
                renderRoadGroupRow(network, group, List.of(edge), showDelete, deleteButtonWidth, deleteLabel, true);
                continue;
            }

            if (showDelete && hasRoadId) {
                if (ImGui.smallButton(
                        PlotI18n.tr("plugin.road.delete_road") + "##delete_road_" + group.roadId())) {
                    ctx.requestDeleteRoad(group.roadId());
                }
                ImGui.sameLine();
            }

            boolean roadSelected = group.edges().stream()
                .allMatch(edge -> ctx.networkManager().getSelectedEdgeIds().contains(edge.getId()));
            String header = group.label() + " (" + PlotI18n.tr(
                "plugin.road.segment_count", group.edges().size()) + ")";
            if (ImGui.selectable(header + "##road_group_" + group.roadId(), roadSelected)) {
                if (hasRoadId) {
                    ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
                } else if (!group.edges().isEmpty()) {
                    ctx.networkManager().handleEdgeSelect(
                        group.edges().getFirst().getId(), ImGui.getIO().getKeyCtrl());
                }
            }
            if (ImGui.treeNode(PlotI18n.tr("plugin.road.show_segments") + "##segments_" + group.roadId())) {
                expandedSegmentGroups.add(group.roadId());
                for (RoadEdge edge : group.edges()) {
                    renderEdgeRow(network, edge, showDelete, deleteButtonWidth, deleteLabel, "  ");
                }
                ImGui.treePop();
            } else {
                expandedSegmentGroups.remove(group.roadId());
            }
        }
    }

    private void renderRoadGroupRow(
            RoadNetwork network,
            RoadEdgeListHelper.RoadGroup group,
            List<RoadEdge> edges,
            boolean showDelete,
            float deleteButtonWidth,
            String deleteLabel,
            boolean useRoadLabel) {
        boolean hasRoadId = group.roadId() != null && !group.roadId().isBlank();
        if (showDelete && hasRoadId) {
            if (ImGui.smallButton(
                    PlotI18n.tr("plugin.road.delete_road") + "##delete_road_" + group.roadId())) {
                ctx.requestDeleteRoad(group.roadId());
            }
            ImGui.sameLine();
        }

        RoadEdge edge = edges.getFirst();
        ImGui.pushID(edge.getId());
        String label = useRoadLabel
            ? group.label()
            : RoadEdgeListHelper.formatEdgeLabel(network, edge);
        boolean selected = edges.stream()
            .allMatch(item -> ctx.networkManager().getSelectedEdgeIds().contains(item.getId()));

        float rowWidth = ImGui.getContentRegionAvail().x;
        float selectableWidth = showDelete
            ? Math.max(0.0f, rowWidth - deleteButtonWidth - ImGui.getStyle().getItemSpacingX())
            : rowWidth;
        if (ImGui.selectable(label + "##road_sel", selected, 0, selectableWidth, 0.0f)) {
            if (hasRoadId) {
                ctx.networkManager().selectRoad(group.roadId(), ImGui.getIO().getKeyCtrl());
            } else {
                ctx.networkManager().handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
            }
        }
        if (showDelete && !hasRoadId) {
            ImGui.sameLine(0.0f, ImGui.getStyle().getItemSpacingX());
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
            if (ImGui.smallButton(deleteLabel + "##del")) {
                ctx.requestDeleteEdge(edge.getId());
            }
            ImGui.popStyleColor(3);
        }
        ImGui.popID();
    }

    private void renderEdgeRow(
            RoadNetwork network,
            RoadEdge edge,
            boolean showDelete,
            float deleteButtonWidth,
            String deleteLabel,
            String prefix) {
        ImGui.pushID(edge.getId());
        String label = (prefix != null ? prefix : "") + RoadEdgeListHelper.formatEdgeLabel(network, edge);
        boolean selected = ctx.networkManager().getSelectedEdgeIds().contains(edge.getId());

        float rowWidth = ImGui.getContentRegionAvail().x;
        float selectableWidth = showDelete
            ? Math.max(0.0f, rowWidth - deleteButtonWidth - ImGui.getStyle().getItemSpacingX())
            : rowWidth;
        if (ImGui.selectable(label + "##sel", selected, 0, selectableWidth, 0.0f)) {
            ctx.networkManager().handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
        }
        if (showDelete) {
            ImGui.sameLine(0.0f, ImGui.getStyle().getItemSpacingX());
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
            if (ImGui.smallButton(deleteLabel + "##del")) {
                ctx.requestDeleteEdge(edge.getId());
            }
            ImGui.popStyleColor(3);
        }
        ImGui.popID();
    }

    public void renderDeleteConfirmPopup() {
        if (ctx.deleteConfirmPending()) {
            ImGui.openPopup("##road_delete_confirm");
            ctx.clearDeleteConfirmPending();
        }

        if (ImGui.beginPopupModal("##road_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            boolean deleteRoad = !ctx.pendingDeleteRoadId().isEmpty();
            ImGui.text(PlotI18n.tr(
                deleteRoad ? "plugin.road.delete_road_confirm" : "plugin.road.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.road.delete"), 100, 0)) {
                if (!ctx.pendingDeleteRoadId().isEmpty()) {
                    ctx.networkManager().deleteRoad(ctx.pendingDeleteRoadId());
                } else if (!ctx.pendingDeleteEdgeId().isEmpty()) {
                    ctx.networkManager().deleteEdge(ctx.pendingDeleteEdgeId());
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
