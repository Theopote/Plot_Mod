package com.plot.plugin.pattern.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 图案 ImGui 界面编排。 */
public final class PatternUIManager {
    private final PatternUiContext ctx;
    private final PatternToolbarPanel toolbarPanel;
    private final PatternRegionPanel regionPanel;
    private final PatternDesignPanel designPanel;
    private final PatternGeneratePanel generatePanel;

    public PatternUIManager(PatternUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PatternToolbarPanel(ctx);
        this.regionPanel = new PatternRegionPanel(ctx);
        this.designPanel = new PatternDesignPanel(ctx);
        this.generatePanel = new PatternGeneratePanel(ctx);
    }

    public void render() {
        if (ctx == null || ctx.host() == null) {
            return;
        }

        ctx.actions().reconcilePreviewLifecycle();

        if (ctx.pickSession().isActive()) {
            regionPanel.tickPickSession();
        }

        toolbarPanel.render();

        if (ImGui.beginTabBar("##pattern_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.pattern.tab.region", regionPanel::render);
            renderTab("plugin.pattern.tab.design", designPanel::render);
            renderTab("plugin.pattern.tab.generate", generatePanel::render);
            ImGui.endTabBar();
        }
    }

    private static void renderTab(String labelKey, Runnable body) {
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey))) {
            body.run();
            ImGui.endTabItem();
        }
    }

    public void renderDeferredModals() {
        regionPanel.renderDeleteConfirmPopup();
        designPanel.renderDeferredModals();
        generatePanel.renderBuildConfirmPopup();
    }
}
