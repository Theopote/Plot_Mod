package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 沿道路中心线密化站采样地表，用于发现「端点不变、中段地形已改」的缓存失效场景。
 * <p>
 * 与 {@link RoadCorridorTerrainPrefetch#fingerprint} 的道路几何指纹分开：本探针只反映世界地表 Y。
 * 每个中心线站额外采样左右侧向点，以便路肩/边坡旁改地也能使指纹变化。
 */
public final class RoadWorldTerrainProbe {
    /** 单次探针中心线站上限，避免巨型路网在预览路径上卡顿。 */
    public static final int MAX_PROBE_STATIONS = 512;
    /** 相对中心线的侧向探针偏移（画布单位，通常≈方块）。 */
    public static final double LATERAL_PROBE_OFFSET = 4.0;

    private RoadWorldTerrainProbe() {
    }

    /**
     * 道路控制点 / 折线几何指纹（不含地表高度）。移动节点会变；改材质不变。
     */
    public static long geometryFingerprint(RoadNetwork network) {
        if (network == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + network.getEdges().size();
        hash = 31L * hash + network.getNodes().size();
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        edges.sort(Comparator.comparing(e -> e != null && e.getId() != null ? e.getId() : ""));
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            hash = 31L * hash + edge.getId().hashCode();
            hash = mixPoints(hash, RoadPlanGeometry.resolveEdgeCenterline(network, edge));
            hash = mixPoints(hash, edge.getCenterlinePoints());
        }
        List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
        nodes.sort(Comparator.comparing(n -> n != null && n.getId() != null ? n.getId() : ""));
        for (RoadNode node : nodes) {
            if (node == null || node.getPosition() == null) {
                continue;
            }
            hash = 31L * hash + node.getId().hashCode();
            hash = 31L * hash + Double.hashCode(node.getPosition().x);
            hash = 31L * hash + Double.hashCode(node.getPosition().y);
        }
        return hash;
    }

    /**
     * 沿中心线站及其左右侧向点采样地表 Y 指纹。端点不变而中段抬土、或仅改一侧边坡时会变化。
     */
    public static long surfaceFingerprint(
            RoadNetwork network,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        if (network == null || terrain == null) {
            return 0L;
        }
        double step = config != null ? Math.max(0.5, config.getPathSampleDistance()) : 2.0;
        List<ProbeStation> stations = collectProbeStationsWithNormals(
            network, step, MAX_PROBE_STATIONS);
        long hash = 17L;
        hash = 31L * hash + stations.size();
        hash = 31L * hash + Double.hashCode(LATERAL_PROBE_OFFSET);
        for (ProbeStation station : stations) {
            if (station == null || station.point() == null) {
                continue;
            }
            hash = mixSurfaceSample(hash, terrain, station.point());
            if (station.leftNormal() != null) {
                Vec2d left = station.point().add(station.leftNormal().multiply(LATERAL_PROBE_OFFSET));
                Vec2d right = station.point().add(station.leftNormal().multiply(-LATERAL_PROBE_OFFSET));
                hash = mixSurfaceSample(hash, terrain, left);
                hash = mixSurfaceSample(hash, terrain, right);
            }
        }
        return hash;
    }

    /**
     * 中心线密化站；超上限时按步长均匀抽稀。
     */
    public static List<Vec2d> collectProbeStations(
            RoadNetwork network,
            double step,
            int maxStations) {
        List<ProbeStation> framed = collectProbeStationsWithNormals(network, step, maxStations);
        List<Vec2d> points = new ArrayList<>(framed.size());
        for (ProbeStation station : framed) {
            points.add(station.point());
        }
        return points;
    }

    /**
     * 中心线密化站 + 单位左法线（用于侧向地表采样）。
     */
    public static List<ProbeStation> collectProbeStationsWithNormals(
            RoadNetwork network,
            double step,
            int maxStations) {
        if (network == null || maxStations <= 0) {
            return List.of();
        }
        double sampleStep = Math.max(0.5, step);
        List<ProbeStation> dense = new ArrayList<>();
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        edges.sort(Comparator.comparing(e -> e != null && e.getId() != null ? e.getId() : ""));
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> path = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
            if (path.size() < 2) {
                continue;
            }
            appendDensifiedWithNormals(dense, path, sampleStep);
        }
        if (dense.size() <= maxStations) {
            return dense;
        }
        List<ProbeStation> thinned = new ArrayList<>(maxStations);
        double stride = (dense.size() - 1) / (double) (maxStations - 1);
        for (int i = 0; i < maxStations; i++) {
            int index = Math.min(dense.size() - 1, (int) Math.round(i * stride));
            thinned.add(dense.get(index));
        }
        return thinned;
    }

    public record ProbeStation(Vec2d point, Vec2d leftNormal) {
    }

    private static void appendDensifiedWithNormals(List<ProbeStation> out, List<Vec2d> path, double step) {
        for (int i = 1; i < path.size(); i++) {
            Vec2d prev = path.get(i - 1);
            Vec2d curr = path.get(i);
            double len = prev.distance(curr);
            if (len < 1e-9) {
                continue;
            }
            Vec2d tangent = curr.subtract(prev).normalize();
            Vec2d left = new Vec2d(-tangent.y, tangent.x);
            int segments = Math.max(1, (int) Math.ceil(len / step));
            if (i == 1) {
                out.add(new ProbeStation(prev, left));
            }
            for (int s = 1; s <= segments; s++) {
                out.add(new ProbeStation(prev.lerp(curr, (double) s / segments), left));
            }
        }
    }

    private static long mixSurfaceSample(long hash, TerrainSampler terrain, Vec2d station) {
        int surfaceY = terrain.sampleSurfaceY(station);
        hash = 31L * hash + Double.hashCode(station.x);
        hash = 31L * hash + Double.hashCode(station.y);
        return 31L * hash + surfaceY;
    }

    private static long mixPoints(long hash, List<Vec2d> points) {
        if (points == null) {
            return hash;
        }
        hash = 31L * hash + points.size();
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            hash = 31L * hash + Double.hashCode(point.x);
            hash = 31L * hash + Double.hashCode(point.y);
        }
        return hash;
    }
}
