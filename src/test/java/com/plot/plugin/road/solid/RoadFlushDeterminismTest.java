package com.plot.plugin.road.solid;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadFlushDeterminismTest {

    static Stream<RoadGoldenScenario> flushScenarios() {
        return Stream.of(
            RoadGoldenScenarioFactory.r09Tunnel(),
            RoadGoldenScenarioFactory.r04CrossJunction(),
            RoadGoldenScenarioFactory.r08Bridge());
    }

    @ParameterizedTest
    @MethodSource("flushScenarios")
    void flushThresholdDoesNotChangeAggregatePlacements(RoadGoldenScenario scenario) {
        Map<BlockPos, String> baseline = generatePlacementMap(scenario, 100_000);
        assertTrue(baseline.size() > 100, scenario.id() + " must produce enough placements to test flush");

        for (int threshold : List.of(100, 1_000)) {
            Map<BlockPos, String> actual = generatePlacementMap(scenario, threshold);
            assertEquals(baseline, actual, scenario.id() + " mismatch at maxDedupKeys=" + threshold);
        }
    }

    static Stream<RoadGoldenScenario> integrityFlushScenarios() {
        return Stream.of(
            RoadGoldenScenarioFactory.r04CrossJunction(),
            RoadGoldenScenarioFactory.r08Bridge());
    }

    @ParameterizedTest
    @MethodSource("integrityFlushScenarios")
    void flushThresholdDoesNotChangeIntegrityMetrics(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());

        for (int threshold : List.of(100, 1_000, 100_000)) {
            generator.testPipelineHost().setMaxDedupKeysForTesting(threshold);
            RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
                scenario.network(),
                generator,
                scenario.terrain());
            for (var edgeResult : preview.edgeResults().values()) {
                assertEquals(0, edgeResult.missingRoadSurfaceCount,
                    scenario.id() + " missing surface at maxDedupKeys=" + threshold);
                assertEquals(0, edgeResult.unclearedObstructionCount,
                    scenario.id() + " uncleared obstruction at maxDedupKeys=" + threshold);
            }
        }
    }

    private static Map<BlockPos, String> generatePlacementMap(RoadGoldenScenario scenario, int maxDedupKeys) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        generator.testPipelineHost().setMaxDedupKeysForTesting(maxDedupKeys);

        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(),
            generator,
            scenario.terrain());

        assertEquals(0, preview.aggregate().droppedSolidCount,
            scenario.id() + " must not drop solids at maxDedupKeys=" + maxDedupKeys);

        Map<BlockPos, String> map = new LinkedHashMap<>();
        preview.aggregate().placementRecords.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(blockPosComparator()))
            .forEach(entry -> map.put(entry.getKey(), entry.getValue().newBlockId));
        return map;
    }

    private static Comparator<BlockPos> blockPosComparator() {
        return Comparator.comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
    }
}
