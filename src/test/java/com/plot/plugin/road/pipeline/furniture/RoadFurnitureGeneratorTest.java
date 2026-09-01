package com.plot.plugin.road.pipeline.furniture;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadFurnitureGeneratorTest {

    @Test
    void streetlightsUseSharedRoadVoxelElevationInsteadOfTerrain() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 72)));
        DesignElevationSource elevations = new DesignElevationSource(alignment, 0, 20, 20);
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(
            new RoadSystemConfig("furniture-profile"));

        RoadFurnitureGenerator.generateStreetlights(
            solids,
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 20, 20, 20, 20, 0)),
            CrossSectionBuildContext.fixed(section),
            4,
            1.0,
            elevations);

        var lights = solids.byLayer(RoadSolidLayer.STREETLIGHT);
        assertFalse(lights.isEmpty());
        assertTrue(lights.stream().allMatch(light -> light.elevation() >= 71));
        assertTrue(lights.stream().allMatch(light ->
            light.elevation() == elevations.elevationAtLocalDistance(light.planPoint().x) + 1));
    }
}
