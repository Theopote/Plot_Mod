package com.plot.plugin.road.profile;

/**
 * 纵断面质量摘要（预览级，非最终三维土方结算）。
 * <p>
 * Design：连续设计纵断面工程指标；Build：Minecraft 方块化结果；Terrain：中心线挖填估算。
 */
public record RoadProfileQualityMetrics(
        DesignMetrics design,
        BuildMetrics build,
        TerrainMetrics terrain) {

    private static final RoadProfileQualityMetrics EMPTY = new RoadProfileQualityMetrics(
        DesignMetrics.empty(),
        BuildMetrics.empty(),
        TerrainMetrics.empty());

    public record DesignMetrics(
            double maxGradePercent,
            double maxGradeChangePercent,
            double longestGradeRun) {

        private static final DesignMetrics EMPTY = new DesignMetrics(0.0, 0.0, 0.0);

        public static DesignMetrics empty() {
            return EMPTY;
        }

        public boolean exceedsMaxGrade(double limitPercent) {
            return limitPercent > 0.0 && maxGradePercent > limitPercent + 1e-6;
        }

        public boolean exceedsMaxGradeChange(double limitPercent) {
            return limitPercent > 0.0 && maxGradeChangePercent > limitPercent + 1e-6;
        }
    }

    public record BuildMetrics(
            int stepCount,
            int longestFlatRun,
            double maxDesignBuildDeviation,
            int abnormalStepCount,
            double cumulativeGradeError) {

        private static final BuildMetrics EMPTY =
            new BuildMetrics(0, 0, 0.0, 0, 0.0);

        public static BuildMetrics empty() {
            return EMPTY;
        }

        public boolean hasAbnormalSteps() {
            return abnormalStepCount > 0;
        }

        public boolean hasDesignBuildMismatch() {
            return maxDesignBuildDeviation > 1.0;
        }
    }

    public record TerrainMetrics(int cutBlockColumns, int fillBlockColumns) {

        private static final TerrainMetrics EMPTY = new TerrainMetrics(0, 0);

        public static TerrainMetrics empty() {
            return EMPTY;
        }

        public int earthworkBlockColumns() {
            return cutBlockColumns + fillBlockColumns;
        }

        /** 填方减挖方；正数表示净填。 */
        public int balanceBlockColumns() {
            return fillBlockColumns - cutBlockColumns;
        }
    }

    public static RoadProfileQualityMetrics empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
