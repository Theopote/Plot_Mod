package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路编辑 Tab：网络级批量操作、边列表、基于选中态的节点/边属性编辑。
 * <p>
 * 选中单条道路时采用 Road Design Stack 分组（Identity → Alignment → Typical Section →
 * Station Controls → Segments），不增加顶层 Tab。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;
    private final RoadNetworkToolsPanel networkToolsPanel;
    private final RoadDesignPanel designPanel;

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadEdgeListPanel edgeListPanel,
            RoadJunctionPanel junctionPanel,
            RoadNodePropertyPanel nodePropertyPanel) {
        this.ctx = ctx;
        this.edgeListPanel = edgeListPanel;
        this.junctionPanel = junctionPanel;
        this.nodePropertyPanel = nodePropertyPanel;
        this.networkToolsPanel = new RoadNetworkToolsPanel(ctx);
        this.designPanel = new RoadDesignPanel(ctx);
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();

        networkToolsPanel.render(network);
        ImGui.separator();

        RoadUiSections.section("plugin.road.section.road_list");
        List<com.plot.plugin.road.model.RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_edges"));
        } else {
            ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_hint"));
            edgeListPanel.renderToolbar("##edit");
            edgeListPanel.renderList(true, "edit_edge_list");
        }

        renderSelectionDispatch(network, allEdges.isEmpty());

        ImGui.separator();
        nodePropertyPanel.renderAllNodesCollapsibleList();
    }

    private void renderSelectionDispatch(RoadNetwork network, boolean edgesEmpty) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        int selectedEdgeCount = ctx.networkManager().getSelectedEdgeIds().size();
        int selectedRoadCount = ctx.networkManager().getSelectedRoadIds().size();
        int selectedLogicalCount = selectedRoadCount > 0 ? selectedRoadCount : selectedEdgeCount;

        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            RoadUiSections.group("plugin.road.section.node_junction");
            nodePropertyPanel.renderForSelectedNode(junctionPanel);
            return;
        }

        if (edgesEmpty) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
            return;
        }

        if (selectedLogicalCount > 1) {
            renderBatchEditPanel();
            return;
        }

        if (selectedEdgeCount >= 1) {
            designPanel.render(network);
            return;
        }

        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
    }

    /**
     * 统一标高二次确认（破坏性：全节点手动 Y + 各路 maxSlope=0）。
     */
    public void renderUniformElevationConfirmPopup() {
        networkToolsPanel.renderConfirmPopup();
    }

    private void renderBatchEditPanel() {
        int selectedEdgeCount = ctx.networkManager().getSelectedEdgeIds().size();
        if (selectedEdgeCount == 0) {
            return;
        }
        int selectedRoadCount = ctx.networkManager().getSelectedRoadIds().size();
        int displayCount = selectedRoadCount > 0 ? selectedRoadCount : selectedEdgeCount;
        int headerFlags = displayCount > 1 ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"), headerFlags)) {
            return;
        }

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", displayCount));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_cross_section_only"));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }
}
