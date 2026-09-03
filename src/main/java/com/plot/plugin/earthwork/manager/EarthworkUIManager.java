package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.ui.EarthworkAdoptPanel;
import com.plot.plugin.earthwork.ui.EarthworkEditPanel;
import com.plot.plugin.earthwork.ui.EarthworkGeneratePanel;
import com.plot.plugin.earthwork.ui.EarthworkOverviewPanel;
import com.plot.plugin.earthwork.ui.EarthworkQuickPanel;
import com.plot.plugin.earthwork.ui.EarthworkToolbarPanel;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private static final String[] TAB_KEYS = {
        "plugin.earthwork.tab.overview",
        "plugin.earthwork.tab.adopt",
        "plugin.earthwork.tab.edit",
        "plugin.earthwork.tab.generate"
    };

    private final EarthworkUiContext ctx;
    private final EarthworkToolbarPanel toolbarPanel;
    private final EarthworkOverviewPanel overviewPanel;
    private final EarthworkAdoptPanel adoptPanel;
    private final EarthworkEditPanel editPanel;
    private final EarthworkGeneratePanel generatePanel;
    private final EarthworkQuickPanel quickPanel;
    private int builderTab;

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
            quickPanel.render();
            return;
        }

        renderBuilderTabs();
    }

    private void renderBuilderTabs() {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float width = Math.max(1f, (ImGui.getContentRegionAvailX() - spacing * 3.0f) / 4.0f);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            boolean selected = builderTab == i;
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(ImGuiCol.ButtonActive));
            }
            if (ImGui.button(PlotI18n.tr(TAB_KEYS[i]) + "##earthwork_tab_" + i, width, 0)) {
                builderTab = i;
            }
            if (selected) {
                ImGui.popStyleColor();
            }
        }
        ImGui.separator();
        switch (builderTab) {
            case 1 -> adoptPanel.render();
            case 2 -> editPanel.render();
            case 3 -> generatePanel.render();
            default -> overviewPanel.render();
        }
    }

    public void renderDeferredModals() {
        overviewPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
