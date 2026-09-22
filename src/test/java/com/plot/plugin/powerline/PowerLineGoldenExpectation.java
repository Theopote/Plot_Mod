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

    private PowerLineGoldenExpectation(Builder builder) {
        this.poleCount = builder.poleCount;
        this.towerGapCount = builder.towerGapCount;
        this.conductorSpanCount = builder.conductorSpanCount;
        this.attachmentCount = builder.attachmentCount;
        this.blockCount = builder.blockCount;
        this.structureBlockCount = builder.structureBlockCount;
        this.invalidPoleCount = builder.invalidPoleCount;
        this.warningCount = builder.warningCount;
        this.roleCounts = builder.roleCounts;
        this.bboxSpanX = builder.bboxSpanX;
        this.bboxSpanY = builder.bboxSpanY;
        this.bboxSpanZ = builder.bboxSpanZ;
        this.requireStructuralFrontSymmetry = builder.requireStructuralFrontSymmetry;
    }

    static Builder builder() {
        return new Builder();
    }

    PowerLineGoldenExpectation withWarnings(IntRange warningCount) {
        return builder()
            .poleCount(poleCount)
            .towerGapCount(towerGapCount)
            .conductorSpanCount(conductorSpanCount)
            .attachmentCount(attachmentCount)
            .blockCount(blockCount)
            .structureBlockCount(structureBlockCount)
            .invalidPoleCount(invalidPoleCount)
            .warningCount(warningCount)
            .roleCounts(roleCounts)
            .bboxSpanX(bboxSpanX)
            .bboxSpanY(bboxSpanY)
            .bboxSpanZ(bboxSpanZ)
            .requireStructuralFrontSymmetry(requireStructuralFrontSymmetry)
            .build();
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

    static final class Builder {
        private IntRange poleCount;
        private IntRange towerGapCount;
        private IntRange conductorSpanCount;
        private IntRange attachmentCount;
        private IntRange blockCount;
        private IntRange structureBlockCount;
        private IntRange invalidPoleCount;
        private IntRange warningCount;
        private Map<TowerRole, IntRange> roleCounts;
        private IntRange bboxSpanX;
        private IntRange bboxSpanY;
        private IntRange bboxSpanZ;
        private boolean requireStructuralFrontSymmetry;

        Builder poleCount(IntRange poleCount) {
            this.poleCount = poleCount;
            return this;
        }

        Builder towerGapCount(IntRange towerGapCount) {
            this.towerGapCount = towerGapCount;
            return this;
        }

        Builder conductorSpanCount(IntRange conductorSpanCount) {
            this.conductorSpanCount = conductorSpanCount;
            return this;
        }

        Builder attachmentCount(IntRange attachmentCount) {
            this.attachmentCount = attachmentCount;
            return this;
        }

        Builder blockCount(IntRange blockCount) {
            this.blockCount = blockCount;
            return this;
        }

        Builder structureBlockCount(IntRange structureBlockCount) {
            this.structureBlockCount = structureBlockCount;
            return this;
        }

        Builder invalidPoleCount(IntRange invalidPoleCount) {
            this.invalidPoleCount = invalidPoleCount;
            return this;
        }

        Builder warningCount(IntRange warningCount) {
            this.warningCount = warningCount;
            return this;
        }

        Builder roleCounts(Map<TowerRole, IntRange> roleCounts) {
            this.roleCounts = roleCounts;
            return this;
        }

        Builder bboxSpanX(IntRange bboxSpanX) {
            this.bboxSpanX = bboxSpanX;
            return this;
        }

        Builder bboxSpanY(IntRange bboxSpanY) {
            this.bboxSpanY = bboxSpanY;
            return this;
        }

        Builder bboxSpanZ(IntRange bboxSpanZ) {
            this.bboxSpanZ = bboxSpanZ;
            return this;
        }

        Builder requireStructuralFrontSymmetry(boolean requireStructuralFrontSymmetry) {
            this.requireStructuralFrontSymmetry = requireStructuralFrontSymmetry;
            return this;
        }

        PowerLineGoldenExpectation build() {
            return new PowerLineGoldenExpectation(this);
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
