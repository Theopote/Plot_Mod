package com.plot.plugin.road.pipeline.corridor;

/**
 * Stable identity for one shoulder batter ray from a single cross-section sample.
 * Different station samples must never share an id, even when world columns overlap.
 */
public record SlopeRayId(long stationSampleId, ShoulderSide side) {

    public enum ShoulderSide {
        LEFT,
        RIGHT
    }
}
