package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全网统一标高：沿路网采样途经地形，用众数（无清晰众数时用平均）推荐路面高度。
 */
public final class RoadUniformElevationUtils {
    private static final double DEFAULT_SAMPLE_SPACING = 1.0;

    private RoadUniformElevationUtils() {
    }

    /**
     * 标高推荐结果。
     *
     * @param elevation   推荐统一标高
     * @param mode        众数高度（无众数时等于 elevation）
     * @param average     样本算术平均
     * @param sampleCount 采样点数
     * @param usedMode    true=采用众数，false=回退平均
     */
    public record ElevationRecommendation(
            int elevation,
            int mode,
            double average,
            int sampleCount,
            boolean usedMode) {
    }

    /**
     * 沿路网所有边中心线采样途经地表高度（按横断面宽度取平均地表）。
     * 按整数格 (x,z) 去重，避免路口端点被多条边重复计权。
     */
    public static List<Integer> sampleNetworkGroundHeights(
            RoadNetwork network,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        List<Integer> samples = new ArrayList<>();
        if (network == null || terrain == null) {
            return samples;
        }
        double spacing = config != null
            ? Math.max(0.5, config.getPathSampleDistance())
            : DEFAULT_SAMPLE_SPACING;

        // key = quantized plan cell，value = height
        Map<Long, Integer> uniqueByCell = new HashMap<>();

        for (RoadEdge edge : network.getEdges().values()) {
            List<Vec2d> centerline = edge.getCenterlinePoints();
            if (centerline == null || centerline.size() < 2) {
                continue;
            }
            int width = config != null
                ? RoadModelUtils.getEffectiveWidth(network, edge, config)
                : 5;
            double halfWidth = RoadDimensionUtils.halfExtentFromCenter(width);

            putSample(uniqueByCell, terrain, centerline.getFirst(), centerline.get(1), halfWidth);

            double accumulated = 0.0;
            double nextSampleAt = spacing;
            for (int i = 0; i < centerline.size() - 1; i++) {
                Vec2d a = centerline.get(i);
                Vec2d b = centerline.get(i + 1);
                double segLen = a.distance(b);
                if (segLen < 1e-9) {
                    continue;
                }
                while (nextSampleAt <= accumulated + segLen + 1e-9) {
                    double t = (nextSampleAt - accumulated) / segLen;
                    t = Math.max(0.0, Math.min(1.0, t));
                    Vec2d point = a.lerp(b, t);
                    putSample(uniqueByCell, terrain, point, b.subtract(a), halfWidth);
                    nextSampleAt += spacing;
                }
                accumulated += segLen;
            }

            Vec2d last = centerline.getLast();
            Vec2d prev = centerline.get(centerline.size() - 2);
            putSample(uniqueByCell, terrain, last, last.subtract(prev), halfWidth);
        }
        samples.addAll(uniqueByCell.values());
        return samples;
    }

    private static void putSample(
            Map<Long, Integer> uniqueByCell,
            TerrainSampler terrain,
            Vec2d point,
            Vec2d tangent,
            double halfWidth) {
        if (point == null || uniqueByCell == null) {
            return;
        }
        long key = cellKey(point);
        // 首次采样保留；路口重复位置不再叠加权重
        uniqueByCell.putIfAbsent(key, sampleAt(terrain, point, tangent, halfWidth));
    }

    /** 将平面点量化到整数格，用于跨边去重。 */
    static long cellKey(Vec2d point) {
        int x = (int) Math.round(point.x);
        int z = (int) Math.round(point.y);
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static int sampleAt(TerrainSampler terrain, Vec2d point, Vec2d tangent, double halfWidth) {
        return terrain.sampleCrossSectionGroundY(point, tangent, halfWidth);
    }

    /**
     * 由地面高度样本推荐统一路面标高：优先众数，无唯一众数时用四舍五入平均。
     */
    public static ElevationRecommendation recommendElevation(List<Integer> groundSamples) {
        if (groundSamples == null || groundSamples.isEmpty()) {
            int fallback = TerrainSampler.DEFAULT_SEA_LEVEL;
            return new ElevationRecommendation(fallback, fallback, fallback, 0, false);
        }

        long sum = 0;
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int height : groundSamples) {
            sum += height;
            frequency.merge(height, 1, Integer::sum);
        }
        double average = sum / (double) groundSamples.size();

        int bestCount = 0;
        List<Integer> topModes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : frequency.entrySet()) {
            int count = entry.getValue();
            if (count > bestCount) {
                bestCount = count;
                topModes.clear();
                topModes.add(entry.getKey());
            } else if (count == bestCount) {
                topModes.add(entry.getKey());
            }
        }

        // 唯一众数且出现次数 > 1（避免全互不相同的样本误把任意一点当众数）
        // 若全部高度各出现一次，视为无众数，用平均
        boolean uniqueMode = topModes.size() == 1 && bestCount > 1;
        // 若只有一个样本，众数=该值
        if (groundSamples.size() == 1) {
            int only = groundSamples.getFirst();
            return new ElevationRecommendation(only, only, only, 1, true);
        }
        // 全部相同
        if (topModes.size() == 1 && bestCount == groundSamples.size()) {
            int mode = topModes.getFirst();
            return new ElevationRecommendation(mode, mode, average, groundSamples.size(), true);
        }
        if (uniqueMode) {
            int mode = topModes.getFirst();
            return new ElevationRecommendation(mode, mode, average, groundSamples.size(), true);
        }
        // 多众数并列：取与平均最接近的众数（仍算“众数策略”的 tie-break）
        if (topModes.size() > 1 && bestCount > 1) {
            int mode = topModes.getFirst();
            double bestDist = Double.POSITIVE_INFINITY;
            for (int candidate : topModes) {
                double dist = Math.abs(candidate - average);
                if (dist < bestDist) {
                    bestDist = dist;
                    mode = candidate;
                }
            }
            return new ElevationRecommendation(mode, mode, average, groundSamples.size(), true);
        }

        int rounded = (int) Math.round(average);
        return new ElevationRecommendation(rounded, rounded, average, groundSamples.size(), false);
    }

    /**
     * 采样 + 推荐一步完成。
     */
    public static ElevationRecommendation recommendForNetwork(
            RoadNetwork network,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        List<Integer> samples = sampleNetworkGroundHeights(network, terrain, config);
        return recommendElevation(samples);
    }
}
