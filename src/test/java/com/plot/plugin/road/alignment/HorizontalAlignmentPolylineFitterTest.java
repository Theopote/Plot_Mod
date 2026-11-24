package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentPolylineFitterTest {

    @Test
    void fitsStraightChainAsSingleTangent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment fitted = HorizontalAlignmentPolylineFitter.fit(network, road).orElseThrow();

        assertEquals(1, fitted.getElements().size());
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().getFirst().getType());
        assertEquals(100.0, fitted.totalLength(), 0.5);
    }

    @Test
    void fitsRightAngleWithTangentArcChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 50));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(50, 50)),
            road.getId());

        RoadHorizontalAlignment fitted = HorizontalAlignmentPolylineFitter.fit(network, road).orElseThrow();

        assertTrue(fitted.getElements().size() >= 3);
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().get(0).getType());
        assertEquals(HorizontalAlignmentElementType.CIRCULAR_ARC, fitted.getElements().get(1).getType());
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().get(2).getType());
    }

    @Test
    void filletPolylineFitsAsSingleCircularArcNotFragments() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 100));
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(80, 0), new Vec2d(80, 80)),
            road.getId());

        assertTrue(RoadCenterlineEditor.filletVertex(network, edge.getId(), 1, 12.0).isSuccess());
        double polylineLength = RoadStationing.canonicalLength(network, road);

        RoadHorizontalAlignment fitted = HorizontalAlignmentPolylineFitter.fit(network, road).orElseThrow();

        long arcCount = fitted.getElements().stream()
            .filter(e -> e.getType() == HorizontalAlignmentElementType.CIRCULAR_ARC)
            .count();
        assertEquals(1, arcCount, "fillet samples should merge into one circular arc");
        assertEquals(3, fitted.getElements().size(), "expect T-A-T, not dozens of micro elements");

        HorizontalAlignmentElement arc = fitted.getElements().get(1);
        assertEquals(HorizontalAlignmentElementType.CIRCULAR_ARC, arc.getType());
        assertEquals(12.0, arc.getRadius(), 0.75);
        assertEquals(polylineLength, fitted.totalLength(), polylineLength * 0.05);
    }

    @Test
    void refitAfterTwoFilletsKeepsCompactAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(200, 200));
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(100, 0),
                new Vec2d(100, 100),
                new Vec2d(200, 100)),
            road.getId());

        assertTrue(RoadCenterlineEditor.filletVertex(network, edge.getId(), 1, 15.0).isSuccess());
        int secondCorner = findSharpCornerNear(edge, new Vec2d(100, 100));
        assertTrue(RoadCenterlineEditor.filletVertex(network, edge.getId(), secondCorner, 15.0).isSuccess());

        RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, road);
        RoadHorizontalAlignment fitted = road.getHorizontalAlignment();
        assertTrue(fitted != null && !fitted.isEmpty());
        long arcs = fitted.getElements().stream()
            .filter(e -> e.getType() == HorizontalAlignmentElementType.CIRCULAR_ARC)
            .count();
        assertEquals(2, arcs, "elements=" + fitted.getElements().size());
        assertEquals(5, fitted.getElements().size(), "expect T-A-T-A-T");
        assertEquals(
            RoadStationing.canonicalLength(network, road),
            fitted.totalLength(),
            8.0);
    }

    /** 找仍接近尖角的内部折点（倒圆角后未采样在弧上的那个 PI）。 */
    private static int findSharpCornerNear(RoadEdge edge, Vec2d target) {
        List<Vec2d> points = edge.getCenterlinePoints();
        int best = 1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size() - 1; i++) {
            double turn = Math.abs(HorizontalAlignmentPolylineFitter.signedTurnAngle(
                points.get(i - 1), points.get(i), points.get(i + 1)));
            if (turn < Math.toRadians(20.0)) {
                continue;
            }
            double score = points.get(i).distance(target);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
