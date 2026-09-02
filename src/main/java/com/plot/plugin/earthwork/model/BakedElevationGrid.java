package com.plot.plugin.earthwork.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 烘焙设计标高缓存（道路导入等）；按世界格点 XZ 索引。
 */
public final class BakedElevationGrid {
    private final Map<Long, Integer> elevations = new LinkedHashMap<>();

    public boolean isEmpty() {
        return elevations.isEmpty();
    }

    public int sampleCount() {
        return elevations.size();
    }

    public void put(int worldX, int worldZ, int targetY) {
        elevations.put(cellKey(worldX, worldZ), targetY);
    }

    public Integer get(int worldX, int worldZ) {
        return elevations.get(cellKey(worldX, worldZ));
    }

    public int evaluateAt(int worldX, int worldZ, int fallbackElevation) {
        Integer exact = get(worldX, worldZ);
        if (exact != null) {
            return exact;
        }
        if (elevations.isEmpty()) {
            return fallbackElevation;
        }
        int bestDistance = Integer.MAX_VALUE;
        int bestElevation = fallbackElevation;
        for (Map.Entry<Long, Integer> entry : elevations.entrySet()) {
            int sampleX = unpackX(entry.getKey());
            int sampleZ = unpackZ(entry.getKey());
            int distance = Math.abs(sampleX - worldX) + Math.abs(sampleZ - worldZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestElevation = entry.getValue();
            }
        }
        return bestElevation;
    }

    public List<Sample> toSamples() {
        List<Sample> samples = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : elevations.entrySet()) {
            samples.add(new Sample(unpackX(entry.getKey()), unpackZ(entry.getKey()), entry.getValue()));
        }
        return Collections.unmodifiableList(samples);
    }

    public static BakedElevationGrid fromSamples(List<Sample> samples) {
        BakedElevationGrid grid = new BakedElevationGrid();
        if (samples == null) {
            return grid;
        }
        for (Sample sample : samples) {
            if (sample == null) {
                continue;
            }
            grid.put(sample.worldX(), sample.worldZ(), sample.targetY());
        }
        return grid;
    }

    public record Sample(int worldX, int worldZ, int targetY) {
    }

    private static long cellKey(int worldX, int worldZ) {
        return ((long) worldX << 32) | (worldZ & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
