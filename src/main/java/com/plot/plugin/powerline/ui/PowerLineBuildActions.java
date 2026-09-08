package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建造 Tab 操作按钮（预览 / 落地）。 */
final class PowerLineBuildActions {
    private final PowerLineUiContext ctx;

    PowerLineBuildActions(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    void render(PowerLineFootprint line) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        ctx.syncPreviewValidity(line);

        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        boolean hasPreview = ctx.hasValidPreview(line);

        if (ImGui.button(PlotI18n.tr("plugin.powerline.calc_preview"), half, 0)) {
            ctx.calculatePreview(line);
        }
        ImGui.sameLine();
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }

        if (ctx.lastGenerationResult() != null && !hasPreview) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.preview_stale"));
        }

        boolean buildDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || !hasPreview;
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.build"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.requestBuildConfirm(line)) {
                ctx.setBuildConfirmPending(true);
            }
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    void renderBuildConfirmPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_build_confirm",
                ctx.buildConfirmPending(),
                () -> ctx.setBuildConfirmPending(false))) {
            PowerLineFootprint line = ctx.selection().primary(ctx.project());
            PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
            int blocks = result != null ? result.blockCount() : 0;
            ImGui.text(PlotI18n.tr("plugin.powerline.build_confirm", blocks));
            boolean canBuild = result != null && ctx.requestBuildConfirm(line);
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                ctx.buildInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
