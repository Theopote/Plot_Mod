package com.plot.plugin.road.pipeline.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hard constraints that voxel short-grade smoothing must not violate.
 * Stations are road-global canonical chainage.
 */
public final class VoxelProfileConstraints {

    private final Map<Double, Integer> lockedStations;
    private final Integer minBlockY;
    private final Integer maxBlockY;

    private VoxelProfileConstraints(
            Map<Double, Integer> lockedStations,
            Integer minBlockY,
            Integer maxBlockY) {
        this.lockedStations = Collections.unmodifiableMap(new LinkedHashMap<>(lockedStations));
        this.minBlockY = minBlockY;
        this.maxBlockY = maxBlockY;
    }

    public static VoxelProfileConstraints empty() {
        return new VoxelProfileConstraints(Map.of(), null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<Double, Integer> lockedStations() {
        return lockedStations;
    }

    public Integer minBlockY() {
        return minBlockY;
    }

    public Integer maxBlockY() {
        return maxBlockY;
    }

    public boolean hasLocks() {
        return !lockedStations.isEmpty();
    }

    public int contentHash() {
        return Objects.hash(lockedStations, minBlockY, maxBlockY);
    }

    public static final class Builder {
        private final Map<Double, Integer> lockedStations = new LinkedHashMap<>();
        private Integer minBlockY;
        private Integer maxBlockY;

        public Builder lockStation(double station, int blockY) {
            if (Double.isFinite(station)) {
                lockedStations.put(station, blockY);
            }
            return this;
        }

        public Builder worldYBounds(Integer minY, Integer maxY) {
            this.minBlockY = minY;
            this.maxBlockY = maxY;
            return this;
        }

        public VoxelProfileConstraints build() {
            return new VoxelProfileConstraints(lockedStations, minBlockY, maxBlockY);
        }
    }
}
