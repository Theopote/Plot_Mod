package com.plot.plugin.road.pipeline.profile.environment;

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
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSolver;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterCrossingP1ModeTest {

    @Test
    void autoSmoothFlatRoadRespectsWaterClearance() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        Scenario scenario = riverScenario(100.0, 35.0, 65.0, 50, 64);

        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            scenario.segments(),
            scenario.terrain(),
            scenario.network(),
            scenario.edge(),
            config,
            2.5,
            null,
            null,
            support);

        for (int i = 0; i < result.profileDesignElevations().size(); i++) {
            Integer water = result.profileWaterHeights().get(i);
            if (water != null) {
                assertTrue(
                    result.profileDesignElevations().get(i) >= water
                        + com.plot.plugin.road.pipeline.profile.environment.WaterCrossingSettings
                            .defaults().waterRoadClearanceBlocks() - 1e-6,
                    "AUTO_SMOOTH must clear water at station " + i);
            }
        }
    }

    @Test
    void flatRoadAutoRaisesAboveWater() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        Scenario scenario = riverScenario(100.0, 35.0, 65.0, 50, 68);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 65.0),
            PointOfVerticalIntersection.of(100.0, 65.0)));

        ProfileSolveResult result = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            0.0,
            100.0,
            scenario.segments(),
            scenario.terrain(),
            2.5,
            null,
            null,
            support,
            config,
            RoadVerticalMode.FLAT,
            TerrainFollowPreset.STANDARD);

        assertTrue(result.waterConstraintFeasible());
        for (double elevation : result.profileDesignElevations()) {
            assertTrue(elevation >= 69.0 - 1e-6, "flat road should auto-raise above water + clearance");
        }
    }

    @Test
    void manualFlatBelowWaterMarkedInfeasible() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        Scenario scenario = riverScenario(100.0, 35.0, 65.0, 50, 68);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 65.0),
            PointOfVerticalIntersection.of(100.0, 65.0)));

        ProfileSolveResult result = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            0.0,
            100.0,
            scenario.segments(),
            scenario.terrain(),
            2.5,
            65,
            65,
            support,
            config,
            RoadVerticalMode.FLAT,
            TerrainFollowPreset.STANDARD);

        assertFalse(result.waterConstraintFeasible());
    }

    private static Scenario riverScenario(
            double pathLengthMeters,
            double riverStartX,
            double riverEndX,
            int bedY,
            int waterY) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("river-road");
        road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(pathLengthMeters, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(pathLengthMeters, 0)),
            road.getId());
        List<PathSegment> segments = sampledSegments(pathLengthMeters, 10.0);
        TerrainSampler terrain = riverTerrain(68, bedY, waterY, riverStartX, riverEndX);
        return new Scenario(network, edge, segments, terrain);
    }

    private record Scenario(
            RoadNetwork network,
            RoadEdge edge,
            List<PathSegment> segments,
            TerrainSampler terrain) {
    }

    private static TerrainSampler riverTerrain(
            int landY,
            int bedY,
            int waterY,
            double riverStartX,
            double riverEndX) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return inRiver(planPoint.x) ? bedY : landY;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return inRiver(planPoint.x) ? OptionalInt.of(waterY) : OptionalInt.empty();
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= (inRiver(worldX) ? bedY : landY);
            }

            private boolean inRiver(double x) {
                return x >= riverStartX && x <= riverEndX;
            }
        };
    }

    private static List<PathSegment> sampledSegments(double lengthMeters, double stepMeters) {
        Vec2d start = new Vec2d(0, 0);
        Vec2d end = new Vec2d(lengthMeters, 0);
        List<PathSegment> result = new ArrayList<>();
        Vec2d delta = end.subtract(start);
        int count = Math.max(1, (int) Math.round(lengthMeters / stepMeters));
        for (int i = 0; i < count; i++) {
            result.add(new PathSegment(
                start.add(delta.multiply((double) i / count)),
                start.add(delta.multiply((double) (i + 1) / count))));
        }
        return result;
    }
}
