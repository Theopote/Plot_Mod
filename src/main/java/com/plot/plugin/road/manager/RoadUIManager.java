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
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

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
    private RoadUiTab activeTab = RoadUiTab.OVERVIEW;

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
        if (pendingTab != null) {
            activeTab = pendingTab;
            ctx.clearPendingTab();
        }

        renderTabSelector();
        ImGui.separator();
        renderActiveTabBody();
    }

    /**
     * 用按钮切换 Tab，避免嵌套 Child 内 ImGui TabBar + 每帧 SetSelected 导致原生层卡死。
     */
    private void renderTabSelector() {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = (ImGui.getContentRegionAvailX() - spacing * 3f) / 4f;
        if (tabButton(RoadUiTab.OVERVIEW, "plugin.road.tab.overview", buttonWidth)) {
            activeTab = RoadUiTab.OVERVIEW;
        }
        ImGui.sameLine(0, spacing);
        if (tabButton(RoadUiTab.ADOPT, "plugin.road.tab.adopt", buttonWidth)) {
            activeTab = RoadUiTab.ADOPT;
        }
        ImGui.sameLine(0, spacing);
        if (tabButton(RoadUiTab.EDIT, "plugin.road.tab.edit", buttonWidth)) {
            activeTab = RoadUiTab.EDIT;
        }
        ImGui.sameLine(0, spacing);
        if (tabButton(RoadUiTab.GENERATE, "plugin.road.tab.build", buttonWidth)) {
            activeTab = RoadUiTab.GENERATE;
        }
    }

    private boolean tabButton(RoadUiTab tab, String labelKey, float width) {
        boolean selected = activeTab == tab;
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.INFO_BLUE);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.ACCENT_BLUE);
        }
        boolean clicked = ImGui.button(PlotI18n.tr(labelKey) + "##road_tab_" + tab.name(), width, 0);
        if (selected) {
            ImGui.popStyleColor(3);
        }
        return clicked;
    }

    private void renderActiveTabBody() {
        switch (activeTab) {
            case OVERVIEW -> overviewPanel.render();
            case ADOPT -> adoptPanel.render();
            case EDIT -> editPanel.render();
            case GENERATE -> {
                String profileEdgeId = ctx.consumePendingProfileEdgeId();
                if (profileEdgeId != null && !profileEdgeId.isBlank()) {
                    generatePanel.openProfileForEdge(profileEdgeId);
                }
                generatePanel.render();
            }
        }
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
