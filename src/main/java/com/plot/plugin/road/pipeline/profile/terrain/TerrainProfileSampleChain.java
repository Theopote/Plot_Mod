package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.plugin.road.pipeline.profile.ProfileGroundSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 道路纵断面地形采样链：canonical station + double 高程（求解全程保持浮点精度）。 */
public record TerrainProfileSampleChain(List<Double> stations, List<Double> rawElevations) {

    public TerrainProfileSampleChain {
        stations = List.copyOf(stations);
        rawElevations = List.copyOf(rawElevations);
        if (stations.size() != rawElevations.size()) {
            throw new IllegalArgumentException("stations and elevations must have equal length");
        }
    }

    public static TerrainProfileSampleChain fromWorldSamples(
            List<Double> worldStations,
            List<Integer> groundSamples) {
        Objects.requireNonNull(worldStations, "worldStations");
        Objects.requireNonNull(groundSamples, "groundSamples");
        if (worldStations.isEmpty() || groundSamples.isEmpty()) {
            return new TerrainProfileSampleChain(List.of(), List.of());
        }
        if (worldStations.size() != groundSamples.size()) {
            throw new IllegalArgumentException("station and ground sample counts must match");
        }
        List<Double> elevations = new ArrayList<>(groundSamples.size());
        for (int groundSample : groundSamples) {
            elevations.add((double) groundSample);
        }
        return new TerrainProfileSampleChain(worldStations, elevations);
    }

    public static TerrainProfileSampleChain fromSampleData(
            ProfileGroundSampler.SampleData sampleData,
            List<Double> worldStations) {
        return fromWorldSamples(worldStations, sampleData.groundSamples());
    }

    public TerrainProfileSampleChain withElevations(List<Double> elevations) {
        return new TerrainProfileSampleChain(stations, elevations);
    }

    public int size() {
        return stations.size();
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    public double totalLength() {
        return stations.isEmpty() ? 0.0 : stations.getLast();
    }
}
