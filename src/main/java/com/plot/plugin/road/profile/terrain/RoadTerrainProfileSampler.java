package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 仅沿当前道路中心线采样工程地表（halfWidth=0），不求解道路设计、不生成方块。
 */
public final class RoadTerrainProfileSampler {
    private static final double MIN_SPACING = 0.5;
    private static final double MAX_SPACING = 8.0;
    private static final int MAX_SAMPLES = 2048;

    private RoadTerrainProfileSampler() {
    }

    public static long geometryFingerprint(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return 0L;
        }
        long hash = Double.hashCode(RoadStationing.canonicalLength(network, road));
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
            for (Vec2d point : points) {
                if (point == null) {
                    continue;
                }
                hash = 31L * hash + Double.hashCode(point.x);
                hash = 31L * hash + Double.hashCode(point.y);
            }
        }
        return hash;
    }

    public static RoadTerrainProfileData sample(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config,
            long terrainRevision) {
        if (network == null || road == null || terrain == null) {
            return null;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        double totalStation = RoadStationing.canonicalLength(network, road);
        if (!(totalStation > 1e-6)) {
            return null;
        }
        double spacing = resolveSpacing(config);
        int sampleCount = Math.clamp((int) Math.floor(totalStation / spacing) + 1, 2,
                MAX_SAMPLES);
        List<Double> stations = new ArrayList<>(sampleCount + 1);
        List<Double> terrainY = new ArrayList<>(sampleCount + 1);
        List<Double> waterY = new ArrayList<>(sampleCount + 1);
        List<Boolean> loaded = new ArrayList<>(sampleCount + 1);

        for (int i = 0; i < sampleCount; i++) {
            double station = Math.min(totalStation, i * spacing);
            appendSample(network, road, terrain, station, stations, terrainY, waterY, loaded);
        }
        if (stations.isEmpty() || stations.getLast() + 1e-6 < totalStation) {
            appendSample(network, road, terrain, totalStation, stations, terrainY, waterY, loaded);
        }
        if (stations.size() < 2) {
            return null;
        }
        return new RoadTerrainProfileData(
            road.getId(),
            totalStation,
            stations,
            terrainY,
            waterY,
            loaded,
            geometryFingerprint(network, road),
            terrainRevision);
    }

    private static void appendSample(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            double station,
            List<Double> stations,
            List<Double> terrainY,
            List<Double> waterY,
            List<Boolean> loaded) {
        if (!stations.isEmpty() && Math.abs(stations.getLast() - station) < 1e-9) {
            return;
        }
        Optional<Vec2d> point = RoadPlanGeometry.pointAtStation(network, road, station);
        stations.add(station);
        if (point.isEmpty()) {
            terrainY.add(Double.NaN);
            waterY.add(Double.NaN);
            loaded.add(false);
            return;
        }
        OptionalInt ground = terrain.sampleLoadedSurfaceY(point.get());
        if (ground.isEmpty()) {
            terrainY.add(Double.NaN);
            waterY.add(Double.NaN);
            loaded.add(false);
            return;
        }
        terrainY.add((double) ground.getAsInt());
        OptionalInt water = terrain.findExposedWaterSurface(point.get());
        if (water.isPresent() && water.getAsInt() > ground.getAsInt()) {
            waterY.add((double) water.getAsInt());
        } else {
            waterY.add(Double.NaN);
        }
        loaded.add(true);
    }

    private static double resolveSpacing(RoadSystemConfig config) {
        double configured = config != null ? config.getPathSampleDistance() : 1.0;
        if (!Double.isFinite(configured) || configured <= 0) {
            configured = 1.0;
        }
        return Math.clamp(configured, MIN_SPACING, MAX_SPACING);
    }
}
