package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.ui.PluginUiColors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseCursor;

import java.util.List;

/**
 * 道路级纵断面图渲染与交互（canonical road station 0…L）。
 */
public final class RoadProfileChartRenderer {

    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_GROUND = ProfileChartSeriesStyle.RAW_TERRAIN;
    private static final int COLOR_DESIGN = ProfileChartSeriesStyle.DESIGN_PROFILE;
    private static final int COLOR_BUILD = ProfileChartSeriesStyle.BUILD_PROFILE;
    private static final int COLOR_LABEL = 0x88AAAAAA;
    private static final int COLOR_WORLD_BOUND = 0x66FFB84D;
    private static final int COLOR_GRID = 0x18FFFFFF;
    private static final int COLOR_CONTROL = 0xFFFFC04D;
    private static final int COLOR_CONTROL_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_CONTROL_INVALID = PluginUiColors.ERROR;
    private static final int COLOR_ENDPOINT = 0xFFFFE066;
    private static final int COLOR_JUNCTION_FIXED = 0xFF9AA0A6;
    private static final float DRAG_THRESHOLD_PX = 5f;
    private static final float DASH_LENGTH = 6f;
    private static final float DASH_GAP = 4f;

    private RoadProfileChartRenderer() {
    }

    public static void renderOverview(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        renderOverview(
            chart, design, intersections, chartHeight, flatOverlay, ProfileChartRenderMode.OVERVIEW, null);
    }

    public static void renderOverview(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode mode) {
        renderOverview(chart, design, intersections, chartHeight, flatOverlay, mode, null);
    }

    public static void renderOverview(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode mode,
            RoadVerticalMode verticalMode) {
        if (chart == null || !chart.hasProfileData()) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(
            origin.x, origin.y, width, chartHeight, mode);
        RoadProfilePlotRange range = plotRange(chart, design, List.of(), intersections, flatOverlay);
        drawBackground(drawList, layout);
        drawAxesAndGrid(drawList, layout, range, mode);
        drawSeries(
            drawList, layout, range, chart, design, flatOverlay, mode,
            ProfileChartGuideSemantics.fromVerticalMode(verticalMode));
        RoadLongitudinalProfileRenderer.drawIntersectionMarkersRoad(
            drawList, intersections, -1, layout,
            range.totalStation(), range.minElevation(), range.maxElevation(), mode);
        ImGui.invisibleButton("##road_profile_overview_surface", width, chartHeight);
    }

    public static RoadLongitudinalProfileRenderer.ControlInteraction renderInteractive(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveHandle) {
        return renderInteractive(
            chart, design, controls, selectedPviIndex, activePviIndex, maxGradePercent,
            intersections, selectedIntersectionIndex, chartHeight,
            activeIntersectionDragIndex, activeIntersectionDragTarget, flatOverlay,
            curveHandles, pendingPviIndex, pendingClickX, pendingClickY,
            activeCurveHandlePvi, activeCurveHandle, null);
    }

    public static RoadLongitudinalProfileRenderer.ControlInteraction renderInteractive(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveHandle,
            ProfileRenderCache renderCache) {
        return renderInteractive(
            chart, design, controls, selectedPviIndex, activePviIndex, maxGradePercent,
            intersections, selectedIntersectionIndex, chartHeight,
            activeIntersectionDragIndex, activeIntersectionDragTarget, flatOverlay,
            curveHandles, pendingPviIndex, pendingClickX, pendingClickY,
            activeCurveHandlePvi, activeCurveHandle, ProfileChartRenderMode.EDITOR, renderCache, null, null, null);
    }

    public static RoadLongitudinalProfileRenderer.ControlInteraction renderInteractive(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveHandle,
            ProfileChartRenderMode mode,
            ProfileRenderCache renderCache,
            RoadVerticalMode verticalMode,
            RoadElevationBounds elevationBounds,
            RoadVerticalAlignment insertAlignment) {
        if (chart == null || !chart.hasProfileData()) {
            return new RoadLongitudinalProfileRenderer.ControlInteraction(
                selectedPviIndex, -1, null, null, false, false);
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return new RoadLongitudinalProfileRenderer.ControlInteraction(
                selectedPviIndex, activePviIndex, null, null, false, false);
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(
            origin.x, origin.y, width, chartHeight, mode);
        RoadProfilePlotRange range = resolvePlotRange(
            renderCache, chart, design, controls, intersections, flatOverlay);
        drawBackground(drawList, layout);
        drawAxesAndGrid(drawList, layout, range, mode);
        drawSeries(
            drawList, layout, range, chart, design, flatOverlay, mode,
            ProfileChartGuideSemantics.fromVerticalMode(verticalMode));
        if (mode == ProfileChartRenderMode.EDITOR) {
            drawWorldElevationBounds(drawList, layout, range, elevationBounds);
        }
        drawRoadControlPoints(drawList, controls, selectedPviIndex, maxGradePercent, layout, range);
        drawRoadCurveHandles(
            drawList, controls, curveHandles, activeCurveHandlePvi, activeCurveHandle, layout, range);
        RoadLongitudinalProfileRenderer.drawIntersectionMarkersRoad(
            drawList, intersections, selectedIntersectionIndex, layout,
            range.totalStation(), range.minElevation(), range.maxElevation(), mode);
        ImGui.invisibleButton("##road_profile_control_surface", width, chartHeight);

        return handleInteraction(
            chart, controls, intersections, layout, range, selectedPviIndex, activePviIndex,
            maxGradePercent,
            selectedIntersectionIndex, activeIntersectionDragIndex, activeIntersectionDragTarget,
            curveHandles, pendingPviIndex, pendingClickX, pendingClickY,
            activeCurveHandlePvi, activeCurveHandle, elevationBounds, insertAlignment);
    }

    private static RoadLongitudinalProfileRenderer.ControlInteraction handleInteraction(
            RoadProfileChartData chart,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            int selectedIntersectionIndex,
            int activeIntersectionDragIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveHandle,
            RoadElevationBounds elevationBounds,
            RoadVerticalAlignment insertAlignment) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        int selected = selectedPviIndex;
        int active = activePviIndex;
        int pending = pendingPviIndex;
        float pendingX = pendingClickX;
        float pendingY = pendingClickY;
        boolean started = false;
        boolean finished = false;
        Double elevation = null;
        Double roadStation = null;
        int hoveredIntersection = -1;
        int selectedIntersection = selectedIntersectionIndex;
        int activeIntersectionDrag = activeIntersectionDragIndex;
        RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionTarget =
            activeIntersectionDragTarget != null
                ? activeIntersectionDragTarget
                : RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
        Double intersectionElevation = null;
        boolean intersectionStarted = false;
        boolean intersectionFinished = false;
        boolean addPointRequested = false;
        Double addPointRoadStation = null;
        Double addPointElevation = null;
        int contextMenuPvi = -1;
        int activeCurvePvi = activeCurveHandlePvi;
        RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveSide =
            activeCurveHandle != null ? activeCurveHandle
                : RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.NONE;
        Double draggedCurveLength = null;
        boolean curveHandleStarted = false;
        boolean curveHandleFinished = false;

        if (ImGui.isItemHovered()) {
            RoadLongitudinalProfileRenderer.IntersectionHit hoveredHit =
                ProfileChartHitTester.hitIntersection(
                    intersections, layout, range, mouseX, mouseY);
            if (hoveredHit != null) {
                hoveredIntersection = hoveredHit.index();
            }
            ProfileControlPoint hoveredPvi = ProfileChartHitTester.hitPvi(
                controls, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_PVI_HIT_RADIUS_PX);
            if (hoveredPvi != null && active < 0 && activeCurvePvi < 0 && activeIntersectionDrag < 0) {
                ProfileChartInteractionTooltips.renderPviHover(hoveredPvi, maxGradePercent);
            }
            updateHoverCursor(controls, layout, range, mouseX, mouseY, active, activeIntersectionDrag);
        }

        if (activeCurvePvi >= 0 && ImGui.isMouseDown(0)) {
            ProfileControlPoint pvi = findControlPoint(controls, activeCurvePvi);
            if (pvi != null) {
                double handleStation = layout.stationAtMouseX(mouseX, range.totalStation());
                double halfLength = Math.abs(pvi.roadStation() - handleStation);
                draggedCurveLength = Math.max(0.0, halfLength * 2.0);
            }
        }
        if (activeCurvePvi >= 0 && ImGui.isMouseReleased(0)) {
            curveHandleFinished = true;
            activeCurvePvi = -1;
            activeCurveSide = RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.NONE;
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            ProfileChartHitTester.CurveHandleHit curveHit = ProfileChartHitTester.hitCurveHandle(
                curveHandles, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_CURVE_HANDLE_HIT_RADIUS_PX);
            RoadLongitudinalProfileRenderer.IntersectionHit intersectionHit =
                ProfileChartHitTester.hitIntersection(intersections, layout, range, mouseX, mouseY);
            int nearest = ProfileChartHitTester.hitPviIndex(
                controls, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_PVI_HIT_RADIUS_PX);
            if (nearest < 0 && curveHit == null
                    && (intersectionHit == null
                        || intersectionHit.target()
                            == RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE)) {
                double insertStation = layout.stationAtMouseX(mouseX, range.totalStation());
                if (VerticalProfileControlPoints.canInsertAt(
                        insertAlignment, insertStation, range.totalStation())) {
                    addPointRequested = true;
                    addPointRoadStation = insertStation;
                    addPointElevation = clampElevation(
                        layout.elevationAtMouseY(mouseY, range.minElevation(), range.maxElevation()),
                        elevationBounds);
                }
                pending = -1;
            }
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
            ProfileControlPoint point = ProfileChartHitTester.hitPvi(
                controls, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_PVI_HIT_RADIUS_PX);
            if (ProfileChartHitTester.canOpenPviContextMenu(point)) {
                contextMenuPvi = point.pviIndex();
                ImGui.openPopup("##road_profile_pvi_context");
            }
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
            RoadLongitudinalProfileRenderer.IntersectionHit hit =
                ProfileChartHitTester.hitIntersection(intersections, layout, range, mouseX, mouseY);
            ProfileChartHitTester.CurveHandleHit curveHit = ProfileChartHitTester.hitCurveHandle(
                curveHandles, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_CURVE_HANDLE_HIT_RADIUS_PX);
            int nearest = ProfileChartHitTester.hitPviIndex(
                controls, layout, range, mouseX, mouseY,
                ProfileChartHitTester.DEFAULT_PVI_HIT_RADIUS_PX);
            if (hit != null && hit.target()
                    != RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE) {
                activeIntersectionDrag = hit.index();
                activeIntersectionTarget = hit.target();
                selectedIntersection = hit.index();
                intersectionStarted = true;
                selected = -1;
                pending = -1;
            } else if (curveHit != null) {
                activeCurvePvi = curveHit.pviIndex();
                activeCurveSide = curveHit.side();
                curveHandleStarted = true;
                selected = curveHit.pviIndex();
                selectedIntersection = -1;
                pending = -1;
            } else if (nearest >= 0 && isEditable(controls, nearest)) {
                selected = nearest;
                pending = nearest;
                pendingX = mouseX;
                pendingY = mouseY;
                selectedIntersection = -1;
            } else if (hit != null) {
                selectedIntersection = hit.index();
                selected = -1;
                pending = -1;
            } else {
                selected = -1;
                pending = -1;
                selectedIntersection = -1;
            }
        }

        if (pending >= 0 && active < 0 && activeCurvePvi < 0 && ImGui.isMouseDown(0)) {
            double dx = mouseX - pendingX;
            double dy = mouseY - pendingY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX
                    && isEditable(controls, pending)) {
                active = pending;
                started = true;
            }
        }
        if (pending >= 0 && active < 0 && ImGui.isMouseReleased(0)) {
            pending = -1;
        }

        if (activeIntersectionDrag >= 0 && ImGui.isMouseDown(0)) {
            intersectionElevation = clampElevation(
                layout.elevationAtMouseY(mouseY, range.minElevation(), range.maxElevation()),
                elevationBounds);
        }
        if (activeIntersectionDrag >= 0 && ImGui.isMouseReleased(0)) {
            intersectionFinished = true;
            activeIntersectionDrag = -1;
            activeIntersectionTarget =
                RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
        }
        if (active >= 0 && ImGui.isMouseDown(0)) {
            ProfileControlPoint activePoint = findControlPoint(controls, active);
            if (activePoint != null && activePoint.elevationEditable()) {
                elevation = clampElevation(
                    layout.elevationAtMouseY(mouseY, range.minElevation(), range.maxElevation()),
                    elevationBounds);
                boolean shiftDrag = ImGui.getIO().getKeyShift();
                if (activePoint.role() == ProfilePointRole.START_ENDPOINT
                        || activePoint.role() == ProfilePointRole.LOOP_SEAM_START) {
                    roadStation = 0.0;
                } else if (activePoint.role() == ProfilePointRole.END_ENDPOINT
                        || activePoint.role() == ProfilePointRole.LOOP_SEAM_END) {
                    roadStation = range.totalStation();
                } else if (shiftDrag && !activePoint.sharedJunction()) {
                    roadStation = layout.stationAtMouseX(mouseX, range.totalStation());
                } else {
                    roadStation = activePoint.roadStation();
                }
            }
        }
        if (active >= 0 && ImGui.isMouseReleased(0)) {
            finished = true;
            active = -1;
            pending = -1;
        }
        return new RoadLongitudinalProfileRenderer.ControlInteraction(
            selected, active, elevation, roadStation, started, finished,
            hoveredIntersection, selectedIntersection,
            activeIntersectionDrag, activeIntersectionTarget, intersectionElevation,
            intersectionStarted, intersectionFinished,
            addPointRequested, addPointRoadStation, addPointElevation, contextMenuPvi,
            pending, pendingX, pendingY, activeCurvePvi, activeCurveSide, draggedCurveLength,
            curveHandleStarted, curveHandleFinished);
    }

    private static Double clampElevation(Double elevation, RoadElevationBounds bounds) {
        if (elevation == null || bounds == null) {
            return elevation;
        }
        return bounds.clamp(elevation);
    }

    private static void drawWorldElevationBounds(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadElevationBounds bounds) {
        if (bounds == null) {
            return;
        }
        double span = Math.max(1.0, range.maxElevation() - range.minElevation());
        double margin = Math.max(8.0, span * 0.06);
        if (range.maxElevation() >= bounds.maxY() - margin) {
            drawWorldBoundLine(drawList, layout, range, bounds.maxY());
        }
        if (range.minElevation() <= bounds.minY() + margin) {
            drawWorldBoundLine(drawList, layout, range, bounds.minY());
        }
    }

    private static void drawWorldBoundLine(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            double elevation) {
        float y = layout.plotY(elevation, range.minElevation(), range.maxElevation());
        if (y < layout.plotTop() - 2f || y > layout.plotBottom() + 2f) {
            return;
        }
        drawDashedLine(
            drawList, layout.plotLeft(), y, layout.plotRight(), y, COLOR_WORLD_BOUND, 1.2f);
        String label = String.format("Y=%.0f", elevation);
        drawList.addText(layout.plotRight() + AXIS_LABEL_PAD, y - 6f, COLOR_WORLD_BOUND, label);
    }

    static RoadProfilePlotRange plotRange(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        ElevationBounds bounds = mergeBounds(
            staticChartBounds(chart, flatOverlay),
            dynamicLayerBounds(design, controls, intersections));
        return toPlotRange(chart.totalStation(), bounds);
    }

    static RoadProfilePlotRange resolvePlotRange(
            ProfileRenderCache cache,
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        if (cache == null || chart == null || !chart.hasProfileData()) {
            return plotRange(chart, design, controls, intersections, flatOverlay);
        }
        ElevationBounds dynamic = dynamicLayerBounds(design, controls, intersections);
        if (dynamic == null) {
            return cache.staticPlotRange();
        }
        ElevationBounds merged = mergeBounds(cache.staticRawBounds(), dynamic);
        RoadProfilePlotRange expanded = toPlotRange(cache.totalStation(), merged);
        if (expanded.minElevation() >= cache.staticPlotRange().minElevation()
                && expanded.maxElevation() <= cache.staticPlotRange().maxElevation()) {
            return cache.staticPlotRange();
        }
        return expanded;
    }

    record ElevationBounds(double min, double max) { }

    static ElevationBounds staticChartBounds(
            RoadProfileChartData chart,
            FlatElevationProfileOverlay flatOverlay) {
        if (chart == null) {
            return null;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        min = extendMin(min, chart.groundElevations());
        max = extendMax(max, chart.groundElevations());
        min = extendMin(min, chart.previewElevations());
        max = extendMax(max, chart.previewElevations());
        min = extendMin(min, chart.buildElevations());
        max = extendMax(max, chart.buildElevations());
        if (chart.hasBuildSamples()) {
            for (BuildHeightSample sample : chart.buildSamples()) {
                min = Math.min(min, sample.buildY());
                max = Math.max(max, sample.buildY());
            }
        }
        min = extendMin(min, chart.guideElevations());
        max = extendMax(max, chart.guideElevations());
        if (chart.hasWaterElevations()) {
            min = extendMin(min, chart.waterElevations());
            max = extendMax(max, chart.waterElevations());
        }
        if (flatOverlay != null) {
            if (flatOverlay.showCurrent()) {
                min = Math.min(min, flatOverlay.currentElevation());
                max = Math.max(max, flatOverlay.currentElevation());
            }
            if (flatOverlay.showSuggested()) {
                min = Math.min(min, flatOverlay.suggestedElevation());
                max = Math.max(max, flatOverlay.suggestedElevation());
            }
        }
        return finiteBounds(min, max);
    }

    private static ElevationBounds dynamicLayerBounds(
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        if (design != null) {
            min = extendMin(min, design.elevations());
            max = extendMax(max, design.elevations());
        }
        if (controls != null) {
            for (ProfileControlPoint point : controls) {
                min = Math.min(min, point.elevation());
                max = Math.max(max, point.elevation());
            }
        }
        if (intersections != null) {
            for (RoadProfileIntersection intersection : intersections) {
                min = Math.min(min, intersection.currentRoadElevation());
                min = Math.min(min, intersection.otherRoadElevation());
                max = Math.max(max, intersection.currentRoadElevation());
                max = Math.max(max, intersection.otherRoadElevation());
            }
        }
        return finiteBounds(min, max);
    }

    private static ElevationBounds mergeBounds(ElevationBounds left, ElevationBounds right) {
        if (left == null && right == null) {
            return new ElevationBounds(62.0, 66.0);
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return new ElevationBounds(
            Math.min(left.min(), right.min()),
            Math.max(left.max(), right.max()));
    }

    private static ElevationBounds finiteBounds(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return null;
        }
        return new ElevationBounds(min, max);
    }

    static RoadProfilePlotRange toPlotRange(double totalStation, ElevationBounds bounds) {
        ElevationBounds resolved = bounds != null ? bounds : new ElevationBounds(62.0, 66.0);
        double margin = ProfileElevationTicks.displayMargin(resolved.min(), resolved.max());
        return new RoadProfilePlotRange(
            totalStation,
            Math.floor(resolved.min() - margin),
            Math.ceil(resolved.max() + margin));
    }

    private static double extendMin(double min, List<Double> values) {
        if (values == null) {
            return min;
        }
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            min = Math.min(min, value);
        }
        return min;
    }

    private static double extendMax(double max, List<Double> values) {
        if (values == null) {
            return max;
        }
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            max = Math.max(max, value);
        }
        return max;
    }

    private static void drawWaterCrossingMarkers(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadProfileChartData chart) {
        if (chart.waterCrossings() == null || chart.waterCrossings().isEmpty()) {
            return;
        }
        float top = layout.plotTop();
        for (WaterCrossingChartMarker marker : chart.waterCrossings()) {
            float x0 = layout.plotX(marker.startStation(), range.totalStation());
            float x1 = layout.plotX(marker.endStation(), range.totalStation());
            int color = crossingMarkerColor(marker.strategy());
            drawList.addRectFilled(x0, top + 2f, x1, top + 8f, color);
        }
    }

    private static void drawConstructionRunMarkers(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadProfileChartData chart) {
        if (chart.constructionRuns() == null || chart.constructionRuns().isEmpty()) {
            return;
        }
        float top = layout.plotTop();
        for (ConstructionRunChartMarker marker : chart.constructionRuns()) {
            float x0 = layout.plotX(marker.startStation(), range.totalStation());
            float x1 = layout.plotX(marker.endStation(), range.totalStation());
            int color = constructionRunColor(marker.type());
            drawList.addRectFilled(x0, top + 2f, x1, top + 8f, color);
        }
    }

    private static int crossingMarkerColor(
            com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy strategy) {
        return switch (strategy) {
            case CAUSEWAY -> 0x66FFE066;
            case BRIDGE, LONG_BRIDGE -> 0x66FF9966;
            case TUNNEL_CANDIDATE -> 0x66CC99FF;
        };
    }

    private static int constructionRunColor(com.plot.plugin.road.RoadConstructionType type) {
        return switch (type) {
            case BRIDGE -> 0x66FF9966;
            case TUNNEL -> 0x66CC99FF;
            default -> 0x00000000;
        };
    }

    private static void drawBackground(ImDrawList drawList, ProfileChartLayout layout) {
        drawList.addRectFilled(
            layout.outerLeft(), layout.outerTop(),
            layout.outerRight(), layout.outerBottom(), COLOR_BG);
        drawList.addRect(
            layout.outerLeft(), layout.outerTop(),
            layout.outerRight(), layout.outerBottom(), COLOR_BORDER);
    }

    private static final float AXIS_LABEL_PAD = 6f;
    private static final float AXIS_LABEL_LINE_OFFSET = 6f;

    private static void drawAxesAndGrid(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            ProfileChartRenderMode mode) {
        List<Double> ticks = ProfileElevationTicks.elevationTicks(
            range.minElevation(), range.maxElevation());
        if (mode.maxElevationTicks() < Integer.MAX_VALUE && mode.maxElevationTicks() > 0) {
            ticks = subsampleTicks(ticks, mode.maxElevationTicks());
        } else if (mode.maxElevationTicks() == 0) {
            ticks = subsampleTicks(ticks, 3);
        }
        for (double tick : ticks) {
            float y = layout.plotY(tick, range.minElevation(), range.maxElevation());
            drawList.addLine(
                layout.plotLeft(), y, layout.plotRight(), y, COLOR_GRID, 1f);
            if (mode.showElevationAxisLabels()) {
                String label = String.format("%.0f", tick);
                float textWidth = ImGui.calcTextSize(label).x;
                float textY = y - AXIS_LABEL_LINE_OFFSET;
                drawList.addText(
                    layout.plotLeft() - AXIS_LABEL_PAD - textWidth, textY, COLOR_LABEL, label);
                if (mode != ProfileChartRenderMode.EDITOR) {
                    drawList.addText(layout.plotRight() + AXIS_LABEL_PAD, textY, COLOR_LABEL, label);
                }
            }
        }
        List<Double> stationTicks = ProfileElevationTicks.stationTicks(
            range.totalStation(), mode.maxStationTicks());
        if (mode.showStationAxisLabels()) {
            float stationLabelY = layout.plotBottom() + 4f;
            for (int i = 0; i < stationTicks.size(); i++) {
                double station = stationTicks.get(i);
                String label = RoadStationing.format(station, RoadStationFormat.DISTANCE_METERS);
                float textWidth = ImGui.calcTextSize(label).x;
                float x;
                if (i == 0) {
                    x = layout.plotLeft();
                } else if (i == stationTicks.size() - 1) {
                    x = layout.plotRight() - textWidth;
                } else {
                    x = layout.plotX(station, range.totalStation()) - textWidth * 0.5f;
                }
                drawList.addText(x, stationLabelY, COLOR_LABEL, label);
            }
        }
    }

    private static List<Double> subsampleTicks(List<Double> ticks, int maxCount) {
        if (ticks == null || ticks.size() <= maxCount || maxCount <= 0) {
            return ticks;
        }
        if (maxCount == 1) {
            return List.of(ticks.getFirst());
        }
        List<Double> sampled = new java.util.ArrayList<>(maxCount);
        for (int i = 0; i < maxCount; i++) {
            int index = (int) Math.round(i * (ticks.size() - 1) / (double) (maxCount - 1));
            sampled.add(ticks.get(index));
        }
        return sampled;
    }

    private static void drawSeries(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode mode,
            ProfileChartGuideSemantics guideSemantics) {
        drawPolyline(
            drawList, layout, range, chart.stations(), chart.groundElevations(),
            COLOR_GROUND, mode.groundLineWidth(), false);
        if (chart.hasWaterElevations()) {
            drawPolyline(
                drawList,
                layout,
                range,
                chart.stations(),
                chart.waterElevations(),
                ProfileChartSeriesStyle.WATER_SURFACE,
                1.8f,
                true);
        }
        drawWaterCrossingMarkers(drawList, layout, range, chart);
        drawConstructionRunMarkers(drawList, layout, range, chart);
        if (mode.showGuideLine()
                && guideSemantics != ProfileChartGuideSemantics.NONE
                && !chart.guideElevations().isEmpty()) {
            drawPolyline(
                drawList,
                layout,
                range,
                chart.stations(),
                chart.guideElevations(),
                ProfileChartSeriesStyle.guideLineColor(guideSemantics),
                1.6f,
                true);
        }
        if (mode.showDesignProfileLine()) {
            boolean skipPreviewDesign = mode == ProfileChartRenderMode.EDITOR
                && design != null && !design.isEmpty();
            if (!skipPreviewDesign) {
                drawPolyline(
                    drawList, layout, range, chart.stations(), chart.previewElevations(),
                    COLOR_DESIGN, mode.roadLineWidth(), false);
            }
        }
        if (mode == ProfileChartRenderMode.EDITOR && chart.hasBuildSamples()) {
            drawBuildStairStep(
                drawList,
                layout,
                range,
                chart.buildSamples(),
                COLOR_BUILD,
                Math.max(1.4f, mode.roadLineWidth() - 0.4f));
        } else if (chart.buildElevations() != null && !chart.buildElevations().isEmpty()) {
            drawPolyline(
                drawList, layout, range, chart.stations(), chart.buildElevations(),
                COLOR_BUILD, Math.max(1.4f, mode.roadLineWidth() - 0.4f), false);
        }
        if (design != null && !design.isEmpty()) {
            drawPolyline(
                drawList, layout, range, design.stations(), design.elevations(), COLOR_DESIGN, 2.6f, false);
        }
        if (flatOverlay != null && flatOverlay.showCurrent()) {
            float y = layout.plotY(flatOverlay.currentElevation(), range.minElevation(), range.maxElevation());
            drawList.addLine(layout.plotLeft(), y, layout.plotRight(), y, 0xFF66D9EF, 1.8f);
        }
        if (flatOverlay != null && flatOverlay.showSuggested()) {
            float y = layout.plotY(flatOverlay.suggestedElevation(), range.minElevation(), range.maxElevation());
            drawList.addLine(layout.plotLeft(), y, layout.plotRight(), y, 0xFFFFB84D, 1.8f);
        }
    }

    private static void drawSeries(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            FlatElevationProfileOverlay flatOverlay) {
        drawSeries(
            drawList, layout, range, chart, design, flatOverlay,
            ProfileChartRenderMode.EDITOR, ProfileChartGuideSemantics.NONE);
    }

    private static void drawAxesAndGrid(ImDrawList drawList, ProfileChartLayout layout, RoadProfilePlotRange range) {
        drawAxesAndGrid(drawList, layout, range, ProfileChartRenderMode.EDITOR);
    }

    private static void drawBuildStairStep(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            List<BuildHeightSample> samples,
            int color,
            float thickness) {
        if (samples == null || samples.size() < 2) {
            return;
        }
        for (int i = 0; i < samples.size() - 1; i++) {
            BuildHeightSample current = samples.get(i);
            BuildHeightSample next = samples.get(i + 1);
            float x0 = layout.plotX(current.station(), range.totalStation());
            float y0 = layout.plotY(current.buildY(), range.minElevation(), range.maxElevation());
            float x1 = layout.plotX(next.station(), range.totalStation());
            float y1 = layout.plotY(next.buildY(), range.minElevation(), range.maxElevation());
            drawList.addLine(x0, y0, x1, y0, color, thickness);
            if (next.buildY() != current.buildY()) {
                drawList.addLine(x1, y0, x1, y1, color, thickness);
            }
        }
    }

    private static void drawPolyline(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            List<Double> stations,
            List<Double> elevations,
            int color,
            float thickness,
            boolean dashed) {
        if (stations.size() < 2 || elevations.size() != stations.size()) {
            return;
        }
        for (int i = 1; i < stations.size(); i++) {
            Double start = elevations.get(i - 1);
            Double end = elevations.get(i);
            if (start == null || end == null || !Double.isFinite(start) || !Double.isFinite(end)) {
                continue;
            }
            float x0 = layout.plotX(stations.get(i - 1), range.totalStation());
            float y0 = layout.plotY(start, range.minElevation(), range.maxElevation());
            float x1 = layout.plotX(stations.get(i), range.totalStation());
            float y1 = layout.plotY(end, range.minElevation(), range.maxElevation());
            if (dashed) {
                drawDashedLine(drawList, x0, y0, x1, y1, color, thickness);
            } else {
                drawList.addLine(x0, y0, x1, y1, color, thickness);
            }
        }
    }

    private static void drawDashedLine(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            float thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 1e-3f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float traveled = 0f;
        boolean drawing = true;
        while (traveled < length) {
            float segment = drawing ? DASH_LENGTH : DASH_GAP;
            float next = Math.min(length, traveled + segment);
            if (drawing) {
                float sx = x0 + ux * traveled;
                float sy = y0 + uy * traveled;
                float ex = x0 + ux * next;
                float ey = y0 + uy * next;
                drawList.addLine(sx, sy, ex, ey, color, thickness);
            }
            traveled = next;
            drawing = !drawing;
        }
    }

    private static void drawRoadControlPoints(
            ImDrawList drawList,
            List<ProfileControlPoint> controls,
            int selected,
            double maxGradePercent,
            ProfileChartLayout layout,
            RoadProfilePlotRange range) {
        if (controls == null) {
            return;
        }
        for (ProfileControlPoint point : controls) {
            float x = layout.plotX(point.roadStation(), range.totalStation());
            float y = layout.plotY(point.elevation(), range.minElevation(), range.maxElevation());
            boolean invalid = VerticalProfileControlPoints.exceedsGradeLimit(point, maxGradePercent);
            boolean junctionFixed = point.role() == ProfilePointRole.JUNCTION_FIXED;
            int color = invalid ? COLOR_CONTROL_INVALID : COLOR_CONTROL;
            if (point.pviIndex() == selected) {
                color = COLOR_CONTROL_SELECTED;
            }
            float radius = point.endpoint() ? 6.5f : junctionFixed ? 3.5f : 4.5f;
            if (point.endpoint()) {
                color = point.pviIndex() == selected ? COLOR_CONTROL_SELECTED : COLOR_ENDPOINT;
            } else if (junctionFixed) {
                color = point.pviIndex() == selected ? COLOR_CONTROL_SELECTED : COLOR_JUNCTION_FIXED;
            }
            if (junctionFixed) {
                drawList.addRectFilled(x - radius, y - radius, x + radius, y + radius, color);
            } else {
                drawList.addCircleFilled(x, y, radius, color);
            }
            drawList.addCircle(x, y, radius + 1.2f, COLOR_BG, 12, 1.2f);
        }
    }

    private static void drawRoadCurveHandles(
            ImDrawList drawList,
            List<ProfileControlPoint> controls,
            List<RoadLongitudinalProfileRenderer.CurveHandle> handles,
            int activePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeSide,
            ProfileChartLayout layout,
            RoadProfilePlotRange range) {
        if (handles == null) {
            return;
        }
        for (RoadLongitudinalProfileRenderer.CurveHandle handle : handles) {
            float x = layout.plotX(handle.localDistance(), range.totalStation());
            float y = layout.plotY(handle.elevation(), range.minElevation(), range.maxElevation());
            int color = handle.pviIndex() == activePvi ? 0xFFFFFFFF : 0xFF88DDFF;
            drawList.addRectFilled(x - 3f, y - 3f, x + 3f, y + 3f, color);
        }
    }

    private static ProfileControlPoint findControlPoint(List<ProfileControlPoint> controls, int index) {
        if (controls == null) {
            return null;
        }
        for (ProfileControlPoint point : controls) {
            if (point.pviIndex() == index) {
                return point;
            }
        }
        return null;
    }

    private static boolean isEditable(List<ProfileControlPoint> controls, int index) {
        ProfileControlPoint point = findControlPoint(controls, index);
        return point != null && point.elevationEditable();
    }

    private static void updateHoverCursor(
            List<ProfileControlPoint> controls,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY,
            int activePvi,
            int activeIntersectionDrag) {
        if (activePvi >= 0 || activeIntersectionDrag >= 0) {
            return;
        }
        ProfileControlPoint hovered = findHoveredControl(controls, layout, range, mouseX, mouseY);
        if (hovered != null && hovered.endpoint() && hovered.elevationEditable()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
        }
    }

    private static ProfileControlPoint findHoveredControl(
            List<ProfileControlPoint> controls,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY) {
        if (controls == null || controls.isEmpty()) {
            return null;
        }
        double best = 12.0 * 12.0;
        ProfileControlPoint bestPoint = null;
        for (ProfileControlPoint point : controls) {
            float x = layout.plotX(point.roadStation(), range.totalStation());
            float y = layout.plotY(point.elevation(), range.minElevation(), range.maxElevation());
            double dist = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (dist <= best) {
                best = dist;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    private static int nearestControl(
            List<ProfileControlPoint> controls,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY) {
        if (controls == null || controls.isEmpty()) {
            return -1;
        }
        double best = 12.0 * 12.0;
        int bestIndex = -1;
        for (ProfileControlPoint point : controls) {
            float x = layout.plotX(point.roadStation(), range.totalStation());
            float y = layout.plotY(point.elevation(), range.minElevation(), range.maxElevation());
            double dist = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (dist <= best) {
                best = dist;
                bestIndex = point.pviIndex();
            }
        }
        return bestIndex;
    }
}
