package com.plot.plugin.road.pipeline.profile.environment;

/** Internal thresholds for water-crossing classification. */
public record WaterCrossingSettings(
        int waterRoadClearanceBlocks,
        double causewayMaxLengthMeters,
        int causewayMaxDepthBlocks,
        double bridgePreferredMinLengthMeters,
        double longBridgeLengthMeters,
        boolean allowUnderwaterRoad) {

    public static final double DEFAULT_LONG_BRIDGE_LENGTH_METERS = 60.0;

    /**
     * 默认不自动选 CAUSEWAY（causewayMaxLengthMeters=0）：普通道路不在水域填土。
     * 需要堤道时由调用方显式传入正的 causewayMaxLengthMeters。
     */
    public static WaterCrossingSettings defaults() {
        return new WaterCrossingSettings(1, 0.0, 2, 6.0, DEFAULT_LONG_BRIDGE_LENGTH_METERS, false);
    }
}
