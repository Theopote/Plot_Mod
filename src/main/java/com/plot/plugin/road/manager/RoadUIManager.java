package com.plot.plugin.road.manager;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.ui.RoadAdoptPanel;
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEdgeListPanel;
import com.plot.plugin.road.ui.RoadEditPanel;
import com.plot.plugin.road.ui.RoadGeneratePanel;
import com.plot.plugin.road.ui.RoadJunctionPanel;
import com.plot.plugin.road.ui.RoadNodePropertyPanel;
import com.plot.plugin.road.ui.RoadOverviewPanel;
import com.plot.plugin.road.ui.RoadToolbarPanel;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiTab;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;

/**
 * 道路系统 ImGui 界面编排。
 */
public final class RoadUIManager implements RoadJunctionPropertyProvider {
    private final RoadUiContext ctx;
    private final RoadToolbarPanel toolbarPanel;
    private final RoadOverviewPanel overviewPanel;
    private final RoadAdoptPanel adoptPanel;
    private final RoadEditPanel editPanel;
    private final RoadGeneratePanel generatePanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    public RoadUIManager(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status,
            com.plot.core.context.PluginContext host) {
        this.ctx = new RoadUiContext(
            networkManager, previewManager, persistenceManager, toolManager, status, host);

        this.edgeListPanel = new RoadEdgeListPanel(ctx);
        this.junctionPanel = new RoadJunctionPanel(ctx);
        this.nodePropertyPanel = new RoadNodePropertyPanel(ctx);
        this.toolbarPanel = new RoadToolbarPanel(ctx);
        this.overviewPanel = new RoadOverviewPanel(ctx);
        this.adoptPanel = new RoadAdoptPanel(ctx, new RoadDefaultParamsPanel(ctx));
        this.editPanel = new RoadEditPanel(ctx, edgeListPanel, junctionPanel, nodePropertyPanel);
        this.generatePanel = new RoadGeneratePanel(ctx);
    }

    public void render() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        if (ctx.toolManager().getPathPickSession().isActive()) {
            ctx.toolManager().tick();
        }

        toolbarPanel.render();

        RoadUiTab pendingTab = ctx.pendingTab();

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            renderTab(RoadUiTab.OVERVIEW, "plugin.road.tab.overview", pendingTab, overviewPanel::render);
            renderTab(RoadUiTab.ADOPT, "plugin.road.tab.adopt", pendingTab, adoptPanel::render);
            renderTab(RoadUiTab.EDIT, "plugin.road.tab.edit", pendingTab, editPanel::render);
            renderTab(RoadUiTab.GENERATE, "plugin.road.tab.generate", pendingTab, this::renderGenerateTab);
            ImGui.endTabBar();
        }

        if (pendingTab != null) {
            ctx.clearPendingTab();
        }
    }

    private void renderTab(RoadUiTab tab, String labelKey, RoadUiTab pendingTab, Runnable body) {
        int flags = pendingTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey), flags)) {
            body.run();
            ImGui.endTabItem();
        }
    }

    private void renderGenerateTab() {
        String profileEdgeId = ctx.consumePendingProfileEdgeId();
        if (profileEdgeId != null && !profileEdgeId.isBlank()) {
            generatePanel.openProfileForEdge(profileEdgeId);
        }
        generatePanel.render();
    }

    public void renderDeferredModals() {
        edgeListPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
        editPanel.renderUniformElevationConfirmPopup();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return ctx.networkManager().getSelectedNode() != null;
    }

    @Override
    public void renderJunctionPropertySection() {
        nodePropertyPanel.renderPropertySection(junctionPanel);
    }

    @Override
    public String getPropertySectionTitleKey() {
        RoadNode node = ctx.networkManager().getSelectedNode();
        if (node != null && node.isJunction()) {
            return "panel.plot.road_junction";
        }
        return "panel.plot.road_node";
    }
}
