package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.ui.EarthworkAdoptPanel;
import com.plot.plugin.earthwork.ui.EarthworkEditPanel;
import com.plot.plugin.earthwork.ui.EarthworkGeneratePanel;
import com.plot.plugin.earthwork.ui.EarthworkOverviewPanel;
import com.plot.plugin.earthwork.ui.EarthworkQuickPanel;
import com.plot.plugin.earthwork.ui.EarthworkToolbarPanel;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.ui.PluginTabScrollUi;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private final EarthworkUiContext ctx;
    private final EarthworkToolbarPanel toolbarPanel;
    private final EarthworkOverviewPanel overviewPanel;
    private final EarthworkAdoptPanel adoptPanel;
    private final EarthworkEditPanel editPanel;
    private final EarthworkGeneratePanel generatePanel;
    private final EarthworkQuickPanel quickPanel;

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new EarthworkToolbarPanel(ctx);
        this.overviewPanel = new EarthworkOverviewPanel(ctx);
        this.adoptPanel = new EarthworkAdoptPanel(ctx);
        this.editPanel = new EarthworkEditPanel(ctx);
        this.generatePanel = new EarthworkGeneratePanel(ctx);
        this.quickPanel = new EarthworkQuickPanel(ctx, adoptPanel, generatePanel);
    }

    public void render() {
        if (ctx.config() == null) {
            return;
        }

        if (ctx.pickSession().isActive()) {
            adoptPanel.tickPickSession();
        }
        if (ctx.threePointPickSession().isActive()) {
            editPanel.tickThreePointPickSession();
        }

        toolbarPanel.render();

        float availX = ImGui.getContentRegionAvailX();
        float availY = ImGui.getContentRegionAvailY();
        if (availX < 24f || availY < 24f) {
            return;
        }

        if (ctx.config().getWorkMode() == EarthworkWorkMode.QUICK) {
            PluginTabScrollUi.renderScrollBody("##earthwork_quick", quickPanel::render);
            return;
        }

        renderBuilderTabs();
    }

    private void renderBuilderTabs() {
        if (!ImGui.beginTabBar("##earthwork_tabs", ImGuiTabBarFlags.None)) {
            return;
        }
        PluginTabScrollUi.renderTab(
            "plugin.earthwork.tab.overview",
            "##earthwork_tab_overview",
            overviewPanel::render);
        PluginTabScrollUi.renderTab(
            "plugin.earthwork.tab.adopt",
            "##earthwork_tab_adopt",
            adoptPanel::render);
        PluginTabScrollUi.renderTab(
            "plugin.earthwork.tab.edit",
            "##earthwork_tab_edit",
            editPanel::render);
        PluginTabScrollUi.renderTab(
            "plugin.earthwork.tab.generate",
            "##earthwork_tab_generate",
            generatePanel::render);
        ImGui.endTabBar();
    }

    public void renderDeferredModals() {
        overviewPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
