package com.plot.plugin.road.profile;

import imgui.ImDrawList;
import imgui.ImGui;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Canonical intersection markers and hit testing for profile charts. */
public final class ProfileChartIntersectionSupport {

    public static final double AT_GRADE_ELEVATION_TOLERANCE = 0.26;
    private static final double HIT_RADIUS_PX = 12.0;
    private static final double HIT_RADIUS_SQ = HIT_RADIUS_PX * HIT_RADIUS_PX;

    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_INTERSECTION = 0xFF66CCFF;
    private static final int COLOR_INTERSECTION_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_INTERSECTION_GRADE = 0xFFFF9966;
    private static final int COLOR_INTERSECTION_WARNING = 0xFFFF5252;
    private static final int COLOR_OTHER_ROAD = 0xFFCC99FF;

    private ProfileChartIntersectionSupport() {
    }

    private record IntersectionMarkerCoordinates(
            ToDoubleFunction<RoadProfileIntersection> plotX,
            ToDoubleFunction<Double> plotY) {
        float plotX(RoadProfileIntersection intersection) {
            return (float) plotX.applyAsDouble(intersection);
        }

        float plotY(double elevation) {
            return (float) plotY.applyAsDouble(elevation);
        }
    }

    public static boolean atGradeElevationConflict(RoadProfileIntersection intersection) {
        if (intersection == null || intersection.gradeSeparated()) {
            return false;
        }
        return Math.abs(intersection.currentRoadElevation() - intersection.otherRoadElevation())
            > AT_GRADE_ELEVATION_TOLERANCE;
    }

    public static double sharedAtGradeElevation(RoadProfileIntersection intersection) {
        if (intersection == null) {
            return 0.0;
        }
        return (intersection.currentRoadElevation() + intersection.otherRoadElevation()) * 0.5;
    }

    public static boolean otherRoadAboveCurrent(RoadProfileIntersection intersection) {
        return intersection != null
            && intersection.otherRoadElevation() > intersection.currentRoadElevation();
    }

    public static ProfileChartInteraction.IntersectionHit hitIntersection(
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY) {
        return hitIntersection(
            intersections,
            layout,
            range.totalStation(),
            range.minElevation(),
            range.maxElevation(),
            mouseX,
            mouseY);
    }

    public static ProfileChartInteraction.IntersectionHit hitIntersection(
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            double totalStation,
            double minElevation,
            double maxElevation,
            float mouseX,
            float mouseY) {
        return hitIntersection(
            intersections,
            coordinatesForLayout(layout, totalStation, minElevation, maxElevation),
            mouseX,
            mouseY);
    }

    public static void drawIntersectionMarkers(
            ImDrawList drawList,
            List<RoadProfileIntersection> intersections,
            int selectedIndex,
            ProfileChartLayout layout,
            double totalStation,
            double minElevation,
            double maxElevation,
            ProfileChartRenderMode mode) {
        drawIntersectionMarkers(
            drawList,
            intersections,
            selectedIndex,
            coordinatesForLayout(layout, totalStation, minElevation, maxElevation),
            mode);
    }

    private static IntersectionMarkerCoordinates coordinatesForLayout(
            ProfileChartLayout layout,
            double totalStation,
            double minElevation,
            double maxElevation) {
        return new IntersectionMarkerCoordinates(
            intersection -> layout.plotX(intersection.roadStation(), totalStation),
            elevation -> layout.plotY(elevation, minElevation, maxElevation));
    }

    private static ProfileChartInteraction.IntersectionHit hitIntersection(
            List<RoadProfileIntersection> intersections,
            IntersectionMarkerCoordinates coordinates,
            float mouseX,
            float mouseY) {
        if (intersections == null || intersections.isEmpty()) {
            return null;
        }
        double bestDist = HIT_RADIUS_SQ;
        ProfileChartInteraction.IntersectionHit best = null;
        for (int i = 0; i < intersections.size(); i++) {
            RoadProfileIntersection intersection = intersections.get(i);
            float x = coordinates.plotX(intersection);
            if (intersection.gradeSeparated()) {
                float currentY = coordinates.plotY(intersection.currentRoadElevation());
                float otherY = coordinates.plotY(intersection.otherRoadElevation());
                double currentDist = distanceSquared(mouseX, mouseY, x, currentY);
                double otherDist = distanceSquared(mouseX, mouseY, x, otherY);
                if (currentDist <= bestDist) {
                    bestDist = currentDist;
                    best = new ProfileChartInteraction.IntersectionHit(
                        i, ProfileChartInteraction.IntersectionDragTarget.CURRENT);
                }
                if (otherDist <= bestDist) {
                    bestDist = otherDist;
                    best = new ProfileChartInteraction.IntersectionHit(
                        i, ProfileChartInteraction.IntersectionDragTarget.OTHER);
                }
            } else if (atGradeElevationConflict(intersection)) {
                float currentY = coordinates.plotY(intersection.currentRoadElevation());
                float otherY = coordinates.plotY(intersection.otherRoadElevation());
                double currentDist = distanceSquared(mouseX, mouseY, x, currentY);
                double otherDist = distanceSquared(mouseX, mouseY, x, otherY);
                if (currentDist <= bestDist) {
                    bestDist = currentDist;
                    best = new ProfileChartInteraction.IntersectionHit(
                        i, ProfileChartInteraction.IntersectionDragTarget.CURRENT);
                }
                if (otherDist <= bestDist) {
                    bestDist = otherDist;
                    best = new ProfileChartInteraction.IntersectionHit(
                        i, ProfileChartInteraction.IntersectionDragTarget.OTHER);
                }
            } else {
                float sharedY = coordinates.plotY(sharedAtGradeElevation(intersection));
                double sharedDist = distanceSquared(mouseX, mouseY, x, sharedY);
                if (sharedDist <= bestDist) {
                    bestDist = sharedDist;
                    best = new ProfileChartInteraction.IntersectionHit(
                        i, ProfileChartInteraction.IntersectionDragTarget.SHARED);
                }
            }
        }
        return best;
    }

    private static void drawIntersectionMarkers(
            ImDrawList drawList,
            List<RoadProfileIntersection> intersections,
            int selectedIndex,
            IntersectionMarkerCoordinates coordinates,
            ProfileChartRenderMode mode) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        for (int i = 0; i < intersections.size(); i++) {
            RoadProfileIntersection intersection = intersections.get(i);
            float x = coordinates.plotX(intersection);
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
                float currentY = coordinates.plotY(intersection.currentRoadElevation());
                float otherY = coordinates.plotY(intersection.otherRoadElevation());
                int connectorColor = intersection.steepGradeWarning()
                    ? COLOR_INTERSECTION_WARNING
                    : markerColor;
                if (mode.showIntersectionConnectors()) {
                    drawList.addLine(x, currentY, x, otherY, connectorColor, 1.6f);
                }
                drawList.addCircleFilled(x, currentY, 3.5f, markerColor);
                drawList.addCircle(x, currentY, 4.5f, COLOR_BG, 12, 1.2f);
                drawDiamond(drawList, x, otherY, 5.5f, COLOR_OTHER_ROAD);
                drawList.addCircle(x, otherY, 6.5f, COLOR_BG, 12, 1.2f);
                if (intersection.steepGradeWarning()) {
                    drawWarningBadge(drawList, x, otherY, COLOR_INTERSECTION_WARNING);
                }
                if (mode.showIntersectionLabels()) {
                    renderIntersectionLabel(
                        drawList, intersection, x, otherY, markerColor, otherRoadAboveCurrent(intersection));
                }
            } else if (atGradeElevationConflict(intersection)) {
                float currentY = coordinates.plotY(intersection.currentRoadElevation());
                float otherY = coordinates.plotY(intersection.otherRoadElevation());
                int conflictColor = COLOR_INTERSECTION_WARNING;
                if (mode.showIntersectionConnectors()) {
                    drawList.addLine(x, currentY, x, otherY, conflictColor, 1.6f);
                }
                drawList.addCircleFilled(x, currentY, 4.5f, conflictColor);
                drawList.addCircle(x, currentY, 5.5f, COLOR_BG, 12, 1.2f);
                drawList.addCircleFilled(x, otherY, 4.5f, conflictColor);
                drawList.addCircle(x, otherY, 5.5f, COLOR_BG, 12, 1.2f);
                drawWarningBadge(drawList, x, (currentY + otherY) * 0.5f, conflictColor);
                if (mode.showIntersectionLabels()) {
                    renderIntersectionLabel(
                        drawList, intersection, x, (currentY + otherY) * 0.5f, conflictColor, false);
                }
            } else {
                float sharedY = coordinates.plotY(sharedAtGradeElevation(intersection));
                drawList.addCircleFilled(x, sharedY, 5.5f, markerColor);
                drawList.addCircle(x, sharedY, 6.5f, COLOR_BG, 12, 1.5f);
                if (mode.showIntersectionLabels()) {
                    renderIntersectionLabel(drawList, intersection, x, sharedY, markerColor, false);
                }
            }
        }
    }

    private static double distanceSquared(float mouseX, float mouseY, float x, float y) {
        double dx = mouseX - x;
        double dy = mouseY - y;
        return dx * dx + dy * dy;
    }

    private static void renderIntersectionLabel(
            ImDrawList drawList,
            RoadProfileIntersection intersection,
            float x,
            float anchorY,
            int markerColor,
            boolean otherAbove) {
        String label = intersection.otherRoadLabel();
        if (label == null || label.isBlank()) {
            return;
        }
        float textY = otherAbove ? anchorY - ImGui.getTextLineHeight() - 2f : anchorY + 4f;
        drawList.addText(x + 6f, textY, markerColor, label);
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
        drawList.addText(cx + 4f, cy - 11f, color, "!");
    }
}
