package com.plot.plugin.road.manager;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.ui.RoadAdoptPanel;
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEdgeListPanel;
import com.plot.plugin.road.ui.RoadEditPanel;
import com.plot.plugin.road.ui.RoadGeneratePanel;
import com.plot.plugin.road.ui.RoadJunctionPanel;
import com.plot.plugin.road.ui.RoadOverviewPanel;
import com.plot.plugin.road.ui.RoadToolbarPanel;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

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

    public RoadUIManager(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status) {
        this.ctx = new RoadUiContext(
            networkManager, previewManager, persistenceManager, toolManager, status);

        this.edgeListPanel = new RoadEdgeListPanel(ctx);
        this.junctionPanel = new RoadJunctionPanel(ctx);
        this.toolbarPanel = new RoadToolbarPanel(ctx);
        this.overviewPanel = new RoadOverviewPanel(ctx, edgeListPanel, junctionPanel);
        this.adoptPanel = new RoadAdoptPanel(ctx, new RoadDefaultParamsPanel(ctx));
        this.editPanel = new RoadEditPanel(ctx, edgeListPanel, junctionPanel);
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

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.overview"))) {
                overviewPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.adopt"))) {
                adoptPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.edit"))) {
                editPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.generate"))) {
                generatePanel.render();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        edgeListPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return ctx.networkManager().getSelectedJunctionNode() != null;
    }

    @Override
    public void renderJunctionPropertySection() {
        junctionPanel.renderPropertySection();
    }
}
