package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileCurveFitter;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.plugin.road.vertical.VoxelVerticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end contract for junction propagation, profile solving, voxelization and persistence. */
class RoadVerticalDesignWorkflowTest {

    @Test
    void sharedJunctionDesignSurvivesRegenerationAndSnapshotRoundTrip() {
        Fixture fixture = fixture();

        // The user raises Road B's shared PVI from Y72 to Y78.
        fixture.roadB.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
            fixture.roadB.getVerticalAlignment(), 2, 78));
        VerticalProfileNetworkPropagator.Result propagation =
            VerticalProfileNetworkPropagator.propagate(
                fixture.network, fixture.roadB, road -> road.getEffectiveMaxSlope(fixture.config));

        assertEquals(78, fixture.junctionElevation(), 1e-6);
        assertTrue(propagation.roads().stream().anyMatch(result ->
            result.roadId().equals(fixture.roadA.getId())
                && result.mode() == RoadVerticalMode.AUTO_SMOOTH
                && result.regenerationRequired()));

        List<PointOfVerticalIntersection> manualPvis = fixture.roadB.getVerticalAlignment().getPvis();
        PointOfVerticalIntersection junctionPvi = manualPvis.get(2);
        assertEquals(VerticalControlPointConstraint.JUNCTION_FIXED, junctionPvi.getConstraint());
        assertTrue(manualPvis.get(1).getStation() < 60,
            "the incoming grade start should extend away from the junction");
        assertTrue(manualPvis.get(3).getStation() > 140,
            "the outgoing grade end should extend away from the junction");

        VerticalProfileCurveFitter.Result curve =
            VerticalProfileCurveFitter.fitAt(fixture.roadB.getVerticalAlignment(), 1);
        assertTrue(curve.hasSpace());
        assertTrue(curve.alignment().getPvis().get(1).getCurveLength()
            >= VerticalProfileDesignRules.MIN_VERTICAL_TRANSITION_LENGTH);
        assertFalse(curve.alignment().getPvis().get(2).hasCurve());
        fixture.roadB.setVerticalAlignment(curve.alignment());

        GeneratedProfiles before = generate(fixture);
        assertMaximumGrade(before.roadAContinuous(), fixture.roadA.getEffectiveMaxSlope(fixture.config));
        assertVoxelContinuity(before.roadAVoxel());
        assertVoxelContinuity(before.roadBVoxel());
        assertEquals(before.roadAVoxel().elevationAt(100), before.roadBVoxel().elevationAt(100));
        assertEquals(78, before.roadAVoxel().elevationAt(100));

        String snapshot = fixture.network.toJson();
        RoadNetwork restoredNetwork = RoadNetwork.parseSnapshot(snapshot);
        Fixture restored = fixture.withNetwork(restoredNetwork);
        GeneratedProfiles after = generate(restored);

        assertArrayEquals(before.roadAVoxel().elevations(), after.roadAVoxel().elevations());
        assertArrayEquals(before.roadBVoxel().elevations(), after.roadBVoxel().elevations());
        assertEquals(VerticalControlPointConstraint.JUNCTION_FIXED,
            restored.roadB.getVerticalAlignment().getPvis().get(2).getConstraint());
    }

    private static GeneratedProfiles generate(Fixture fixture) {
        RoadVerticalAlignment left = solveAutoEdge(
            fixture.network, fixture.roadA, fixture.westEdge(), fixture.config,
            fixture.terrain, null, 78);
        RoadVerticalAlignment right = solveAutoEdge(
            fixture.network, fixture.roadA, fixture.eastEdge(), fixture.config,
            fixture.terrain, 78, null);
        List<PointOfVerticalIntersection> combined = new ArrayList<>(left.getPvis());
        for (int i = 1; i < right.pviCount(); i++) {
            PointOfVerticalIntersection pvi = right.getPvis().get(i);
            combined.add(new PointOfVerticalIntersection(
                100 + pvi.getStation(), pvi.getElevation(), pvi.getCurveLength(), pvi.getConstraint()));
        }
        RoadVerticalAlignment roadAProfile = new RoadVerticalAlignment(combined);
        return new GeneratedProfiles(
            roadAProfile,
            VoxelVerticalProfile.fromAlignment(roadAProfile),
            VoxelVerticalProfile.fromAlignment(fixture.roadB.getVerticalAlignment()));
    }

    private static RoadVerticalAlignment solveAutoEdge(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadSystemConfig config,
            TerrainSampler terrain,
            Integer startY,
            Integer endY) {
        List<PathSegment> segments = sampledSegments(edge.getCenterlinePoints().getFirst(),
            edge.getCenterlinePoints().getLast(), 50);
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, startY, endY, support);
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        for (int i = 0; i < result.profileDistances().size(); i++) {
            pvis.add(PointOfVerticalIntersection.of(
                result.profileDistances().get(i), result.profileTargetHeights().get(i)));
        }
        assertEquals(RoadVerticalMode.AUTO_SMOOTH, road.getVerticalMode());
        return new RoadVerticalAlignment(pvis);
    }

    private static List<PathSegment> sampledSegments(Vec2d start, Vec2d end, double step) {
        List<PathSegment> result = new ArrayList<>();
        Vec2d delta = end.subtract(start);
        int count = (int) Math.round(start.distance(end) / step);
        for (int i = 0; i < count; i++) {
            result.add(new PathSegment(
                start.add(delta.multiply((double) i / count)),
                start.add(delta.multiply((double) (i + 1) / count))));
        }
        return result;
    }

    private static void assertMaximumGrade(RoadVerticalAlignment alignment, double limit) {
        List<PointOfVerticalIntersection> pvis = alignment.getPvis();
        for (int i = 1; i < pvis.size(); i++) {
            double grade = Math.abs(VerticalAlignmentGeometry.tangentGradePercent(
                pvis.get(i - 1), pvis.get(i)));
            assertTrue(grade <= limit + 1e-6,
                "grade " + grade + "% exceeds " + limit + "% between stations "
                    + pvis.get(i - 1).getStation() + " and " + pvis.get(i).getStation());
        }
    }

    private static void assertVoxelContinuity(VoxelVerticalProfile profile) {
        int[] elevations = profile.elevations();
        for (int i = 1; i < elevations.length; i++) {
            assertTrue(Math.abs(elevations[i] - elevations[i - 1]) <= 1,
                "voxel profile must never jump by more than one block");
        }
    }

    private static Fixture fixture() {
        RoadSystemConfig config = new RoadSystemConfig("workflow");
        RoadNetwork network = new RoadNetwork();
        var west = network.createNode(new Vec2d(-100, 0));
        var junction = network.createNode(new Vec2d(0, 0));
        var east = network.createNode(new Vec2d(100, 0));
        var south = network.createNode(new Vec2d(0, -100));
        var north = network.createNode(new Vec2d(0, 100));

        Road roadA = network.createRoad("road-a-auto");
        roadA.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        roadA.setMaxSlope(8f);
        RoadEdge westEdge = network.createEdge(west.getId(), junction.getId(),
            List.of(west.getPosition(), junction.getPosition()), roadA.getId());
        RoadEdge eastEdge = network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), roadA.getId());

        Road roadB = network.createRoad("road-b-manual");
        roadB.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        roadB.setMaxSlope(8f);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(60, 72),
            PointOfVerticalIntersection.of(100, 72),
            PointOfVerticalIntersection.of(140, 72),
            PointOfVerticalIntersection.of(200, 70))));
        network.createEdge(south.getId(), junction.getId(),
            List.of(south.getPosition(), junction.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadB.getId());

        TerrainSampler terrain = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d point) {
                double x = point.x;
                if (x < -35 && x > -65) return 74;
                if (x > 60) return 80;
                return 70;
            }
            @Override public boolean isSolidBlock(int x, int y, int z) { return false; }
        };
        return new Fixture(network, config, terrain, roadA, roadB,
            junction.getId(), westEdge.getId(), eastEdge.getId());
    }

    private record GeneratedProfiles(
        RoadVerticalAlignment roadAContinuous,
        VoxelVerticalProfile roadAVoxel,
        VoxelVerticalProfile roadBVoxel) { }

    private record Fixture(
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain,
            Road roadA,
            Road roadB,
            String junctionId,
            String westEdgeId,
            String eastEdgeId) {
        double junctionElevation() { return network.getNode(junctionId).getManualElevation(); }
        RoadEdge westEdge() { return network.getEdge(westEdgeId); }
        RoadEdge eastEdge() { return network.getEdge(eastEdgeId); }
        Fixture withNetwork(RoadNetwork restored) {
            return new Fixture(restored, config, terrain,
                restored.getRoad(roadA.getId()), restored.getRoad(roadB.getId()),
                junctionId, westEdgeId, eastEdgeId);
        }
    }
}
