package com.plot.plugin.road.station;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;

/**
 * 中心线/道路几何编辑对沿桩号工程数据的显式策略。
 * <p>
 * 所有 VA、可变横断面、设施均绑定 {@link RoadStationing} 链上位置；策略描述编辑后如何更新桩号坐标。
 * <ul>
 *   <li>{@link #PRESERVE_STATION} — 桩号数值不变（物理链位置不变）</li>
 *   <li>{@link #REPARAMETERIZE_STATION} — 编辑段内按<strong>弧长比例</strong>重映射，段后整体平移 ΔL（非绝对 chainage）</li>
 *   <li>{@link #PARTITION_AND_RESET_TAIL} — 在 splitStation 切分；head 保留 [0, split)，tail 取 [split, total) 并重置为 K0+</li>
 *   <li>{@link #OFFSET_BY_HEAD_LENGTH} — tail 全部桩号 += headLength 后拼入 head</li>
 *   <li>{@link #MIRROR_IN_RANGE} — 段内桩号区间镜像（显式工具；单段几何反向不再使用）</li>
 *   <li>{@link #MIRROR_FULL_ROAD} — 整路反向：全链镜像</li>
 * </ul>
 */
public enum CenterlineEditStationPolicy {

    /** 沿桩号数据不做变换。 */
    PRESERVE_STATION,

    /**
     * 分段长度变化：编辑段 [rangeStart, rangeStart+oldLen) 内按弧长比例缩放，
     * 段外下游桩号 + (newLen − oldLen)。不保留段内绝对 chainage。
     */
    REPARAMETERIZE_STATION,

    /**
     * 逻辑拆路：head 裁剪至 splitStation；tail 提取 [splitStation, total) 且桩号归零重编。
     */
    PARTITION_AND_RESET_TAIL,

    /**
     * 逻辑并路：Road B（tail）全部 PVI / 断面 / 设施桩号 += Road A（head）长度。
     */
    OFFSET_BY_HEAD_LENGTH,

    /** 段内桩号区间镜像（显式工具；{@code reverseEdge} 已改为 {@link #PRESERVE_STATION}）。 */
    MIRROR_IN_RANGE,

    /** 整路反向：VA / VCS / 设施全链镜像。 */
    MIRROR_FULL_ROAD;

    private static final double EPSILON = 1e-6;

    /**
     * 段几何编辑后应用策略：Insert PI / Fillet。
     * 段长不变 → {@link #PRESERVE_STATION}；否则 → {@link #REPARAMETERIZE_STATION}。
     */
    public static CenterlineEditStationPolicy forSegmentGeometryEdit(
            double oldSegmentLength,
            double newSegmentLength) {
        if (Math.abs(oldSegmentLength - newSegmentLength) <= EPSILON) {
            return PRESERVE_STATION;
        }
        return REPARAMETERIZE_STATION;
    }

    public void applySegmentGeometryEdit(
            Road road,
            SegmentGeometrySnapshot before,
            double newSegmentLength) {
        if (road == null || before == null) {
            return;
        }
        CenterlineEditStationPolicy policy = forSegmentGeometryEdit(
            before.oldSegmentLength(),
            newSegmentLength);
        if (policy == PRESERVE_STATION) {
            return;
        }
        RoadStationDataTransforms.rescaleAfterGeometryEdit(
            road,
            before.rangeStart(),
            before.oldSegmentLength(),
            newSegmentLength,
            before.totalLengthBefore());
    }

    public StationMigrationResult applyRoadSplit(
            Road head,
            Road tail,
            double splitStation,
            double totalLength) {
        return applyRoadSplit(head, tail, splitStation, totalLength, null);
    }

    public StationMigrationResult applyRoadSplit(
            Road head,
            Road tail,
            double splitStation,
            double totalLength,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        if (this != PARTITION_AND_RESET_TAIL) {
            throw new IllegalStateException("Expected PARTITION_AND_RESET_TAIL, got " + this);
        }
        return RoadStationDataTransforms.applyRoadSplit(
            head, tail, splitStation, totalLength, defaults);
    }

    public StationMigrationResult applyRoadSplit(
            RoadNetwork network, Road head, Road tail, double splitStation) {
        return applyRoadSplit(network, head, tail, splitStation, null);
    }

    public StationMigrationResult applyRoadSplit(
            RoadNetwork network,
            Road head,
            Road tail,
            double splitStation,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        if (this != PARTITION_AND_RESET_TAIL) {
            throw new IllegalStateException("Expected PARTITION_AND_RESET_TAIL, got " + this);
        }
        return RoadStationDataTransforms.applyRoadSplit(
            network, head, tail, splitStation, defaults);
    }

    public StationMigrationResult applyRoadMerge(
            Road target,
            Road head,
            Road tail,
            double headLength,
            double tailLength) {
        return applyRoadMerge(target, head, tail, headLength, tailLength, null);
    }

    public StationMigrationResult applyRoadMerge(
            Road target,
            Road head,
            Road tail,
            double headLength,
            double tailLength,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        if (this != OFFSET_BY_HEAD_LENGTH) {
            throw new IllegalStateException("Expected OFFSET_BY_HEAD_LENGTH, got " + this);
        }
        return RoadStationDataTransforms.applyRoadMerge(
            target, head, tail, headLength, tailLength, defaults);
    }

    public StationMigrationResult applyRoadMerge(RoadNetwork network, Road head, Road tail) {
        return applyRoadMerge(network, head, tail, null);
    }

    public StationMigrationResult applyRoadMerge(
            RoadNetwork network,
            Road head,
            Road tail,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        if (this != OFFSET_BY_HEAD_LENGTH) {
            throw new IllegalStateException("Expected OFFSET_BY_HEAD_LENGTH, got " + this);
        }
        if (network == null || head == null || tail == null) {
            return StationMigrationResult.rejected("invalid merge network args");
        }
        double headLength = RoadStationing.canonicalLength(network, head);
        double tailLength = RoadStationing.canonicalLength(network, tail);
        return RoadStationDataTransforms.applyRoadMerge(
            head, head, tail, headLength, tailLength, defaults);
    }

    public void applyReverseEdge(RoadNetwork network, String edgeId) {
        if (this != PRESERVE_STATION) {
            throw new IllegalStateException("reverseEdge expects PRESERVE_STATION, got " + this);
        }
        // Canonical station data unchanged; OrientedRoadSegment.forward flips for generation.
    }

    public void applyReverseRoad(Road road, double totalLength) {
        if (this != MIRROR_FULL_ROAD) {
            throw new IllegalStateException("Expected MIRROR_FULL_ROAD, got " + this);
        }
        RoadStationMirroring.mirrorRoadStationData(road, totalLength);
    }
}
