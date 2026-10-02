package com.plot.plugin.road.pipeline.profile;

import com.plot.core.geometry.VoxelElevationDiscretizer;

import java.util.List;

/**
 * Pre-rasterized Minecraft build elevations indexed by world-station (blocks).
 */
public final class BuildHeightProfile {

    private static final double EPSILON = 1e-9;
    private static final BuildHeightProfile INACTIVE = new BuildHeightProfile(new int[0], 0.0, 0);

    private final int[] elevationsByBlock;
    private final double endStation;
    private final int endElevation;

    public BuildHeightProfile(List<BuildHeightSample> samples) {
        if (samples == null || samples.isEmpty()) {
            elevationsByBlock = new int[0];
            endStation = 0.0;
            endElevation = 0;
            return;
        }
        BuildHeightSample lastSample = samples.getLast();
        double lastStation = lastSample.station();
        endElevation = lastSample.buildY();
        int blockCount = (int) Math.floor(lastStation + EPSILON);
        elevationsByBlock = new int[blockCount + 1];
        int sampleIndex = 0;
        for (int block = 0; block <= blockCount; block++) {
            while (sampleIndex + 1 < samples.size()
                    && samples.get(sampleIndex + 1).station() <= block + EPSILON) {
                sampleIndex++;
            }
            elevationsByBlock[block] = samples.get(sampleIndex).buildY();
        }
        endStation = lastStation;
    }

    private BuildHeightProfile(int[] elevationsByBlock, double endStation, int endElevation) {
        this.elevationsByBlock = elevationsByBlock;
        this.endStation = endStation;
        this.endElevation = endElevation;
    }

    public static BuildHeightProfile inactive() {
        return INACTIVE;
    }

    public static BuildHeightProfile fromSamples(List<BuildHeightSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return inactive();
        }
        return new BuildHeightProfile(samples);
    }

    public boolean isActive() {
        return elevationsByBlock.length > 0;
    }

    public double endStation() {
        return endStation;
    }

    public int endElevation() {
        return endElevation;
    }

    public int elevationAtWorldStation(double worldStation) {
        if (elevationsByBlock.length == 0) {
            return 0;
        }
        if (!Double.isFinite(worldStation) || worldStation <= EPSILON) {
            return elevationsByBlock[0];
        }
        if (worldStation >= endStation - EPSILON) {
            return endElevation;
        }
        return VoxelElevationDiscretizer.elevationAtStation(worldStation, elevationsByBlock);
    }
}
