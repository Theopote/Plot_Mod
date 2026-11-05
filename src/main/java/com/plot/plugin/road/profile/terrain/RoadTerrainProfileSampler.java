package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.terrain.RoadCorridorTerrainPrefetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * 仅沿当前道路中心线采样工程地表（halfWidth=0），不求解道路设计、不生成方块。
 * <p>
 * 样本数受 {@link #MAX_SAMPLES} 限制时，按全长均匀重算间距，避免前密后空。
 */
public final class RoadTerrainProfileSampler {
    public static final int MAX_SAMPLES = 2048;
    public static final int SAMPLES_PER_TICK = 64;
    private static final double MIN_SPACING = 0.5;
    private static final double MAX_SPACING = 8.0;

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

    public static double resolveSpacing(RoadSystemConfig config) {
        double configured = config != null ? config.getPathSampleDistance() : 1.0;
        if (!Double.isFinite(configured) || configured <= 0) {
            configured = 1.0;
        }
        return Math.clamp(configured, MIN_SPACING, MAX_SPACING);
    }

    /**
     * 完整投影指纹（含世界边界绝对位置）。地形采样必须用此值，
     * 不可复用 {@code uiFingerprint()}（后者刻意忽略绝对世界位移）。
     */
    public static long transformFingerprint(ICoordinateService coordinates) {
        if (coordinates == null) {
            return 0L;
        }
        try {
            var snapshot = coordinates.captureProjection();
            return snapshot != null ? Integer.toUnsignedLong(snapshot.fingerprint()) : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public static TerrainProfileKey buildKey(
            String worldId,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates,
            RoadSystemConfig config) {
        double spacing = resolveSpacing(config);
        double totalStation = RoadStationing.canonicalLength(network, road);
        int count = resolveSampleCount(totalStation, spacing);
        double actualSpacing = count <= 1 ? spacing : totalStation / (count - 1.0);
        return new TerrainProfileKey(
            worldId != null ? worldId : "",
            road.getId(),
            geometryFingerprint(network, road),
            transformFingerprint(coordinates),
            RoadCorridorTerrainPrefetch.worldTerrainEpoch(),
            actualSpacing);
    }

    public static int resolveSampleCount(double totalStation, double spacing) {
        if (!(totalStation > 1e-6) || !(spacing > 0)) {
            return 2;
        }
        int ideal = Math.max(2, (int) Math.ceil(totalStation / spacing) + 1);
        return Math.min(MAX_SAMPLES, ideal);
    }

    /** 全长均匀桩号，并并入分段起终点等关键站。 */
    public static List<Double> buildStations(RoadNetwork network, Road road, double spacing) {
        double totalStation = RoadStationing.canonicalLength(network, road);
        int count = resolveSampleCount(totalStation, spacing);
        TreeSet<Double> stations = new TreeSet<>();
        for (int i = 0; i < count; i++) {
            double station = i == count - 1
                ? totalStation
                : totalStation * i / (count - 1.0);
            stations.add(station);
        }
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            stations.add(Math.max(0.0, Math.min(totalStation, segment.startStation())));
            stations.add(Math.max(0.0, Math.min(totalStation, segment.endStation())));
        }
        // 合并后仍可能超过 MAX_SAMPLES：保留均匀网格，关键站并入后若超限则只保留网格
        if (stations.size() > MAX_SAMPLES) {
            List<Double> trimmed = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double station = i == count - 1
                    ? totalStation
                    : totalStation * i / (count - 1.0);
                trimmed.add(station);
            }
            return List.copyOf(trimmed);
        }
        return List.copyOf(stations);
    }

    /**
     * 解析各桩号平面点。无法定位的站记为 {@code null}（采样时按未加载处理），
     * 不得使用 {@link List#copyOf}（其拒绝 null）。
     */
    public static List<Vec2d> resolvePoints(
            RoadNetwork network,
            Road road,
            List<Double> stations) {
        List<Vec2d> points = new ArrayList<>(stations.size());
        for (double station : stations) {
            points.add(RoadPlanGeometry.pointAtStation(network, road, station).orElse(null));
        }
        return java.util.Collections.unmodifiableList(points);
    }

    public static SampleColumn sampleColumn(TerrainSampler terrain, Vec2d point) {
        if (point == null || terrain == null) {
            return SampleColumn.unloaded();
        }
        OptionalInt ground = terrain.sampleLoadedSurfaceY(point);
        if (ground.isEmpty()) {
            return SampleColumn.unloaded();
        }
        OptionalInt water = terrain.findExposedWaterSurface(point);
        double waterY = Double.NaN;
        if (water.isPresent() && water.getAsInt() > ground.getAsInt()) {
            waterY = water.getAsInt();
        }
        return new SampleColumn(ground.getAsInt(), waterY, true);
    }

    public static RoadTerrainProfileData sample(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config,
            String worldId,
            ICoordinateService coordinates) {
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
        TerrainProfileKey key = buildKey(worldId, network, road, coordinates, config);
        List<Double> stations = buildStations(network, road, resolveSpacing(config));
        List<Double> terrainY = new ArrayList<>(stations.size());
        List<Double> waterY = new ArrayList<>(stations.size());
        List<Boolean> loaded = new ArrayList<>(stations.size());
        for (double station : stations) {
            Optional<Vec2d> point = RoadPlanGeometry.pointAtStation(network, road, station);
            SampleColumn column = sampleColumn(terrain, point.orElse(null));
            terrainY.add(column.terrainY());
            waterY.add(column.waterY());
            loaded.add(column.loaded());
        }
        if (stations.size() < 2) {
            return null;
        }
        return new RoadTerrainProfileData(
            key,
            totalStation,
            stations,
            terrainY,
            waterY,
            loaded);
    }

    public record SampleColumn(double terrainY, double waterY, boolean loaded) {
        public static SampleColumn unloaded() {
            return new SampleColumn(Double.NaN, Double.NaN, false);
        }
    }
}
