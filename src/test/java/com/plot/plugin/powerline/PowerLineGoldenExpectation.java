package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.TowerRole;

import java.util.Map;

/** 单场景可接受的指标区间与契约。 */
final class PowerLineGoldenExpectation {
    private final IntRange poleCount;
    private final IntRange towerGapCount;
    private final IntRange conductorSpanCount;
    private final IntRange attachmentCount;
    private final IntRange blockCount;
    private final IntRange structureBlockCount;
    private final IntRange invalidPoleCount;
    private final IntRange warningCount;
    private final Map<TowerRole, IntRange> roleCounts;
    private final IntRange bboxSpanX;
    private final IntRange bboxSpanY;
    private final IntRange bboxSpanZ;
    private final boolean requireStructuralFrontSymmetry;

    PowerLineGoldenExpectation(
            IntRange poleCount,
            IntRange towerGapCount,
            IntRange conductorSpanCount,
            IntRange attachmentCount,
            IntRange blockCount,
            IntRange structureBlockCount,
            IntRange invalidPoleCount,
            IntRange warningCount,
            Map<TowerRole, IntRange> roleCounts,
            IntRange bboxSpanX,
            IntRange bboxSpanY,
            IntRange bboxSpanZ,
            boolean requireStructuralFrontSymmetry) {
        this.poleCount = poleCount;
        this.towerGapCount = towerGapCount;
        this.conductorSpanCount = conductorSpanCount;
        this.attachmentCount = attachmentCount;
        this.blockCount = blockCount;
        this.structureBlockCount = structureBlockCount;
        this.invalidPoleCount = invalidPoleCount;
        this.warningCount = warningCount;
        this.roleCounts = roleCounts;
        this.bboxSpanX = bboxSpanX;
        this.bboxSpanY = bboxSpanY;
        this.bboxSpanZ = bboxSpanZ;
        this.requireStructuralFrontSymmetry = requireStructuralFrontSymmetry;
    }

    void assertMatches(String scenarioId, PowerLineGoldenMetrics metrics) {
        poleCount.assertEquals(scenarioId, "poleCount", metrics.poleCount());
        towerGapCount.assertEquals(scenarioId, "towerGapCount", metrics.towerGapCount());
        conductorSpanCount.assertEquals(scenarioId, "conductorSpanCount", metrics.conductorSpanCount());
        attachmentCount.assertEquals(scenarioId, "attachmentCount", metrics.totalAttachmentCount());
        blockCount.assertEquals(scenarioId, "blockCount", metrics.blockCount());
        structureBlockCount.assertEquals(scenarioId, "structureBlockCount", metrics.structureBlockCount());
        invalidPoleCount.assertEquals(scenarioId, "invalidPoleCount", metrics.invalidPoleCount());
        warningCount.assertEquals(scenarioId, "warningCount", metrics.warningCount());

        PowerLineGoldenMetrics.IntCuboid box = metrics.boundingBox();
        bboxSpanX.assertEquals(scenarioId, "bboxSpanX", box.spanX());
        bboxSpanY.assertEquals(scenarioId, "bboxSpanY", box.spanY());
        bboxSpanZ.assertEquals(scenarioId, "bboxSpanZ", box.spanZ());

        if (roleCounts != null) {
            for (Map.Entry<TowerRole, IntRange> entry : roleCounts.entrySet()) {
                int actual = metrics.roleCounts().getOrDefault(entry.getKey(), 0);
                entry.getValue().assertEquals(scenarioId, "role:" + entry.getKey(), actual);
            }
        }

        if (requireStructuralFrontSymmetry && !metrics.structuralFrontSymmetry()) {
            throw new AssertionError(scenarioId + " expected structural front symmetry on z=0 mirror");
        }
    }

    static final class IntRange {
        private final int min;
        private final int max;

        IntRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        static IntRange exactly(int value) {
            return new IntRange(value, value);
        }

        static IntRange atLeast(int min) {
            return new IntRange(min, Integer.MAX_VALUE);
        }

        static IntRange between(int min, int max) {
            return new IntRange(min, max);
        }

        void assertEquals(String scenarioId, String field, int actual) {
            if (actual < min || actual > max) {
                throw new AssertionError(
                    scenarioId + " " + field + " expected [" + min + ", " + max + "] but was " + actual);
            }
        }
    }
}
