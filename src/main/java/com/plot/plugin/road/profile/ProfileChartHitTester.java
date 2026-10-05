package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadLongitudinalProfileRenderer;

import java.util.List;

/** Pure hit-testing for profile chart interactions (unit-testable, ImGui-free geometry). */
public final class ProfileChartHitTester {

    public static final float DEFAULT_PVI_HIT_RADIUS_PX = 12f;
    public static final float DEFAULT_CURVE_HANDLE_HIT_RADIUS_PX = 8f;

    private ProfileChartHitTester() {
    }

    public record CurveHandleHit(
            int pviIndex,
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide side) {
    }

    public static ProfileControlPoint hitPvi(
            List<ProfileControlPoint> controls,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY,
            float maxDistancePx) {
        int index = hitPviIndex(controls, layout, range, mouseX, mouseY, maxDistancePx);
        if (index < 0 || controls == null) {
            return null;
        }
        for (ProfileControlPoint point : controls) {
            if (point.pviIndex() == index) {
                return point;
            }
        }
        return null;
    }

    public static int hitPviIndex(
            List<ProfileControlPoint> controls,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY,
            float maxDistancePx) {
        if (controls == null || controls.isEmpty()) {
            return -1;
        }
        double best = maxDistancePx * maxDistancePx;
        int nearest = -1;
        for (ProfileControlPoint point : controls) {
            float x = layout.plotX(point.roadStation(), range.totalStation());
            float y = layout.plotY(point.elevation(), range.minElevation(), range.maxElevation());
            double dist = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (dist <= best) {
                best = dist;
                nearest = point.pviIndex();
            }
        }
        return nearest;
    }

    public static CurveHandleHit hitCurveHandle(
            List<RoadLongitudinalProfileRenderer.CurveHandle> handles,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY,
            float maxDistancePx) {
        if (handles == null || handles.isEmpty()) {
            return null;
        }
        double best = maxDistancePx * maxDistancePx;
        CurveHandleHit nearest = null;
        for (RoadLongitudinalProfileRenderer.CurveHandle handle : handles) {
            float x = layout.plotX(handle.localDistance(), range.totalStation());
            float y = layout.plotY(handle.elevation(), range.minElevation(), range.maxElevation());
            double distance = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (distance <= best) {
                best = distance;
                nearest = new CurveHandleHit(
                    handle.pviIndex(),
                    handle.leftSide()
                        ? RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.LEFT
                        : RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.RIGHT);
            }
        }
        return nearest;
    }

    public static RoadLongitudinalProfileRenderer.IntersectionHit hitIntersection(
            List<RoadProfileIntersection> intersections,
            ProfileChartLayout layout,
            RoadProfilePlotRange range,
            float mouseX,
            float mouseY) {
        return RoadLongitudinalProfileRenderer.hitIntersectionRoad(
            intersections, layout, range, mouseX, mouseY);
    }

    /** Endpoints may open menu (delete disabled); junction-fixed points do not. */
    public static boolean canOpenPviContextMenu(ProfileControlPoint point) {
        return point != null
            && point.elevationEditable()
            && point.role() != ProfilePointRole.JUNCTION_FIXED;
    }
}
