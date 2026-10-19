package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JunctionNegativeElevationTest {
    @ParameterizedTest
    @ValueSource(ints = {-32, -1, 0, 64})
    void peripheralCellUsesSignedProfileElevation(int elevation) {
        RoadNetwork network = new RoadNetwork();
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(20, 0));
        var edge = network.createEdge(start.getId(), end.getId(),
            List.of(start.getPosition(), end.getPosition()));
        var result = new RoadGenerationResult(20);
        result.buildProfile = BuildHeightProfile.fromSamples(List.of(
            new BuildHeightSample(0, elevation, elevation),
            new BuildHeightSample(20, elevation, elevation)));

        for (boolean useBuildProfile : List.of(true, false)) {
            if (!useBuildProfile) {
                result.buildProfile = BuildHeightProfile.inactive();
                result.profileDistances = List.of(0.0, 20.0);
                result.profileBuildHeights = List.of(elevation, elevation);
            }
            assertEquals(elevation, JunctionCellElevationResolver.resolve(
                new Vec2d(10, 0), start.getPosition(), start.getId(), 68,
                List.of(edge), network, new RoadSystemConfig("negative-elevation"),
                Map.of(edge.getId(), result), 1.0, 5.0));
        }

        result.profileDistances = List.of();
        result.profileBuildHeights = List.of();
        assertNull(JunctionCellElevationResolver.elevationAtStation(result, 10));
        assertEquals(68, JunctionCellElevationResolver.resolve(
            new Vec2d(10, 0), start.getPosition(), start.getId(), 68,
            List.of(edge), network, new RoadSystemConfig("missing-elevation"),
            Map.of(edge.getId(), result), 1.0, 5.0));
    }
}
