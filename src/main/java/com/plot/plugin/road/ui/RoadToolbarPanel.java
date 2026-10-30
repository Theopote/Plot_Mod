package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.command.Command;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.plugin.road.manager.RoadNetworkPreviewJob;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 道路插件顶部工具栏与落地进度控制。
 */
public final class RoadToolbarPanel {
    private final RoadUiContext ctx;

    public RoadToolbarPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }

    private void renderToolbar() {
        boolean generateTab = ctx.activeTab() == RoadUiTab.GENERATE;
        renderNetworkUndoRedo(generateTab);
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_scope_hint"));

        if (canUndoWorldPlacement()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_world_section"));
            float worldUndoWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.45f, 96f, 168f);
            if (ImGui.button(PlotI18n.tr("plugin.road.undo_world"), worldUndoWidth, 0)) {
                ctx.host().commands().undo();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.undo_world"));
            }
        }

        renderOverlayOptions();

        if (!ctx.status().isEmpty()) {
            RoadStatusUi.render(ctx.status().getStatus());
        }
        renderPreviewJobControls();
        ImGui.separator();
    }

    private void renderOverlayOptions() {
        if (ImGui.checkbox(
                PlotI18n.tr("plugin.road.overlay.show_slope_batter"),
                ctx.showSlopeBatterOverlay())) {
            ctx.requestOverlayRefresh();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.overlay.show_slope_batter_hint"));
        }
    }

    private void renderPreviewJobControls() {
        if (!ctx.previewManager().isPreviewJobRunning()) {
            return;
        }
        RoadNetworkPreviewJob job = ctx.previewManager().previewJob();
        if (job != null) {
            PluginJobProgressUi.renderJobProgress(
                PlotI18n.tr(job.phaseTranslationKey()),
                job.processedWorkUnits(),
                job.totalWorkUnits(),
                ImGui.getContentRegionAvailX(),
                "plugin.road.cancel_preview",
                ctx::cancelPreviewJob);
        }
    }

    private void renderNetworkUndoRedo(boolean generateTab) {
        float avail = ImGui.getContentRegionAvailX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = Math.clamp((avail - spacing) / 2.0f, 96f, 168f);
        if (generateTab) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_network_section"));
        }

        boolean undoDisabled = !ctx.networkManager().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.undo_network"), buttonWidth, 0)) {
            ctx.networkManager().undo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.undo_network"));
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean redoDisabled = !ctx.networkManager().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.redo_network"), buttonWidth, 0)) {
            ctx.networkManager().redo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.redo_network"));
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }
    }

    private boolean canUndoWorldPlacement() {
        Command command = ctx.host().commands().peekUndoCommand();
        return command instanceof GenerateRoadCommand;
    }

    private void renderActivePlacementControls() {
        PluginJobProgressUi.renderPlacementProgress(
            ctx.host().placement(),
            "plugin.road.placement_progress",
            "plugin.road.build_in_progress_hint",
            "plugin.road.cancel_placement");
        if (ctx.host().placement().isBusy()) {
            ImGui.separator();
        }
    }
}
