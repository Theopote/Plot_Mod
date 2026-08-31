package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边存储方向（start/end、折线点序）随机化时，canonical 桩号与链相对 LEFT/RIGHT 保持不变。
 */
class RoadOrientationInvariantTest {

    private static final double EDGE_LENGTH = 100.0;
    private static final double TOTAL_LENGTH = 300.0;
    private static final double QUERY_STATION = 150.0;

    record OrientationSpec(boolean abReversed, boolean bcReversed, boolean cdReversed) {
        String label() {
            return "ab=" + (abReversed ? "rev" : "fwd")
                + ",bc=" + (bcReversed ? "rev" : "fwd")
                + ",cd=" + (cdReversed ? "rev" : "fwd");
        }
    }

    record AbcdRoad(
            RoadNetwork network,
            Road road,
            RoadNode a,
            RoadNode b,
            RoadNode c,
            RoadNode d,
            RoadEdge ab,
            RoadEdge bc,
            RoadEdge cd) {
    }

    static Stream<OrientationSpec> allEdgeStoragePermutations() {
        List<OrientationSpec> specs = new ArrayList<>();
        for (boolean ab : new boolean[] {false, true}) {
            for (boolean bc : new boolean[] {false, true}) {
                for (boolean cd : new boolean[] {false, true}) {
                    specs.add(new OrientationSpec(ab, bc, cd));
                }
            }
        }
        return specs.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEdgeStoragePermutations")
    void chainageAndPhase2DataInvariantUnderEdgeStorage(OrientationSpec spec) {
        AbcdRoad chain = buildAbcdChain(spec);
        applyCanonicalPhase2Data(chain.road);

        assertEquals(TOTAL_LENGTH, RoadStationing.totalLength(chain.network, chain.road), 1e-6);
        assertEquals(chain.a.getId(),
            RoadStationing.chainEntryNodeId(chain.network, chain.road).orElseThrow());
        assertEquals(chain.d.getId(),
            RoadStationing.chainExitNodeId(chain.network, chain.road).orElseThrow());

        RoadDesignDirection direction = chain.road.designDirection(chain.network).orElseThrow();
        assertEquals(chain.a.getId(), direction.entryNodeId());
        assertEquals(chain.d.getId(), direction.exitNodeId());

        assertEquals(0.0, chainageAtNode(chain.network, chain.road, chain.a.getId()), 1e-6);
        assertEquals(100.0, chainageAtNode(chain.network, chain.road, chain.b.getId()), 1e-6);
        assertEquals(200.0, chainageAtNode(chain.network, chain.road, chain.c.getId()), 1e-6);
        assertEquals(300.0, chainageAtNode(chain.network, chain.road, chain.d.getId()), 1e-6);

        List<OrientedRoadSegment> oriented = chain.road.orientedSegments(chain.network);
        assertEquals(3, oriented.size());
        assertEquals(0.0, oriented.get(0).startStation(), 1e-6);
        assertEquals(100.0, oriented.get(1).startStation(), 1e-6);
        assertEquals(200.0, oriented.get(2).startStation(), 1e-6);
        assertEquals(spec.abReversed, !oriented.get(0).forward());
        assertEquals(spec.bcReversed, !oriented.get(1).forward());
        assertEquals(spec.cdReversed, !oriented.get(2).forward());

        assertVerticalAlignmentAtQueryStation(chain.road);
        assertVariableCrossSectionAtQueryStation(chain.road);
        assertFacilityAtQueryStation(chain.network, chain.road);
        assertChainLeftPointsNorthAtQueryStation(chain.network, chain.road);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEdgeStoragePermutations")
    void segmentStorageOrderDoesNotAffectChainage(OrientationSpec spec) {
        AbcdRoad chain = buildAbcdChain(spec);
        List<String> shuffled = new ArrayList<>(chain.road.getOrderedSegmentIds());
        Collections.shuffle(shuffled, new Random(spec.abReversed ? 1 : 2 ^ (spec.bcReversed ? 3 : 0) ^ (spec.cdReversed ? 7 : 0)));
        chain.road.reorderSegments(shuffled);

        assertEquals(0.0, chainageAtNode(chain.network, chain.road, chain.a.getId()), 1e-6);
        assertEquals(300.0, chainageAtNode(chain.network, chain.road, chain.d.getId()), 1e-6);
        assertEquals(TOTAL_LENGTH, RoadStationing.totalLength(chain.network, chain.road), 1e-6);
    }

    private static AbcdRoad buildAbcdChain(OrientationSpec spec) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("abcd");

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        RoadNode c = network.createNode(new Vec2d(200, 0));
        RoadNode d = network.createNode(new Vec2d(300, 0));

        RoadEdge ab = createOrientedEdge(
            network, road, a, b, spec.abReversed,
            new Vec2d(0, 0), new Vec2d(100, 0));
        RoadEdge bc = createOrientedEdge(
            network, road, b, c, spec.bcReversed,
            new Vec2d(100, 0), new Vec2d(200, 0));
        RoadEdge cd = createOrientedEdge(
            network, road, c, d, spec.cdReversed,
            new Vec2d(200, 0), new Vec2d(300, 0));

        return new AbcdRoad(network, road, a, b, c, d, ab, bc, cd);
    }

    private static RoadEdge createOrientedEdge(
            RoadNetwork network,
            Road road,
            RoadNode chainStart,
            RoadNode chainEnd,
            boolean reversed,
            Vec2d chainStartPos,
            Vec2d chainEndPos) {
        if (reversed) {
            return network.createEdge(
                chainEnd.getId(),
                chainStart.getId(),
                List.of(chainEndPos.copy(), chainStartPos.copy()),
                road.getId());
        }
        return network.createEdge(
            chainStart.getId(),
            chainEnd.getId(),
            List.of(chainStartPos.copy(), chainEndPos.copy()),
            road.getId());
    }

    private static void applyCanonicalPhase2Data(Road road) {
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 65.0),
            PointOfVerticalIntersection.of(200.0, 66.0),
            PointOfVerticalIntersection.of(300.0, 67.0)
        )));

        RoadCrossSection width6 = sectionWithWidth(6);
        RoadCrossSection width10 = sectionWithWidth(10);
        RoadCrossSection width14 = sectionWithWidth(14);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, width6),
            StationCrossSection.at(100.0, width10),
            StationCrossSection.at(200.0, width14)
        )));

        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(50.0, 250.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }

    private static double chainageAtNode(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            OptionalDouble station = segment.roadStationAtNode(nodeId);
            if (station.isPresent()) {
                return station.getAsDouble();
            }
        }
        throw new AssertionError("node not on chain: " + nodeId);
    }

    private static void assertVerticalAlignmentAtQueryStation(Road road) {
        OptionalDouble elevation = VerticalAlignmentGeometry.elevationAt(
            road.getVerticalAlignment(),
            QUERY_STATION);
        assertTrue(elevation.isPresent());
        assertEquals(65.5, elevation.getAsDouble(), 1e-6);
    }

    private static void assertVariableCrossSectionAtQueryStation(Road road) {
        assertEquals(10, widthOf(VariableCrossSectionResolver.resolveTemplate(road, QUERY_STATION)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 50.0)));
        assertEquals(14, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 250.0)));
    }

    private static void assertFacilityAtQueryStation(RoadNetwork network, Road road) {
        List<StationFacilityRun> active = StationFacilityResolver.activeAt(
            network, road, QUERY_STATION);
        assertEquals(1, active.size());
        assertEquals(RoadFacilityKind.GUARDRAIL, active.getFirst().getKind());
        assertEquals(RoadFacilitySide.LEFT, active.getFirst().getSide());
        assertFalse(StationFacilityResolver.activeAt(network, road, 40.0).stream()
            .anyMatch(run -> run.getKind() == RoadFacilityKind.GUARDRAIL));
    }

    private static void assertChainLeftPointsNorthAtQueryStation(RoadNetwork network, Road road) {
        SegmentStation segmentStation = RoadStationing.resolve(network, road, QUERY_STATION).orElseThrow();
        OrientedRoadSegment oriented = RoadStationing.orientedSegment(
            network, road, segmentStation.segmentId()).orElseThrow();
        RoadEdge edge = network.getEdge(segmentStation.segmentId());
        PathSegment geometrySegment = pathSegmentAtGeometryLocal(edge, segmentStation.localDistance());

        Vec2d chainLeft = PathSegmentGeometry.chainLeftNormal(geometrySegment, oriented.forward());
        assertEquals(0.0, chainLeft.dot(new Vec2d(1, 0)), 1e-6);
        assertTrue(chainLeft.dot(new Vec2d(0, 1)) > 0.99);

        Optional<Vec2d> atStation = RoadStationing.instancePointAtStation(network, road, QUERY_STATION);
        Optional<Vec2d> ahead = RoadStationing.instancePointAtStation(network, road, QUERY_STATION + 1.0);
        assertTrue(atStation.isPresent() && ahead.isPresent());
        Vec2d chainTangent = ahead.get().subtract(atStation.get()).normalize();
        assertTrue(chainTangent.dot(new Vec2d(1, 0)) > 0.99);
        Vec2d chainLeftFromPose = new Vec2d(-chainTangent.y, chainTangent.x);
        assertTrue(chainLeftFromPose.dot(chainLeft) > 0.99);
    }

    private static PathSegment pathSegmentAtGeometryLocal(RoadEdge edge, double geometryLocal) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            throw new IllegalArgumentException("edge needs at least two points");
        }
        double length = edge.getLength();
        double t = length > 1e-9 ? Math.max(0.0, Math.min(1.0, geometryLocal / length)) : 0.0;
        double delta = Math.min(1.0, length * 0.01);
        double localBefore = Math.max(0.0, geometryLocal - delta);
        double localAfter = Math.min(length, geometryLocal + delta);
        Vec2d before = interpolatePolyline(points, localBefore);
        Vec2d after = interpolatePolyline(points, localAfter);
        if (before.distanceSquared(after) < 1e-12) {
            return new PathSegment(points.getFirst().copy(), points.getLast().copy());
        }
        return new PathSegment(before, after);
    }

    private static Vec2d interpolatePolyline(List<Vec2d> points, double distanceAlong) {
        double remaining = distanceAlong;
        for (int i = 1; i < points.size(); i++) {
            Vec2d start = points.get(i - 1);
            Vec2d end = points.get(i);
            double segmentLength = start.distance(end);
            if (remaining <= segmentLength || i == points.size() - 1) {
                double t = segmentLength > 1e-9 ? Math.max(0.0, Math.min(1.0, remaining / segmentLength)) : 0.0;
                return start.lerp(end, t);
            }
            remaining -= segmentLength;
        }
        return points.getLast().copy();
    }

    private static int widthOf(RoadCrossSection section) {
        Integer width = section.getCarriageway().getWidth();
        return width != null ? width : 0;
    }
}
