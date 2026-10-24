package com.plot.plugin.road;

import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.profile.ProfileChartIntersectionSupport;
import com.plot.plugin.road.profile.ProfileChartLayout;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;

/**
 * 道路纵断面预览（ImGui 绘制）。
 *
 * <p>纵断面编辑器主路径已迁移至道路级 canonical station，请使用
 * {@link com.plot.plugin.road.profile.RoadProfileChartRenderer} 与
 * {@link com.plot.plugin.road.profile.RoadProfileChartData}。
 * 本类中基于 edge-local {@code profileDistances} / {@code localDistance} 的
 * {@code render*} 方法已废弃，仅保留供过渡与单测。
 */
public final class RoadLongitudinalProfileRenderer {
    public static final float DEFAULT_PREVIEW_HEIGHT = 120f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_AXIS = 0xFF888888;
    private static final int COLOR_GROUND = 0xFF8B5A2B;
    private static final int COLOR_GUIDE = 0xFF4DA3FF;
    private static final int COLOR_TARGET = 0xFFB0B0B0;
    private static final int COLOR_DESIGN = 0xFF5FD35F;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_CONTROL = 0xFFFFC04D;
    private static final int COLOR_CONTROL_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_CONTROL_INVALID = PluginUiColors.ERROR;
    private static final int COLOR_INTERSECTION_WARNING = 0xFFFF5252;
    private static final int COLOR_FLAT_CURRENT = 0xFF66D9EF;
    private static final int COLOR_FLAT_SUGGESTED = 0xFFFFB84D;

    /** @deprecated Use {@link ProfileChartInteraction.CurveHandle}. */
    @Deprecated
    public record CurveHandle(
            int pviIndex,
            double localDistance,
            double elevation,
            boolean leftSide) { }

    /** @deprecated Use {@link ProfileChartInteraction.ControlInteraction}. */
    @Deprecated
    public record ControlInteraction(
            int selectedPviIndex,
            int activePviIndex,
            Double draggedElevation,
            Double draggedLocalDistance,
            boolean dragStarted,
            boolean dragFinished,
            int hoveredIntersectionIndex,
            int selectedIntersectionIndex,
            int activeIntersectionDragIndex,
            IntersectionDragTarget activeIntersectionDragTarget,
            Double draggedIntersectionElevation,
            boolean intersectionDragStarted,
            boolean intersectionDragFinished,
            boolean addPointRequested,
            Double addPointLocalDistance,
            Double addPointElevation,
            int contextMenuPviIndex,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            CurveHandleSide activeCurveHandle,
            Double draggedCurveLength,
            boolean curveHandleDragStarted,
            boolean curveHandleDragFinished) {

        public enum IntersectionDragTarget {
            NONE,
            CURRENT,
            OTHER,
            SHARED
        }

        public enum CurveHandleSide {
            NONE,
            LEFT,
            RIGHT
        }

        public ControlInteraction(
                int selectedPviIndex,
                int activePviIndex,
                Double draggedElevation,
                Double draggedLocalDistance,
                boolean dragStarted,
                boolean dragFinished) {
            this(selectedPviIndex, activePviIndex, draggedElevation, draggedLocalDistance,
                dragStarted, dragFinished, -1, -1, -1, IntersectionDragTarget.NONE, null, false, false,
                false, null, null, -1, -1, 0f, 0f, -1, CurveHandleSide.NONE, null, false, false);
        }

        public ControlInteraction(
                int selectedPviIndex,
                int activePviIndex,
                Double draggedElevation,
                Double draggedLocalDistance,
                boolean dragStarted,
                boolean dragFinished,
                int hoveredIntersectionIndex,
                int selectedIntersectionIndex) {
            this(selectedPviIndex, activePviIndex, draggedElevation, draggedLocalDistance,
                dragStarted, dragFinished, hoveredIntersectionIndex, selectedIntersectionIndex,
                -1, IntersectionDragTarget.NONE, null, false, false,
                false, null, null, -1, -1, 0f, 0f, -1, CurveHandleSide.NONE, null, false, false);
        }
    }

    private RoadLongitudinalProfileRenderer() {
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer}。 */
    @Deprecated
    public static void render(RoadGenerationResult result) {
        render(result, true);
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer}。 */
    @Deprecated
    public static void render(RoadGenerationResult result, boolean showTitle) {
        render(result, showTitle, null);
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer}。 */
    @Deprecated
    public static void render(
            RoadGenerationResult result,
            boolean showTitle,
            VerticalAlignmentProfileOverlay designOverlay) {
        render(result, showTitle, designOverlay, DEFAULT_PREVIEW_HEIGHT);
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer}。 */
    @Deprecated
    public static void render(
            RoadGenerationResult result,
            boolean showTitle,
            VerticalAlignmentProfileOverlay designOverlay,
            float chartHeight) {
        if (result == null || !result.hasProfileData()) {
            return;
        }

        if (showTitle) {
            ImGui.text(PlotI18n.tr("plugin.road.longitudinal_profile"));
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + chartHeight;

        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        PlotRange range = plotRange(
            result, designOverlay, List.of(), List.of(), FlatElevationProfileOverlay.EMPTY, 1.0);
        drawProfile(
            drawList,
            result.profileDistances,
            result.profileGroundHeights,
            result.profileGuideLine,
            result.profileBuildHeights,
            designOverlay,
            FlatElevationProfileOverlay.EMPTY,
            x0,
            y0,
            width,
            chartHeight,
            range);

        ImGui.dummy(width, chartHeight);
        ImGui.textColored(COLOR_GROUND, "■ " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(COLOR_GUIDE, "--- " + PlotI18n.tr("plugin.road.profile_guide"));
        ImGui.sameLine();
        ImGui.textColored(COLOR_TARGET, "■ " + PlotI18n.tr("plugin.road.profile_target"));
        if (designOverlay != null && !designOverlay.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(COLOR_DESIGN, "■ " + PlotI18n.tr("plugin.road.profile_design"));
        }
    }

    /**
     * 只读纵剖面概览：地形/设计线 + 交叉点标记，不含可拖动控制点。
     *
     * @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer#renderOverview}。
     */
    @Deprecated
    public static void renderOverview(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<RoadProfileIntersection> intersections,
            float chartHeight) {
        renderOverview(result, designOverlay, intersections, chartHeight, FlatElevationProfileOverlay.EMPTY);
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer#renderOverview}。 */
    @Deprecated
    public static void renderOverview(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        renderOverview(result, designOverlay, intersections, chartHeight, flatOverlay, 1.0);
    }

    /** @deprecated Edge-local 渲染已废弃，请使用 {@link com.plot.plugin.road.profile.RoadProfileChartRenderer#renderOverview}。 */
    @Deprecated
    public static void renderOverview(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            double geometryToProfileScale) {
        if (result == null || !result.hasProfileData()) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        drawList.addRectFilled(x0, y0, x0 + width, y0 + chartHeight, COLOR_BG);
        drawList.addRect(x0, y0, x0 + width, y0 + chartHeight, COLOR_BORDER);
        PlotRange range = plotRange(
            result, designOverlay, List.of(), intersections, flatOverlay, geometryToProfileScale);
        drawProfile(drawList, result.profileDistances, result.profileGroundHeights,
            result.profileGuideLine, result.profileBuildHeights, designOverlay, flatOverlay,
            x0, y0, width, chartHeight, range);
        ProfileChartIntersectionSupport.drawIntersectionMarkersEdgeLocal(
            drawList,
            intersections,
            -1,
            range.maxDistance(),
            range.geometryToProfileScale(),
            range.minHeight(),
            range.maxHeight(),
            x0,
            y0,
            width,
            chartHeight,
            ProfileChartRenderMode.OVERVIEW);
        ImGui.invisibleButton("##road_profile_overview_surface", width, chartHeight);
        if (ImGui.isItemHovered()) {
            renderHoveredIntersectionTooltip(
                intersections,
                range,
                x0,
                y0,
                width,
                chartHeight,
                ImGui.getMousePosX(),
                ImGui.getMousePosY());
        }
    }

    private record PlotRange(
            double maxDistance,
            int minHeight,
            int maxHeight,
            double geometryToProfileScale) {

        PlotRange {
            if (geometryToProfileScale <= 0.0 || !Double.isFinite(geometryToProfileScale)) {
                geometryToProfileScale = 1.0;
            }
        }

        double chartDistance(double geometryLocal) {
            return geometryLocal * geometryToProfileScale;
        }
    }

    private static PlotRange plotRange(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay overlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            double geometryToProfileScale) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (List<Integer> values : List.of(
                result.profileGroundHeights, result.profileGuideLine, result.profileBuildHeights)) {
            for (int value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (overlay != null) {
            for (double value : overlay.elevations()) {
                min = Math.min(min, (int) Math.floor(value));
                max = Math.max(max, (int) Math.ceil(value));
            }
        }
        if (controls != null) {
            for (VerticalProfileControlPoints.ControlPoint point : controls) {
                min = Math.min(min, (int) Math.floor(point.elevation()));
                max = Math.max(max, (int) Math.ceil(point.elevation()));
            }
        }
        if (intersections != null) {
            for (RoadProfileIntersection intersection : intersections) {
                min = Math.min(min, (int) Math.floor(intersection.currentRoadElevation()));
                min = Math.min(min, (int) Math.floor(intersection.otherRoadElevation()));
                max = Math.max(max, (int) Math.ceil(intersection.currentRoadElevation()));
                max = Math.max(max, (int) Math.ceil(intersection.otherRoadElevation()));
            }
        }
        int[] flatRange = applyFlatElevationToRange(flatOverlay, min, max);
        min = flatRange[0];
        max = flatRange[1];
        if (min == Integer.MAX_VALUE) {
            min = 62;
            max = 66;
        } else if (min == max) {
            min -= 2;
            max += 2;
        } else {
            min--;
            max++;
        }
        return new PlotRange(result.profileDistances.getLast(), min, max, geometryToProfileScale);
    }

    /** @deprecated Use {@link ProfileChartIntersectionSupport#AT_GRADE_ELEVATION_TOLERANCE}. */
    @Deprecated
    static final double AT_GRADE_ELEVATION_TOLERANCE =
        ProfileChartIntersectionSupport.AT_GRADE_ELEVATION_TOLERANCE;

    static boolean atGradeElevationConflict(RoadProfileIntersection intersection) {
        return ProfileChartIntersectionSupport.atGradeElevationConflict(intersection);
    }

    static double sharedAtGradeElevation(RoadProfileIntersection intersection) {
        return ProfileChartIntersectionSupport.sharedAtGradeElevation(intersection);
    }

    static boolean otherRoadAboveCurrent(RoadProfileIntersection intersection) {
        return ProfileChartIntersectionSupport.otherRoadAboveCurrent(intersection);
    }

    /** @deprecated Use {@link ProfileChartIntersectionSupport#drawIntersectionMarkers}. */
    @Deprecated
    public static void drawIntersectionMarkersRoad(
            ImDrawList drawList,
            List<RoadProfileIntersection> intersections,
            int selectedIndex,
            ProfileChartLayout layout,
            double totalStation,
            double minElevation,
            double maxElevation) {
        drawIntersectionMarkersRoad(
            drawList, intersections, selectedIndex, layout,
            totalStation, minElevation, maxElevation, ProfileChartRenderMode.EDITOR);
    }

    /** @deprecated Use {@link ProfileChartIntersectionSupport#drawIntersectionMarkers}. */
    @Deprecated
    public static void drawIntersectionMarkersRoad(
            ImDrawList drawList,
            List<RoadProfileIntersection> intersections,
            int selectedIndex,
            ProfileChartLayout layout,
            double totalStation,
            double minElevation,
            double maxElevation,
            ProfileChartRenderMode mode) {
        ProfileChartIntersectionSupport.drawIntersectionMarkers(
            drawList, intersections, selectedIndex, layout,
            totalStation, minElevation, maxElevation, mode);
    }

    /** @deprecated Use {@link ProfileChartIntersectionSupport#hitIntersection}. */
    @Deprecated
    public static IntersectionHit hitIntersectionRoad(
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            com.plot.plugin.road.profile.RoadProfilePlotRange range,
            float mouseX,
            float mouseY) {
        return IntersectionHit.fromCanonical(
            ProfileChartIntersectionSupport.hitIntersection(
                intersections, layout, range, mouseX, mouseY));
    }

    private static void renderHoveredIntersectionTooltip(
            List<RoadProfileIntersection> intersections,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height,
            float mouseX,
            float mouseY) {
        IntersectionHit hit = IntersectionHit.fromCanonical(
            ProfileChartIntersectionSupport.hitIntersectionEdgeLocal(
                intersections,
                range.maxDistance(),
                range.geometryToProfileScale(),
                range.minHeight(),
                range.maxHeight(),
                x0,
                y0,
                width,
                height,
                mouseX,
                mouseY));
        if (hit == null || hit.index() < 0 || hit.index() >= intersections.size()) {
            return;
        }
        RoadProfileIntersection intersection = intersections.get(hit.index());
        ImGui.beginTooltip();
        ImGui.text(intersection.otherRoadLabel());
        if (intersection.gradeSeparated()) {
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_current_elevation",
                String.format("%.1f", intersection.currentRoadElevation())));
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_other_elevation",
                String.format("%.1f", intersection.otherRoadElevation())));
            String relation = otherRoadAboveCurrent(intersection)
                ? PlotI18n.tr("plugin.road.profile_intersection_other_over")
                : PlotI18n.tr("plugin.road.profile_intersection_current_over");
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_relation", relation));
        } else if (atGradeElevationConflict(intersection)) {
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_current_elevation",
                String.format("%.1f", intersection.currentRoadElevation())));
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_other_elevation",
                String.format("%.1f", intersection.otherRoadElevation())));
            ImGui.textColored(
                COLOR_INTERSECTION_WARNING,
                PlotI18n.tr("plugin.road.profile_intersection_at_grade_conflict"));
        } else {
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_at_grade_elevation",
                String.format("%.1f", sharedAtGradeElevation(intersection))));
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_at_grade"));
        }
        ImGui.endTooltip();
    }

    /** @deprecated Use {@link ProfileChartInteraction.IntersectionHit}. */
    @Deprecated
    public record IntersectionHit(int index, ControlInteraction.IntersectionDragTarget target) {
        static IntersectionHit fromCanonical(ProfileChartInteraction.IntersectionHit hit) {
            if (hit == null) {
                return null;
            }
            return new IntersectionHit(
                hit.index(),
                ControlInteraction.IntersectionDragTarget.valueOf(hit.target().name()));
        }
    }

    private static void drawControlPoints(
            ImDrawList drawList,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selected,
            double maxGrade,
            PlotRange range,
            float x0, float y0, float width, float height) {
        if (controls == null) return;
        float padding = 10f;
        for (VerticalProfileControlPoints.ControlPoint point : controls) {
            float x = toPlotX(
                range.chartDistance(point.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float y = toPlotY((int) Math.round(point.elevation()), range.minHeight(), range.maxHeight(),
                y0 + padding, height - 2 * padding);
            int color = VerticalProfileControlPoints.exceedsGradeLimit(point, maxGrade)
                ? COLOR_CONTROL_INVALID
                : point.pviIndex() == selected ? COLOR_CONTROL_SELECTED : COLOR_CONTROL;
            float radius = point.sharedJunction() ? 6f : point.endpoint() ? 5f : 4f;
            drawList.addCircleFilled(x, y, radius, color);
            drawList.addCircle(x, y, radius + 1f, COLOR_BG, 12, 1.5f);
        }
    }

    /** @deprecated Edge-local 绘制辅助，仅供本类废弃路径内部使用。 */
    @Deprecated
    static void drawProfile(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> guideLine,
            List<Integer> targetHeights,
            float x0,
            float y0,
            float width,
            float height) {
        drawProfile(drawList, distances, groundHeights, guideLine, targetHeights, null, x0, y0, width, height);
    }

    /** @deprecated Edge-local 绘制辅助，仅供本类废弃路径内部使用。 */
    @Deprecated
    static void drawProfile(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> guideLine,
            List<Integer> targetHeights,
            VerticalAlignmentProfileOverlay designOverlay,
            float x0,
            float y0,
            float width,
            float height) {
        drawProfile(
            drawList,
            distances,
            groundHeights,
            guideLine,
            targetHeights,
            designOverlay,
            FlatElevationProfileOverlay.EMPTY,
            x0,
            y0,
            width,
            height);
    }

    /** @deprecated Edge-local 绘制辅助，仅供本类废弃路径内部使用。 */
    @Deprecated
    static void drawProfile(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> guideLine,
            List<Integer> targetHeights,
            VerticalAlignmentProfileOverlay designOverlay,
            FlatElevationProfileOverlay flatOverlay,
            float x0,
            float y0,
            float width,
            float height) {
        if (distances == null || distances.isEmpty()) {
            return;
        }
        RoadGenerationResult snapshot = new RoadGenerationResult(distances.getLast());
        snapshot.profileDistances = distances;
        snapshot.profileGroundHeights = groundHeights;
        snapshot.profileGuideLine = guideLine;
        snapshot.profileBuildHeights = new java.util.ArrayList<>(targetHeights);
        PlotRange range = plotRange(
            snapshot, designOverlay, List.of(), List.of(), flatOverlay, 1.0);
        drawProfile(
            drawList,
            distances,
            groundHeights,
            guideLine,
            targetHeights,
            designOverlay,
            flatOverlay,
            x0,
            y0,
            width,
            height,
            range);
    }

    /** @deprecated Edge-local 绘制辅助，仅供本类废弃路径内部使用。 */
    @Deprecated
    static void drawProfile(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> guideLine,
            List<Integer> targetHeights,
            VerticalAlignmentProfileOverlay designOverlay,
            FlatElevationProfileOverlay flatOverlay,
            float x0,
            float y0,
            float width,
            float height,
            PlotRange range) {
        if (distances == null || distances.isEmpty() || range == null) {
            return;
        }

        float padding = 10f;
        float plotX0 = x0 + padding;
        float plotY0 = y0 + padding;
        float plotX1 = x0 + width - padding;
        float plotY1 = y0 + height - padding;
        float plotWidth = plotX1 - plotX0;
        float plotHeight = plotY1 - plotY0;
        if (plotWidth <= 1f || plotHeight <= 1f) {
            return;
        }

        double maxDistance = range.maxDistance();
        int minHeight = range.minHeight();
        int maxHeight = range.maxHeight();
        double geometryToProfileScale = range.geometryToProfileScale();

        drawList.addLine(plotX0, plotY1, plotX1, plotY1, COLOR_AXIS, 1f);
        drawList.addLine(plotX0, plotY0, plotX0, plotY1, COLOR_AXIS, 1f);

        drawPolyline(drawList, distances, groundHeights, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_GROUND, 1.8f, false);
        drawPolyline(drawList, distances, guideLine, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_GUIDE, 1.4f, true);

        List<Double> targetDistances = targetHeights.size() == distances.size()
            ? distances
            : buildTargetDistances(distances, targetHeights.size());
        drawPolyline(drawList, targetDistances, targetHeights, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_TARGET, 2.4f, false);

        if (designOverlay != null && !designOverlay.isEmpty()) {
            double scale = geometryToProfileScale > 0.0 && Double.isFinite(geometryToProfileScale)
                ? geometryToProfileScale
                : 1.0;
            java.util.ArrayList<Double> designDistances =
                new java.util.ArrayList<>(designOverlay.distances().size());
            for (double distance : designOverlay.distances()) {
                designDistances.add(distance * scale);
            }
            drawPolyline(
                drawList,
                designDistances,
                designOverlay.heights(),
                maxDistance,
                minHeight,
                maxHeight,
                plotX0,
                plotY0,
                plotWidth,
                plotHeight,
                COLOR_DESIGN,
                2.0f,
                false);
        }

        if (flatOverlay != null && !flatOverlay.isEmpty()) {
            if (flatOverlay.showSuggested()) {
                drawHorizontalReferenceLine(
                    drawList,
                    flatOverlay.suggestedElevation(),
                    minHeight,
                    maxHeight,
                    plotX0,
                    plotY0,
                    plotWidth,
                    plotHeight,
                    COLOR_FLAT_SUGGESTED,
                    2.4f,
                    14f,
                    6f);
            }
            if (flatOverlay.showCurrent()) {
                drawHorizontalReferenceLine(
                    drawList,
                    flatOverlay.currentElevation(),
                    minHeight,
                    maxHeight,
                    plotX0,
                    plotY0,
                    plotWidth,
                    plotHeight,
                    COLOR_FLAT_CURRENT,
                    1.6f,
                    8f,
                    8f);
            }
        }

        drawList.addText(plotX0, plotY0 - 2f, COLOR_LABEL, "Y=" + maxHeight);
        drawList.addText(plotX0, plotY1 - ImGui.getTextLineHeight(), COLOR_LABEL, "Y=" + minHeight);
        drawList.addText(plotX1 - 36f, plotY1 + 2f, COLOR_LABEL, String.format("%.0fm", maxDistance));
    }

    private static int[] applyFlatElevationToRange(
            FlatElevationProfileOverlay flatOverlay,
            int min,
            int max) {
        if (flatOverlay == null || flatOverlay.isEmpty()) {
            return new int[] {min, max};
        }
        if (flatOverlay.showSuggested()) {
            min = Math.min(min, flatOverlay.suggestedElevation());
            max = Math.max(max, flatOverlay.suggestedElevation());
        }
        if (flatOverlay.showCurrent()) {
            min = Math.min(min, flatOverlay.currentElevation());
            max = Math.max(max, flatOverlay.currentElevation());
        }
        return new int[] {min, max};
    }

    private static void drawHorizontalReferenceLine(
            ImDrawList drawList,
            int elevation,
            int minHeight,
            int maxHeight,
            float plotX0,
            float plotY0,
            float plotWidth,
            float plotHeight,
            int color,
            float thickness,
            float dashLength,
            float gapLength) {
        float y = toPlotY(elevation, minHeight, maxHeight, plotY0, plotHeight);
        float x = plotX0;
        float endX = plotX0 + plotWidth;
        while (x < endX) {
            float segmentEnd = Math.min(endX, x + dashLength);
            drawList.addLine(x, y, segmentEnd, y, color, thickness);
            x = segmentEnd + gapLength;
        }
    }

    private static List<Double> buildTargetDistances(List<Double> distances, int targetCount) {
        if (targetCount <= 1 || distances.isEmpty()) {
            return distances;
        }
        double total = distances.getLast();
        java.util.ArrayList<Double> targetDistances = new java.util.ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            double ratio = (double) i / (targetCount - 1);
            targetDistances.add(total * ratio);
        }
        return targetDistances;
    }

    private static void drawPolyline(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> heights,
            double maxDistance,
            int minHeight,
            int maxHeight,
            float plotX0,
            float plotY0,
            float plotWidth,
            float plotHeight,
            int color,
            float thickness,
            boolean dashed) {
        if (distances.size() < 2 || heights.size() < 2) {
            return;
        }

        float previousX = 0f;
        float previousY = 0f;
        boolean hasPrevious = false;
        for (int i = 0; i < Math.min(distances.size(), heights.size()); i++) {
            float x = toPlotX(distances.get(i), maxDistance, plotX0, plotWidth);
            float y = toPlotY(heights.get(i), minHeight, maxHeight, plotY0, plotHeight);
            if (hasPrevious) {
                if (!dashed || i % 2 == 0) {
                    drawList.addLine(previousX, previousY, x, y, color, thickness);
                }
            }
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
    }

    private static float toPlotX(double distance, double maxDistance, float plotX0, float plotWidth) {
        if (maxDistance <= 1e-9) {
            return plotX0;
        }
        return plotX0 + (float) (distance / maxDistance) * plotWidth;
    }

    private static float toPlotY(int height, int minHeight, int maxHeight, float plotY0, float plotHeight) {
        if (maxHeight <= minHeight) {
            return plotY0 + plotHeight * 0.5f;
        }
        float ratio = (height - minHeight) / (float) (maxHeight - minHeight);
        return plotY0 + plotHeight * (1f - ratio);
    }
}
