package com.plot.plugin.road;

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
 */
public final class RoadLongitudinalProfileRenderer {
    public static final float DEFAULT_PREVIEW_HEIGHT = 120f;
    private static final float PREVIEW_HEIGHT = DEFAULT_PREVIEW_HEIGHT;
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
    private static final int COLOR_INTERSECTION = 0xFF66CCFF;
    private static final int COLOR_INTERSECTION_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_INTERSECTION_GRADE = 0xFFFF9966;
    private static final int COLOR_INTERSECTION_WARNING = 0xFFFF5252;
    private static final int COLOR_OTHER_ROAD = 0xFFCC99FF;
    private static final int COLOR_FLAT_CURRENT = 0xFF66D9EF;
    private static final int COLOR_FLAT_SUGGESTED = 0xFFFFB84D;

    public record CurveHandle(
            int pviIndex,
            double localDistance,
            double elevation,
            boolean leftSide) { }

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

    private static final float DRAG_THRESHOLD_PX = 5f;
    private static final int COLOR_CURVE_HANDLE = 0xFF88DDFF;
    private static final int COLOR_CURVE_HANDLE_SELECTED = 0xFFFFFFFF;

    private RoadLongitudinalProfileRenderer() {
    }

    public static void render(RoadGenerationResult result) {
        render(result, true);
    }

    public static void render(RoadGenerationResult result, boolean showTitle) {
        render(result, showTitle, null);
    }

    public static void render(
            RoadGenerationResult result,
            boolean showTitle,
            VerticalAlignmentProfileOverlay designOverlay) {
        render(result, showTitle, designOverlay, DEFAULT_PREVIEW_HEIGHT);
    }

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
            result.profileTargetHeights,
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
     */
    public static void renderOverview(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<RoadProfileIntersection> intersections,
            float chartHeight) {
        renderOverview(result, designOverlay, intersections, chartHeight, FlatElevationProfileOverlay.EMPTY);
    }

    public static void renderOverview(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        renderOverview(result, designOverlay, intersections, chartHeight, flatOverlay, 1.0);
    }

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
            result.profileGuideLine, result.profileTargetHeights, designOverlay, flatOverlay,
            x0, y0, width, chartHeight, range);
        drawIntersectionMarkers(drawList, intersections, -1, range, x0, y0, width, chartHeight);
        ImGui.dummy(width, chartHeight);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex,
            maxGradePercent, List.of(), -1, DEFAULT_PREVIEW_HEIGHT);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex,
            maxGradePercent, intersections, selectedIntersectionIndex, DEFAULT_PREVIEW_HEIGHT,
            -1, ControlInteraction.IntersectionDragTarget.NONE);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex,
            maxGradePercent, intersections, selectedIntersectionIndex, chartHeight,
            -1, ControlInteraction.IntersectionDragTarget.NONE);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex,
            maxGradePercent, intersections, selectedIntersectionIndex, chartHeight,
            activeIntersectionDragIndex, activeIntersectionDragTarget,
            FlatElevationProfileOverlay.EMPTY);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex, maxGradePercent,
            intersections, selectedIntersectionIndex, chartHeight, activeIntersectionDragIndex,
            activeIntersectionDragTarget, flatOverlay, List.of(), -1, 0f, 0f, -1,
            ControlInteraction.CurveHandleSide.NONE);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay,
            List<CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            ControlInteraction.CurveHandleSide activeCurveHandle) {
        return renderInteractive(
            result, designOverlay, controls, selectedPviIndex, activePviIndex, maxGradePercent,
            intersections, selectedIntersectionIndex, chartHeight, activeIntersectionDragIndex,
            activeIntersectionDragTarget, flatOverlay, curveHandles, pendingPviIndex,
            pendingClickX, pendingClickY, activeCurveHandlePvi, activeCurveHandle, 1.0);
    }

    public static ControlInteraction renderInteractive(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay designOverlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int selectedPviIndex,
            int activePviIndex,
            double maxGradePercent,
            List<RoadProfileIntersection> intersections,
            int selectedIntersectionIndex,
            float chartHeight,
            int activeIntersectionDragIndex,
            ControlInteraction.IntersectionDragTarget activeIntersectionDragTarget,
            FlatElevationProfileOverlay flatOverlay,
            List<CurveHandle> curveHandles,
            int pendingPviIndex,
            float pendingClickX,
            float pendingClickY,
            int activeCurveHandlePvi,
            ControlInteraction.CurveHandleSide activeCurveHandle,
            double geometryToProfileScale) {
        if (result == null || !result.hasProfileData()) {
            return new ControlInteraction(selectedPviIndex, -1, null, null, false, false);
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return new ControlInteraction(selectedPviIndex, activePviIndex, null, null, false, false);
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        drawList.addRectFilled(x0, y0, x0 + width, y0 + chartHeight, COLOR_BG);
        drawList.addRect(x0, y0, x0 + width, y0 + chartHeight, COLOR_BORDER);
        PlotRange range = plotRange(
            result, designOverlay, controls, intersections, flatOverlay, geometryToProfileScale);
        drawProfile(drawList, result.profileDistances, result.profileGroundHeights,
            result.profileGuideLine, result.profileTargetHeights, designOverlay, flatOverlay,
            x0, y0, width, chartHeight, range);
        drawIntersectionMarkers(
            drawList, intersections, selectedIntersectionIndex, range, x0, y0, width, chartHeight);
        drawControlPoints(drawList, controls, selectedPviIndex, maxGradePercent,
            range, x0, y0, width, chartHeight);
        drawCurveHandles(
            drawList,
            controls,
            curveHandles,
            activeCurveHandlePvi,
            activeCurveHandle,
            range,
            x0,
            y0,
            width,
            chartHeight);
        ImGui.invisibleButton("##road_profile_control_surface", width, chartHeight);

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
        Double localDistance = null;
        int hoveredIntersection = -1;
        int selectedIntersection = selectedIntersectionIndex;
        int activeIntersectionDrag = activeIntersectionDragIndex;
        ControlInteraction.IntersectionDragTarget activeIntersectionTarget =
            activeIntersectionDragTarget != null
                ? activeIntersectionDragTarget
                : ControlInteraction.IntersectionDragTarget.NONE;
        Double intersectionElevation = null;
        boolean intersectionStarted = false;
        boolean intersectionFinished = false;
        boolean addPointRequested = false;
        Double addPointLocalDistance = null;
        Double addPointElevation = null;
        int contextMenuPvi = -1;
        int activeCurvePvi = activeCurveHandlePvi;
        ControlInteraction.CurveHandleSide activeCurveSide = activeCurveHandle != null
            ? activeCurveHandle
            : ControlInteraction.CurveHandleSide.NONE;
        Double draggedCurveLength = null;
        boolean curveHandleStarted = false;
        boolean curveHandleFinished = false;

        if (ImGui.isItemHovered()) {
            if (active < 0 && activeCurvePvi < 0 && activeIntersectionDrag < 0) {
                VerticalProfileControlPoints.ControlPoint hoveredControl = findHoveredControl(
                    controls, range, x0, y0, width, chartHeight, mouseX, mouseY);
                if (hoveredControl != null) {
                    renderControlPointElevationTooltip(hoveredControl);
                }
            }
            IntersectionHit hoveredHit = hitIntersection(
                intersections, range, x0, y0, width, chartHeight, mouseX, mouseY);
            if (hoveredHit != null) {
                hoveredIntersection = hoveredHit.index();
            }
        }

        if (activeCurvePvi >= 0 && ImGui.isMouseDown(0)) {
            CurveHandle activeHandle = findCurveHandle(curveHandles, activeCurvePvi, activeCurveSide);
            if (activeHandle != null) {
                VerticalProfileControlPoints.ControlPoint pvi =
                    findControlPoint(controls, activeCurvePvi);
                if (pvi != null) {
                    double handleDistance = distanceAtMouseX(mouseX, range, x0, width);
                    double halfLength = Math.abs(range.chartDistance(pvi.localDistance()) - handleDistance);
                    draggedCurveLength = Math.max(0.0, halfLength * 2.0);
                }
            }
        }
        if (activeCurvePvi >= 0 && ImGui.isMouseReleased(0)) {
            curveHandleFinished = true;
            activeCurvePvi = -1;
            activeCurveSide = ControlInteraction.CurveHandleSide.NONE;
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            int nearest = nearestControl(controls, range, x0, y0, width, chartHeight, mouseX, mouseY);
            if (nearest < 0) {
                addPointRequested = true;
                addPointLocalDistance = distanceAtMouseX(mouseX, range, x0, width);
                addPointElevation = elevationAtMouseY(mouseY, range, y0, chartHeight);
                pending = -1;
            }
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
            int nearest = nearestControl(controls, range, x0, y0, width, chartHeight, mouseX, mouseY);
            if (nearest >= 0 && isElevationEditableControl(controls, nearest)) {
                contextMenuPvi = nearest;
                ImGui.openPopup("##road_profile_pvi_context");
            }
        }

        if (activeCurvePvi < 0 && activeIntersectionDrag < 0 && active < 0
                && ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
            IntersectionHit hit = hitIntersection(
                intersections, range, x0, y0, width, chartHeight, mouseX, mouseY);
            int nearest = nearestControl(controls, range, x0, y0, width, chartHeight, mouseX, mouseY);
            CurveHandleHit curveHit = hitCurveHandle(curveHandles, range, x0, y0, width, chartHeight, mouseX, mouseY);
            if (hit != null && hit.target() != ControlInteraction.IntersectionDragTarget.NONE) {
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
            } else if (nearest >= 0 && isElevationEditableControl(controls, nearest)) {
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
                    && isElevationEditableControl(controls, pending)) {
                active = pending;
                started = true;
            }
        }
        if (pending >= 0 && active < 0 && ImGui.isMouseReleased(0)) {
            pending = -1;
        }

        if (activeIntersectionDrag >= 0 && ImGui.isMouseDown(0)) {
            intersectionElevation = elevationAtMouseY(mouseY, range, y0, chartHeight);
        }
        if (activeIntersectionDrag >= 0 && ImGui.isMouseReleased(0)) {
            intersectionFinished = true;
            activeIntersectionDrag = -1;
            activeIntersectionTarget = ControlInteraction.IntersectionDragTarget.NONE;
        }
        if (active >= 0 && ImGui.isMouseDown(0)) {
            VerticalProfileControlPoints.ControlPoint activePoint =
                findControlPoint(controls, active);
            if (activePoint != null && activePoint.elevationEditable()) {
                elevation = elevationAtMouseY(mouseY, range, y0, chartHeight);
                localDistance = distanceAtMouseX(mouseX, range, x0, width);
            }
        }
        if (active >= 0 && ImGui.isMouseReleased(0)) {
            finished = true;
            active = -1;
            pending = -1;
        }
        return new ControlInteraction(
            selected, active, elevation, localDistance, started, finished,
            hoveredIntersection, selectedIntersection,
            activeIntersectionDrag, activeIntersectionTarget, intersectionElevation,
            intersectionStarted, intersectionFinished,
            addPointRequested, addPointLocalDistance, addPointElevation, contextMenuPvi,
            pending, pendingX, pendingY, activeCurvePvi, activeCurveSide, draggedCurveLength,
            curveHandleStarted, curveHandleFinished);
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
            List<VerticalProfileControlPoints.ControlPoint> controls) {
        return plotRange(
            result, overlay, controls, List.of(), FlatElevationProfileOverlay.EMPTY, 1.0);
    }

    private static PlotRange plotRange(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay overlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            List<RoadProfileIntersection> intersections) {
        return plotRange(
            result, overlay, controls, intersections, FlatElevationProfileOverlay.EMPTY, 1.0);
    }

    private static PlotRange plotRange(
            RoadGenerationResult result,
            VerticalAlignmentProfileOverlay overlay,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        return plotRange(result, overlay, controls, intersections, flatOverlay, 1.0);
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
                result.profileGroundHeights, result.profileGuideLine, result.profileTargetHeights)) {
            for (int value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (overlay != null) {
            for (int value : overlay.heights()) {
                min = Math.min(min, value);
                max = Math.max(max, value);
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

    private static void drawIntersectionMarkers(
            ImDrawList drawList,
            List<RoadProfileIntersection> intersections,
            int selectedIndex,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        float padding = 10f;
        float plotX0 = x0 + padding;
        float plotY0 = y0 + padding;
        float plotWidth = width - 2 * padding;
        float plotHeight = height - 2 * padding;
        for (int i = 0; i < intersections.size(); i++) {
            RoadProfileIntersection intersection = intersections.get(i);
            float x = toPlotX(
                range.chartDistance(intersection.localDistance()), range.maxDistance(), plotX0, plotWidth);
            float currentY = toPlotY(
                (int) Math.round(intersection.currentRoadElevation()),
                range.minHeight(),
                range.maxHeight(),
                plotY0,
                plotHeight);
            int markerColor = intersection.gradeSeparated()
                ? COLOR_INTERSECTION_GRADE
                : COLOR_INTERSECTION;
            if (intersection.steepGradeWarning()) {
                markerColor = COLOR_INTERSECTION_WARNING;
            }
            if (i == selectedIndex) {
                markerColor = COLOR_INTERSECTION_SELECTED;
            }
            if (intersection.gradeSeparated()) {
                float otherY = toPlotY(
                    (int) Math.round(intersection.otherRoadElevation()),
                    range.minHeight(),
                    range.maxHeight(),
                    plotY0,
                    plotHeight);
                float halfWidth = 10f;
                int connectorColor = intersection.steepGradeWarning()
                    ? COLOR_INTERSECTION_WARNING
                    : markerColor;
                drawList.addLine(x - halfWidth, currentY, x + halfWidth, currentY, connectorColor, 2.2f);
                drawList.addLine(x - halfWidth, otherY, x + halfWidth, otherY, COLOR_OTHER_ROAD, 2.0f);
                drawList.addLine(x, currentY, x, otherY, connectorColor, 1.2f);
                int diamondColor = intersection.steepGradeWarning()
                    ? COLOR_INTERSECTION_WARNING
                    : markerColor;
                drawDiamond(drawList, x, currentY, 5f, diamondColor);
                drawDiamond(drawList, x, otherY, 5f, COLOR_OTHER_ROAD);
                if (intersection.steepGradeWarning()) {
                    drawWarningBadge(drawList, x, currentY, diamondColor);
                }
            } else {
                drawList.addCircleFilled(x, currentY, 5f, markerColor);
                drawList.addCircle(x, currentY, 6f, COLOR_BG, 12, 1.5f);
            }
            String label = intersection.otherRoadLabel();
            if (label != null && !label.isBlank()) {
                drawList.addText(x + 6f, currentY - ImGui.getTextLineHeight(), markerColor, label);
            }
        }
    }

    private static void drawDiamond(ImDrawList drawList, float cx, float cy, float radius, int color) {
        drawList.addQuadFilled(
            cx, cy - radius,
            cx + radius, cy,
            cx, cy + radius,
            cx - radius, cy,
            color);
    }

    private static void drawWarningBadge(ImDrawList drawList, float cx, float cy, int color) {
        String badge = "!";
        drawList.addText(cx + 4f, cy - 11f, color, badge);
    }

    record IntersectionHit(int index, ControlInteraction.IntersectionDragTarget target) { }

    static IntersectionHit hitIntersectionForTest(
            List<RoadProfileIntersection> intersections,
            double maxDistance,
            int minHeight,
            int maxHeight,
            float x0,
            float y0,
            float width,
            float height,
            float mouseX,
            float mouseY) {
        return hitIntersection(
            intersections,
            new PlotRange(maxDistance, minHeight, maxHeight, 1.0),
            x0,
            y0,
            width,
            height,
            mouseX,
            mouseY);
    }

    private static IntersectionHit hitIntersection(
            List<RoadProfileIntersection> intersections,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height,
            float mouseX,
            float mouseY) {
        if (intersections == null || intersections.isEmpty()) {
            return null;
        }
        float padding = 10f;
        float plotX0 = x0 + padding;
        float plotY0 = y0 + padding;
        float plotWidth = width - 2 * padding;
        float plotHeight = height - 2 * padding;
        double bestDist = 12.0 * 12.0;
        IntersectionHit best = null;
        for (int i = 0; i < intersections.size(); i++) {
            RoadProfileIntersection intersection = intersections.get(i);
            float x = toPlotX(
                range.chartDistance(intersection.localDistance()), range.maxDistance(), plotX0, plotWidth);
            float currentY = toPlotY(
                (int) Math.round(intersection.currentRoadElevation()),
                range.minHeight(),
                range.maxHeight(),
                plotY0,
                plotHeight);
            if (intersection.gradeSeparated()) {
                float otherY = toPlotY(
                    (int) Math.round(intersection.otherRoadElevation()),
                    range.minHeight(),
                    range.maxHeight(),
                    plotY0,
                    plotHeight);
                double currentDist = distanceSquared(mouseX, mouseY, x, currentY);
                double otherDist = distanceSquared(mouseX, mouseY, x, otherY);
                if (currentDist <= bestDist) {
                    bestDist = currentDist;
                    best = new IntersectionHit(i, ControlInteraction.IntersectionDragTarget.CURRENT);
                }
                if (otherDist <= bestDist) {
                    bestDist = otherDist;
                    best = new IntersectionHit(i, ControlInteraction.IntersectionDragTarget.OTHER);
                }
            } else {
                double currentDist = distanceSquared(mouseX, mouseY, x, currentY);
                if (currentDist <= bestDist) {
                    bestDist = currentDist;
                    best = new IntersectionHit(i, ControlInteraction.IntersectionDragTarget.SHARED);
                }
            }
        }
        return best;
    }

    private static double distanceSquared(float mouseX, float mouseY, float x, float y) {
        double dx = mouseX - x;
        double dy = mouseY - y;
        return dx * dx + dy * dy;
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

    private static boolean isElevationEditableControl(
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int pviIndex) {
        if (controls == null) {
            return false;
        }
        for (VerticalProfileControlPoints.ControlPoint point : controls) {
            if (point.pviIndex() == pviIndex) {
                return point.elevationEditable();
            }
        }
        return false;
    }

    private static VerticalProfileControlPoints.ControlPoint findControlPoint(
            List<VerticalProfileControlPoints.ControlPoint> controls,
            int pviIndex) {
        if (controls == null) {
            return null;
        }
        for (VerticalProfileControlPoints.ControlPoint point : controls) {
            if (point.pviIndex() == pviIndex) {
                return point;
            }
        }
        return null;
    }

    private record CurveHandleHit(int pviIndex, ControlInteraction.CurveHandleSide side) { }

    private static void drawCurveHandles(
            ImDrawList drawList,
            List<VerticalProfileControlPoints.ControlPoint> controls,
            List<CurveHandle> handles,
            int activeCurveHandlePvi,
            ControlInteraction.CurveHandleSide activeCurveHandle,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height) {
        if (drawList == null || handles == null || handles.isEmpty()) {
            return;
        }
        float padding = 10f;
        for (CurveHandle handle : handles) {
            VerticalProfileControlPoints.ControlPoint pvi = findControlPoint(controls, handle.pviIndex());
            if (pvi == null) {
                continue;
            }
            float handleX = toPlotX(
                range.chartDistance(handle.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float handleY = toPlotY(
                (int) Math.round(handle.elevation()),
                range.minHeight(),
                range.maxHeight(),
                y0 + padding,
                height - 2 * padding);
            float pviX = toPlotX(
                range.chartDistance(pvi.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float pviY = toPlotY(
                (int) Math.round(pvi.elevation()),
                range.minHeight(),
                range.maxHeight(),
                y0 + padding,
                height - 2 * padding);
            boolean active = handle.pviIndex() == activeCurveHandlePvi
                && ((handle.leftSide()
                        && activeCurveHandle == ControlInteraction.CurveHandleSide.LEFT)
                    || (!handle.leftSide()
                        && activeCurveHandle == ControlInteraction.CurveHandleSide.RIGHT));
            int color = active ? COLOR_CURVE_HANDLE_SELECTED : COLOR_CURVE_HANDLE;
            drawList.addLine(pviX, pviY, handleX, handleY, color, 1.2f);
            drawList.addRectFilled(handleX - 3f, handleY - 3f, handleX + 3f, handleY + 3f, color);
        }
        CurveHandle left = findCurveHandle(handles, handles.getFirst().pviIndex(),
            ControlInteraction.CurveHandleSide.LEFT);
        CurveHandle right = findCurveHandle(handles, handles.getFirst().pviIndex(),
            ControlInteraction.CurveHandleSide.RIGHT);
        if (left != null && right != null) {
            float leftX = toPlotX(
                range.chartDistance(left.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float rightX = toPlotX(
                range.chartDistance(right.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float midY = toPlotY(
                (int) Math.round((left.elevation() + right.elevation()) * 0.5),
                range.minHeight(),
                range.maxHeight(),
                y0 + padding,
                height - 2 * padding);
            drawList.addLine(leftX, midY, rightX, midY, COLOR_CURVE_HANDLE, 1.0f);
        }
    }

    private static CurveHandle findCurveHandle(
            List<CurveHandle> handles,
            int pviIndex,
            ControlInteraction.CurveHandleSide side) {
        if (handles == null || side == ControlInteraction.CurveHandleSide.NONE) {
            return null;
        }
        for (CurveHandle handle : handles) {
            if (handle.pviIndex() == pviIndex
                    && ((side == ControlInteraction.CurveHandleSide.LEFT && handle.leftSide())
                        || (side == ControlInteraction.CurveHandleSide.RIGHT && !handle.leftSide()))) {
                return handle;
            }
        }
        return null;
    }

    private static CurveHandleHit hitCurveHandle(
            List<CurveHandle> handles,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height,
            float mouseX,
            float mouseY) {
        if (handles == null || handles.isEmpty()) {
            return null;
        }
        float padding = 10f;
        double best = 8.0 * 8.0;
        CurveHandleHit nearest = null;
        for (CurveHandle handle : handles) {
            float x = toPlotX(
                range.chartDistance(handle.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float y = toPlotY(
                (int) Math.round(handle.elevation()),
                range.minHeight(),
                range.maxHeight(),
                y0 + padding,
                height - 2 * padding);
            double distance = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (distance <= best) {
                best = distance;
                nearest = new CurveHandleHit(
                    handle.pviIndex(),
                    handle.leftSide()
                        ? ControlInteraction.CurveHandleSide.LEFT
                        : ControlInteraction.CurveHandleSide.RIGHT);
            }
        }
        return nearest;
    }

    private static VerticalProfileControlPoints.ControlPoint findHoveredControl(
            List<VerticalProfileControlPoints.ControlPoint> controls,
            PlotRange range,
            float x0,
            float y0,
            float width,
            float height,
            float mouseX,
            float mouseY) {
        int nearest = nearestControl(controls, range, x0, y0, width, height, mouseX, mouseY);
        return nearest >= 0 ? findControlPoint(controls, nearest) : null;
    }

    private static void renderControlPointElevationTooltip(
            VerticalProfileControlPoints.ControlPoint point) {
        if (point == null) {
            return;
        }
        ImGui.beginTooltip();
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_control_elevation_tooltip",
            String.format("%.1f", point.elevation())));
        ImGui.endTooltip();
    }

    private static int nearestControl(
            List<VerticalProfileControlPoints.ControlPoint> controls,
            PlotRange range,
            float x0, float y0, float width, float height,
            float mouseX, float mouseY) {
        if (controls == null) return -1;
        float padding = 10f;
        double best = 9.0 * 9.0;
        int nearest = -1;
        for (VerticalProfileControlPoints.ControlPoint point : controls) {
            float x = toPlotX(
                range.chartDistance(point.localDistance()), range.maxDistance(), x0 + padding, width - 2 * padding);
            float y = toPlotY((int) Math.round(point.elevation()), range.minHeight(), range.maxHeight(),
                y0 + padding, height - 2 * padding);
            double distance = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (distance <= best) {
                best = distance;
                nearest = point.pviIndex();
            }
        }
        return nearest;
    }

    private static double elevationAtMouseY(float mouseY, PlotRange range, float y0, float height) {
        float padding = 10f;
        float top = y0 + padding;
        float plotHeight = height - 2 * padding;
        double ratio = 1.0 - Math.max(0.0, Math.min(1.0, (mouseY - top) / plotHeight));
        double raw = range.minHeight() + ratio * (range.maxHeight() - range.minHeight());
        return Math.rint(raw * 4.0) / 4.0;
    }

    private static double distanceAtMouseX(float mouseX, PlotRange range, float x0, float width) {
        float padding = 10f;
        float left = x0 + padding;
        float plotWidth = width - 2 * padding;
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - left) / plotWidth));
        return Math.rint(ratio * range.maxDistance() * 4.0) / 4.0;
    }

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
        snapshot.profileTargetHeights = targetHeights;
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
