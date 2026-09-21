package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;

/** 建造 Tab 操作按钮（预览 / 落地）。 */
final class PowerLineBuildActions {
    private final PowerLineUiContext ctx;

    PowerLineBuildActions(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    void renderPreviewActions(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        boolean hasPreview = ctx.hasValidPreview(line);
        String previewLabel = hasPreview
            ? PlotI18n.tr("plugin.powerline.build.refresh_preview")
            : PlotI18n.tr("plugin.powerline.build.generate_preview");

        float spacing = ImGui.getStyle().getItemSpacingX();
        float half = (ImGui.getContentRegionAvailX() - spacing) * 0.5f;

        if (ImGui.button(previewLabel + "##build_preview", half, 0)) {
            ctx.calculatePreview(line, true);
        }
        ImGui.sameLine(0f, spacing);
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_preview") + "##build_clear_preview", half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ctx.lastGenerationResult() != null && !hasPreview) {
            PowerLineStatusIcon.renderWarningLine(PlotI18n.tr("plugin.powerline.preview_stale"));
        }
    }

    void renderBuildAction(PowerLineFootprint line) {
        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        boolean hasPreview = ctx.hasValidPreview(line);

        if (!readiness.ready()) {
            PowerLineUiWidgets.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }

        boolean parametricBlocked = ctx.actions().hasParametricBuildBlocking(line);
        boolean buildDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || !hasPreview
            || parametricBlocked;
        if (parametricBlocked) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.ERROR_SOFT,
                PlotI18n.tr("plugin.powerline.build_blocked_parametric"));
        }
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
            if (parametricBlocked && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.powerline.build_blocked_parametric_tooltip"));
            }
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
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build_confirm", blocks));
            boolean parametricBlocked = ctx.actions().hasParametricBuildBlocking(line);
            if (parametricBlocked) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr("plugin.powerline.build_blocked_parametric"));
            }
            boolean canBuild = result != null
                && ctx.requestBuildConfirm(line)
                && !parametricBlocked;
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
