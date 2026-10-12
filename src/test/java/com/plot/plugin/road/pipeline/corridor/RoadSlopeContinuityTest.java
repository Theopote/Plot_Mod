package com.plot.plugin.road.pipeline.corridor;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSlopeContinuityTest {

    static Stream<RoadGoldenScenario> cutScenarios() {
        return Stream.of(
            RoadGoldenScenarioFactory.r07TerrainFit(),
            RoadGoldenScenarioFactory.r06Sloped(),
            RoadGoldenScenarioFactory.r08Bridge());
    }

    @ParameterizedTest
    @MethodSource("cutScenarios")
    void cutAndFillRoadsHaveNoIntegrityViolations(RoadGoldenScenario scenario) {
        assertIntegrityClean(scenario);
    }

    @Test
    void bridgeScenarioHasContinuousSurfaceAcrossValley() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r08Bridge();
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            generator,
            scenario.terrain());
        RoadGenerationResult edge = preview.edgeResults().values().iterator().next();
        assertEquals(0, edge.missingRoadSurfaceCount, "R08 bridge must not miss planned surfaces");
        assertEquals(0, edge.orphanSlopePillarCount, "R08 must not leave orphan slope pillars");

        int roadColumnsInValley = 0;
        for (var entry : edge.placementRecords.entrySet()) {
            var pos = entry.getKey();
            if (pos.getX() >= 10 && pos.getX() <= 20
                    && edge.placementLayers.get(pos) == com.plot.plugin.road.solid.RoadSolidLayer.ROAD) {
                roadColumnsInValley++;
            }
        }
        assertTrue(roadColumnsInValley >= 5,
            "bridge valley must keep continuous ROAD placements, found " + roadColumnsInValley);
    }

    private static void assertIntegrityClean(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());

        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            generator,
            scenario.terrain());

        for (RoadGenerationResult edgeResult : preview.edgeResults().values()) {
            assertEquals(0, edgeResult.unclearedObstructionCount,
                scenario.id() + " uncleared obstructions");
            assertEquals(0, edgeResult.missingRoadSurfaceCount,
                scenario.id() + " missing road surface");
        }
    }
}
