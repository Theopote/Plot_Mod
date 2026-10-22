package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AutoDesignIgnoresStoredEditedCurveTest {

    private static final String ROAD_ID = "test-road";

    @Test
    void autoDesignMustIgnoreStoredEditedCurve() {
        RoadNetwork networkA = createRoadNetwork();
        RoadNetwork networkB = networkA.snapshot();

        Road roadB = networkB.getRoad(ROAD_ID);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 20.0),
            PointOfVerticalIntersection.of(50.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 20.0))));
        roadB.setProfileSource(ProfileSource.AUTO);
        roadB.setEditedGeometrySignature(0);

        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(networkB);

        ProfileSolveResult resultA = solveAuto(networkA);
        ProfileSolveResult resultB = solveAuto(networkB);

        assertEquals(resultA.profileDesignElevations(), resultB.profileDesignElevations());
        assertEquals(resultA.profileBuildHeights(), resultB.profileBuildHeights());
    }

    @Test
    void autoDesignMatchesAfterPropagatedNodeElevationIsCleared() {
        RoadNetwork networkA = createRoadNetwork();
        RoadNetwork networkB = networkA.snapshot();

        RoadNode endB = findEndNode(networkB);
        endB.setManualElevation(88.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(networkB);

        ProfileSolveResult resultA = solveAuto(networkA);
        ProfileSolveResult resultB = solveAuto(networkB);

        assertEquals(resultA.profileDesignElevations(), resultB.profileDesignElevations());
        assertEquals(resultA.profileBuildHeights(), resultB.profileBuildHeights());
    }

    @Test
    void autoDesignDiffersWhenUserLockedNodeElevationPresent() {
        RoadNetwork networkA = createRoadNetwork();
        RoadNetwork networkB = networkA.snapshot();

        findEndNode(networkB).setManualElevation(72.0, ManualElevationOrigin.USER);
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(networkB);

        ProfileSolveResult resultA = solveAuto(networkA);
        ProfileSolveResult resultB = solveAuto(networkB);

        assertNotEquals(resultA.profileDesignElevations(), resultB.profileDesignElevations());
        assertEquals(72.0, resultB.profileDesignElevations().getLast(), 1e-6);
    }

    private static ProfileSolveResult solveAuto(RoadNetwork network) {
        RoadNetwork generationSnapshot = network.snapshot();
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(generationSnapshot);

        Road road = generationSnapshot.getRoad(ROAD_ID);
        RoadEdge edge = generationSnapshot.getEdges().values().iterator().next();
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(10.0f);
        List<PathSegment> segments = sampledSegments(100.0, 10.0);
        RoadNode start = generationSnapshot.getNode(edge.getStartNodeId());
        RoadNode end = generationSnapshot.getNode(edge.getEndNodeId());
        return RoadProfileSolver.solveForEdge(
            segments,
            flatTerrain(),
            generationSnapshot,
            edge,
            config,
            2.5,
            userLockedHeight(start),
            userLockedHeight(end),
            ProfileSolveSupport.fromConfig(config, ignored -> 1.0));
    }

    private static Integer userLockedHeight(RoadNode node) {
        if (node == null
                || node.getManualElevation() == null
                || node.getManualElevationOrigin() != ManualElevationOrigin.USER) {
            return null;
        }
        return node.getManualElevation().intValue();
    }

    private static RoadNode findEndNode(RoadNetwork network) {
        return network.getNodes().values().stream()
            .filter(node -> node.getPosition().x > 0)
            .findFirst()
            .orElseThrow();
    }

    private static RoadNetwork createRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(ROAD_ID);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.AUTO);
        road.setMaxSlope(10.0f);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
        return network;
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

    private static TerrainSampler flatTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }
}
