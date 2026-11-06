package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 急弯外侧相邻边坡射线之间的连续二维坡面带：高度取自射线剖面，覆盖由等距环带戳印补齐三角空隙。
 */
public final class SlopeBandRasterizer {
    private SlopeBandRasterizer() {
    }

    /** 单条肩部射线的剖面快照，供环带在相邻射线间插值。 */
    public record ShoulderRay(
            SlopeRayId rayId,
            Vec2d outerEdge,
            Vec2d outwardNormal,
            int[] heightByOffset,
            boolean fill,
            String materialId,
            float slopeRatio,
            SlopeConstructionOutcome outcome,
            boolean allowWaterEmbankment,
            double scale) {
        public ShoulderRay {
            heightByOffset = heightByOffset != null ? heightByOffset.clone() : new int[0];
            outcome = outcome != null ? outcome : SlopeConstructionOutcome.COMPLETE;
            scale = scale > 1e-9 ? scale : 1.0;
        }

        public int maxOffset() {
            return Math.max(0, heightByOffset.length - 1);
        }

        public Integer heightAt(int offset) {
            if (offset < 0 || offset >= heightByOffset.length) {
                return null;
            }
            return heightByOffset[offset];
        }
    }

    /**
     * 两条外侧射线是否允许形成环带：挖填类型与堤道授权必须一致。
     * 禁坡/水面打断由调用方在连续分组时处理。
     */
    public static boolean canConnectBand(ShoulderRay a, ShoulderRay b) {
        if (a == null || b == null) {
            return false;
        }
        return a.fill() == b.fill()
            && a.allowWaterEmbankment() == b.allowWaterEmbankment();
    }

    /**
     * 在两条同侧肩部射线之间，按共享水平偏移环带戳印世界柱。
     * 仅用于急弯外侧；调用方负责只传入外侧肩。
     */
    public static List<SlopeProfileSample> fillBetweenRays(
            ShoulderRay a,
            ShoulderRay b,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        if (a == null || b == null || terrain == null || host == null) {
            return samples;
        }
        if (!canConnectBand(a, b)) {
            return samples;
        }
        if (a.outerEdge() == null || b.outerEdge() == null
                || a.outwardNormal() == null || b.outwardNormal() == null) {
            return samples;
        }
        int sharedMax = Math.min(a.maxOffset(), b.maxOffset());
        sharedMax = Math.min(sharedMax, RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS);
        if (sharedMax < 1) {
            return samples;
        }
        Vec2d normalA = normalize(a.outwardNormal());
        Vec2d normalB = normalize(b.outwardNormal());
        double scaleA = a.scale();
        double scaleB = b.scale();

        // 负向合成 id，避免环带柱并入父射线后破坏按偏移的单调校正。
        long bandSeed = mixRayIds(a.rayId(), b.rayId());
        int stampIndex = 0;
        for (int offset = 1; offset <= sharedMax; offset++) {
            Integer heightA = a.heightAt(offset);
            Integer heightB = b.heightAt(offset);
            if (heightA == null || heightB == null) {
                continue;
            }
            Vec2d pointA = a.outerEdge().add(normalA.multiply(offset * scaleA));
            Vec2d pointB = b.outerEdge().add(normalB.multiply(offset * scaleB));
            List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(pointA, pointB);
            int last = span.size() - 1;
            for (int i = 0; i < span.size(); i++) {
                double t = last <= 0 ? 0.0 : (double) i / last;
                int slopeY = (int) Math.round(heightA + (heightB - heightA) * t);
                Vec2d samplePoint = span.get(i);
                if (!terrain.isChunkLoaded(samplePoint)) {
                    continue;
                }
                boolean closerToA = t <= 0.5;
                ShoulderRay parent = closerToA ? a : b;
                // 弦向补格不得进入未授权水面；端点陆地不能把禁填水域连成坡面。
                if (RoadEarthworkPolicy.suppressFillOverWater(
                        parent.fill(),
                        parent.allowWaterEmbankment(),
                        terrain.findExposedWaterSurface(samplePoint))) {
                    continue;
                }
                Vec2d planPoint = host.canonicalColumnPoint(samplePoint);
                ColumnKey key = new ColumnKey(host.worldX(planPoint), host.worldZ(planPoint));
                SlopeRayId bandRayId = new SlopeRayId(
                    bandSeed - stampIndex++,
                    parent.rayId().side());
                // 环带只补覆盖：固定 COMPLETE，不继承父射线挡墙末端判定。
                samples.add(new SlopeProfileSample(
                    key,
                    planPoint,
                    slopeY,
                    parent.fill(),
                    parent.materialId(),
                    bandRayId,
                    offset,
                    parent.slopeRatio(),
                    SlopeConstructionOutcome.COMPLETE,
                    parent.allowWaterEmbankment(),
                    true));
            }
        }
        return samples;
    }

    private static long mixRayIds(SlopeRayId a, SlopeRayId b) {
        long left = a != null ? a.stationSampleId() : 0L;
        long right = b != null ? b.stationSampleId() : 0L;
        // 保持为负，与正常 stationSampleId（递增非负）区分。
        return -1L - Math.abs((left * 1315423911L) ^ (right * 2654435761L));
    }

    /** 从 {@code RoadSlopeUtils} 剖面点列构建按偏移索引的高度表（缺档线性插值）。 */
    public static int[] heightTableFromProfile(List<int[]> profile, int maxOffsetInclusive) {
        int capped = Math.clamp(maxOffsetInclusive, 0, RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS);
        int[] table = new int[capped + 1];
        if (profile == null || profile.isEmpty()) {
            return table;
        }
        boolean[] known = new boolean[capped + 1];
        for (int[] point : profile) {
            if (point == null || point.length < 2) {
                continue;
            }
            int offset = point[0];
            if (offset < 0 || offset > capped) {
                continue;
            }
            table[offset] = point[1];
            known[offset] = true;
        }
        int lastKnown = -1;
        for (int o = 0; o <= capped; o++) {
            if (known[o]) {
                if (lastKnown >= 0 && o - lastKnown > 1) {
                    int y0 = table[lastKnown];
                    int y1 = table[o];
                    for (int mid = lastKnown + 1; mid < o; mid++) {
                        double t = (double) (mid - lastKnown) / (o - lastKnown);
                        table[mid] = (int) Math.round(y0 + (y1 - y0) * t);
                        known[mid] = true;
                    }
                }
                lastKnown = o;
            }
        }
        if (lastKnown >= 0) {
            for (int o = lastKnown + 1; o <= capped; o++) {
                table[o] = table[lastKnown];
                known[o] = true;
            }
        }
        // 若起点缺失，用第一个已知值回填
        int firstKnown = -1;
        for (int o = 0; o <= capped; o++) {
            if (known[o]) {
                firstKnown = o;
                break;
            }
        }
        if (firstKnown > 0) {
            for (int o = 0; o < firstKnown; o++) {
                table[o] = table[firstKnown];
            }
        }
        return table;
    }

    private static Vec2d normalize(Vec2d value) {
        if (value == null) {
            return new Vec2d(0, 1);
        }
        double len = value.length();
        if (len < 1e-9) {
            return new Vec2d(0, 1);
        }
        return value.multiply(1.0 / len);
    }
}
