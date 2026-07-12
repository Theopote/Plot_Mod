package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.List;

/**
 * 道路边列表工具栏与列表（概览/编辑 Tab 共用）。
 */
public final class RoadEdgeListPanel {
    private final RoadUiContext ctx;

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
        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.selection_count",
                ctx.networkManager().getSelectedEdgeIds().size(),
                ctx.networkManager().filteredEdges(
                    ctx.edgeSearchBuffer().get(),
                    ctx.edgeSortMode(),
                    ctx.currentCoordFilter()).size()));
    }

    public void renderList(float height, boolean showDelete, String childId) {
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

        ImGui.beginChild(childId, 0, height, true);
        if (edges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.edge_list_empty"));
        }
        if (ctx.edgeSortMode() == RoadEdgeListHelper.SortMode.ROAD_GROUP) {
            renderGroupedList(network, edges, showDelete, deleteButtonWidth);
        } else {
            renderFlatList(network, edges, showDelete, deleteButtonWidth, deleteLabel);
        }
        ImGui.endChild();
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
            if (ImGui.collapsingHeader(group.label() + " (" + group.edges().size() + ")##" + group.roadId())) {
                for (RoadEdge edge : group.edges()) {
                    renderEdgeRow(network, edge, showDelete, deleteButtonWidth, deleteLabel, "  ");
                }
            }
        }
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
            ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
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
            ImGui.text(PlotI18n.tr("plugin.road.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.road.delete"), 100, 0)) {
                if (!ctx.pendingDeleteEdgeId().isEmpty()) {
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
