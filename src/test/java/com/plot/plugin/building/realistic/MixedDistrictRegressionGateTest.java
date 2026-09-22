package com.plot.plugin.building.realistic;

import com.plot.plugin.building.generation.DistrictGenerationResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI 门禁：D-B19 混合片区 100 栋真实生成链（sampled site + fail-soft + 按类分桶）。
 * <p>
 * 运行：
 * {@code ./gradlew test --tests "com.plot.plugin.building.realistic.MixedDistrictRegressionGateTest"}
 */
class MixedDistrictRegressionGateTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/MixedDistrictGate");
    /** 100 栋混合轮廓（含椭圆/凹形/重叠）在 CI 上的生成时间上限。 */
    private static final long CI_MAX_GENERATION_MILLIS = 120_000L;

    @Test
    void ciGateMixedDistrict100_D_B19() {
        List<TaggedFootprint> catalog = RealisticDistrictFixtures.mixedDistrict100();

        long startNanos = System.nanoTime();
        DistrictGenerationResult district = RealisticGenerationChainHarness.runDistrict(
            catalog, RealisticGenerationChainHarness.SiteMode.SAMPLED);
        long generationMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        RealisticDistrictGapReport.Summary report = RealisticDistrictGapReport.analyze(district, catalog);
        String summary = String.format(
            Locale.ROOT,
            "D-B19 attempted=%d generated=%d skipped=%d blocks=%d genMs=%d overlapPairs=%d",
            report.attempted(),
            report.generated(),
            report.skipped(),
            district.totalBlocks(),
            generationMillis,
            report.overlapPairs());
        LOGGER.info(summary);
        System.out.println("[MixedDistrictGate] " + summary);
        LOGGER.info(report.format());

        MixedDistrictRegressionAssertions.assertBaseline(district, report);
        assertTrue(
            generationMillis < CI_MAX_GENERATION_MILLIS,
            () -> "D-B19 mixed district too slow: " + summary
                + " (limit " + CI_MAX_GENERATION_MILLIS + "ms)");
    }
}
