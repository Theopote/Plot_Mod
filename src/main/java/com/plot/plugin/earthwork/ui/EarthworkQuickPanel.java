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
    private final EarthworkAdoptPanel adoptPanel;
    private final EarthworkGeneratePanel generatePanel;

    public EarthworkQuickPanel(
            EarthworkUiContext ctx,
            EarthworkAdoptPanel adoptPanel,
            EarthworkGeneratePanel generatePanel) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.generatePanel = generatePanel;
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.quick.hint"));
        ImGui.separator();

        if (ctx.project().getRegionCount() == 0) {
            adoptPanel.render();
            return;
        }

        EarthworkUiWidgets.renderRegionSelector(ctx);
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
            EarthworkUiWidgets.renderPlayerCutFill(
                preview.volumeReport,
                preview.resolvedElevation,
                preview.slopedSurface,
                preview.resolvedElevationMin,
                preview.resolvedElevationMax);
            EarthworkInsightCharts.render(ctx, region, preview);
            generatePanel.renderPreviewBuildButtons(preview);
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
                ctx.recalculatePreview();
            }
        }
        if (ImGui.radioButton(PlotI18n.tr("plugin.earthwork.quick.target_manual"), !auto)) {
            if (auto) {
                ctx.projectHistory().push(ctx.project());
                int seed = resolveCurrentHeight(region);
                region.setAutoBalance(false);
                region.setManualTargetElevation(seed);
                syncAutoPolicy(region);
                ctx.recalculatePreview();
            }
        }

        if (auto) {
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.quick.recommend_height"), ImGui.getContentRegionAvailX(), 0)) {
                ctx.projectHistory().push(ctx.project());
                region.setAutoBalance(true);
                syncAutoPolicy(region);
                ctx.recalculatePreview();
            }
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            if (preview != null) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.earthwork.quick.recommended_y", preview.resolvedElevation));
            }
            return;
        }

        int current = region.getManualTargetElevation() != null
            ? region.getManualTargetElevation()
            : resolveCurrentHeight(region);
        int[] elevation = {current};
        int[] range = sliderRange();
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
            ctx.recalculatePreview();
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
                ctx.recalculatePreview();
            }
        }
    }

    private void syncAutoPolicy(GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null) {
            zone.syncVerticalPolicyWithAutoBalance();
        }
    }

    private int resolveCurrentHeight(GradingRegion region) {
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview != null) {
            return preview.resolvedElevation;
        }
        if (region.getManualTargetElevation() != null) {
            return region.getManualTargetElevation();
        }
        return 64;
    }

    private int[] sliderRange() {
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview == null || preview.existingTerrainSnapshot.isEmpty()) {
            return new int[] {HEIGHT_MIN, HEIGHT_MAX};
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (var column : preview.existingTerrainSnapshot.columns()) {
            min = Math.min(min, column.groundY());
            max = Math.max(max, column.groundY());
        }
        if (min == Integer.MAX_VALUE) {
            return new int[] {HEIGHT_MIN, HEIGHT_MAX};
        }
        return new int[] {
            Math.max(HEIGHT_MIN, min - 8),
            Math.min(HEIGHT_MAX, max + 8)
        };
    }
}
