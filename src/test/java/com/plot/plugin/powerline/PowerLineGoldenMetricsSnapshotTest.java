package com.plot.plugin.powerline;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** 开发用：打印各 Golden 场景实测指标，便于校准 {@link PowerLineGoldenScenario} 期望值。 */
class PowerLineGoldenMetricsSnapshotTest {

    @Test
    @Disabled("Dev helper: run manually to refresh golden expectations")
    void printAllScenarioMetrics() {
        for (PowerLineGoldenScenario scenario : PowerLineGoldenScenario.values()) {
            PowerLineGoldenRun run = scenario.run();
            PowerLineGoldenMetrics metrics = run.metrics();
            PowerLineGoldenMetrics.IntCuboid box = metrics.boundingBox();
            System.out.printf(
                "%s poles=%d gaps=%d conductors=%d attachments=%d blocks=%d struct=%d invalid=%d warnings=%d roles=%s bbox=%d,%d,%d closed=%s%n",
                scenario.id(),
                metrics.poleCount(),
                metrics.towerGapCount(),
                metrics.conductorSpanCount(),
                metrics.totalAttachmentCount(),
                metrics.blockCount(),
                metrics.structureBlockCount(),
                metrics.invalidPoleCount(),
                metrics.warningCount(),
                metrics.roleCounts(),
                box.spanX(),
                box.spanY(),
                box.spanZ(),
                run.footprint().isClosedLoop());
        }
    }
}
