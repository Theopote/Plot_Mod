package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.ui.EarthworkAdoptPanel;
import com.plot.plugin.earthwork.ui.EarthworkEditPanel;
import com.plot.plugin.earthwork.ui.EarthworkGeneratePanel;
import com.plot.plugin.earthwork.ui.EarthworkOverviewPanel;
import com.plot.plugin.earthwork.ui.EarthworkToolbarPanel;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.utils.PlotI18n;
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

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new EarthworkToolbarPanel(ctx);
        this.overviewPanel = new EarthworkOverviewPanel(ctx);
        this.adoptPanel = new EarthworkAdoptPanel(ctx);
        this.editPanel = new EarthworkEditPanel(ctx);
        this.generatePanel = new EarthworkGeneratePanel(ctx);
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

        if (ImGui.beginTabBar("##earthwork_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.overview"))) {
                overviewPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.adopt"))) {
                adoptPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.edit"))) {
                editPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.generate"))) {
                generatePanel.render();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        overviewPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
