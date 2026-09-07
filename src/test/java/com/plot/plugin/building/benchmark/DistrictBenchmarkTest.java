package com.plot.plugin.building.benchmark;

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
        return DistrictMassingFixtures.massingFootprint(index, floors);
    }

    static List<BuildingFootprint> district(int count, int floors) {
        return DistrictMassingFixtures.district(count, floors);
    }

    static Metrics run(String id, int count, int floors) {
        return runWithGenerator(id, count, floors, DistrictBenchmarkTest::generateOne);
    }

    static Metrics runSampledSite(String id, int count, int floors) {
        return runWithGenerator(id, count, floors, DistrictBenchmarkTest::generateOneWithSampledSite);
    }

    static Metrics runWithGenerator(String id, int count, int floors, Generator generator) {
        List<BuildingFootprint> buildings = district(count, floors);
        generator.generate(buildings.getFirst());

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMem = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        DistrictGenerationResult district = DistrictMassingGenerator.generate(buildings, generator::generate);
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

    @FunctionalInterface
    interface Generator {
        BuildingGenerationResult generate(BuildingFootprint footprint);
    }

    private static BuildingGenerationResult generateOneWithSampledSite(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTestingWithSampledSite(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        return PIPELINE.generate(context);
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "D06, 10, 4, 20000",
        "D07, 50, 4, 80000"
    })
    void ciGateSampledSiteDistrictScales(String id, int count, int floors, long maxMillis) {
        Metrics metrics = runSampledSite(id, count, floors);
        assertEquals(count, metrics.generated());
        assertEquals(0, metrics.skipped());
        assertTrue(metrics.blocks() > 0);
        assertTrue(
            metrics.generationMillis() < maxMillis,
            () -> id + " sampled-site too slow: " + metrics.summary() + " (limit " + maxMillis + "ms)");
    }

    @Test
    void overlapFootprintPairBenchmarkAt500() {
        List<BuildingFootprint> buildings = district(500, 3);
        long start = System.nanoTime();
        int pairCount = com.plot.plugin.building.generation.DistrictOverlapAnalyzer
            .findFootprintOverlapPairs(buildings).size();
        double overlapMs = (System.nanoTime() - start) / 1_000_000.0;
        String summary = String.format(
            Locale.ROOT,
            "Overlap500 buildings=500 pairs=%d overlapMs=%.1f",
            pairCount,
            overlapMs);
        LOGGER.info(summary);
        System.out.println("[DistrictBenchmark] " + summary);
        assertTrue(overlapMs < 5000, () -> "O(n²) overlap too slow: " + summary);
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
        Metrics d06 = runSampledSite("D06-full", 100, 4);
        Metrics d07 = runSampledSite("D07-full", 500, 3);
        assertEquals(100, d03.generated());
        assertEquals(250, d04.generated());
        assertEquals(500, d05.generated());
        assertEquals(100, d06.generated());
        assertEquals(500, d07.generated());
        assertTrue(d03.blocks() > 0 && d04.blocks() > 0 && d05.blocks() > 0);
        assertTrue(d06.blocks() > 0 && d07.blocks() > 0);
    }
}
