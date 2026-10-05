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

    public static WaterCrossingSettings defaults() {
        return new WaterCrossingSettings(1, 6.0, 2, 6.0, DEFAULT_LONG_BRIDGE_LENGTH_METERS, false);
    }
}
