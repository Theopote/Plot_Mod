package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
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
    private static final int COLOR_GROUND = 0xFF8B5A2B;
    private static final int COLOR_GUIDE = 0xFF4DA3FF;
    private static final int COLOR_TARGET = 0xFFB0B0B0;
    private static final int COLOR_DESIGN = 0xFF5FD35F;
    private static final int COLOR_LABEL = 0x88AAAAAA;
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
            origin.x, origin.y, width, chartHeight);
        RoadProfilePlotRange range = plotRange(chart, design, List.of(), intersections, flatOverlay);
        drawBackground(drawList, layout);
        drawAxesAndGrid(drawList, layout, range);
        drawSeries(drawList, layout, range, chart, design, flatOverlay);
        RoadLongitudinalProfileRenderer.drawIntersectionMarkersRoad(
            drawList, intersections, -1, layout,
            range.totalStation(), range.minElevation(), range.maxElevation());
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
            origin.x, origin.y, width, chartHeight);
        RoadProfilePlotRange range = plotRange(chart, design, controls, intersections, flatOverlay);
        drawBackground(drawList, layout);
        drawAxesAndGrid(drawList, layout, range);
        drawSeries(drawList, layout, range, chart, design, flatOverlay);
        drawRoadControlPoints(drawList, controls, selectedPviIndex, maxGradePercent, layout, range);
        drawRoadCurveHandles(
            drawList, controls, curveHandles, activeCurveHandlePvi, activeCurveHandle, layout, range);
        RoadLongitudinalProfileRenderer.drawIntersectionMarkersRoad(
            drawList, intersections, selectedIntersectionIndex, layout,
            range.totalStation(), range.minElevation(), range.maxElevation());
        ImGui.invisibleButton("##road_profile_control_surface", width, chartHeight);

        return handleInteraction(
            chart, controls, intersections, layout, range, selectedPviIndex, activePviIndex,
            selectedIntersectionIndex, activeIntersectionDragIndex, activeIntersectionDragTarget,
            curveHandles, pendingPviIndex, pendingClickX, pendingClickY,
            activeCurveHandlePvi, activeCurveHandle);
    }

    private static RoadLongitudinalProfileRenderer.ControlInteraction handleInteraction(
            RoadProfileChartData chart,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            int selectedPviIndex,
            int activePviIndex,
            int selectedIntersectionIndex,
            int activeIntersectionDragIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide activeCurveHandle) {
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
                RoadLongitudinalProfileRenderer.hitIntersectionRoad(
                    intersections, layout, range, mouseX, mouseY);
            if (hoveredHit != null) {
                hoveredIntersection = hoveredHit.index();
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
            int nearest = nearestControl(controls, layout, range, mouseX, mouseY);
            if (nearest < 0) {
                addPointRequested = true;
                addPointRoadStation = layout.stationAtMouseX(mouseX, range.totalStation());
                addPointElevation = layout.elevationAtMouseY(mouseY, range.minElevation(), range.maxElevation());
                pending = -1;
            }
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
            RoadLongitudinalProfileRenderer.IntersectionHit hit =
                RoadLongitudinalProfileRenderer.hitIntersectionRoad(
                    intersections, layout, range, mouseX, mouseY);
            int nearest = nearestControl(controls, layout, range, mouseX, mouseY);
            if (hit != null && hit.target()
                    != RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE) {
                activeIntersectionDrag = hit.index();
                activeIntersectionTarget = hit.target();
                selectedIntersection = hit.index();
                intersectionStarted = true;
                selected = -1;
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
            intersectionElevation = layout.elevationAtMouseY(
                mouseY, range.minElevation(), range.maxElevation());
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
                elevation = layout.elevationAtMouseY(
                    mouseY, range.minElevation(), range.maxElevation());
                if (activePoint.role() == ProfilePointRole.START_ENDPOINT) {
                    roadStation = 0.0;
                } else if (activePoint.role() == ProfilePointRole.END_ENDPOINT) {
                    roadStation = range.totalStation();
                } else {
                    roadStation = layout.stationAtMouseX(mouseX, range.totalStation());
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

    static RoadProfilePlotRange plotRange(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        min = extendMin(min, chart.groundElevations());
        max = extendMax(max, chart.groundElevations());
        min = extendMin(min, chart.previewElevations());
        max = extendMax(max, chart.previewElevations());
        min = extendMin(min, chart.guideElevations());
        max = extendMax(max, chart.guideElevations());
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
        if (!Double.isFinite(min)) {
            min = 62.0;
            max = 66.0;
        }
        double margin = ProfileElevationTicks.displayMargin(min, max);
        return new RoadProfilePlotRange(
            chart.totalStation(),
            Math.floor(min - margin),
            Math.ceil(max + margin));
    }

    private static double extendMin(double min, List<Double> values) {
        for (double value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private static double extendMax(double max, List<Double> values) {
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static void drawBackground(ImDrawList drawList, ProfileChartLayout layout) {
        drawList.addRectFilled(
            layout.outerLeft(), layout.outerTop(),
            layout.outerRight(), layout.outerBottom(), COLOR_BG);
        drawList.addRect(
            layout.outerLeft(), layout.outerTop(),
            layout.outerRight(), layout.outerBottom(), COLOR_BORDER);
    }

    private static void drawAxesAndGrid(ImDrawList drawList, ProfileChartLayout layout, RoadProfilePlotRange range) {
        List<Double> ticks = ProfileElevationTicks.elevationTicks(
            range.minElevation(), range.maxElevation());
        for (double tick : ticks) {
            float y = layout.plotY(tick, range.minElevation(), range.maxElevation());
            drawList.addLine(
                layout.plotLeft(), y, layout.plotRight(), y, COLOR_GRID, 1f);
            String label = String.format("%.0f", tick);
            drawList.addText(layout.plotLeft() - 34f, y - 6f, COLOR_LABEL, label);
            drawList.addText(layout.plotRight() + 4f, y - 6f, COLOR_LABEL, label);
        }
        List<Double> stationTicks = ProfileElevationTicks.stationTicks(range.totalStation(), 5);
        for (double station : stationTicks) {
            float x = layout.plotX(station, range.totalStation());
            String label = RoadStationing.format(station, RoadStationFormat.KILOMETER_PLUS);
            drawList.addText(x - 16f, layout.plotBottom() + 4f, COLOR_LABEL, label);
        }
    }

    private static void drawSeries(
            ImDrawList drawList,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            FlatElevationProfileOverlay flatOverlay) {
        drawPolyline(
            drawList, layout, range, chart.stations(), chart.groundElevations(), COLOR_GROUND, 2.2f, false);
        if (!chart.guideElevations().isEmpty()) {
            drawPolyline(
                drawList, layout, range, chart.stations(), chart.guideElevations(), COLOR_GUIDE, 1.6f, true);
        }
        drawPolyline(
            drawList, layout, range, chart.stations(), chart.previewElevations(), COLOR_TARGET, 2.4f, false);
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
            float x0 = layout.plotX(stations.get(i - 1), range.totalStation());
            float y0 = layout.plotY(elevations.get(i - 1), range.minElevation(), range.maxElevation());
            float x1 = layout.plotX(stations.get(i), range.totalStation());
            float y1 = layout.plotY(elevations.get(i), range.minElevation(), range.maxElevation());
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
