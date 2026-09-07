package com.plot.plugin.road.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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
