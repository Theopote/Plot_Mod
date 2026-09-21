package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.preview.overlay.PowerLineCanvasPreviewOverlay;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建造 Tab：预览摘要、生成预览、落地。 */
public final class PowerLineBuildPanel {
    private final PowerLineUiContext ctx;
    private final PowerLineBuildActions buildActions;

    public PowerLineBuildPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.buildActions = new PowerLineBuildActions(ctx);
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        renderCurrentLineHeader();
        if (line == null) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            return;
        }
        if (PowerLineUiWidgets.renderMultiLineEditBlocked(ctx)) {
            return;
        }
        renderPreviewSection(line);
        ImGui.separator();
        buildActions.renderBuildAction(line);
    }

    private void renderPreviewSection(PowerLineFootprint line) {
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build.preview_section"));
        renderPreviewSummary(line);
        ImGui.spacing();
        buildActions.renderPreviewActions(line);
    }

    private void renderCurrentLineHeader() {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.current_line"));
        ImGui.sameLine();
        float width = ImGui.getContentRegionAvail().x;
        if (width > 0f) {
            ImGui.setNextItemWidth(width);
        }
        PowerLineUiWidgets.renderLineSelector(ctx, false);
    }

    private void renderPreviewSummary(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;

        if (result == null) {
            double worldLength = line.computeWorldPathLength(ctx.coordinates());
            int poleEstimate = line.estimatePoleCount(ctx.coordinates());
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.build.compact_summary_estimate",
                    poleEstimate,
                    formatBlocks(worldLength)));
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }

        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.build.compact_summary",
            result.poleCount,
            formatBlocks(worldLength),
            formatBlockCount(result.blockCount())));
        renderInspectionSummary(line);
        renderStyleLine(line);
    }

    private void renderInspectionSummary(PowerLineFootprint line) {
        PowerLineCanvasPreviewOverlay overlay = ctx.state().getCanvasPreviewOverlay();
        if (overlay == null || !line.getId().equals(overlay.lineId())) {
            return;
        }
        PowerLineCanvasPreviewOverlay.PreviewStats stats = overlay.stats();
        PowerLineUiWidgets.textColored(
            PluginUiColors.STATUS_OK,
            PlotI18n.tr(
                "plugin.powerline.build.preview_inspection",
                stats.towerCount(),
                stats.spanCount(),
                stats.angleTowerCount(),
                stats.terrainInsertCount()));
        if (stats.userOverrideCount() > 0) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.build.preview_user_overrides",
                    stats.userOverrideCount()));
        }
        String selectedSiteId = ctx.state().getSelectedCanvasPoleSiteId();
        if (selectedSiteId != null && !selectedSiteId.isBlank()) {
            overlay.markers().stream()
                .filter(marker -> selectedSiteId.equals(marker.poleSiteId()))
                .findFirst()
                .ifPresent(marker -> PowerLineUiWidgets.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.powerline.build.preview_selected_tower",
                        PowerLineUiFormat.format(marker.stationing()),
                        marker.designLabel() != null && !marker.designLabel().isBlank()
                            ? marker.designLabel()
                            : PlotI18n.tr("plugin.powerline.canvas_preview.design_unknown"))));
        }
    }

    private void renderStyleLine(PowerLineFootprint line) {
        PowerLineStylePreset basePreset = PowerLineStyleEditor.resolveBasePreset(
            line,
            ctx.state().getDesignProject());
        if (basePreset != null) {
            String styleLabel = com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog.displayLabel(
                basePreset);
            if (PowerLineStyleEditor.isModified(line)) {
                styleLabel += PowerLineUiTextGlyphSafety.INLINE_SEPARATOR
                    + PlotI18n.tr("plugin.powerline.style.modified_badge");
            }
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.style_preset", styleLabel));
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.build.style_custom"));
    }

    private static String formatBlocks(double blocks) {
        return PowerLineUiFormat.format(blocks);
    }

    private static String formatBlockCount(int blocks) {
        return String.format("%d", blocks);
    }

    public void renderBuildConfirmPopup() {
        buildActions.renderBuildConfirmPopup();
    }
}
