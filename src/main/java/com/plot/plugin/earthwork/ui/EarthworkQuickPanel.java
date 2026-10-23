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
    private static final int HEIGHT_MIN = -64;
    private static final int HEIGHT_MAX = 320;

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
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.quick.add_region"))) {
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
            if (!ctx.previewManager().hasValidPreview()) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.earthwork.preview_stale_hint"));
            }
            EarthworkUiWidgets.renderPlayerCutFill(
                preview.volumeReport,
                preview.resolvedElevation,
                preview.slopedSurface,
                preview.resolvedElevationMin,
                preview.resolvedElevationMax,
                true);
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
                if (seed == null) {
                    return;
                }
                ctx.projectHistory().push(ctx.project());
                region.setAutoBalance(false);
                region.setManualTargetElevation(seed);
                syncAutoPolicy(region);
                ctx.scheduleRecalculatePreview();
            }
        }

        if (auto) {
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            if (preview != null) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.earthwork.quick.recommended_y", preview.resolvedElevation));
                if (preview.unloadedColumnCount > 0) {
                    ImGui.textColored(
                        PluginUiColors.WARNING,
                        PlotI18n.tr(
                            "plugin.earthwork.unloaded_columns_hint",
                            preview.unloadedColumnCount));
                }
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.quick.auto_pending"));
            }
            return;
        }

        Integer seed = region.getManualTargetElevation() != null
            ? region.getManualTargetElevation()
            : resolveManualSeed(region);
        int[] range = sliderRange();
        if (seed == null || range == null) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.earthwork.no_loaded_terrain_samples"));
            return;
        }
        int current = seed;
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

    /** 有预览/已加载样本时返回种子标高；否则 null（不静默用 Y64）。 */
    private Integer resolveManualSeed(GradingRegion region) {
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview != null) {
            return preview.resolvedElevation;
        }
        if (region.getManualTargetElevation() != null) {
            return region.getManualTargetElevation();
        }
        return null;
    }

    /** @return [min, max]，无已加载样本时 null */
    private int[] sliderRange() {
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview == null || preview.existingTerrainSnapshot.isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (var column : preview.existingTerrainSnapshot.columns()) {
            if (!column.chunkLoaded()) {
                continue;
            }
            min = Math.min(min, column.groundY());
            max = Math.max(max, column.groundY());
        }
        if (min == Integer.MAX_VALUE) {
            return null;
        }
        return new int[] {
            Math.max(HEIGHT_MIN, min - 8),
            Math.min(HEIGHT_MAX, max + 8)
        };
    }
}
