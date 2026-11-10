package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFilletElevationLoftTest {

    @Test
    void angularBlendSweepsFromStartCurbToEndCurb() {
        Vec2d sharp = new Vec2d(2, 2);
        Vec2d dirA = new Vec2d(0, 1); // north curb
        Vec2d dirB = new Vec2d(1, 0); // east curb
        assertEquals(0.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirA)), 1e-6);
        assertEquals(1.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirB)), 1e-6);
        double mid = JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(new Vec2d(1, 1).normalize()));
        assertTrue(mid > 0.35 && mid < 0.65, "mid angle should be near 0.5; mid=" + mid);
    }

    @Test
    void reconstructSharpCornerMatchesOrthogonalFillet() {
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);
        Vec2d sharp = com.plot.plugin.road.RoadJunctionGenerator.reconstructFilletSharpCorner(
            fillet, sweep);
        assertTrue(sharp != null);
        assertEquals(2.0, sharp.x, 0.15);
        assertEquals(2.0, sharp.y, 0.15);
    }

    @Test
    void resolveTangentOwnerBindsEachCurbToNearestApproach() {
        RoadNetwork network = orthogonalNetwork();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        RoadEdge ew = edgeToward(network, new Vec2d(40, 0));
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);

        RoadEdge ownerNorth = JunctionFilletElevationLoft.resolveTangentOwner(
            new Vec2d(2, 6), List.copyOf(network.getEdges().values()), centerlines);
        RoadEdge ownerEast = JunctionFilletElevationLoft.resolveTangentOwner(
            new Vec2d(6, 2), List.copyOf(network.getEdges().values()), centerlines);

        assertSame(ns, ownerNorth, "north tangent belongs to NS approach");
        assertSame(ew, ownerEast, "east tangent belongs to EW approach");
    }

    private static RoadNetwork orthogonalNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road nsRoad = network.createRoad("ns");
        nsRoad.setWidth(7);
        Road ewRoad = network.createRoad("ew");
        ewRoad.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), nsRoad.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), ewRoad.getId());
        return network;
    }

    private static RoadEdge edgeToward(RoadNetwork network, Vec2d leaf) {
        return network.getEdges().values().stream()
            .filter(edge -> {
                RoadNode start = network.getNode(edge.getStartNodeId());
                RoadNode end = network.getNode(edge.getEndNodeId());
                return (start != null && start.getPosition().distance(leaf) < 1.0)
                    || (end != null && end.getPosition().distance(leaf) < 1.0);
            })
            .findFirst()
            .orElseThrow();
    }
}
