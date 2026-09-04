package com.plot.plugin.building.generation.facade;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.spec.CardinalDirection;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;

import java.util.List;

/**
 * 将 Facade 上的 {@code wallSegmentIndex} 解析到目标 FloorPlate 外轮廓边。
 * <p>
 * {@link FacadeEdgeScope#BASE_FOOTPRINT}：按外法向方向继承（inheritByDirection），
 * 避免矩形→L / 裙房→塔楼时 raw index 语义错乱。
 */
public final class FacadeEdgeResolver {
    private FacadeEdgeResolver() {
    }

    /**
     * @param scope            边索引作用域
     * @param wallSegmentIndex Facade / Opening 上存储的边索引
     * @param baseOuterPoints  建筑基础 footprint（scope=BASE 时必填）
     * @param plateOuterPoints 当前层 FloorPlate 外轮廓
     * @return 落在 {@code plateOuterPoints} 上的边索引
     */
    public static int resolveSegmentIndex(
            FacadeEdgeScope scope,
            int wallSegmentIndex,
            List<Vec2d> baseOuterPoints,
            List<Vec2d> plateOuterPoints) {
        if (plateOuterPoints == null || plateOuterPoints.size() < 3) {
            return 0;
        }
        int plateCount = plateOuterPoints.size();
        if (scope == FacadeEdgeScope.FLOOR_LOCAL || baseOuterPoints == null || baseOuterPoints.size() < 3) {
            return Math.floorMod(wallSegmentIndex, plateCount);
        }
        if (sameTopology(baseOuterPoints, plateOuterPoints)) {
            return Math.floorMod(wallSegmentIndex, plateCount);
        }
        return inheritByDirection(wallSegmentIndex, baseOuterPoints, plateOuterPoints);
    }

    /**
     * 用基础轮廓边的外法向，在目标轮廓上找最接近的边（方向继承）。
     */
    public static int inheritByDirection(
            int baseSegmentIndex,
            List<Vec2d> baseOuterPoints,
            List<Vec2d> plateOuterPoints) {
        int baseCount = baseOuterPoints.size();
        int plateCount = plateOuterPoints.size();
        int baseIndex = Math.floorMod(baseSegmentIndex, baseCount);
        Vec2d baseOutward = BuildingGeometryUtils.outwardNormal(baseOuterPoints, baseIndex);

        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < plateCount; i++) {
            Vec2d plateOutward = BuildingGeometryUtils.outwardNormal(plateOuterPoints, i);
            double score = baseOutward.x * plateOutward.x + baseOutward.y * plateOutward.y;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * 将 Facade 上存储的边索引映射为「查窗型」所用的源索引。
     * <p>
     * 当前层边 → 匹配的基础轮廓边（方向反向继承），使 wallFacades 始终相对 base。
     */
    public static int patternSourceIndex(
            FacadeEdgeScope scope,
            int plateSegmentIndex,
            List<Vec2d> baseOuterPoints,
            List<Vec2d> plateOuterPoints) {
        if (plateOuterPoints == null || plateOuterPoints.size() < 3) {
            return 0;
        }
        int plateCount = plateOuterPoints.size();
        if (scope == FacadeEdgeScope.FLOOR_LOCAL || baseOuterPoints == null || baseOuterPoints.size() < 3) {
            return Math.floorMod(plateSegmentIndex, plateCount);
        }
        if (sameTopology(baseOuterPoints, plateOuterPoints)) {
            return Math.floorMod(plateSegmentIndex, baseOuterPoints.size());
        }
        return inheritByDirection(plateSegmentIndex, plateOuterPoints, baseOuterPoints);
    }

    public static CardinalDirection directionOfSegment(List<Vec2d> outerPoints, int segmentIndex) {
        return CardinalDirection.fromOutwardNormal(
            BuildingGeometryUtils.outwardNormal(outerPoints, segmentIndex));
    }

    private static boolean sameTopology(List<Vec2d> a, List<Vec2d> b) {
        return a.size() == b.size();
    }
}
