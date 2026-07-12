package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadConstructionEvaluatorTest {

    private static final RoadConstructionEvaluator.RoadConstructionCostConfig DEFAULT_CONFIG =
        new RoadConstructionEvaluator.RoadConstructionCostConfig(
            1.0, 15.0, 0.8, 1.2, 25.0, 1.5, 2.0, 5, 8);

    @Test
    void smallHeightDifferenceReturnsRoad() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluateSegment(
            1.0, 64, 65, DEFAULT_CONFIG);
        assertEquals(RoadConstructionType.ROAD, type);
    }

    @Test
    void defaultConfigCostCompareSelectsBridgeForTenMeterSpan() {
        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            Collections.nCopies(10, 1.0),
            Collections.nCopies(10, 64),
            Collections.nCopies(10, 68),
            DEFAULT_CONFIG,
            3.0);

        for (RoadConstructionType type : types) {
            assertEquals(RoadConstructionType.BRIDGE, type);
        }
    }

    @Test
    void defaultConfigShallowDitchPrefersCutOverTunnel() {
        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            Collections.nCopies(10, 1.0),
            Collections.nCopies(10, 67),
            Collections.nCopies(10, 64),
            DEFAULT_CONFIG,
            3.0);

        for (RoadConstructionType type : types) {
            assertEquals(RoadConstructionType.CUT, type);
        }
    }

    @Test
    void defaultConfigDeepDitchSelectsTunnelByCost() {
        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            Collections.nCopies(15, 1.0),
            Collections.nCopies(15, 70),
            Collections.nCopies(15, 64),
            DEFAULT_CONFIG,
            3.0);

        for (RoadConstructionType type : types) {
            assertEquals(RoadConstructionType.TUNNEL, type);
        }
    }

    @Test
    void defaultConfigShortSpanStaysFill() {
        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0, 1.0),
            List.of(64, 64),
            List.of(68, 68),
            DEFAULT_CONFIG,
            3.0);

        assertEquals(RoadConstructionType.FILL, types.get(0));
        assertEquals(RoadConstructionType.FILL, types.get(1));
    }

    @Test
    void lowBridgeCostPerLengthPrefersBridge() {
        RoadConstructionEvaluator.RoadConstructionCostConfig cheapBridge =
            new RoadConstructionEvaluator.RoadConstructionCostConfig(
                1.0, 1.0, 0.01, 1.2, 25.0, 1.5, 2.0, 100, 100);
        RoadConstructionType type = RoadConstructionEvaluator.evaluateSegment(
            5.0, 64, 68, cheapBridge);
        assertEquals(RoadConstructionType.BRIDGE, type);
    }

    @Test
    void extremeHeightDifferenceForcesBridgeRegardlessOfCost() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluateSegment(
            1.0, 64, 80, DEFAULT_CONFIG);
        assertEquals(RoadConstructionType.BRIDGE, type);
    }

    @Test
    void extremeDepthDifferenceForcesTunnelRegardlessOfCost() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluateSegment(
            1.0, 80, 64, DEFAULT_CONFIG);
        assertEquals(RoadConstructionType.TUNNEL, type);
    }

    @Test
    void shortBridgeRunIsAbsorbedIntoFillWhenBelowMinimumRunLength() {
        List<Double> distances = List.of(3.0, 2.0, 3.0);
        List<Integer> groundHeights = List.of(64, 64, 64);
        List<Integer> targetHeights = List.of(66, 68, 66);

        RoadConstructionEvaluator.RoadConstructionCostConfig config =
            new RoadConstructionEvaluator.RoadConstructionCostConfig(
                1.0, 10.0, 0.8, 1.2, 25.0, 1.5, 2.0, 100, 100);

        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            distances,
            groundHeights,
            targetHeights,
            config,
            3.0);

        assertEquals(RoadConstructionType.FILL, types.get(0));
        assertEquals(RoadConstructionType.FILL, types.get(1));
        assertEquals(RoadConstructionType.FILL, types.get(2));
    }

    @Test
    void equivalentThresholdConfigMatchesOldBridgeDetection() {
        RoadConstructionEvaluator.RoadConstructionCostConfig oldEquivalent =
            new RoadConstructionEvaluator.RoadConstructionCostConfig(
                1.0, 1000.0, 1000.0, 1.2, 1000.0, 1000.0, 2.0, 5, 8);

        assertEquals(
            RoadConstructionType.FILL,
            RoadConstructionEvaluator.evaluateSegment(1.0, 64, 69, oldEquivalent));
        assertEquals(
            RoadConstructionType.BRIDGE,
            RoadConstructionEvaluator.evaluateSegment(1.0, 64, 70, oldEquivalent));
        assertEquals(
            RoadConstructionType.CUT,
            RoadConstructionEvaluator.evaluateSegment(1.0, 72, 64, oldEquivalent));
        assertEquals(
            RoadConstructionType.TUNNEL,
            RoadConstructionEvaluator.evaluateSegment(1.0, 73, 64, oldEquivalent));
    }
}
