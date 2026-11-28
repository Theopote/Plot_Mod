package com.plot.plugin.road.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B. Semantic Acceptance：手写正确性断言，检测「行为是不是正确」。
 */
class RoadSemanticAcceptanceTest {

    static Stream<RoadGoldenScenario> goldenCases() {
        return RoadGoldenScenarioFactory.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void universalSemanticInvariants(RoadGoldenScenario scenario) {
        RoadGoldenHarness.Run run = RoadGoldenHarness.run(scenario);
        RoadSemanticAcceptanceAssertions.assertUniversal(scenario.id(), run);
    }

    @Test
    void r01StraightRoadSemantics() {
        RoadSemanticAcceptanceAssertions.assertStraightRoad(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r01StraightFlat()));
    }

    @Test
    void r03JunctionSemantics() {
        RoadSemanticAcceptanceAssertions.assertJunctionPresent(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r03TJunction()), 3);
    }

    @Test
    void r04CrossJunctionSemantics() {
        RoadSemanticAcceptanceAssertions.assertJunctionPresent(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r04CrossJunction()), 4);
    }

    @Test
    void r08BridgeSemantics() {
        RoadSemanticAcceptanceAssertions.assertBridgeSemantics(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r08Bridge()));
    }

    @Test
    void r05FiveWayJunctionSemantics() {
        RoadSemanticAcceptanceAssertions.assertJunctionPresent(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r05FiveWay()), 5);
    }

    @Test
    void r06SlopedEmbankmentSemantics() {
        RoadSemanticAcceptanceAssertions.assertEmbankmentSemantics(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r06Sloped()));
    }

    /** VC14: steep EDITED R06 keeps authored PVIs and expected bridge/fill morphology (no pre-gen smooth). */
    @Test
    void r06SteepEditedMorphologyKeepsAuthoredProfile() {
        var scenario = RoadGoldenScenarioFactory.r06Sloped();
        var road = scenario.network().getRoads().values().iterator().next();
        double startY = road.getVerticalAlignment().getPvis().getFirst().getElevation();
        double endY = road.getVerticalAlignment().getPvis().getLast().getElevation();
        var run = RoadGoldenHarness.run(scenario);
        assertEquals(startY,
            run.network().getRoad(road.getId()).getVerticalAlignment().getPvis().getFirst().getElevation(),
            1e-9);
        assertEquals(endY,
            run.network().getRoad(road.getId()).getVerticalAlignment().getPvis().getLast().getElevation(),
            1e-9);
        assertTrue(run.metrics().surfaceBlocks() > 0);
        assertTrue(run.metrics().bridgeBlocks() > 0 || run.metrics().fillBlocks() > 0,
            "unsmoothed steep EDITED must produce bridge and/or fill structure");
        assertTrue(run.metrics().bridgeCount() >= 1 || run.metrics().fillVolume() > 0);
    }

    @Test
    void r07TerrainFitEmbankmentSemantics() {
        RoadSemanticAcceptanceAssertions.assertEmbankmentSemantics(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r07TerrainFit()));
    }

    @Test
    void r09TunnelSemantics() {
        RoadSemanticAcceptanceAssertions.assertTunnelSemantics(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r09Tunnel()));
    }

    @Test
    void r10WaterCrossingBridgeSemantics() {
        RoadSemanticAcceptanceAssertions.assertBridgeSemantics(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r10WaterCrossing()));
    }

    @Test
    void r11GradeSeparatedSemantics() {
        RoadSemanticAcceptanceAssertions.assertGradeSeparated(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r11GradeSeparated()));
    }

    @Test
    void r12ClosedLoopSemantics() {
        RoadSemanticAcceptanceAssertions.assertClosedLoop(
            RoadGoldenHarness.run(RoadGoldenScenarioFactory.r12ClosedShape()));
    }
}
