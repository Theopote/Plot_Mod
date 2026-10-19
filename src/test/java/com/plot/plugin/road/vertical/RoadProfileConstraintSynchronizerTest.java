package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadProfileConstraintSynchronizerTest {
    private final RoadNetwork network = new RoadNetwork();
    private final RoadSystemConfig config = new RoadSystemConfig("constraints");

    @Test
    void linkingACrossingPreservesRemoteUserCurveLengths() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        a.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.withCurve(30, 70, 40),
            PointOfVerticalIntersection.of(60, 64), PointOfVerticalIntersection.of(100, 64))));
        network.registerCrossing(RoadCrossing.atGrade("a", 90, "b", 50, new Vec2d(90, 0)));
        var before = a.getVerticalAlignment();
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        assertEquals(40, a.getVerticalAlignment().getPvis().get(1).getCurveLength());
        assertEquals(VerticalAlignmentGeometry.elevationAt(before, 30).orElseThrow(), at(a, 30), 1e-6);
    }

    @Test
    void curvedCrossingRetainsActualHeightAndContinuousGradeAfterHandleEdit() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var before = a.getVerticalAlignment();
        raise(a, 50, 80);
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        assertTrue(a.getVerticalAlignment().getPvis().get(1).hasCurve());
        double height = at(a, 50);
        before = a.getVerticalAlignment();
        a.setVerticalAlignment(VerticalProfileControlPoints.withCurveLength(before, 1, 20));
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        assertEquals(20, a.getVerticalAlignment().getPvis().get(1).getCurveLength());
        assertEquals(height, at(a, 50), 1e-6);
        assertEquals(height, at(b, 50), 1e-6);
        for (double station : new double[]{40, 50, 60}) {
            assertEquals(VerticalAlignmentGeometry.gradeAt(a.getVerticalAlignment(), station - 1e-5).orElseThrow(),
                VerticalAlignmentGeometry.gradeAt(a.getVerticalAlignment(), station + 1e-5).orElseThrow(), 1e-3);
        }
    }

    @Test
    void neighbouringCurvedAnchorsAreSolvedTogether() {
        Road a = selfRoad();
        var stack = RoadStackCrossing.atGrade(a.getId(), 25, 75, new Vec2d(25, 0));
        network.registerStackCrossing(stack);
        var before = a.getVerticalAlignment();
        raise(a, 75, 80);
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        for (int i : new int[]{1, 2}) {
            before = a.getVerticalAlignment();
            a.setVerticalAlignment(VerticalProfileControlPoints.withCurveLength(before, i, 20));
            var result = RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
            assertTrue(result.unresolvedConstraints().isEmpty());
            assertEquals(80, at(a, 25), 1e-6);
            assertEquals(80, at(a, 75), 1e-6);
        }
    }

    @Test
    void threeWayRegistryCrossingSharesHeightWithoutSmoothingUserProfile() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        Road c = road("c", 64);
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        network.registerCrossing(RoadCrossing.atGrade("b", 50, "c", 50, new Vec2d(50, 0)));
        var before = a.getVerticalAlignment();
        raise(a, 50, 90);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);

        assertTrue(result.unresolvedConstraints().isEmpty());
        assertEquals(90, at(a, 50), 1e-6);
        assertEquals(90, at(b, 50), 1e-6);
        assertEquals(90, at(c, 50), 1e-6);
        assertEquals(64, at(b, 0), 1e-6, "editing must not auto-fit adjacent grades");
        assertTrue(RoadProfileConstraintSynchronizer.validate(network, java.util.Set.of("a"), config).isEmpty());
    }

    @Test
    void intersectionBetweenPvisIsInsertedAndLinked() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        var crossing = RoadCrossing.atGrade("a", 25, "b", 25, new Vec2d(25, 0));
        network.registerCrossing(crossing);
        var before = a.getVerticalAlignment();
        raise(a, 50, 72);
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);

        assertEquals(68, at(a, 25), 1e-6);
        assertEquals(68, at(b, 25), 1e-6);
        assertEquals(68, network.getCrossing(crossing.id()).sharedElevation());
        assertTrue(b.getVerticalAlignment().getPvis().stream().anyMatch(p -> p.getStation() == 25));
    }

    @Test
    void raisingLowerRoadPushesUpperRoadOnlyWhenClearanceIsInsufficient() {
        Road lower = road("lower", 64);
        Road upper = road("upper", 80);
        separated("lu", lower, upper);
        var before = lower.getVerticalAlignment();
        raise(lower, 50, 66);
        RoadProfileConstraintSynchronizer.synchronize(network, lower, before, config);
        assertEquals(80, at(upper, 50), 1e-6, "a surplus clearance must be retained");

        before = lower.getVerticalAlignment();
        raise(lower, 50, 78);
        RoadProfileConstraintSynchronizer.synchronize(network, lower, before, config);
        assertEquals(78, at(lower, 50), 1e-6);
        assertEquals(84, at(upper, 50), 1e-6);
    }

    @Test
    void loweringUpperRoadCanDriveLowerRoadDownward() {
        Road lower = road("lower", 64);
        Road upper = road("upper", 80);
        separated("lu", lower, upper);
        var before = upper.getVerticalAlignment();
        raise(upper, 50, 60);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, upper, before, config);
        assertTrue(result.unresolvedConstraints().isEmpty());
        assertEquals(54, at(lower, 50), 1e-6);
        assertEquals(60, at(upper, 50), 1e-6);
    }

    @Test
    void atGradeSelfCrossingUsesEditedSecondBranch() {
        Road road = selfRoad();
        var stack = RoadStackCrossing.atGrade(road.getId(), 25, 75, new Vec2d(25, 0));
        network.registerStackCrossing(stack);
        var before = road.getVerticalAlignment();
        raise(road, 75, 74);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, road, before, config);
        assertTrue(result.unresolvedConstraints().isEmpty());
        assertEquals(74, at(road, 25), 1e-6);
        assertEquals(74, at(road, 75), 1e-6);
        assertEquals(74, network.getStackCrossing(stack.id()).sharedElevation());
    }

    @Test
    void selfOverpassCanPutEarlierStationAboveLaterStation() {
        Road road = selfRoad();
        var stack = RoadStackCrossing.gradeSeparated(road.getId(), 75, 25, new Vec2d(25, 0), 6);
        assertEquals(75, stack.resolvedLowerStation());
        assertEquals(25, stack.resolvedUpperStation());
        network.registerStackCrossing(stack);
        var before = road.getVerticalAlignment();
        raise(road, 75, 72);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, road, before, config);
        assertTrue(result.unresolvedConstraints().isEmpty());
        assertEquals(72, at(road, 75), 1e-6);
        assertEquals(78, at(road, 25), 1e-6);
    }

    @Test
    void contradictoryClearanceCycleDoesNotPartiallyRewriteNeighbours() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        Road c = road("c", 64);
        separated("ab", a, b);
        separated("bc", b, c);
        separated("ca", c, a);
        var before = a.getVerticalAlignment();
        raise(a, 50, 72);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        assertFalse(result.unresolvedConstraints().isEmpty());
        assertEquals(64, at(b, 50), 1e-6);
        assertEquals(64, at(c, 50), 1e-6);
        assertTrue(result.changedRoadIds().isEmpty());
    }

    @Test
    void flatNeighbourRetainsCrossingLockAfterRecompilation() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        b.setVerticalMode(RoadVerticalMode.FLAT);
        b.setFlatVerticalIntent(new FlatVerticalIntent(64));
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var before = a.getVerticalAlignment();
        raise(a, 50, 70);
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, b, 8);
        assertEquals(RoadVerticalMode.FLAT, b.getVerticalMode());
        assertEquals(70, at(b, 50), 1e-6);
        assertEquals(64, b.getFlatVerticalIntent().getBaseElevation());
    }

    @Test
    void userLockedUpperRoadReportsConflictInsteadOfMovingEitherAnchor() {
        Road lower = road("lower", 64);
        Road upper = road("upper", 70);
        upper.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            new PointOfVerticalIntersection(50, 70, null, VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(100, 70))));
        separated("lu", lower, upper);
        var before = lower.getVerticalAlignment();
        raise(lower, 50, 72);
        var result = RoadProfileConstraintSynchronizer.synchronize(network, lower, before, config);
        assertFalse(result.unresolvedConstraints().isEmpty());
        assertEquals(72, at(lower, 50), 1e-6);
        assertEquals(70, at(upper, 50), 1e-6);
        assertEquals(VerticalControlPointConstraint.USER_LOCKED,
            upper.getVerticalAlignment().getPvis().get(1).getConstraint());
    }

    @Test
    void automaticNeighbourKeepsTerrainFollowingModeAfterReceivingCrossingAnchor() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        b.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var before = a.getVerticalAlignment();
        raise(a, 50, 70);
        RoadProfileConstraintSynchronizer.synchronize(network, a, before, config);
        assertEquals(70, at(b, 50), 1e-6);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, b.getVerticalMode());
    }

    @Test
    void missingTerrainSamplesAreNotSilentlyTreatedAsSolved() {
        Road a = road("a", 64);
        Road b = road("b", 64);
        b.setVerticalAlignment(null);
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var result = RoadProfileConstraintSynchronizer.synchronize(network, a, a.getVerticalAlignment(), config);
        assertFalse(result.unresolvedConstraints().isEmpty());
        assertNull(b.getVerticalAlignment());
    }

    @Test
    void automaticUpperRoleDoesNotFlipWhenLowerRoadIsRaisedPastIt() {
        Road lower = road("lower", 64);
        Road upper = road("upper", 80);
        network.registerCrossing(new RoadCrossing("auto", "lower", 50, "upper", 50,
            new Vec2d(50, 0), CrossingType.GRADE_SEPARATED, null, 6.0, null));
        var before = lower.getVerticalAlignment();
        raise(lower, 50, 90);
        RoadProfileConstraintSynchronizer.synchronize(network, lower, before, config);
        assertEquals(90, at(lower, 50), 1e-6);
        assertEquals(96, at(upper, 50), 1e-6);
    }

    private Road road(String id, double elevation) {
        Road road = network.createRoad(id);
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, elevation), PointOfVerticalIntersection.of(50, elevation),
            PointOfVerticalIntersection.of(100, elevation))));
        return road;
    }

    private Road selfRoad() {
        Road road = road("self", 64);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(25, 64),
            PointOfVerticalIntersection.of(75, 64), PointOfVerticalIntersection.of(100, 64))));
        return road;
    }

    private void separated(String id, Road lower, Road upper) {
        network.registerCrossing(new RoadCrossing(id, lower.getId(), 50, upper.getId(), 50,
            new Vec2d(50, 0), CrossingType.GRADE_SEPARATED, upper.getId(), 6.0, null));
    }

    private static void raise(Road road, double station, double elevation) {
        var alignment = road.getVerticalAlignment();
        for (int i = 0; i < alignment.pviCount(); i++) {
            if (alignment.getPvis().get(i).getStation() == station) {
                road.setVerticalAlignment(VerticalProfileControlPoints.withElevation(alignment, i, elevation));
                return;
            }
        }
        fail("missing fixture station");
    }

    private static double at(Road road, double station) {
        return VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), station).orElseThrow();
    }
}
