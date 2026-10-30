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
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动纵断面遇水面须抬到水面以上；编辑曲线可自行压到水面以下。
 */
class AutomaticProfileWaterClearanceTest {

    @Test
    void fitTerrainAutomaticRaisesAboveRiverSurface() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        Scenario scenario = riverScenario(120.0, 40.0, 80.0, 50, 64, RoadVerticalMode.FIT_TERRAIN);

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

        int clearance = WaterCrossingSettings.defaults().waterRoadClearanceBlocks();
        for (int i = 0; i < result.profileDesignElevations().size(); i++) {
            Integer water = result.profileWaterHeights().get(i);
            if (water == null) {
                continue;
            }
            assertTrue(
                result.profileDesignElevations().get(i) >= water + clearance - 1e-6,
                "AUTO FIT_TERRAIN must clear water+clearance at station " + i
                    + " design=" + result.profileDesignElevations().get(i) + " water=" + water);
            assertTrue(
                result.profileBuildHeights().get(i) >= water + clearance - 1,
                "build height must also stay above water at station " + i);
        }
        assertTrue(result.waterConstraintFeasible());
    }

    @Test
    void editedManualProfileMaySitBelowWaterWithoutForcedRaise() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        Scenario scenario = riverScenario(100.0, 35.0, 65.0, 50, 68, RoadVerticalMode.FIT_TERRAIN);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 60.0)));

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
            RoadVerticalMode.MANUAL_PROFILE,
            TerrainFollowPreset.STANDARD);

        assertFalse(result.waterConstraintFeasible(), "edited curve below water is flagged, not auto-raised");
        for (double elevation : result.profileDesignElevations()) {
            assertTrue(elevation <= 60.0 + 1e-6, "MANUAL_PROFILE must keep user elevations");
        }
    }

    private static Scenario riverScenario(
            double pathLengthMeters,
            double riverStartX,
            double riverEndX,
            int bedY,
            int waterY,
            RoadVerticalMode mode) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("auto-water");
        road.setVerticalMode(mode);
        road.setProfileSource(ProfileSource.AUTO);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(pathLengthMeters, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(pathLengthMeters, 0)),
            road.getId());
        List<PathSegment> segments = sampledSegments(pathLengthMeters, 5.0);
        TerrainSampler terrain = riverTerrain(66, bedY, waterY, riverStartX, riverEndX);
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
        List<PathSegment> result = new ArrayList<>();
        int count = Math.max(1, (int) Math.round(lengthMeters / stepMeters));
        for (int i = 0; i < count; i++) {
            double x0 = lengthMeters * i / count;
            double x1 = lengthMeters * (i + 1) / count;
            result.add(new PathSegment(new Vec2d(x0, 0), new Vec2d(x1, 0)));
        }
        return result;
    }
}
