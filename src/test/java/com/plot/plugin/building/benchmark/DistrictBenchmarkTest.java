package com.plot.plugin.building.benchmark;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * District Massing Phase F：片区规模基准（D01–D05）。
 * <p>
 * CI 默认跑 D01(10) / D02(50)。更大档位需：
 * {@code -Dplot.district.benchmark=full}
 */
class DistrictBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/DistrictBenchmark");
    private static final BuildingGenerationPipeline PIPELINE =
        BuildingGenerationPipeline.createDefault();

    record Metrics(
            String id,
            int buildings,
            int generated,
            int skipped,
            int blocks,
            long generationNanos,
            long usedMemoryBytes) {

        double generationMillis() {
            return generationNanos / 1_000_000.0;
        }

        double blocksPerSecond() {
            double seconds = generationNanos / 1_000_000_000.0;
            return seconds <= 0 ? 0 : blocks / seconds;
        }

        String summary() {
            return String.format(
                Locale.ROOT,
                "%s buildings=%d generated=%d skipped=%d blocks=%d genMs=%.1f blocksPerSec=%.0f memMB=%.1f",
                id,
                buildings,
                generated,
                skipped,
                blocks,
                generationMillis(),
                blocksPerSecond(),
                usedMemoryBytes / (1024.0 * 1024.0));
        }
    }

    static BuildingFootprint massingFootprint(int index, int floors) {
        double x = (index % 20) * 12.0;
        double z = (index / 20) * 10.0;
        BuildingFootprint footprint = new BuildingFootprint("d-" + index, List.of(
            new Vec2d(x, z),
            new Vec2d(x + 8, z),
            new Vec2d(x + 8, z + 6),
            new Vec2d(x, z + 6)
        ), true);
        footprint.setName("B" + index);
        footprint.setFloors(floors);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        footprint.setWindowSpacing(0);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        return footprint;
    }

    static List<BuildingFootprint> district(int count, int floors) {
        List<BuildingFootprint> buildings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buildings.add(massingFootprint(i, floors));
        }
        return buildings;
    }

    static Metrics run(String id, int count, int floors) {
        List<BuildingFootprint> buildings = district(count, floors);
        // warmup one building so classloading does not dominate D01
        generateOne(buildings.getFirst());

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMem = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            buildings,
            DistrictBenchmarkTest::generateOne);
        long elapsed = System.nanoTime() - start;
        long afterMem = runtime.totalMemory() - runtime.freeMemory();

        Metrics metrics = new Metrics(
            id,
            count,
            district.buildingsGenerated(),
            district.buildingsSkipped(),
            district.totalBlocks(),
            elapsed,
            Math.max(0, afterMem - beforeMem));
        LOGGER.info(metrics.summary());
        System.out.println("[DistrictBenchmark] " + metrics.summary());
        return metrics;
    }

    private static BuildingGenerationResult generateOne(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        return PIPELINE.generate(context);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "D01, 10, 4, 15000",
        "D02, 50, 4, 60000"
    })
    void ciGateDistrictScales(String id, int count, int floors, long maxMillis) {
        Metrics metrics = run(id, count, floors);
        assertEquals(count, metrics.generated());
        assertEquals(0, metrics.skipped());
        assertTrue(metrics.blocks() > 0);
        assertTrue(
            metrics.generationMillis() < maxMillis,
            () -> id + " too slow: " + metrics.summary() + " (limit " + maxMillis + "ms)");
    }

    @Test
    void fullSuiteWhenPropertyEnabled() {
        String flag = System.getProperty("plot.district.benchmark", "");
        if (flag.isBlank()) {
            flag = System.getenv().getOrDefault("PLOT_DISTRICT_BENCHMARK", "");
        }
        if (!"full".equalsIgnoreCase(flag)) {
            LOGGER.info("Skip D03–D05; enable with -Dplot.district.benchmark=full or PLOT_DISTRICT_BENCHMARK=full");
            return;
        }
        Metrics d03 = run("D03", 100, 4);
        Metrics d04 = run("D04", 250, 3);
        Metrics d05 = run("D05", 500, 3);
        assertEquals(100, d03.generated());
        assertEquals(250, d04.generated());
        assertEquals(500, d05.generated());
        assertTrue(d03.blocks() > 0 && d04.blocks() > 0 && d05.blocks() > 0);
    }
}
