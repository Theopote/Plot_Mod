package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链向 bearing / leftNormal 不随 Edge 存储方向变化。
 */
class RoadBearingInvariantTest {

    private static final double EAST = 0.0;
    private static final double NORTH = Math.PI / 2.0;

    @Test
    void bearingAndLeftNormalMatchOnForwardAndReversedEdgeStorage() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("abc");

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        RoadNode c = network.createNode(new Vec2d(200, 0));

        network.createEdge(
            a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
        RoadEdge reversedBc = network.createEdge(
            c.getId(), b.getId(),
            List.of(new Vec2d(200, 0), new Vec2d(100, 0)),
            road.getId());

        OrientedRoadSegment bc = RoadStationing.orientedSegment(network, road, reversedBc.getId()).orElseThrow();
        assertFalse(bc.forward());
        assertEquals(100.0, bc.startStation(), 1e-6);

        double bearingAt25 = RoadPlanGeometry.bearingAtStation(network, road, 25.0).orElseThrow();
        double bearingAt75 = RoadPlanGeometry.bearingAtStation(network, road, 75.0).orElseThrow();

        assertEquals(EAST, bearingAt25, 1e-6);
        assertEquals(EAST, bearingAt75, 1e-6);

        Vec2d leftAt25 = chainLeftNormalAt(network, road, 25.0);
        Vec2d leftAt75 = chainLeftNormalAt(network, road, 75.0);

        assertEquals(leftAt25.x, leftAt75.x, 1e-6);
        assertEquals(leftAt25.y, leftAt75.y, 1e-6);
        assertEquals(NORTH, Math.atan2(leftAt25.y, leftAt25.x), 1e-6);
    }

    private static Vec2d chainLeftNormalAt(RoadNetwork network, Road road, double chainage) {
        SegmentStation segmentStation = RoadStationing.resolve(network, road, chainage).orElseThrow();
        OrientedRoadSegment oriented = RoadStationing.orientedSegment(
            network, road, segmentStation.segmentId()).orElseThrow();
        RoadEdge edge = network.getEdge(segmentStation.segmentId());
        PathSegment segment = geometrySegmentAt(edge, segmentStation.localDistance());
        return PathSegmentGeometry.chainLeftNormal(segment, oriented.forward());
    }

    private static PathSegment geometrySegmentAt(RoadEdge edge, double geometryLocal) {
        List<Vec2d> points = edge.getCenterlinePoints();
        double length = edge.getLength();
        double delta = Math.min(1.0, length * 0.01);
        double before = Math.max(0.0, geometryLocal - delta);
        double after = Math.min(length, geometryLocal + delta);
        Vec2d start = interpolate(points, before);
        Vec2d end = interpolate(points, after);
        if (start.distanceSquared(end) < 1e-12) {
            return new PathSegment(points.getFirst().copy(), points.getLast().copy());
        }
        return new PathSegment(start, end);
    }

    private static Vec2d interpolate(List<Vec2d> points, double distanceAlong) {
        double remaining = distanceAlong;
        for (int i = 1; i < points.size(); i++) {
            Vec2d start = points.get(i - 1);
            Vec2d end = points.get(i);
            double segmentLength = start.distance(end);
            if (remaining <= segmentLength || i == points.size() - 1) {
                double t = segmentLength > 1e-9
                    ? Math.max(0.0, Math.min(1.0, remaining / segmentLength))
                    : 0.0;
                return start.lerp(end, t);
            }
            remaining -= segmentLength;
        }
        return points.getLast().copy();
    }
}
