package com.plot.plugin.road.pipeline.profile.environment;

/**
 * A continuous water span with approach / crossing / exit zones along profile chainage.
 */
public record WaterCrossing(
        double approachStartStation,
        double crossingStartStation,
        double crossingEndStation,
        double exitEndStation,
        double lengthMeters,
        double averageDepth,
        double maxDepth,
        int entryBankTerrainY,
        int exitBankTerrainY,
        int waterSurfaceY,
        WaterCrossingStrategy strategy) {

    public double crossingLengthMeters() {
        return Math.max(0.0, crossingEndStation - crossingStartStation);
    }

    public boolean containsStation(double station) {
        return station >= approachStartStation - 1e-9 && station <= exitEndStation + 1e-9;
    }

    public boolean isInCrossingZone(double station) {
        return station >= crossingStartStation - 1e-9 && station <= crossingEndStation + 1e-9;
    }

    public boolean isInApproachZone(double station) {
        return station >= approachStartStation - 1e-9 && station < crossingStartStation - 1e-9;
    }

    public boolean isInExitZone(double station) {
        return station > crossingEndStation + 1e-9 && station <= exitEndStation + 1e-9;
    }
}
