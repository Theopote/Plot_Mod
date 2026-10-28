package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.solver.EarthworkElevationVolumeCurve;
import com.plot.plugin.earthwork.solver.EarthworkSectionProfile;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * P2 玩家可视化：工作量、两个推荐高度、剖面、标高—方量曲线、挖填热力示意。
 */
public final class EarthworkInsightCharts {
    private static final float CHART_HEIGHT = 92f;
    private static final float HEATMAP_HEIGHT = 120f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_CUT = ImColor.rgba(220, 80, 80, 255);
    private static final int COLOR_FILL = ImColor.rgba(80, 150, 240, 255);
    private static final int COLOR_WORK = ImColor.rgba(200, 200, 120, 220);
    private static final int COLOR_EXISTING = ImColor.rgba(160, 160, 160, 255);
    private static final int COLOR_DESIGN = ImColor.rgba(90, 210, 140, 255);
    private static final int COLOR_BALANCE = ImColor.rgba(255, 200, 80, 255);
    private static final int COLOR_MIN_WORK = ImColor.rgba(180, 140, 255, 255);
    private static final int COLOR_CURRENT = ImColor.rgba(255, 255, 255, 180);

    private EarthworkInsightCharts() {
    }

    public static void render(
            EarthworkUiContext ctx,
            GradingRegion region,
            EarthworkGenerationResult preview) {
        if (preview == null) {
            return;
        }
        boolean validPreview = ctx.previewManager().hasValidPreview()
            && EarthworkUiPresentation.belongsTo(preview, region);
        if (!validPreview) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.earthwork.analysis_stale_hint"));
        }

        EarthworkWorkMode mode = ctx.config() != null
            ? ctx.config().getWorkMode()
            : EarthworkWorkMode.QUICK;
        EarthworkElevationVolumeCurve curve = preview.elevationVolumeCurve;

        if (mode == EarthworkWorkMode.QUICK) {
            renderQuickAnalysis(ctx, region, preview, curve, validPreview);
            return;
        }

        // 建造模式生成页顶部已有挖填汇总，此处不再重复「挖填概览」

        if (mode.showsBuilderVisuals()) {
            renderHeatmap(ctx, preview, preview.designTerrainGrid);
        }
        if (preview.sectionProfile != null && !preview.sectionProfile.isEmpty()) {
            renderProfile(preview.sectionProfile);
        }
        if (mode.showsAnalysisVisuals()) {
            if (preview.elevationVolumeCurveRequiresSlopeCoupledSolver) {
                renderSlopeCoupledCurveHint(preview);
            } else if (curve != null && !curve.isEmpty()) {
                renderRecommendations(ctx, region, preview, curve, validPreview);
            }
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.ui.analysis_advanced"))) {
            if (curve != null && !curve.isEmpty()) {
                renderBeforeAfter(curve);
                if (mode.showsAnalysisVisuals() && !preview.elevationVolumeCurveRequiresSlopeCoupledSolver) {
                    renderCurve(curve, preview.resolvedElevation);
                }
            }
            renderDiagnostics(preview);
        }
    }

    /** Quick：默认收起；展开后才显示热力图、曲线、剖面。 */
    static void renderQuickAnalysis(
            EarthworkUiContext ctx,
            GradingRegion region,
            EarthworkGenerationResult preview,
            EarthworkElevationVolumeCurve curve,
            boolean validPreview) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.quick.view_analysis"))) {
            return;
        }
        renderChangeOverview(preview.volumeReport != null ? preview.volumeReport : EarthworkVolumeReport.empty());
        renderHeatmap(ctx, preview, preview.designTerrainGrid);
        if (preview.sectionProfile != null && !preview.sectionProfile.isEmpty()) {
            renderProfile(preview.sectionProfile);
        }
        if (curve != null && !curve.isEmpty()) {
            renderRecommendations(ctx, region, preview, curve, validPreview);
        } else if (preview.elevationVolumeCurveRequiresSlopeCoupledSolver) {
            renderSlopeCoupledCurveHint(preview);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.ui.analysis_advanced"))) {
            if (curve != null && !curve.isEmpty()) {
                renderCurve(curve, preview.resolvedElevation);
            }
            renderDiagnostics(preview);
        }
    }

    static void renderChangeOverview(EarthworkVolumeReport volumes) {
        long cut = volumes.geometricCutVolume();
        long fill = volumes.geometricFillVolume();
        long net = cut - fill;
        ImGui.text(PlotI18n.tr("plugin.earthwork.analysis_overview_header"));
        ImGui.text(PlotI18n.tr("plugin.earthwork.analysis_cut", formatCount(cut)));
        ImGui.text(PlotI18n.tr("plugin.earthwork.analysis_fill", formatCount(fill)));
        ImGui.text(PlotI18n.tr("plugin.earthwork.analysis_net", formatCount(net)));
    }

    static void renderDiagnostics(EarthworkGenerationResult preview) {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.earthwork.calculation_cell_count", formatCount(preview.calculationCellCount)));
        if (preview.existingTerrainSnapshot != null) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.terrain_snapshot_summary",
                    formatCount(preview.existingTerrainSnapshot.columnCount()),
                    formatCount(preview.existingTerrainSnapshot.loadedColumnCount()),
                    formatCount(preview.existingTerrainSnapshot.unloadedColumnCount())));
        }
    }

    /** Minecraft 翻译不支持 {@code %,d} / {@code %.0f}，先在 Java 侧格式化再按 {@code %s} 注入。 */
    private static String formatCount(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    static void renderSlopeCoupledCurveHint(EarthworkGenerationResult preview) {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.earthwork.curve_slope_coupled_only"));
        if (preview.resolvedElevationMin != preview.resolvedElevationMax) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.curve_slope_coupled_resolved",
                    preview.resolvedElevationMin,
                    preview.resolvedElevationMax));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.curve_slope_coupled_resolved_flat",
                    preview.resolvedElevation));
        }
    }

    static void renderBeforeAfter(EarthworkElevationVolumeCurve curve) {
        ImGui.text(PlotI18n.tr(
            "plugin.earthwork.before_after",
            curve.existingMin(),
            curve.existingMax(),
            curve.existingMedian(),
            curve.designMin(),
            curve.designMax()));
    }

    static void renderRecommendations(
            EarthworkUiContext ctx,
            GradingRegion region,
            EarthworkGenerationResult preview,
            EarthworkElevationVolumeCurve curve,
            boolean validPreview) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.recommend.header"));
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        EarthworkElevationVolumeCurve.Sample balance = curve.sampleAt(curve.balanceY());
        EarthworkElevationVolumeCurve.Sample minWork = curve.sampleAt(curve.minWorkY());
        boolean canApplyElevation = validPreview
            && canApplyRecommendedElevation(ctx.project().getActiveSite(), region);
        ImGui.beginDisabled(!canApplyElevation);
        if (ImGui.button(
                PlotI18n.tr("plugin.earthwork.recommend.balance", curve.balanceY()),
                half,
                0)
            && region != null) {
            ctx.projectHistory().push(ctx.project());
            region.setAutoBalance(false);
            region.setManualTargetElevation(curve.balanceY());
            syncAutoPolicy(ctx, region);
            ctx.recalculatePreview();
        }
        ImGui.sameLine();
        if (ImGui.button(
                PlotI18n.tr("plugin.earthwork.recommend.min_work", curve.minWorkY()),
                half,
                0)
            && region != null) {
            ctx.projectHistory().push(ctx.project());
            region.setAutoBalance(false);
            region.setManualTargetElevation(curve.minWorkY());
            syncAutoPolicy(ctx, region);
            ctx.recalculatePreview();
        }
        ImGui.endDisabled();
        if (!validPreview) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.recommend.preview_stale"));
        } else if (!canApplyRecommendedElevation(ctx.project().getActiveSite(), region)) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.recommend.single_flat_only"));
        }
        if (balance != null) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.recommend.balance_hint",
                    balance.cut(),
                    balance.fill(),
                    balance.imbalance()));
        }
        if (minWork != null) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.recommend.min_work_hint",
                    minWork.work(),
                    minWork.cut(),
                    minWork.fill()));
        }
        EarthworkUiWidgets.textWrappedSafe(reasonText(preview, curve));
    }

    static String reasonText(EarthworkGenerationResult preview, EarthworkElevationVolumeCurve curve) {
        long cut = preview.volumeReport.geometricCutVolume();
        long fill = preview.volumeReport.geometricFillVolume();
        int y = preview.resolvedElevation;
        if (!curve.canCompareAdjacentHeights()) {
            return PlotI18n.tr(
                "plugin.earthwork.recommend.reason_sparse",
                y,
                cut,
                fill,
                Math.abs(cut - fill),
                curve.sampleStep());
        }
        EarthworkElevationVolumeCurve.Sample lower = curve.sampleAtExact(y - 1);
        EarthworkElevationVolumeCurve.Sample here = curve.sampleAtExact(y);
        EarthworkElevationVolumeCurve.Sample upper = curve.sampleAtExact(y + 1);
        long hereWork = here != null ? here.work() : cut + fill;
        long vsLower = lower != null ? Math.max(0L, lower.work() - hereWork) : 0L;
        long vsUpper = upper != null ? Math.max(0L, upper.work() - hereWork) : 0L;
        return PlotI18n.tr(
            "plugin.earthwork.recommend.reason",
            y,
            cut,
            fill,
            Math.abs(cut - fill),
            y - 1,
            vsLower,
            y + 1,
            vsUpper);
    }

    static void renderCurve(EarthworkElevationVolumeCurve curve, int currentY) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.curve.header"));
        float width = ImGui.getContentRegionAvailX();
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + CHART_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        int yMin = curve.samples().getFirst().y();
        int yMax = curve.samples().getLast().y();
        long maxWork = Math.max(1L, curve.maxWork());
        float pad = 6f;
        plotPolyline(drawList, curve, yMin, yMax, maxWork, x0, y0, x1, y1, pad, true, COLOR_CUT);
        plotPolyline(drawList, curve, yMin, yMax, maxWork, x0, y0, x1, y1, pad, false, COLOR_FILL);
        plotWork(drawList, curve, yMin, yMax, maxWork, x0, y0, x1, y1, pad);
        markX(drawList, curve.balanceY(), yMin, yMax, x0, y0, x1, y1, pad, COLOR_BALANCE);
        markX(drawList, curve.minWorkY(), yMin, yMax, x0, y0, x1, y1, pad, COLOR_MIN_WORK);
        markX(drawList, currentY, yMin, yMax, x0, y0, x1, y1, pad, COLOR_CURRENT);
        ImGui.dummy(width, CHART_HEIGHT);
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.curve.legend"));
    }

    static void renderProfile(EarthworkSectionProfile profile) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.profile.header"));
        float width = ImGui.getContentRegionAvailX();
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + CHART_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        int elevMin = Integer.MAX_VALUE;
        int elevMax = Integer.MIN_VALUE;
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            elevMin = Math.min(elevMin, Math.min(station.existingY(), station.designY()));
            elevMax = Math.max(elevMax, Math.max(station.existingY(), station.designY()));
        }
        if (elevMin == elevMax) {
            elevMax = elevMin + 1;
        }
        float pad = 8f;
        int span = profile.axisSpan();
        for (int i = 0; i < profile.stations().size() - 1; i++) {
            EarthworkSectionProfile.Station a = profile.stations().get(i);
            EarthworkSectionProfile.Station b = profile.stations().get(i + 1);
            if (profile.axisGap(a, b) > 1) {
                continue;
            }
            float ax = x0 + pad + (x1 - x0 - 2 * pad) * (profile.axisDistance(a) / (float) span);
            float bx = x0 + pad + (x1 - x0 - 2 * pad) * (profile.axisDistance(b) / (float) span);
            drawList.addLine(
                ax, elevToY(a.existingY(), elevMin, elevMax, y0, y1, pad),
                bx, elevToY(b.existingY(), elevMin, elevMax, y0, y1, pad),
                COLOR_EXISTING,
                2f);
            drawList.addLine(
                ax, elevToY(a.designY(), elevMin, elevMax, y0, y1, pad),
                bx, elevToY(b.designY(), elevMin, elevMax, y0, y1, pad),
                COLOR_DESIGN,
                2f);
            float midX = (ax + bx) * 0.5f;
            int cut = Math.max(a.cut(), b.cut());
            int fill = Math.max(a.fill(), b.fill());
            if (cut > fill && cut > 0) {
                drawList.addLine(
                    midX,
                    elevToY(Math.max(a.existingY(), a.designY()), elevMin, elevMax, y0, y1, pad),
                    midX,
                    elevToY(Math.min(a.existingY(), a.designY()), elevMin, elevMax, y0, y1, pad),
                    COLOR_CUT,
                    1.5f);
            } else if (fill > 0) {
                drawList.addLine(
                    midX,
                    elevToY(Math.max(a.existingY(), a.designY()), elevMin, elevMax, y0, y1, pad),
                    midX,
                    elevToY(Math.min(a.existingY(), a.designY()), elevMin, elevMax, y0, y1, pad),
                    COLOR_FILL,
                    1.5f);
            }
        }
        ImGui.dummy(width, CHART_HEIGHT);
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.profile.legend"));
    }

    static void renderHeatmap(
            EarthworkUiContext ctx,
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.heatmap.header"));
        float width = Math.max(40f, ImGui.getContentRegionAvailX());
        float innerW = width - 8f;
        float innerH = HEATMAP_HEIGHT - 8f;
        int gridCols = Math.clamp((int) (innerW / 4f), 8, 64);
        int gridRows = Math.max(6, Math.min(48, (int) (innerH / 4f)));
        EarthworkCutFillHeatmap.Bucket[][] buckets =
            ctx.cutFillHeatmapCache().resolve(preview, grid, gridCols, gridRows);
        if (buckets == null) {
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + HEATMAP_HEIGHT, COLOR_BG);
        float cellW = innerW / gridCols;
        float cellH = innerH / gridRows;
        for (int x = 0; x < gridCols; x++) {
            for (int z = 0; z < gridRows; z++) {
                EarthworkCutFillHeatmap.Bucket bucket = buckets[x][z];
                if (bucket.cutCount() == 0 && bucket.fillCount() == 0 && bucket.unchangedCount() == 0) {
                    continue;
                }
                float px = origin.x + 4f + x * cellW;
                float py = origin.y + 4f + z * cellH;
                int color = switch (bucket.dominant()) {
                    case CUT -> ImColor.rgba(220, 64, 64, 210);
                    case FILL -> ImColor.rgba(64, 140, 230, 210);
                    case UNCHANGED -> ImColor.rgba(72, 176, 96, 140);
                };
                drawList.addRectFilled(px, py, px + cellW - 0.5f, py + cellH - 0.5f, color);
            }
        }
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + HEATMAP_HEIGHT, COLOR_BORDER);
        ImGui.dummy(width, HEATMAP_HEIGHT);
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.heatmap.legend"));
    }

    private static void plotPolyline(
            ImDrawList drawList,
            EarthworkElevationVolumeCurve curve,
            int yMin,
            int yMax,
            long maxWork,
            float x0,
            float y0,
            float x1,
            float y1,
            float pad,
            boolean cut,
            int color) {
        EarthworkElevationVolumeCurve.Sample prev = null;
        for (EarthworkElevationVolumeCurve.Sample sample : curve.samples()) {
            if (prev != null) {
                float ax = xAt(prev.y(), yMin, yMax, x0, x1, pad);
                float bx = xAt(sample.y(), yMin, yMax, x0, x1, pad);
                float ay = valueToY(cut ? prev.cut() : prev.fill(), maxWork, y0, y1, pad);
                float by = valueToY(cut ? sample.cut() : sample.fill(), maxWork, y0, y1, pad);
                drawList.addLine(ax, ay, bx, by, color, 2f);
            }
            prev = sample;
        }
    }

    private static void plotWork(
            ImDrawList drawList,
            EarthworkElevationVolumeCurve curve,
            int yMin,
            int yMax,
            long maxWork,
            float x0,
            float y0,
            float x1,
            float y1,
            float pad) {
        EarthworkElevationVolumeCurve.Sample prev = null;
        for (EarthworkElevationVolumeCurve.Sample sample : curve.samples()) {
            if (prev != null) {
                drawList.addLine(
                    xAt(prev.y(), yMin, yMax, x0, x1, pad),
                    valueToY(prev.work(), maxWork, y0, y1, pad),
                    xAt(sample.y(), yMin, yMax, x0, x1, pad),
                    valueToY(sample.work(), maxWork, y0, y1, pad),
                    COLOR_WORK,
                    1.5f);
            }
            prev = sample;
        }
    }

    private static void markX(
            ImDrawList drawList,
            int y,
            int yMin,
            int yMax,
            float x0,
            float y0,
            float x1,
            float y1,
            float pad,
            int color) {
        float x = xAt(y, yMin, yMax, x0, x1, pad);
        drawList.addLine(x, y0 + pad, x, y1 - pad, color, 1.5f);
    }

    private static float xAt(int y, int yMin, int yMax, float x0, float x1, float pad) {
        float span = Math.max(1, yMax - yMin);
        return x0 + pad + (x1 - x0 - 2 * pad) * ((y - yMin) / span);
    }

    private static float valueToY(long value, long max, float y0, float y1, float pad) {
        float t = (float) (value / (double) max);
        return y1 - pad - t * (y1 - y0 - 2 * pad);
    }

    private static float elevToY(int elev, int elevMin, int elevMax, float y0, float y1, float pad) {
        float t = (elev - elevMin) / (float) (elevMax - elevMin);
        return y1 - pad - t * (y1 - y0 - 2 * pad);
    }

    static boolean canApplyRecommendedElevation(
            com.plot.plugin.earthwork.model.EarthworkSite site, GradingRegion region) {
        if (site == null || region == null || site.getZoneCount() != 1) {
            return false;
        }
        GradingZone zone = site.getGradingZones().get(region.getId());
        return zone != null && zone.getType() == com.plot.plugin.earthwork.model.GradingZoneType.FLAT
            && !zone.getDesignSurface().hasBakedElevation()
            && region.getSurfaceMode() == com.plot.plugin.earthwork.model.GradingSurfaceMode.LEVEL_PAD;
    }

    private static void syncAutoPolicy(EarthworkUiContext ctx, GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null) {
            zone.syncVerticalPolicyWithAutoBalance();
        }
    }
}
