package com.plot.plugin.road.pipeline.profile;

import com.plot.core.geometry.VoxelElevationDiscretizer;

import java.util.List;

/**
 * Pre-rasterized Minecraft build elevations indexed by world-station (blocks).
 */
public final class BuildHeightProfile {

    private static final BuildHeightProfile INACTIVE = new BuildHeightProfile(new int[0], 0.0);

    private final int[] elevationsByBlock;
    private final double endStation;

    public BuildHeightProfile(List<BuildHeightSample> samples) {
        if (samples == null || samples.isEmpty()) {
            elevationsByBlock = new int[0];
            endStation = 0.0;
            return;
        }
        double lastStation = samples.getLast().station();
        int blockCount = (int) Math.floor(lastStation + 1e-9);
        elevationsByBlock = new int[blockCount + 1];
        int sampleIndex = 0;
        for (int block = 0; block <= blockCount; block++) {
            while (sampleIndex + 1 < samples.size()
                    && samples.get(sampleIndex + 1).station() <= block + 1e-9) {
                sampleIndex++;
            }
            elevationsByBlock[block] = samples.get(sampleIndex).buildY();
        }
        endStation = lastStation;
    }

    private BuildHeightProfile(int[] elevationsByBlock, double endStation) {
        this.elevationsByBlock = elevationsByBlock;
        this.endStation = endStation;
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

    public int elevationAtWorldStation(double worldStation) {
        return VoxelElevationDiscretizer.elevationAtStation(worldStation, elevationsByBlock);
    }
}
