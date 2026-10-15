package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileChartIntersectionSupportTest {

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

        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 200f, 120f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(100.0, 60.0, 80.0);
        float plotX = layout.plotX(50.0, range.totalStation());

        ProfileChartInteraction.IntersectionHit currentHit =
            ProfileChartIntersectionSupport.hitIntersection(
                List.of(intersection),
                layout,
                range,
                plotX,
                layout.plotY(76.0, range.minElevation(), range.maxElevation()));
        assertNotNull(currentHit);
        assertEquals(0, currentHit.index());
        assertEquals(
            ProfileChartInteraction.IntersectionDragTarget.CURRENT,
            currentHit.target());

        ProfileChartInteraction.IntersectionHit otherHit =
            ProfileChartIntersectionSupport.hitIntersection(
                List.of(intersection),
                layout,
                range,
                plotX,
                layout.plotY(68.0, range.minElevation(), range.maxElevation()));
        assertNotNull(otherHit);
        assertEquals(0, otherHit.index());
        assertEquals(
            ProfileChartInteraction.IntersectionDragTarget.OTHER,
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

        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 200f, 120f);
        RoadProfilePlotRange range = new RoadProfilePlotRange(80.0, 60.0, 80.0);
        float plotX = layout.plotX(40.0, range.totalStation());

        ProfileChartInteraction.IntersectionHit hit =
            ProfileChartIntersectionSupport.hitIntersection(
                List.of(intersection),
                layout,
                range,
                plotX,
                layout.plotY(70.0, range.minElevation(), range.maxElevation()));
        assertNotNull(hit);
        assertEquals(0, hit.index());
        assertEquals(
            ProfileChartInteraction.IntersectionDragTarget.SHARED,
            hit.target());
    }

    @Test
    void sharedAtGradeElevationUsesAverageWhenAligned() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node", "roadA", "roadB", "Road B", 40.0, 40.0, 70.0, 70.0,
            section, false, false, 0.0, false);
        assertFalse(ProfileChartIntersectionSupport.atGradeElevationConflict(intersection));
        assertEquals(70.0, ProfileChartIntersectionSupport.sharedAtGradeElevation(intersection), 1e-6);
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

        ProfileChartInteraction.IntersectionHit hit =
            ProfileChartIntersectionSupport.hitIntersection(
                List.of(intersection),
                layout,
                range,
                plotX,
                currentY);
        assertNotNull(hit);
        assertEquals(0, hit.index());
        assertEquals(
            ProfileChartInteraction.IntersectionDragTarget.CURRENT,
            hit.target());
    }

    @Test
    void edgeLocalHitUsesLocalDistanceMapping() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node",
            "roadA",
            "roadB",
            "Road B",
            50.0,
            999.0,
            76.0,
            68.0,
            section,
            true,
            true,
            4.0,
            false);

        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        float plotX = layout.plotX(50.0, 100.0);
        float currentY = layout.plotY(76.0, 60.0, 80.0);

        ProfileChartInteraction.IntersectionHit hit =
            ProfileChartIntersectionSupport.hitIntersectionEdgeLocal(
                List.of(intersection),
                100.0,
                1.0,
                60,
                80,
                0f,
                0f,
                400f,
                200f,
                plotX,
                currentY);
        assertNotNull(hit);
        assertEquals(0, hit.index());
        assertEquals(
            ProfileChartInteraction.IntersectionDragTarget.CURRENT,
            hit.target());
    }

    @Test
    void atGradeElevationConflictWhenRoadsDisagree() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node", "roadA", "roadB", "Road B", 40.0, 40.0, 72.0, 68.0,
            section, false, false, 0.0, false);
        assertTrue(ProfileChartIntersectionSupport.atGradeElevationConflict(intersection));
    }
}
