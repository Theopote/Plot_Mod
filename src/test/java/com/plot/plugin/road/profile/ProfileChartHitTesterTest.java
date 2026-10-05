package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileChartHitTesterTest {

    @Test
    void rightClickMiddlePviRequestsContextMenu() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(100.0, 60.0, 80.0);
        List<ProfileControlPoint> controls = List.of(
            pvi(0, 0, 70, ProfilePointRole.START_ENDPOINT),
            pvi(1, 50, 75, ProfilePointRole.INTERIOR_PVI),
            pvi(2, 100, 70, ProfilePointRole.END_ENDPOINT));
        float mouseX = layout.plotX(50, range.totalStation());
        float mouseY = layout.plotY(75, range.minElevation(), range.maxElevation());

        ProfileControlPoint hit = ProfileChartHitTester.hitPvi(
            controls, layout, range, mouseX, mouseY,
            ProfileChartHitTester.DEFAULT_PVI_HIT_RADIUS_PX);
        assertNotNull(hit);
        assertEquals(1, hit.pviIndex());
        assertTrue(ProfileChartHitTester.canOpenPviContextMenu(hit));
    }

    @Test
    void junctionFixedDoesNotOpenContextMenu() {
        ProfileControlPoint junctionFixed = new ProfileControlPoint(
            1, 50, 75, ProfilePointRole.JUNCTION_FIXED, null, null, false, false);
        assertFalse(ProfileChartHitTester.canOpenPviContextMenu(junctionFixed));
    }

    @Test
    void clickCurveHandleStartsCurveDrag() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(100.0, 60.0, 80.0);
        List<RoadLongitudinalProfileRenderer.CurveHandle> handles = List.of(
            new RoadLongitudinalProfileRenderer.CurveHandle(1, 40, 72, true),
            new RoadLongitudinalProfileRenderer.CurveHandle(1, 60, 72, false));
        float mouseX = layout.plotX(40, range.totalStation());
        float mouseY = layout.plotY(72, range.minElevation(), range.maxElevation());

        ProfileChartHitTester.CurveHandleHit hit = ProfileChartHitTester.hitCurveHandle(
            handles, layout, range, mouseX, mouseY,
            ProfileChartHitTester.DEFAULT_CURVE_HANDLE_HIT_RADIUS_PX);
        assertNotNull(hit);
        assertEquals(1, hit.pviIndex());
        assertEquals(
            RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.LEFT,
            hit.side());
    }

    @Test
    void doubleClickTooCloseDoesNotInsert() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(50, 75),
            PointOfVerticalIntersection.of(100, 70)));
        assertFalse(VerticalProfileControlPoints.canInsertAt(alignment, 50, 100));
        assertFalse(VerticalProfileControlPoints.canInsertAt(alignment, 5, 100));

        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        RoadVerticalAlignment tight = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(minRun * 1.25, 75),
            PointOfVerticalIntersection.of(100, 70)));
        assertFalse(VerticalProfileControlPoints.canInsertAt(tight, minRun * 0.625, 100));
    }

    @Test
    void hitPviReturnsNullWhenOutsideRadius() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(100.0, 60.0, 80.0);
        List<ProfileControlPoint> controls = List.of(
            pvi(0, 0, 70, ProfilePointRole.START_ENDPOINT),
            pvi(1, 50, 75, ProfilePointRole.INTERIOR_PVI),
            pvi(2, 100, 70, ProfilePointRole.END_ENDPOINT));
        assertNull(ProfileChartHitTester.hitPvi(
            controls, layout, range, layout.plotLeft() - 40f, layout.plotTop() - 40f, 4f));
    }

    private static ProfileControlPoint pvi(
            int index,
            double station,
            double elevation,
            ProfilePointRole role) {
        return new ProfileControlPoint(
            index, station, elevation, role, 10.0, -10.0, false, true);
    }
}
