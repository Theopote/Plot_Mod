package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.model.EarthworkQuickEdge;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Quick Mode：框选区域、目标高度、边缘、预览施工。 */
public final class EarthworkQuickPanel {
    private final imgui.type.ImString heightInput = new imgui.type.ImString(16);
    private String heightInputRegion = "";
    private Integer displayedHeight;

    private final EarthworkUiContext ctx;
    private final EarthworkRegionPanel regionPanel;
    private final EarthworkGeneratePanel generatePanel;

    public EarthworkQuickPanel(
            EarthworkUiContext ctx,
            EarthworkRegionPanel regionPanel,
            EarthworkGeneratePanel generatePanel) {
        this.ctx = ctx;
        this.regionPanel = regionPanel;
        this.generatePanel = generatePanel;
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.quick.hint"));
        ImGui.separator();

        if (ctx.project().getRegionCount() == 0) {
            regionPanel.renderAdoptSection();
            return;
        }

        EarthworkUiWidgets.renderRegionSelector(ctx);
        if (ctx.pickSession().isActive()
            || ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.quick.add_region"))) {
            regionPanel.renderAdoptSection();
        }

        GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
        if (region == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
            return;
        }

        renderTarget(region);
        ImGui.spacing();
        renderEdge(region);
        ImGui.spacing();
        generatePanel.renderPreviewActions(region);

        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview != null) {
            ImGui.separator();
            generatePanel.renderPreviewStatus(preview);
            generatePanel.renderResultElevation(region, preview);
            EarthworkUiWidgets.renderPlayerCutFill(
                preview.volumeReport,
                preview.resolvedElevation,
                preview.slopedSurface,
                preview.resolvedElevationMin,
                preview.resolvedElevationMax,
                true, false);
            generatePanel.renderPreviewBuildButtons(preview);
            EarthworkInsightCharts.render(ctx, region, preview);
        }
    }

    private void renderTarget(GradingRegion region) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.quick.target_header"));
        boolean auto = region.isAutoBalance();
        if (ImGui.radioButton(PlotI18n.tr("plugin.earthwork.quick.target_auto"), auto)) {
            if (!auto) {
                ctx.projectHistory().push(ctx.project());
                region.setAutoBalance(true);
                syncAutoPolicy(region);
                ctx.scheduleRecalculatePreview();
            }
        }
        if (ImGui.radioButton(PlotI18n.tr("plugin.earthwork.quick.target_manual"), !auto)) {
            if (auto) {
                Integer seed = resolveManualSeed(region);
                ctx.projectHistory().push(ctx.project());
                region.setAutoBalance(false);
                region.setManualTargetElevation(seed);
                syncAutoPolicy(region);
                if (seed != null) {
                    ctx.scheduleRecalculatePreview();
                } else {
                    ctx.invalidatePreview();
                }
            }
        }

        if (auto) {
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            if (EarthworkUiPresentation.belongsTo(preview, region)) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(EarthworkUiPresentation.elevationLabel(true,
                        ctx.previewManager().hasValidPreview()), preview.resolvedElevation));
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.quick.auto_pending"));
            }
            return;
        }

        int[] range = heightRange();
        Integer current = region.getManualTargetElevation();
        if (!region.getId().equals(heightInputRegion)
            || (!java.util.Objects.equals(current, displayedHeight) && !ImGui.isAnyItemActive())) {
            heightInput.set(current != null ? current.toString() : "");
            heightInputRegion = region.getId();
            displayedHeight = current;
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.ui.target_input"));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean inputChanged = ImGui.inputText("##quick_target_input", heightInput);
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (inputChanged) {
            Integer value = EarthworkUiPresentation.parseHeight(heightInput.get(), range[0], range[1]);
            region.setManualTargetElevation(value);
            displayedHeight = value;
            if (value == null) {
                ctx.invalidatePreview();
            } else {
                ctx.scheduleRecalculatePreview();
            }
        }
        current = region.getManualTargetElevation();
        if (current == null) {
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING,
                PlotI18n.tr("plugin.earthwork.ui.height_required", range[0], range[1]));
            return;
        }
        int[] elevation = {current};
        boolean changed = ImGui.sliderInt(
            "##quick_target_y",
            elevation,
            range[0],
            range[1],
            PlotI18n.tr("plugin.earthwork.platform_height", elevation[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (changed && elevation[0] != current) {
            region.setManualTargetElevation(elevation[0]);
            ctx.scheduleRecalculatePreview();
        }
    }

    private void renderEdge(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone == null) {
            return;
        }
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        ImGui.text(PlotI18n.tr("plugin.earthwork.quick.edge_header"));
        EarthworkQuickEdge current = EarthworkQuickEdge.fromSettings(settings);
        for (EarthworkQuickEdge edge : EarthworkQuickEdge.values()) {
            if (ImGui.radioButton(PlotI18n.tr(edge.i18nKey()), current == edge) && current != edge) {
                ctx.projectHistory().push(ctx.project());
                edge.applyTo(settings);
                ctx.scheduleRecalculatePreview();
            }
        }
    }

    private void syncAutoPolicy(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null) {
            zone.syncVerticalPolicyWithAutoBalance();
        }
    }

    /** 切换时采样一次，不依赖用户先生成预览。 */
    private Integer resolveManualSeed(GradingRegion region) {
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        com.plot.plugin.earthwork.terrain.TerrainSnapshot terrain =
            com.plot.plugin.earthwork.terrain.TerrainSnapshot.empty();
        var world = EarthworkUiWidgets.getClientWorld();
        if (world != null && region.getManualTargetElevation() == null
            && !(ctx.previewManager().hasValidPreview() && EarthworkUiPresentation.belongsTo(preview, region))) {
            try {
                terrain = ctx.terrainSnapshotCache().captureFresh(region, world, ctx.host().coordinates());
            } catch (RuntimeException e) {
                ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.no_loaded_terrain_samples"),
                    com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING);
            }
        }
        return EarthworkUiPresentation.initialManualHeight(region, preview,
            ctx.previewManager().hasValidPreview(), terrain);
    }

    private int[] heightRange() {
        var world = EarthworkUiWidgets.getClientWorld();
        return world != null
            ? new int[] {world.getBottomY(), world.getBottomY() + world.getHeight() - 1}
            : new int[] {-64, 319};
    }
}
