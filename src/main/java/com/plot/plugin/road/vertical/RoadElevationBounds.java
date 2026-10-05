package com.plot.plugin.road.vertical;

/**
 * Inclusive world block Y limits for road surface elevation editing and validation.
 */
public record RoadElevationBounds(double minY, double maxY) {

    public RoadElevationBounds {
        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || maxY < minY) {
            throw new IllegalArgumentException("invalid elevation bounds");
        }
    }

    public double clamp(double elevation) {
        if (!Double.isFinite(elevation)) {
            return minY;
        }
        return Math.max(minY, Math.min(maxY, elevation));
    }

    public boolean contains(double elevation) {
        return Double.isFinite(elevation) && elevation >= minY && elevation <= maxY;
    }

    public float minYFloat() {
        return (float) minY;
    }

    public float maxYFloat() {
        return (float) maxY;
    }
}
