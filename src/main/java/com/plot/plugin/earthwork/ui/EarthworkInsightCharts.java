package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
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
        EarthworkVolumeReport volumes = preview.volumeReport != null
            ? preview.volumeReport
            : EarthworkVolumeReport.empty();
        EarthworkWorkMode mode = ctx.config() != null
            ? ctx.config().getWorkMode()
            : EarthworkWorkMode.QUICK;
        EarthworkElevationVolumeCurve curve = preview.elevationVolumeCurve;

        if (mode == EarthworkWorkMode.QUICK) {
            if (curve != null && !curve.isEmpty()) {
                renderRecommendations(ctx, region, preview, curve, true);
            }
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.quick.more_in_learn"));
            return;
        }

        renderWorkAndWorld(volumes, preview.calculationCellCount);
        if (curve != null && !curve.isEmpty()) {
            renderRecommendations(ctx, region, preview, curve, false);
        }
        if (!mode.showsInsightDashboard()) {
            return;
        }
        if (curve != null && !curve.isEmpty()) {
            renderBeforeAfter(curve);
            renderCurve(curve, preview.resolvedElevation);
        }
        if (preview.sectionProfile != null && !preview.sectionProfile.isEmpty()) {
            renderProfile(preview.sectionProfile);
        }
        renderHeatmap(preview.designTerrainGrid);
    }

    static void renderWorkAndWorld(EarthworkVolumeReport volumes, int cellCount) {
        long cut = volumes.geometricCutVolume();
        long fill = volumes.geometricFillVolume();
        long work = cut + fill;
        long changed = volumes.totalChangedBlocks();
        ImGui.text(PlotI18n.tr("plugin.earthwork.earth_calc", cut, fill));
        ImGui.text(PlotI18n.tr(
            "plugin.earthwork.world_edits",
            volumes.cutChangedBlocks(),
            volumes.fillChangedBlocks()));
        float scale = Math.max(1f, (float) Math.max(work, changed));
        ImVec2 origin = ImGui.getCursorScreenPos();
        float width = ImGui.getContentRegionAvailX();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float barH = 10f;
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + barH, COLOR_BG);
        float cutW = width * (cut / scale);
        float fillW = width * (fill / scale);
        drawList.addRectFilled(origin.x, origin.y, origin.x + cutW, origin.y + barH, COLOR_CUT);
        drawList.addRectFilled(origin.x + cutW, origin.y, origin.x + cutW + fillW, origin.y + barH, COLOR_FILL);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + barH, COLOR_BORDER);
        ImGui.dummy(width, barH + 2f);
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.work_scale", work, changed));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.difficulty_stars",
            difficultyStars(work, cellCount)));
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
            boolean compact) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.recommend.header"));
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        EarthworkElevationVolumeCurve.Sample balance = curve.sampleAt(curve.balanceY());
        EarthworkElevationVolumeCurve.Sample minWork = curve.sampleAt(curve.minWorkY());
        if (ImGui.button(
                PlotI18n.tr("plugin.earthwork.recommend.balance", curve.balanceY()),
                half,
                0)
            && region != null) {
            ctx.projectHistory().push(ctx.project());
            region.setAutoBalance(true);
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
        if (compact) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, reasonText(preview, curve));
            return;
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
        ImGui.textWrapped(reasonText(preview, curve));
    }

    static String reasonText(EarthworkGenerationResult preview, EarthworkElevationVolumeCurve curve) {
        long cut = preview.volumeReport.geometricCutVolume();
        long fill = preview.volumeReport.geometricFillVolume();
        int y = preview.resolvedElevation;
        EarthworkElevationVolumeCurve.Sample lower = curve.sampleAt(y - 1);
        EarthworkElevationVolumeCurve.Sample here = curve.sampleAt(y);
        EarthworkElevationVolumeCurve.Sample upper = curve.sampleAt(y + 1);
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

        int yMin = curve.samples().get(0).y();
        int yMax = curve.samples().get(curve.samples().size() - 1).y();
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
        int n = profile.stations().size();
        for (int i = 0; i < n - 1; i++) {
            EarthworkSectionProfile.Station a = profile.stations().get(i);
            EarthworkSectionProfile.Station b = profile.stations().get(i + 1);
            float ax = x0 + pad + (x1 - x0 - 2 * pad) * (i / (float) (n - 1));
            float bx = x0 + pad + (x1 - x0 - 2 * pad) * ((i + 1) / (float) (n - 1));
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

    static void renderHeatmap(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.heatmap.header"));
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            minX = Math.min(minX, cell.worldX());
            maxX = Math.max(maxX, cell.worldX());
            minZ = Math.min(minZ, cell.worldZ());
            maxZ = Math.max(maxZ, cell.worldZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return;
        }
        float width = ImGui.getContentRegionAvailX();
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + HEATMAP_HEIGHT, COLOR_BG);
        float spanX = Math.max(1, maxX - minX + 1);
        float spanZ = Math.max(1, maxZ - minZ + 1);
        float scale = Math.min((width - 8f) / spanX, (HEATMAP_HEIGHT - 8f) / spanZ);
        float cell = Math.max(2f, scale * 0.9f);
        for (DesignTerrainCell terrainCell : grid.cells().values()) {
            if (terrainCell == null || !terrainCell.participatesInEarthwork()) {
                continue;
            }
            float px = origin.x + 4f + (terrainCell.worldX() - minX) * scale;
            float py = origin.y + 4f + (terrainCell.worldZ() - minZ) * scale;
            int color = terrainCell.deltaY() > 0
                ? ImColor.rgba(64, 140, 230, 200)
                : terrainCell.deltaY() < 0
                    ? ImColor.rgba(220, 64, 64, 200)
                    : ImColor.rgba(72, 176, 96, 120);
            drawList.addRectFilled(px, py, px + cell, py + cell, color);
        }
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

    static String difficultyStars(long work, int cellCount) {
        double load = cellCount <= 0 ? 0.0 : work / (double) cellCount;
        int stars;
        if (load < 1.0) {
            stars = 1;
        } else if (load < 2.0) {
            stars = 2;
        } else if (load < 4.0) {
            stars = 3;
        } else if (load < 8.0) {
            stars = 4;
        } else {
            stars = 5;
        }
        return "★".repeat(stars) + "☆".repeat(5 - stars);
    }

    private static void syncAutoPolicy(EarthworkUiContext ctx, GradingRegion region) {
        GradingZone zone = ctx.project().getZone(region.getId());
        if (zone != null) {
            zone.syncVerticalPolicyWithAutoBalance();
        }
    }
}
