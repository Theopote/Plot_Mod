package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingDetector;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RoadStationing#chainageAtPosition} 在 HA 道路上的精度特征测试。
 * <p>
 * 设计桩号权威来源为 {@link HorizontalAlignmentGeometry#poseAt}；
 * 当前实现经由 plan 折线局部距离 → 实例链长 → {@link RoadStationing#toCanonicalChainage} 全局比例换算。
 * 物化一致的几何应满足 tight 往返；设计/实例长度不一致或曲线段采样稀疏时误差会放大。
 */
class RoadStationingChainageAtPositionTest {

    /** 直线段 / 已物化且与设计一致的区段，以及 HA 采样反查目标精度。 */
    private static final double MATERIALIZED_TOLERANCE = 0.1;
    /** 单直线、设计/实例长度不一致时仍应回到设计桩号。 */
    private static final double DESIGN_CHAINAGE_TOLERANCE = 0.05;

    @Test
    void roundTripsOnPlainPolylineWithoutDesignAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("plain");
        network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(100, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        assertChainageAtDesignPosition(network, road, 0.0, 1e-3);
        assertChainageAtDesignPosition(network, road, 37.5, 1e-3);
        assertChainageAtDesignPosition(network, road, 100.0, 1e-3);
    }

    @Test
    void roundTripsOnMaterializedTangentAlignment() {
        RoadNetwork network = buildSingleTangentHaRoad(100.0, 100.0);
        Road road = network.getRoad("tangent-ha");
        HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        assertChainageAtDesignPosition(network, road, 0.0, MATERIALIZED_TOLERANCE);
        assertChainageAtDesignPosition(network, road, 40.0, MATERIALIZED_TOLERANCE);
        assertChainageAtDesignPosition(network, road, 100.0, MATERIALIZED_TOLERANCE);
    }

    @ParameterizedTest
    @MethodSource("tangentArcTangentDesignChainages")
    void roundTripsOnMaterializedTangentArcTangentAlignment(double designChainage) {
        RoadNetwork network = buildMaterializedTangentArcTangentRoad();
        Road road = network.getRoad("tat");

        double tolerance = MATERIALIZED_TOLERANCE;
        assertChainageAtDesignPosition(network, road, designChainage, tolerance);
    }

    static Stream<Double> tangentArcTangentDesignChainages() {
        double arcLength = Math.PI * 25.0 / 2.0;
        return Stream.of(
            0.0,
            25.0,
            50.0,
            50.0 + arcLength * 0.25,
            50.0 + arcLength * 0.5,
            50.0 + arcLength,
            50.0 + arcLength + 25.0,
            50.0 + arcLength + 50.0
        );
    }

    @Test
    void lengthMismatchOnSingleTangent_returnsDesignChainageAtInteriorPoint() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mismatch");
        double designLength = 300.0;
        double instanceLength = 299.3;
        network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(instanceLength, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instanceLength, 0)),
            road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(designLength))));

        assertChainageAtDesignPosition(network, road, 150.0, DESIGN_CHAINAGE_TOLERANCE);
    }

    @Test
    void staleInstanceOnTangentArcTangent_arcMidpointMatchesDesignChainage() {
        RoadNetwork network = buildTangentArcTangentRoadWithStaleInstance();
        Road road = network.getRoad("tat");
        double arcLength = Math.PI * 25.0 / 2.0;
        double designChainage = 50.0 + arcLength * 0.5;

        assertChainageAtDesignPosition(network, road, designChainage, MATERIALIZED_TOLERANCE);
    }

    @Test
    void crossingDetectorReportsDesignChainageOnHorizontalAlignmentRoad() {
        RoadNetwork network = new RoadNetwork();
        Road horizontal = network.createRoad("road-a");
        Road vertical = network.createRoad("road-b");

        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(100, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(100, 5)),
            horizontal.getId());

        network.createEdge(
            network.createNode(new Vec2d(8, 0)).getId(),
            network.createNode(new Vec2d(8, 10)).getId(),
            List.of(new Vec2d(8, 0), new Vec2d(8, 10)),
            vertical.getId());

        vertical.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(5, 0), Math.PI / 2, List.of(HorizontalAlignmentElement.tangent(10.0))));

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(1, detected.size());

        RoadCrossing crossing = detected.getFirst();
        assertEquals(5.0, crossing.position().x, 1e-3);
        assertEquals(5.0, crossing.position().y, 1e-3);
        assertEquals(5.0, crossing.stationOn(horizontal.getId()), DESIGN_CHAINAGE_TOLERANCE);
        assertEquals(5.0, crossing.stationOn(vertical.getId()), DESIGN_CHAINAGE_TOLERANCE);
    }

    @Test
    void crossingDetectorReportsDesignChainageOnTangentArcTangentHorizontal() {
        RoadNetwork network = buildMaterializedTangentArcTangentRoad();
        Road horizontal = network.getRoad("tat");

        Road vertical = network.createRoad("cross");
        double arcLength = Math.PI * 25.0 / 2.0;
        double crossingDesignChainage = 50.0 + arcLength * 0.5;
        Vec2d crossingPoint = RoadStationing.pointAtStation(network, horizontal, crossingDesignChainage).orElseThrow();

        network.createEdge(
            network.createNode(new Vec2d(crossingPoint.x, crossingPoint.y - 5)).getId(),
            network.createNode(new Vec2d(crossingPoint.x, crossingPoint.y + 5)).getId(),
            List.of(
                new Vec2d(crossingPoint.x, crossingPoint.y - 5),
                new Vec2d(crossingPoint.x, crossingPoint.y + 5)),
            vertical.getId());

        List<RoadCrossing> detected = RoadCrossingDetector.detectAll(network);
        assertEquals(1, detected.size());

        RoadCrossing crossing = detected.getFirst();
        assertEquals(crossingPoint.x, crossing.position().x, MATERIALIZED_TOLERANCE);
        assertEquals(crossingPoint.y, crossing.position().y, MATERIALIZED_TOLERANCE);

        double horizontalStation = crossing.stationOn(horizontal.getId());
        assertEquals(crossingDesignChainage, horizontalStation, MATERIALIZED_TOLERANCE);
    }

    @Test
    void chainageAtPosition_matchesDirectQueryOnMaterializedMultiElementRoad() {
        RoadNetwork network = buildMaterializedTangentArcTangentRoad();
        Road road = network.getRoad("tat");

        double arcLength = Math.PI * 25.0 / 2.0;
        assertChainageAtDesignPosition(network, road, 12.0, MATERIALIZED_TOLERANCE);
        assertChainageAtDesignPosition(network, road, 50.0 + arcLength * 0.5, MATERIALIZED_TOLERANCE);
        assertChainageAtDesignPosition(network, road, 50.0 + arcLength + 12.0, MATERIALIZED_TOLERANCE);
    }

    private static void assertChainageAtDesignPosition(
            RoadNetwork network,
            Road road,
            double designChainage,
            double tolerance) {
        Vec2d position = RoadStationing.pointAtStation(network, road, designChainage).orElseThrow();
        double actual = RoadStationing.chainageAtPosition(network, road, position).orElseThrow();
        assertEquals(designChainage, actual, tolerance,
            () -> "design=" + designChainage + " position=" + position);
    }

    private static RoadNetwork buildSingleTangentHaRoad(double designLength, double instanceLength) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tangent-ha");
        network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(instanceLength, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instanceLength, 0)),
            road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(designLength))));
        return network;
    }

    private static RoadNetwork buildTangentArcTangentRoad() {
        double arcLength = Math.PI * 25.0 / 2.0;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(50.0),
                HorizontalAlignmentElement.circularArc(arcLength, 25.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(50.0)));

        List<Vec2d> sampled = HorizontalAlignmentGeometry.sample(
            alignment, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
        Vec2d start = sampled.getFirst();
        Vec2d end = sampled.getLast();

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tat");
        road.setHorizontalAlignment(alignment);
        network.createEdge(
            network.createNode(start).getId(),
            network.createNode(end).getId(),
            sampled,
            road.getId());
        return network;
    }

    private static RoadNetwork buildMaterializedTangentArcTangentRoad() {
        RoadNetwork network = buildTangentArcTangentRoad();
        HorizontalAlignmentCenterlineMaterializer.materialize(network, network.getRoad("tat"));
        return network;
    }

    /**
     * 实例折线为弦线近似，设计总长与实例链长不一致，用于放大圆弧段桩号误差。
     */
    private static RoadNetwork buildTangentArcTangentRoadWithStaleInstance() {
        RoadNetwork network = buildTangentArcTangentRoad();
        Road road = network.getRoad("tat");
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        edge.setCenterlinePoints(List.of(
            new Vec2d(0, 0),
            new Vec2d(50, 0),
            new Vec2d(70, 20),
            new Vec2d(75, 75)));
        return network;
    }
}
