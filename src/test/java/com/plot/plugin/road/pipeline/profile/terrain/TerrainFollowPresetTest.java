package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainFollowPresetTest {

    @Test
    void fromStoredParsesKnownValues() {
        assertEquals(TerrainFollowPreset.GENTLE, TerrainFollowPreset.fromStored("GENTLE"));
        assertEquals(TerrainFollowPreset.STANDARD, TerrainFollowPreset.fromStored("STANDARD"));
        assertNull(TerrainFollowPreset.fromStored(null));
        assertNull(TerrainFollowPreset.fromStored("UNKNOWN"));
    }

    @Test
    void roadInheritsConfigTerrainStyleWhenUnset() {
        Road road = new Road("road-1");
        RoadSystemConfig config = new RoadSystemConfig("test");
        assertNull(road.getStoredTerrainStyle());
        assertEquals(RoadTerrainStyle.BALANCED, road.getEffectiveTerrainStyle(config));
        assertEquals(TerrainFollowPreset.STANDARD, road.getEffectiveTerrainFollowPreset(config));
    }

    @Test
    void solverUsesRoadTerrainFollowPresetForGuideLine() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("preset-road");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(8.0f);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < 50.0 ? 60 : 75;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };

        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(20, 0)),
            new PathSegment(new Vec2d(20, 0), new Vec2d(30, 0)),
            new PathSegment(new Vec2d(30, 0), new Vec2d(40, 0)),
            new PathSegment(new Vec2d(40, 0), new Vec2d(50, 0)),
            new PathSegment(new Vec2d(50, 0), new Vec2d(60, 0)),
            new PathSegment(new Vec2d(60, 0), new Vec2d(70, 0)),
            new PathSegment(new Vec2d(70, 0), new Vec2d(80, 0)),
            new PathSegment(new Vec2d(80, 0), new Vec2d(90, 0)),
            new PathSegment(new Vec2d(90, 0), new Vec2d(100, 0)));
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(8.0f);
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);

        road.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        ProfileSolveResult gentle = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        road.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        ProfileSolveResult tight = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        assertNotEquals(gentle.profileGuideLine(), tight.profileGuideLine(),
            "different terrain follow presets should change smoothed guide trend");
        assertTrue(meanAbsoluteError(gentle.profileGuideLine(), gentle.profileGroundHeights())
            >= meanAbsoluteError(tight.profileGuideLine(), tight.profileGroundHeights()),
            "gentle preset should track raw ground less closely than tight on step terrain");
    }

    private static double meanAbsoluteError(List<Integer> actual, List<Integer> reference) {
        double sum = 0.0;
        int count = Math.min(actual.size(), reference.size());
        for (int i = 0; i < count; i++) {
            sum += Math.abs(actual.get(i) - reference.get(i));
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }
}
