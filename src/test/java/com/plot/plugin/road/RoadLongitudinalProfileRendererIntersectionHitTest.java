package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.profile.ProfileChartLayout;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfilePlotRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLongitudinalProfileRendererIntersectionHitTest {

    @Test
    void gradeSeparatedHitReturnsCurrentOrOtherMarker() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node",
            "roadA",
            "roadB",
            "Road B",
            50.0,
            50.0,
            76.0,
            68.0,
            section,
            true,
            true,
            4.0,
            false);

        float x0 = 0f;
        float y0 = 0f;
        float width = 200f;
        float height = 120f;
        float plotX = x0 + 10f + (50f / 100f) * (width - 20f);

        RoadLongitudinalProfileRenderer.IntersectionHit currentHit =
            RoadLongitudinalProfileRenderer.hitIntersectionForTest(
                List.of(intersection),
                100.0,
                60,
                80,
                x0,
                y0,
                width,
                height,
                plotX,
                yToPlot(76, 60, 80, y0, height));
        assertNotNull(currentHit);
        assertEquals(0, currentHit.index());
        assertEquals(
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.CURRENT,
            currentHit.target());

        RoadLongitudinalProfileRenderer.IntersectionHit otherHit =
            RoadLongitudinalProfileRenderer.hitIntersectionForTest(
                List.of(intersection),
                100.0,
                60,
                80,
                x0,
                y0,
                width,
                height,
                plotX,
                yToPlot(68, 60, 80, y0, height));
        assertNotNull(otherHit);
        assertEquals(0, otherHit.index());
        assertEquals(
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.OTHER,
            otherHit.target());
    }

    @Test
    void atGradeHitReturnsSharedMarker() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node",
            "roadA",
            "roadB",
            "Road B",
            40.0,
            40.0,
            70.0,
            70.0,
            section,
            false,
            false,
            0.0,
            false);

        float x0 = 0f;
        float y0 = 0f;
        float width = 200f;
        float height = 120f;
        float plotX = x0 + 10f + (40f / 80f) * (width - 20f);

        RoadLongitudinalProfileRenderer.IntersectionHit hit =
            RoadLongitudinalProfileRenderer.hitIntersectionForTest(
                List.of(intersection),
                80.0,
                60,
                80,
                x0,
                y0,
                width,
                height,
                plotX,
                yToPlot(70, 60, 80, y0, height));
        assertNotNull(hit);
        assertEquals(0, hit.index());
        assertEquals(
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.SHARED,
            hit.target());
    }

    @Test
    void sharedAtGradeElevationUsesAverageWhenAligned() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node", "roadA", "roadB", "Road B", 40.0, 40.0, 70.0, 70.0,
            section, false, false, 0.0, false);
        assertFalse(RoadLongitudinalProfileRenderer.atGradeElevationConflict(intersection));
        assertEquals(70.0, RoadLongitudinalProfileRenderer.sharedAtGradeElevation(intersection), 1e-6);
    }

    @Test
    void roadStationHitUsesProfileChartLayout() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node",
            "roadA",
            "roadB",
            "Road B",
            50.0,
            50.0,
            76.0,
            68.0,
            section,
            true,
            true,
            4.0,
            false);

        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(100.0, 60.0, 80.0);
        float plotX = layout.plotX(50.0, range.totalStation());
        float currentY = layout.plotY(76.0, range.minElevation(), range.maxElevation());

        RoadLongitudinalProfileRenderer.IntersectionHit hit =
            RoadLongitudinalProfileRenderer.hitIntersectionRoad(
                List.of(intersection),
                layout,
                range,
                plotX,
                currentY);
        assertNotNull(hit);
        assertEquals(0, hit.index());
        assertEquals(
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.CURRENT,
            hit.target());
    }

    @Test
    void atGradeElevationConflictWhenRoadsDisagree() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node", "roadA", "roadB", "Road B", 40.0, 40.0, 72.0, 68.0,
            section, false, false, 0.0, false);
        assertTrue(RoadLongitudinalProfileRenderer.atGradeElevationConflict(intersection));
    }

    private static float yToPlot(int elevation, int minHeight, int maxHeight, float y0, float height) {
        float padding = 10f;
        float plotY0 = y0 + padding;
        float plotHeight = height - 2 * padding;
        float span = Math.max(1, maxHeight - minHeight);
        return plotY0 + (maxHeight - elevation) / span * plotHeight;
    }
}
