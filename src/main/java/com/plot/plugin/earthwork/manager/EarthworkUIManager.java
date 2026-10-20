package com.plot.plugin.earthwork.manager;

import com.plot.core.model.Shape;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.overlay.EarthworkRegionCanvasSelectionController;
import com.plot.plugin.earthwork.overlay.EarthworkRegionOverlayController;
import com.plot.plugin.earthwork.overlay.EarthworkRegionOverlayEntry;
import com.plot.plugin.earthwork.ui.EarthworkEditPanel;
import com.plot.plugin.earthwork.ui.EarthworkGeneratePanel;
import com.plot.plugin.earthwork.ui.EarthworkQuickPanel;
import com.plot.plugin.earthwork.ui.EarthworkRegionPanel;
import com.plot.plugin.earthwork.ui.EarthworkToolbarPanel;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.ui.PluginTabScrollUi;
import com.plot.ui.canvas.CanvasAccess;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

import java.util.List;

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private final EarthworkUiContext ctx;
    private final EarthworkToolbarPanel toolbarPanel;
    private final EarthworkRegionPanel regionPanel;
    private final EarthworkEditPanel editPanel;
    private final EarthworkGeneratePanel generatePanel;
    private final EarthworkQuickPanel quickPanel;
    private final EarthworkRegionCanvasSelectionController canvasSelectionController =
        new EarthworkRegionCanvasSelectionController();

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new EarthworkToolbarPanel(ctx);
        this.regionPanel = new EarthworkRegionPanel(ctx);
        this.editPanel = new EarthworkEditPanel(ctx);
        this.generatePanel = new EarthworkGeneratePanel(ctx);
        this.quickPanel = new EarthworkQuickPanel(ctx, regionPanel, generatePanel);
    }

    public void render() {
        if (ctx.config() == null) {
            return;
        }

        if (ctx.pickSession().isActive()) {
            regionPanel.tickPickSession();
        }
        if (ctx.threePointPickSession().isActive()) {
            editPanel.tickThreePointPickSession();
        }
        tickCanvasSelection();

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
            "plugin.earthwork.tab.region",
            "##earthwork_tab_region",
            regionPanel::render);
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
        regionPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }

    private void tickCanvasSelection() {
        boolean pickActive = ctx.pickSession().isActive();
        boolean overlayVisible = ctx.showRegionOverlayRef().get() || pickActive;
        if (!overlayVisible) {
            canvasSelectionController.resetPointer();
            return;
        }
        List<Shape> canvasShapes = pickActive
            ? (!ctx.pickSession().getAccumulatedRegions().isEmpty()
                ? ctx.pickSession().getAccumulatedRegions()
                : ctx.host().appState().getSelectedShapes())
            : List.of();
        List<EarthworkRegionOverlayEntry> entries = EarthworkRegionOverlayController.snapshot(
            ctx.project(),
            ctx.selectedRegionId(),
            canvasShapes,
            pickActive,
            true);
        canvasSelectionController.tick(
            CanvasAccess.get(),
            ctx.host().appState(),
            entries,
            pickActive,
            () -> true,
            ctx::setSelectedRegionId);
    }
}
