package com.plot.plugin.road.profile;

/**
 * Hit-test and drag interaction results for {@link RoadProfileChartRenderer}.
 */
public final class ProfileChartInteraction {

    private ProfileChartInteraction() {
    }

    public record CurveHandle(
            int pviIndex,
            double localDistance,
            double elevation,
            boolean leftSide) {
    }

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

    public record IntersectionHit(int index, IntersectionDragTarget target) {
    }

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
}
