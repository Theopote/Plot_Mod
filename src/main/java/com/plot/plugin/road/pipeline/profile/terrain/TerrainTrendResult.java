package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.ArrayList;
import java.util.List;

/** 地形趋势构建结果：保留 raw 与 trend 双链，对外落盘时 round 为块 Y。 */
public record TerrainTrendResult(
        List<Double> rawElevations,
        List<Double> trendElevations) {

    public TerrainTrendResult {
        rawElevations = List.copyOf(rawElevations);
        trendElevations = List.copyOf(trendElevations);
        if (rawElevations.size() != trendElevations.size()) {
            throw new IllegalArgumentException("raw and trend elevations must have equal length");
        }
    }

    public List<Integer> toIntegerGuideLine() {
        List<Integer> guide = new ArrayList<>(trendElevations.size());
        for (double elevation : trendElevations) {
            guide.add((int) Math.round(elevation));
        }
        return guide;
    }
}
