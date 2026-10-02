package com.plot.plugin.road.pipeline.profile;

/**
 * Block-placement resolution sample along road chainage (world blocks).
 */
public record BuildHeightSample(double station, double designElevation, int buildY) {
}
