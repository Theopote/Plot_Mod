package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadConstructionEvaluatorTest {

    private static final RoadConstructionEvaluator.RoadConstructionScoreConfig DEFAULT_CONFIG =
        new RoadConstructionEvaluator.RoadConstructionScoreConfig(
            1.0, 15.0, 0.8, 1.2, 25.0, 1.5, 2.0, 5, 8);

    @Test
    void smallHeightDifferenceReturnsRoad() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(64), List.of(65), DEFAULT_CONFIG, 0.0).getFirst();
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
    void lowbridgeLengthPenaltyPrefersBridge() {
        RoadConstructionEvaluator.RoadConstructionScoreConfig cheapBridge =
            new RoadConstructionEvaluator.RoadConstructionScoreConfig(
                1.0, 1.0, 0.01, 1.2, 25.0, 1.5, 2.0, 100, 100);
        RoadConstructionType type = RoadConstructionEvaluator.evaluatePath(
            List.of(5.0), List.of(64), List.of(68), cheapBridge, 0.0).getFirst();
        assertEquals(RoadConstructionType.BRIDGE, type);
    }

    @Test
    void extremeHeightDifferenceForcesBridgeRegardlessOfCost() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(64), List.of(80), DEFAULT_CONFIG, 0.0).getFirst();
        assertEquals(RoadConstructionType.BRIDGE, type);
    }

    @Test
    void extremeDepthDifferenceForcesTunnelRegardlessOfCost() {
        RoadConstructionType type = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(80), List.of(64), DEFAULT_CONFIG, 0.0).getFirst();
        assertEquals(RoadConstructionType.TUNNEL, type);
    }

    @Test
    void shortBridgeRunIsAbsorbedIntoFillWhenBelowMinimumRunLength() {
        List<Double> distances = List.of(3.0, 2.0, 3.0);
        List<Integer> groundHeights = List.of(64, 64, 64);
        List<Integer> targetHeights = List.of(66, 68, 66);

        RoadConstructionEvaluator.RoadConstructionScoreConfig config =
            new RoadConstructionEvaluator.RoadConstructionScoreConfig(
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
    void shortScoredBridgeRunStaysWhenAtLeastOneBlockLong() {
        RoadConstructionEvaluator.RoadConstructionScoreConfig cheapBridge =
            new RoadConstructionEvaluator.RoadConstructionScoreConfig(
                1.0, 1.0, 0.01, 1.2, 25.0, 1.5, 2.0, 100, 100);

        List<RoadConstructionType> types = RoadConstructionEvaluator.evaluatePath(
            List.of(2.0),
            List.of(64),
            List.of(68),
            cheapBridge,
            RoadConstructionHeuristics.MIN_STRUCTURE_RUN);

        assertEquals(RoadConstructionType.BRIDGE, types.getFirst());
    }

    @Test
    void shortHardThresholdBridgeAndTunnelRunsStayStructural() {
        List<RoadConstructionType> bridge = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(60), List.of(70), DEFAULT_CONFIG, 3.0);
        List<RoadConstructionType> tunnel = RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(80), List.of(64), DEFAULT_CONFIG, 3.0);

        assertEquals(RoadConstructionType.BRIDGE, bridge.getFirst());
        assertEquals(RoadConstructionType.TUNNEL, tunnel.getFirst());
    }

    @Test
    void equivalentThresholdConfigMatchesOldBridgeDetection() {
        RoadConstructionEvaluator.RoadConstructionScoreConfig oldEquivalent =
            new RoadConstructionEvaluator.RoadConstructionScoreConfig(
                1.0, 1000.0, 1000.0, 1.2, 1000.0, 1000.0, 2.0, 5, 8);

        assertEquals(
            RoadConstructionType.FILL,
            RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(64), List.of(69), oldEquivalent, 0.0).getFirst());
        assertEquals(
            RoadConstructionType.BRIDGE,
            RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(64), List.of(70), oldEquivalent, 0.0).getFirst());
        assertEquals(
            RoadConstructionType.CUT,
            RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(72), List.of(64), oldEquivalent, 0.0).getFirst());
        assertEquals(
            RoadConstructionType.TUNNEL,
            RoadConstructionEvaluator.evaluatePath(
            List.of(1.0), List.of(73), List.of(64), oldEquivalent, 0.0).getFirst());
    }
}
