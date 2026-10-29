package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
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

import java.util.ArrayList;
import java.util.List;

/**
 * P2 玩家可视化：工作量、两个推荐高度、剖面、标高—方量曲线、挖填热力示意。
 */
public final class EarthworkInsightCharts {
    private static final float CHART_HEIGHT = 92f;
    /** 剖面图略高于通用曲线，便于读挖填带。 */
    private static final float PROFILE_CHART_HEIGHT = 112f;
    private static final float PROFILE_LEFT_AXIS = 34f;
    /** 挖填平面示意：短边下限 / 长边上限，具体尺寸按场地 XZ 比例适配。 */
    private static final float HEATMAP_MIN_SIDE = 72f;
    private static final float HEATMAP_MAX_SIDE = 220f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_ZONE_OUTLINE = ImColor.rgba(220, 220, 220, 160);
    private static final int COLOR_CUT = ImColor.rgba(220, 80, 80, 255);
    private static final int COLOR_FILL = ImColor.rgba(80, 150, 240, 255);
    private static final int COLOR_CUT_BAND = ImColor.rgba(220, 80, 80, 140);
    private static final int COLOR_FILL_BAND = ImColor.rgba(80, 150, 240, 140);
    private static final int COLOR_ZONE_SEP = ImColor.rgba(255, 220, 120, 200);
    private static final int COLOR_WORK = ImColor.rgba(200, 200, 120, 220);
    private static final int COLOR_EXISTING = ImColor.rgba(160, 160, 160, 255);
    private static final int COLOR_DESIGN = ImColor.rgba(90, 210, 140, 255);
    private static final int COLOR_EXISTING_FAINT = ImColor.rgba(160, 160, 160, 100);
    private static final int COLOR_DESIGN_FAINT = ImColor.rgba(90, 210, 140, 90);
    private static final int COLOR_AXIS_LABEL = ImColor.rgba(180, 180, 180, 220);
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
            && EarthworkUiPresentation.sharesSitePreview(preview, ctx.project(), region);
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
        renderAllZoneProfiles(ctx);
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
        renderAllZoneProfiles(ctx);
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
        int currentY = preview.resolvedElevation;
        // 顶部已有挖/填/总量：仅在推荐高度与当前不同时补充该高度的方量，避免原文重复
        if (balance != null && balance.y() != currentY) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.recommend.balance_hint",
                    balance.y(),
                    balance.cut(),
                    balance.fill(),
                    balance.imbalance()));
        }
        if (minWork != null
            && minWork.y() != currentY
            && (balance == null || minWork.y() != balance.y())) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.earthwork.recommend.min_work_hint",
                    minWork.y(),
                    minWork.work(),
                    minWork.cut(),
                    minWork.fill()));
        }
        EarthworkUiWidgets.textWrappedSafe(reasonText(preview, curve));
    }

    static String reasonText(EarthworkGenerationResult preview, EarthworkElevationVolumeCurve curve) {
        int y = preview.resolvedElevation;
        if (!curve.canCompareAdjacentHeights()) {
            return PlotI18n.tr(
                "plugin.earthwork.recommend.reason_sparse",
                y,
                curve.sampleStep());
        }
        long cut = preview.volumeReport.geometricCutVolume();
        long fill = preview.volumeReport.geometricFillVolume();
        EarthworkElevationVolumeCurve.Sample lower = curve.sampleAtExact(y - 1);
        EarthworkElevationVolumeCurve.Sample here = curve.sampleAtExact(y);
        EarthworkElevationVolumeCurve.Sample upper = curve.sampleAtExact(y + 1);
        long hereWork = here != null ? here.work() : cut + fill;
        long vsLower = lower != null ? Math.max(0L, lower.work() - hereWork) : 0L;
        long vsUpper = upper != null ? Math.max(0L, upper.work() - hereWork) : 0L;
        return PlotI18n.tr(
            "plugin.earthwork.recommend.reason",
            y,
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

    /**
     * 默认完整显示当前选中分区剖面；其它分区以折叠标题展示，可展开对比。
     */
    static void renderAllZoneProfiles(EarthworkUiContext ctx) {
        if (ctx == null || !ctx.previewManager().hasCachedPreview()) {
            return;
        }
        var profiles = ctx.sectionProfilesByZone();
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        String selectedId = ctx.selectedRegionId();
        java.util.List<String> order = new java.util.ArrayList<>(profiles.size());
        if (selectedId != null && !selectedId.isBlank() && profiles.containsKey(selectedId)) {
            order.add(selectedId);
        }
        for (String zoneId : profiles.keySet()) {
            if (!order.contains(zoneId)) {
                order.add(zoneId);
            }
        }
        for (String zoneId : order) {
            EarthworkSectionProfile profile = profiles.get(zoneId);
            if (profile == null || !profile.hasDrawableCutLine()) {
                continue;
            }
            boolean selected = zoneId.equals(selectedId);
            String zoneName = resolveZoneName(ctx, zoneId);
            if (selected) {
                renderProfile(ctx, profile, zoneName, true, true);
            } else {
                int[] elevRange = profileElevRange(profile);
                String collapsed = PlotI18n.tr(
                    "plugin.earthwork.profile.header_zone_collapsed",
                    zoneName,
                    elevRange[0],
                    elevRange[1]);
                if (ImGui.collapsingHeader(collapsed + "##earthwork_profile_" + zoneId)) {
                    renderProfile(ctx, profile, zoneName, false, false);
                }
            }
            ImGui.spacing();
        }
    }

    /** @return {elevMin, elevMax}；无站时退回 0,0 */
    private static int[] profileElevRange(EarthworkSectionProfile profile) {
        int elevMin = Integer.MAX_VALUE;
        int elevMax = Integer.MIN_VALUE;
        if (profile != null) {
            for (EarthworkSectionProfile.Station station : profile.stations()) {
                elevMin = Math.min(elevMin, Math.min(station.existingY(), station.designY()));
                elevMax = Math.max(elevMax, Math.max(station.existingY(), station.designY()));
            }
        }
        if (elevMin == Integer.MAX_VALUE) {
            return new int[] {0, 0};
        }
        return new int[] {elevMin, elevMax};
    }

    private static String resolveZoneName(EarthworkUiContext ctx, String zoneId) {
        GradingRegion region = ctx.project() != null ? ctx.project().getRegion(zoneId) : null;
        if (region != null && region.getName() != null && !region.getName().isBlank()) {
            return region.getName();
        }
        GradingZone zone = ctx.project() != null ? ctx.project().getZone(zoneId) : null;
        if (zone != null && zone.getName() != null && !zone.getName().isBlank()) {
            return zone.getName();
        }
        return zoneId;
    }

    static void renderProfile(
            EarthworkUiContext ctx,
            EarthworkSectionProfile profile,
            String zoneName,
            boolean selected) {
        renderProfile(ctx, profile, zoneName, selected, true);
    }

    static void renderProfile(
            EarthworkUiContext ctx,
            EarthworkSectionProfile profile,
            String zoneName,
            boolean selected,
            boolean showTitle) {
        if (showTitle) {
            ImGui.text(PlotI18n.tr(
                selected
                    ? "plugin.earthwork.profile.header_zone_selected"
                    : "plugin.earthwork.profile.header_zone",
                zoneName));
        }
        if (selected) {
            // 控件始终显示：即使截线未与分区相交，也要能继续旋转/平移
            renderProfileControls(ctx);
        }
        if (profile == null || profile.isEmpty()) {
            if (selected) {
                ImGui.textColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.earthwork.profile.no_intersection_hint"));
            }
            return;
        }
        float width = ImGui.getContentRegionAvailX();
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + PROFILE_CHART_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        int elevMin = Integer.MAX_VALUE;
        int elevMax = Integer.MIN_VALUE;
        double zoneAlongMin = Double.POSITIVE_INFINITY;
        double zoneAlongMax = Double.NEGATIVE_INFINITY;
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            elevMin = Math.min(elevMin, Math.min(station.existingY(), station.designY()));
            elevMax = Math.max(elevMax, Math.max(station.existingY(), station.designY()));
            if (station.zoned()) {
                double along = profile.axisDistanceExact(station);
                zoneAlongMin = Math.min(zoneAlongMin, along);
                zoneAlongMax = Math.max(zoneAlongMax, along);
            }
        }
        if (elevMin == elevMax) {
            elevMax = elevMin + 1;
        }
        float pad = 8f;
        float plotX0 = x0 + PROFILE_LEFT_AXIS;
        double chartMin = profile.chartAxisMin();
        double span = profile.chartAxisSpan();
        float innerW = Math.max(1f, x1 - plotX0 - pad);
        // 标高轴：顶/底标注
        drawList.addText(x0 + 4f, y0 + pad - 2f, COLOR_AXIS_LABEL, "Y" + elevMax);
        drawList.addText(x0 + 4f, y1 - pad - 12f, COLOR_AXIS_LABEL, "Y" + elevMin);
        drawList.addLine(plotX0 - 2f, y0 + pad, plotX0 - 2f, y1 - pad, COLOR_BORDER, 1f);

        // 区内范围浅底，突出挖填主体；两侧空白为截线外扩（区外对照）
        if (zoneAlongMax >= zoneAlongMin && span > 1e-6) {
            float zx0 = plotX0 + innerW * (float) ((zoneAlongMin - chartMin) / span);
            float zx1 = plotX0 + innerW * (float) ((zoneAlongMax - chartMin) / span);
            drawList.addRectFilled(zx0, y0 + 2f, zx1, y1 - 2f, ImColor.rgba(255, 255, 255, 18));
        }

        for (int i = 0; i < profile.stations().size() - 1; i++) {
            EarthworkSectionProfile.Station a = profile.stations().get(i);
            EarthworkSectionProfile.Station b = profile.stations().get(i + 1);
            float ax = plotX0 + innerW * (float) ((profile.axisDistanceExact(a) - chartMin) / span);
            float bx = plotX0 + innerW * (float) ((profile.axisDistanceExact(b) - chartMin) / span);
            boolean enterExit = a.zoned() != b.zoned()
                || (a.zoned() && b.zoned() && !java.util.Objects.equals(a.zoneId(), b.zoneId()));
            boolean gap = profile.shouldBreakPolyline(a, b);
            if (gap || enterExit) {
                float sepX = (ax + bx) * 0.5f;
                drawList.addLine(sepX, y0 + 4f, sepX, y1 - 4f, COLOR_ZONE_SEP, gap ? 1.5f : 1.2f);
            }
            float aExist = elevToY(a.existingY(), elevMin, elevMax, y0, y1, pad);
            float bExist = elevToY(b.existingY(), elevMin, elevMax, y0, y1, pad);
            float aDesign = elevToY(a.designY(), elevMin, elevMax, y0, y1, pad);
            float bDesign = elevToY(b.designY(), elevMin, elevMax, y0, y1, pad);
            boolean bothInZone = a.zoned() && b.zoned();
            int band = 0;
            if (bothInZone) {
                int cutSum = a.cut() + b.cut();
                int fillSum = a.fill() + b.fill();
                if (cutSum > 0 || fillSum > 0) {
                    band = cutSum >= fillSum ? COLOR_CUT_BAND : COLOR_FILL_BAND;
                }
            }
            if (band != 0) {
                drawList.addQuadFilled(ax, aExist, bx, bExist, bx, bDesign, ax, aDesign, band);
            }
            if (!gap) {
                boolean inZoneSeg = a.zoned() || b.zoned();
                drawList.addLine(
                    ax, aExist, bx, bExist,
                    inZoneSeg ? COLOR_EXISTING : COLOR_EXISTING_FAINT,
                    inZoneSeg ? 2.2f : 1.4f);
                drawList.addLine(
                    ax, aDesign, bx, bDesign,
                    inZoneSeg ? COLOR_DESIGN : COLOR_DESIGN_FAINT,
                    inZoneSeg ? 2.2f : 1.4f);
            }
        }
        for (EarthworkSectionProfile.Station station : profile.stations()) {
            if (!station.zoned()) {
                continue;
            }
            int cut = station.cut();
            int fill = station.fill();
            if (cut <= 0 && fill <= 0) {
                continue;
            }
            float sx = plotX0 + innerW * (float) ((profile.axisDistanceExact(station) - chartMin) / span);
            int color = cut >= fill ? COLOR_CUT : COLOR_FILL;
            drawList.addLine(
                sx,
                elevToY(Math.max(station.existingY(), station.designY()), elevMin, elevMax, y0, y1, pad),
                sx,
                elevToY(Math.min(station.existingY(), station.designY()), elevMin, elevMax, y0, y1, pad),
                color,
                2.0f);
        }

        // 悬停读数：取最近采样站
        ImGui.dummy(width, PROFILE_CHART_HEIGHT);
        if (ImGui.isItemHovered() && span > 1e-6 && innerW > 1f) {
            float mouseX = ImGui.getMousePosX();
            double along = chartMin + ((mouseX - plotX0) / innerW) * span;
            EarthworkSectionProfile.Station nearest = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (EarthworkSectionProfile.Station station : profile.stations()) {
                double d = Math.abs(profile.axisDistanceExact(station) - along);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = station;
                }
            }
            if (nearest != null && bestDist <= Math.max(1.25, span * 0.04)) {
                float markerX = plotX0 + innerW * (float) ((profile.axisDistanceExact(nearest) - chartMin) / span);
                drawList.addLine(markerX, y0 + 3f, markerX, y1 - 3f, COLOR_CURRENT, 1.0f);
                if (nearest.zoned()) {
                    ImGui.setTooltip(PlotI18n.tr(
                        "plugin.earthwork.profile.hover_zone",
                        nearest.existingY(),
                        nearest.designY(),
                        nearest.cut(),
                        nearest.fill()));
                } else {
                    ImGui.setTooltip(PlotI18n.tr(
                        "plugin.earthwork.profile.hover_exterior",
                        nearest.existingY()));
                }
            }
        }
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.profile.legend"));
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.profile.hover_hint"));
    }

    private static void renderProfileControls(EarthworkUiContext ctx) {
        if (ctx == null) {
            return;
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.horizontal"))) {
            ctx.setSectionHorizontal();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.vertical"))) {
            ctx.setSectionVertical();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.rotate_ccw"))) {
            ctx.rotateSectionDegrees(15.0);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.rotate_cw"))) {
            ctx.rotateSectionDegrees(-15.0);
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.nudge_view"))) {
            ctx.nudgeSectionBlocks(1.0);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.nudge_back"))) {
            ctx.nudgeSectionBlocks(-1.0);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.flip_view"))) {
            ctx.flipSectionView();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.earthwork.profile.reset_auto"))) {
            ctx.resetSectionCut();
        }
    }

    static void renderHeatmap(
            EarthworkUiContext ctx,
            EarthworkGenerationResult preview,
            DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return;
        }
        List<List<Vec2d>> zoneOutlines = heatmapZoneOutlines(ctx);
        EarthworkCutFillHeatmap.CanvasExtents extents = EarthworkCutFillHeatmap.withDisplayMargin(
            EarthworkCutFillHeatmap.canvasExtents(grid, zoneOutlines));
        if (extents == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.heatmap.header"));
        float availW = Math.max(40f, ImGui.getContentRegionAvailX());
        // 按分区画布包围盒比例排版，使示意与俯视布局一致
        float[] size = heatmapDisplaySize(availW, extents.aspectWidthOverHeight());
        float width = size[0];
        float height = size[1];
        float innerW = Math.max(1f, width - 8f);
        float innerH = Math.max(1f, height - 8f);
        int gridCols = Math.clamp(Math.round(innerW / 4f), 8, 64);
        int gridRows = Math.clamp(
            (int) Math.round(gridCols * (extents.spanY() / extents.spanX())),
            6,
            64);
        EarthworkCutFillHeatmap.Bucket[][] buckets =
            ctx.cutFillHeatmapCache().resolve(preview, grid, gridCols, gridRows, extents);
        if (buckets == null) {
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, COLOR_BG);
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
        drawHeatmapZoneOutlines(drawList, origin.x + 4f, origin.y + 4f, innerW, innerH, extents, zoneOutlines);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, COLOR_BORDER);
        ImGui.dummy(width, height);
        ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.earthwork.heatmap.legend"));
    }

    private static List<List<Vec2d>> heatmapZoneOutlines(EarthworkUiContext ctx) {
        List<List<Vec2d>> outlines = new ArrayList<>();
        if (ctx == null || ctx.project() == null) {
            return outlines;
        }
        EarthworkSite site = ctx.project().getActiveSite();
        if (site == null) {
            return outlines;
        }
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            List<Vec2d> outer = zone.getOuterPoints();
            if (outer != null && outer.size() >= 3) {
                outlines.add(outer);
            }
        }
        return outlines;
    }

    private static void drawHeatmapZoneOutlines(
            ImDrawList drawList,
            float originX,
            float originY,
            float innerW,
            float innerH,
            EarthworkCutFillHeatmap.CanvasExtents extents,
            List<List<Vec2d>> zoneOutlines) {
        if (drawList == null || extents == null || zoneOutlines == null || zoneOutlines.isEmpty()) {
            return;
        }
        double spanX = extents.spanX();
        double spanY = extents.spanY();
        for (List<Vec2d> outline : zoneOutlines) {
            if (outline == null || outline.size() < 3) {
                continue;
            }
            float prevX = Float.NaN;
            float prevY = Float.NaN;
            float firstX = Float.NaN;
            float firstY = Float.NaN;
            for (Vec2d point : outline) {
                if (point == null) {
                    continue;
                }
                float px = originX + (float) ((point.x - extents.minX()) / spanX * innerW);
                float py = originY + (float) ((point.y - extents.minY()) / spanY * innerH);
                if (!Float.isNaN(prevX)) {
                    drawList.addLine(prevX, prevY, px, py, COLOR_ZONE_OUTLINE, 1.25f);
                } else {
                    firstX = px;
                    firstY = py;
                }
                prevX = px;
                prevY = py;
            }
            if (!Float.isNaN(prevX) && !Float.isNaN(firstX)) {
                drawList.addLine(prevX, prevY, firstX, firstY, COLOR_ZONE_OUTLINE, 1.25f);
            }
        }
    }

    /**
     * 在面板可用宽度内按场地 X/Z 比例排版挖填平面示意框。
     * @return {@code {width, height}}
     */
    static float[] heatmapDisplaySize(float availWidth, double aspectWidthOverHeight) {
        double aspect = Double.isFinite(aspectWidthOverHeight) && aspectWidthOverHeight > 1e-6
            ? aspectWidthOverHeight
            : 1.0;
        float maxW = Math.max(HEATMAP_MIN_SIDE, Math.min(availWidth, HEATMAP_MAX_SIDE * Math.max(1f, (float) aspect)));
        float maxH = HEATMAP_MAX_SIDE;
        float width;
        float height;
        if (maxW / maxH > aspect) {
            height = Math.max(HEATMAP_MIN_SIDE, maxH);
            width = (float) (height * aspect);
        } else {
            width = Math.max(HEATMAP_MIN_SIDE, maxW);
            height = (float) (width / aspect);
        }
        if (height < HEATMAP_MIN_SIDE) {
            height = HEATMAP_MIN_SIDE;
            width = (float) (height * aspect);
            if (width > availWidth && availWidth >= HEATMAP_MIN_SIDE) {
                width = availWidth;
                height = (float) (width / aspect);
            }
        }
        if (width > availWidth && availWidth > 0f) {
            width = availWidth;
            height = (float) (width / aspect);
        }
        return new float[] {Math.max(1f, width), Math.max(1f, height)};
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
