package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 直线道路平交路口区域几何：重叠核区 + 铺装并集 + 凹角路缘圆角。
 * <p>
 * 支持两条贯通道路十字，以及「一对贯通 + 一条支路」的 T 字。
 * 不从交叉点外推 plaza，而是用局部铺装条带求交/并，再在并集外环凹角补弧。
 */
public final class JunctionRegionBuilder {
    private static final double MIN_RADIUS = 0.15;
    private static final double MIN_ABS_SIN = 0.08;
    private static final double MIN_CORNER_ANGLE = 0.15;
    private static final double MAX_CORNER_ANGLE = Math.PI - 0.15;
    private static final double DEDUPE_EPS = 1e-4;

    private JunctionRegionBuilder() {
    }

    /** 一条恒定宽度直线道路在交叉处的局部描述。 */
    public record StraightRoad(Vec2d direction, double halfWidth) {
        public StraightRoad {
            if (direction == null || direction.lengthSquared() < 1e-12) {
                direction = new Vec2d(1, 0);
            } else {
                direction = direction.normalize();
            }
            halfWidth = Math.max(0.0, halfWidth);
        }

        public Vec2d normal() {
            return direction.perpendicular();
        }
    }

    public static JunctionRegionGeometry build(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            double cornerRadius) {
        return build(center, roadA, roadB, cornerRadius, Double.NaN, 10);
    }

    public static JunctionRegionGeometry build(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            double cornerRadius,
            double extent,
            int arcSegments) {
        if (center == null || roadA == null || roadB == null) {
            return JunctionRegionGeometry.empty();
        }
        if (roadA.halfWidth() <= 1e-9 || roadB.halfWidth() <= 1e-9) {
            return JunctionRegionGeometry.empty();
        }
        double absSin = Math.abs(roadA.direction().cross(roadB.direction()));
        if (absSin < MIN_ABS_SIN) {
            return JunctionRegionGeometry.empty();
        }

        double requested = Math.max(0.0, cornerRadius);

        List<Vec2d> overlap = buildOverlapParallelogram(center, roadA, roadB);
        if (overlap.size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        // 铺装臂长至少覆盖重叠核；显式 extent（广场半径）只可加长、不随圆角 R 变化
        double fromOverlap = pavementExtentFromOverlap(center, roadA, roadB, overlap);
        double pavementExtent = Double.isFinite(extent) && extent > 0
            ? Math.max(fromOverlap, extent)
            : fromOverlap;
        double computeExtent = Math.max(
            pavementExtent,
            pavementExtent + Math.max(0.0, requested) + 1.0);

        List<Vec2d> stripA = buildStripRectangle(center, roadA, pavementExtent);
        List<Vec2d> stripB = buildStripRectangle(center, roadB, pavementExtent);
        // 仅保留仍落在有限条带内（或边界上）的重叠角点，防止锐角虚假外扩
        List<Vec2d> clippedOverlap = clipOverlapToStrips(overlap, stripA, stripB);
        if (clippedOverlap.size() < 3) {
            clippedOverlap = overlap;
        }

        List<Vec2d> pavementUnion = buildUnionCross(center, stripA, stripB, clippedOverlap);
        if (pavementUnion.size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        List<JunctionRegionGeometry.ApproachBoundary> approaches = List.of(
            approachBoundary(center, roadA, pavementExtent),
            approachBoundary(center, roadB, pavementExtent));

        if (requested < MIN_RADIUS) {
            return new JunctionRegionGeometry(
                overlap,
                pavementUnion,
                pavementUnion,
                List.of(),
                List.of(),
                approaches,
                requested,
                List.of());
        }

        // 只在重叠核四角做街角圆角；勿对极角排序并集的远端假凹角倒圆（斜交会拉出路臂弧）
        FilletPass fillets = applyStreetCornerFillets(
            center, roadA, roadB, clippedOverlap, pavementUnion, requested, Math.max(3, arcSegments));
        return new JunctionRegionGeometry(
            overlap,
            pavementUnion,
            fillets.finalRing(),
            fillets.additions(),
            fillets.arcs(),
            approaches,
            requested,
            fillets.effectiveRadii());
    }

    /**
     * 进口局部真实条带（可弯曲）并集 + 切线重叠核。
     * {@code tangents} 用于重叠平行四边形/T 识别；{@code bandPolygons} 为各进口 stub 多边形。
     */
    public static JunctionRegionGeometry buildFromApproachBands(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons,
            double cornerRadius) {
        return buildFromApproachBands(center, tangents, bandPolygons, cornerRadius, Double.NaN);
    }

    /**
     * @param minExtent 广场最小臂长（通常为有效 plazaRadius）；与重叠核取较大值
     */
    public static JunctionRegionGeometry buildFromApproachBands(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons,
            double cornerRadius,
            double minExtent) {
        if (center == null || tangents == null || bandPolygons == null) {
            return JunctionRegionGeometry.empty();
        }
        if (bandPolygons.size() < 3) {
            return JunctionRegionGeometry.empty();
        }
        // 先用切线拓扑求重叠核（十字/T）；失败则无法区域式
        JunctionRegionGeometry tangentCore = buildFromApproaches(center, tangents, 0.0, minExtent);
        if (!tangentCore.hasPavement() || tangentCore.overlapRegion().size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        List<Vec2d> candidates = new ArrayList<>();
        for (int i = 0; i < bandPolygons.size(); i++) {
            List<Vec2d> band = bandPolygons.get(i);
            if (band == null || band.size() < 3) {
                continue;
            }
            for (Vec2d vertex : band) {
                boolean insideOther = false;
                for (int j = 0; j < bandPolygons.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    List<Vec2d> other = bandPolygons.get(j);
                    if (other != null && other.size() >= 3 && strictlyInside(vertex, other)) {
                        insideOther = true;
                        break;
                    }
                }
                if (!insideOther) {
                    candidates.add(vertex);
                }
            }
        }
        // 条带边交点（弯曲时凹凸衔接）
        for (int i = 0; i < bandPolygons.size(); i++) {
            for (int j = i + 1; j < bandPolygons.size(); j++) {
                candidates.addAll(segmentIntersections(bandPolygons.get(i), bandPolygons.get(j)));
            }
        }
        candidates.addAll(tangentCore.overlapRegion());
        // 广场半径加长的切线条带并集，避免仅靠远端条带顶点无法扩大核区
        candidates.addAll(tangentCore.originalRoadUnion());
        List<Vec2d> pavementUnion = sortCcw(center, dedupe(candidates));
        if (pavementUnion.size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        double requested = Math.max(0.0, cornerRadius);
        List<JunctionRegionGeometry.ApproachBoundary> bounds = tangentCore.approaches();
        if (requested < MIN_RADIUS) {
            return new JunctionRegionGeometry(
                tangentCore.overlapRegion(),
                pavementUnion,
                pavementUnion,
                List.of(),
                List.of(),
                bounds,
                requested,
                List.of());
        }
        // 圆角仍用切线条带求解（边长足够，半径不被折线锯齿卡住）；
        // 原始并集轮廓保留真实弯曲条带。
        JunctionRegionGeometry filletedTangents = buildFromApproaches(
            center, tangents, requested, minExtent);
        if (!filletedTangents.hasPavement() || filletedTangents.curbArcs().isEmpty()) {
            return new JunctionRegionGeometry(
                tangentCore.overlapRegion(),
                pavementUnion,
                pavementUnion,
                List.of(),
                List.of(),
                bounds,
                requested,
                List.of());
        }
        return new JunctionRegionGeometry(
            tangentCore.overlapRegion(),
            pavementUnion,
            pavementUnion,
            filletedTangents.filletAdditions(),
            filletedTangents.curbArcs(),
            bounds,
            requested,
            filletedTangents.effectiveCornerRadii());
    }


    /**
     * 从进口列表构建区域几何。
     * <ul>
     *   <li>4 进口：两组对向贯通 → 十字</li>
     *   <li>3 进口：一对对向贯通 + 一条支路 → T 字</li>
     *   <li>2 进口：拒绝（不得扩张成四臂）</li>
     * </ul>
     */
    public static JunctionRegionGeometry buildFromApproaches(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            double cornerRadius) {
        return buildFromApproaches(center, approaches, cornerRadius, Double.NaN);
    }

    /**
     * @param minExtent 广场最小臂长；与重叠核投影取较大值。NaN 表示仅用重叠核。
     */
    public static JunctionRegionGeometry buildFromApproaches(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            double cornerRadius,
            double minExtent) {
        if (center == null || approaches == null) {
            return JunctionRegionGeometry.empty();
        }
        List<JunctionCurbFilletBuilder.Approach> valid = new ArrayList<>();
        for (JunctionCurbFilletBuilder.Approach approach : approaches) {
            if (approach != null
                    && approach.outward().lengthSquared() > 1e-12
                    && approach.halfWidth() > 1e-9) {
                valid.add(approach);
            }
        }
        if (valid.size() == 3) {
            return buildTFromApproaches(center, valid, cornerRadius, minExtent);
        }
        if (valid.size() != 4) {
            return JunctionRegionGeometry.empty();
        }
        valid.sort(Comparator.comparingDouble(a -> Math.atan2(a.outward().y, a.outward().x)));
        StraightRoad roadA = pairOpposite(valid.get(0), valid.get(2));
        StraightRoad roadB = pairOpposite(valid.get(1), valid.get(3));
        if (roadA == null || roadB == null) {
            return JunctionRegionGeometry.empty();
        }
        return build(center, roadA, roadB, cornerRadius, minExtent, 10);
    }

    /**
     * T 字：贯通道路双向条带 ∪ 支路单向 stub，重叠核取贯通路与支路无限条带交，
     * 并集仅保留支路半平面侧的核区角点作为凹角。
     */
    public static JunctionRegionGeometry buildT(
            Vec2d center,
            StraightRoad through,
            JunctionCurbFilletBuilder.Approach stem,
            double cornerRadius) {
        return buildT(center, through, stem, cornerRadius, Double.NaN, 10);
    }

    public static JunctionRegionGeometry buildT(
            Vec2d center,
            StraightRoad through,
            JunctionCurbFilletBuilder.Approach stem,
            double cornerRadius,
            double extent,
            int arcSegments) {
        if (center == null || through == null || stem == null) {
            return JunctionRegionGeometry.empty();
        }
        if (through.halfWidth() <= 1e-9 || stem.halfWidth() <= 1e-9) {
            return JunctionRegionGeometry.empty();
        }
        if (stem.outward().lengthSquared() < 1e-12) {
            return JunctionRegionGeometry.empty();
        }
        StraightRoad stemRoad = new StraightRoad(stem.outward(), stem.halfWidth());
        double absSin = Math.abs(through.direction().cross(stemRoad.direction()));
        if (absSin < MIN_ABS_SIN) {
            return JunctionRegionGeometry.empty();
        }

        double requested = Math.max(0.0, cornerRadius);
        List<Vec2d> overlap = buildOverlapParallelogram(center, through, stemRoad);
        if (overlap.size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        double fromOverlap = pavementExtentFromOverlap(center, through, stemRoad, overlap);
        double pavementExtent = Double.isFinite(extent) && extent > 0
            ? Math.max(fromOverlap, extent)
            : fromOverlap;
        double computeExtent = Math.max(
            pavementExtent,
            pavementExtent + Math.max(0.0, requested) + 1.0);
        double backIntoThrough = through.halfWidth() / absSin;

        List<Vec2d> throughStrip = buildStripRectangle(center, through, pavementExtent);
        List<Vec2d> stemStub = buildStemStub(
            center, stemRoad.direction(), stemRoad.halfWidth(), backIntoThrough, pavementExtent);
        List<Vec2d> stemSideOverlap = filterOverlapOnStemSide(center, stemRoad.direction(), overlap);
        if (stemSideOverlap.size() < 2) {
            stemSideOverlap = overlap;
        }

        List<Vec2d> pavementUnion = buildUnionCross(center, throughStrip, stemStub, stemSideOverlap);
        if (pavementUnion.size() < 3) {
            return JunctionRegionGeometry.empty();
        }

        List<JunctionRegionGeometry.ApproachBoundary> approachBounds = List.of(
            approachBoundary(center, through, pavementExtent),
            new JunctionRegionGeometry.ApproachBoundary(
                stemRoad.direction(),
                stemRoad.halfWidth(),
                stemMouthCorners(center, stemRoad, pavementExtent)));

        if (requested < MIN_RADIUS) {
            return new JunctionRegionGeometry(
                overlap,
                pavementUnion,
                pavementUnion,
                List.of(),
                List.of(),
                approachBounds,
                requested,
                List.of());
        }

        FilletPass fillets = applyStreetCornerFillets(
            center, through, stemRoad, stemSideOverlap, pavementUnion, requested,
            Math.max(3, arcSegments));
        return new JunctionRegionGeometry(
            overlap,
            pavementUnion,
            fillets.finalRing(),
            fillets.additions(),
            fillets.arcs(),
            approachBounds,
            requested,
            fillets.effectiveRadii());
    }

    private static JunctionRegionGeometry buildTFromApproaches(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> valid,
            double cornerRadius,
            double minExtent) {
        // 找对向贯通对；其余为支路
        for (int i = 0; i < valid.size(); i++) {
            for (int j = i + 1; j < valid.size(); j++) {
                StraightRoad through = pairOpposite(valid.get(i), valid.get(j));
                if (through == null) {
                    continue;
                }
                for (int k = 0; k < valid.size(); k++) {
                    if (k == i || k == j) {
                        continue;
                    }
                    return buildT(center, through, valid.get(k), cornerRadius, minExtent, 10);
                }
            }
        }
        return JunctionRegionGeometry.empty();
    }

    /**
     * 由重叠平行四边形顶点投影到各道路中心线，取覆盖全部核区角点所需的最小单侧长度，
     * 再加一截进口裕量。比统一的 maxHalf/sinθ 更能覆盖锐角斜交。
     */
    static double pavementExtentFromOverlap(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            List<Vec2d> overlap) {
        double alongA = 0.0;
        double alongB = 0.0;
        for (Vec2d corner : overlap) {
            if (corner == null) {
                continue;
            }
            Vec2d delta = corner.subtract(center);
            alongA = Math.max(alongA, Math.abs(delta.dot(roadA.direction())));
            alongB = Math.max(alongB, Math.abs(delta.dot(roadB.direction())));
        }
        double cover = Math.max(alongA, alongB);
        double mouth = Math.max(roadA.halfWidth(), roadB.halfWidth()) + 1.0;
        return cover + mouth;
    }

    /** 近似估算（测试/调试）；正式构建请用 {@link #pavementExtentFromOverlap}。 */
    static double pavementExtent(double halfA, double halfB, double absSin) {
        double overlapHalf = Math.max(halfA, halfB) / Math.max(absSin, MIN_ABS_SIN);
        return overlapHalf + Math.max(halfA, halfB) + 1.0;
    }

    static List<Vec2d> clipOverlapToStrips(List<Vec2d> overlap, List<Vec2d> stripA, List<Vec2d> stripB) {
        List<Vec2d> kept = new ArrayList<>();
        for (Vec2d corner : overlap) {
            if (corner == null) {
                continue;
            }
            boolean inA = RoadGeometryUtils.pointInPolygon(corner, stripA)
                || distanceToPolygonBoundary(corner, stripA) < 1e-4;
            boolean inB = RoadGeometryUtils.pointInPolygon(corner, stripB)
                || distanceToPolygonBoundary(corner, stripB) < 1e-4;
            if (inA && inB) {
                kept.add(corner);
            }
        }
        return kept;
    }

    private static double distanceToPolygonBoundary(Vec2d point, List<Vec2d> polygon) {
        double min = Double.POSITIVE_INFINITY;
        if (polygon == null || polygon.size() < 2) {
            return min;
        }
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            min = Math.min(min, distanceToSegment(point, polygon.get(i), polygon.get((i + 1) % n)));
        }
        return min;
    }

    /** 无限条带交：四条路缘线交成平行四边形（正交时为矩形）。 */
    static List<Vec2d> buildOverlapParallelogram(Vec2d center, StraightRoad a, StraightRoad b) {
        Vec2d nA = a.normal();
        Vec2d nB = b.normal();
        Vec2d aPos = center.add(nA.multiply(a.halfWidth()));
        Vec2d aNeg = center.subtract(nA.multiply(a.halfWidth()));
        Vec2d bPos = center.add(nB.multiply(b.halfWidth()));
        Vec2d bNeg = center.subtract(nB.multiply(b.halfWidth()));

        List<Vec2d> corners = new ArrayList<>(4);
        addIfPresent(corners, lineIntersection(aPos, a.direction(), bPos, b.direction()));
        addIfPresent(corners, lineIntersection(aPos, a.direction(), bNeg, b.direction()));
        addIfPresent(corners, lineIntersection(aNeg, a.direction(), bPos, b.direction()));
        addIfPresent(corners, lineIntersection(aNeg, a.direction(), bNeg, b.direction()));
        if (corners.size() < 3) {
            return List.of();
        }
        return sortCcw(center, corners);
    }

    static List<Vec2d> buildStripRectangle(Vec2d center, StraightRoad road, double extent) {
        Vec2d u = road.direction();
        Vec2d n = road.normal();
        Vec2d along = u.multiply(extent);
        Vec2d across = n.multiply(road.halfWidth());
        List<Vec2d> rect = List.of(
            center.add(along).add(across),
            center.add(along).subtract(across),
            center.subtract(along).subtract(across),
            center.subtract(along).add(across));
        return sortCcw(center, rect);
    }

    /** 支路单向 stub：向贯通道路内侧回伸 back，向外延伸 forward。 */
    static List<Vec2d> buildStemStub(
            Vec2d center,
            Vec2d stemOut,
            double stemHalfWidth,
            double back,
            double forward) {
        Vec2d u = stemOut.normalize();
        Vec2d n = u.perpendicular();
        Vec2d near = center.subtract(u.multiply(Math.max(0.0, back)));
        Vec2d far = center.add(u.multiply(Math.max(forward, back + 1.0)));
        Vec2d across = n.multiply(stemHalfWidth);
        List<Vec2d> rect = List.of(
            far.add(across),
            far.subtract(across),
            near.subtract(across),
            near.add(across));
        return sortCcw(center, rect);
    }

    static List<Vec2d> filterOverlapOnStemSide(Vec2d center, Vec2d stemOut, List<Vec2d> overlap) {
        Vec2d unit = stemOut.normalize();
        List<Vec2d> kept = new ArrayList<>();
        for (Vec2d corner : overlap) {
            if (corner == null) {
                continue;
            }
            // 保留支路半平面（含过中心的分界线），去掉贯通道路背侧虚假凹角
            if (corner.subtract(center).dot(unit) >= -1e-4) {
                kept.add(corner);
            }
        }
        return kept;
    }

    private static List<Vec2d> stemMouthCorners(Vec2d center, StraightRoad stem, double extent) {
        Vec2d mouth = center.add(stem.direction().multiply(extent));
        Vec2d n = stem.normal();
        return List.of(
            mouth.add(n.multiply(stem.halfWidth())),
            mouth.subtract(n.multiply(stem.halfWidth())));
    }

    /**
     * 两交叉条带的并集外环：各条带不在对方内部的顶点 + 重叠平行四边形顶点（凹角），
     * 对过中心的直线条带并集可用极角排序。
     */
    static List<Vec2d> buildUnionCross(
            Vec2d center,
            List<Vec2d> stripA,
            List<Vec2d> stripB,
            List<Vec2d> overlap) {
        List<Vec2d> candidates = new ArrayList<>();
        for (Vec2d vertex : stripA) {
            if (!strictlyInside(vertex, stripB)) {
                candidates.add(vertex);
            }
        }
        for (Vec2d vertex : stripB) {
            if (!strictlyInside(vertex, stripA)) {
                candidates.add(vertex);
            }
        }
        candidates.addAll(overlap);
        return sortCcw(center, dedupe(candidates));
    }

    private static JunctionRegionGeometry.ApproachBoundary approachBoundary(
            Vec2d center,
            StraightRoad road,
            double extent) {
        Vec2d mouth = center.add(road.direction().multiply(extent));
        Vec2d n = road.normal();
        List<Vec2d> corners = List.of(
            mouth.add(n.multiply(road.halfWidth())),
            mouth.subtract(n.multiply(road.halfWidth())),
            center.subtract(road.direction().multiply(extent)).add(n.multiply(road.halfWidth())),
            center.subtract(road.direction().multiply(extent)).subtract(n.multiply(road.halfWidth())));
        return new JunctionRegionGeometry.ApproachBoundary(road.direction(), road.halfWidth(), corners);
    }

    private static StraightRoad pairOpposite(
            JunctionCurbFilletBuilder.Approach a,
            JunctionCurbFilletBuilder.Approach b) {
        if (a == null || b == null) {
            return null;
        }
        if (a.outward().dot(b.outward()) > -0.85) {
            return null;
        }
        if (Math.abs(a.halfWidth() - b.halfWidth()) > 1e-3) {
            return null;
        }
        return new StraightRoad(a.outward(), 0.5 * (a.halfWidth() + b.halfWidth()));
    }

    private record FilletPass(
            List<Vec2d> finalRing,
            List<List<Vec2d>> additions,
            List<JunctionRegionGeometry.CurbArc> arcs,
            List<Double> effectiveRadii) {
    }

    /**
     * 仅在重叠核四角做街角圆角，避免极角排序并集的远端假凹角。
     * 圆弧由两条路缘带在该角的外法向偏置求交得到，不依赖并集环邻接（极角排序会接错边）。
     */
    private static FilletPass applyStreetCornerFillets(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            List<Vec2d> overlapCorners,
            List<Vec2d> pavementUnion,
            double requested,
            int arcSegments) {
        List<List<Vec2d>> additions = new ArrayList<>();
        List<JunctionRegionGeometry.CurbArc> arcs = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        if (center == null || roadA == null || roadB == null
                || overlapCorners == null || pavementUnion == null || requested < MIN_RADIUS) {
            return new FilletPass(
                pavementUnion != null ? pavementUnion : List.of(),
                List.of(), List.of(), List.of());
        }
        double maxSetback = streetCornerMaxSetback(center, roadA, roadB, overlapCorners, requested);
        for (Vec2d corner : overlapCorners) {
            if (corner == null) {
                continue;
            }
            CornerFillet fillet = streetCornerFillet(center, corner, roadA, roadB, requested, maxSetback);
            if (fillet == null) {
                radii.add(0.0);
                continue;
            }
            radii.add(fillet.radius());
            arcs.add(new JunctionRegionGeometry.CurbArc(
                fillet.center(),
                fillet.tangentStart(),
                fillet.tangentEnd(),
                fillet.radius(),
                fillet.cornerAngle(),
                fillet.shrinkReason()));
            additions.add(filletAddition(corner, fillet, arcSegments));
        }
        // 最终轮廓环保持锐角并集：圆角只体现在 curbArcs / filletAdditions。
        // 生成/预览的 fillPolygon 经 {@link #expandUnionWithStreetArcs} 在重叠核角局部织入弧。
        return new FilletPass(pavementUnion, List.copyOf(additions), List.copyOf(arcs), List.copyOf(radii));
    }

    /**
     * 仅在重叠核角顶点处把锐角并集换成路缘弧，供 fillPolygon / 清障外形使用。
     * 不碰极角排序产生的远端假凹角。
     */
    public static List<Vec2d> expandUnionWithStreetArcs(
            List<Vec2d> union,
            List<Vec2d> overlapCorners,
            List<JunctionRegionGeometry.CurbArc> arcs,
            int arcSegments) {
        if (union == null || union.size() < 3 || arcs == null || arcs.isEmpty()) {
            return union != null ? union : List.of();
        }
        int n = union.size();
        List<Vec2d> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Vec2d curr = union.get(i);
            if (!isNearAny(curr, overlapCorners, 0.75)) {
                out.add(curr);
                continue;
            }
            JunctionRegionGeometry.CurbArc best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (JunctionRegionGeometry.CurbArc arc : arcs) {
                if (arc == null || arc.radius() < MIN_RADIUS || arc.center() == null) {
                    continue;
                }
                double d = curr.distance(arc.center());
                if (d < bestDist) {
                    bestDist = d;
                    best = arc;
                }
            }
            if (best == null || bestDist > best.radius() * 3.0 + 2.0) {
                out.add(curr);
                continue;
            }
            CornerFillet fillet = new CornerFillet(
                best.center(), best.tangentStart(), best.tangentEnd(),
                best.radius(), best.cornerAngle(),
                best.shrinkReason() != null ? best.shrinkReason() : "");
            appendArc(out, fillet, Math.max(3, arcSegments));
        }
        List<Vec2d> ring = dedupe(out);
        return ring.size() >= 3 ? ring : union;
    }

    private static boolean isNearAny(Vec2d point, List<Vec2d> candidates, double tol) {
        if (point == null || candidates == null) {
            return false;
        }
        for (Vec2d candidate : candidates) {
            if (candidate != null && point.distance(candidate) <= tol) {
                return true;
            }
        }
        return false;
    }

    private static double streetCornerMaxSetback(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            List<Vec2d> overlapCorners,
            double requested) {
        // 正交街角 setback≈R，故上限至少为请求 R；再参考路宽，避免钝角楔形沿路臂拉出长假弧
        double widthSpan = 2.0 * Math.max(roadA.halfWidth(), roadB.halfWidth());
        return Math.max(Math.max(3.0, requested), widthSpan);
    }

    /**
     * 重叠核角点处：对 A/B 路缘外法向各偏置 R，交点为圆心；切点落在两条路缘上。
     */
    static CornerFillet streetCornerFillet(
            Vec2d center,
            Vec2d corner,
            StraightRoad roadA,
            StraightRoad roadB,
            double requested,
            double maxSetback) {
        if (center == null || corner == null || roadA == null || roadB == null) {
            return null;
        }
        if (requested < MIN_RADIUS) {
            return null;
        }
        Vec2d radial = corner.subtract(center);
        if (radial.lengthSquared() < 1e-12) {
            return null;
        }
        double sideA = Math.signum(radial.dot(roadA.normal()));
        double sideB = Math.signum(radial.dot(roadB.normal()));
        if (Math.abs(sideA) < 1e-9 || Math.abs(sideB) < 1e-9) {
            return null;
        }
        Vec2d outA = roadA.normal().multiply(sideA);
        Vec2d outB = roadB.normal().multiply(sideB);
        // 两外法向夹角 = 该象限街角（草地楔形）
        double cos = Math.max(-1.0, Math.min(1.0, outA.dot(outB)));
        double streetAngle = Math.acos(cos);
        if (streetAngle < MIN_CORNER_ANGLE || streetAngle > MAX_CORNER_ANGLE) {
            return null;
        }
        double halfPhi = streetAngle * 0.5;
        double tanHalf = Math.tan(halfPhi);
        if (tanHalf < 1e-6) {
            return null;
        }
        // 偏置圆角：切点后退 ≈ R·tan(φ/2)。钝角楔形 tan 大，必须用 maxSetback/tan 限 R，
        // 否则 R=12 会在钝角落出数十米路臂假弧。
        double maxBySetback = Math.max(0.0, maxSetback) / tanHalf * 0.95;
        double limited = Math.min(requested, maxBySetback);
        String reason = "";
        if (limited + 1e-6 < requested) {
            reason = "adjacent_edge_too_short";
        }
        if (limited < MIN_RADIUS) {
            return null;
        }

        double normalDot = outA.dot(outB);
        if (normalDot <= -0.999) {
            return null;
        }
        // (C-corner)·outA = (C-corner)·outB = R  →  C = corner + R/(1+outA·outB)·(outA+outB)
        Vec2d filletCenter = corner.add(outA.add(outB).multiply(limited / (1.0 + normalDot)));
        if (filletCenter.subtract(corner).dot(radial.normalize()) <= 0.0) {
            return null;
        }
        Vec2d tangentA = filletCenter.subtract(outA.multiply(limited));
        Vec2d tangentB = filletCenter.subtract(outB.multiply(limited));
        if (Math.abs(filletCenter.distance(tangentA) - limited) > 0.35
                || Math.abs(filletCenter.distance(tangentB) - limited) > 0.35) {
            return null;
        }
        // 切点须落在角点附近（后退 ≤ maxSetback）
        if (corner.distance(tangentA) > maxSetback + 1e-3
                || corner.distance(tangentB) > maxSetback + 1e-3) {
            return null;
        }
        Vec2d tangentStart = tangentA;
        Vec2d tangentEnd = tangentB;
        double startAngle = Math.atan2(
            tangentStart.y - filletCenter.y, tangentStart.x - filletCenter.x);
        double endAngle = Math.atan2(
            tangentEnd.y - filletCenter.y, tangentEnd.x - filletCenter.x);
        double sweep = endAngle - startAngle;
        while (sweep <= -Math.PI) {
            sweep += Math.PI * 2.0;
        }
        while (sweep > Math.PI) {
            sweep -= Math.PI * 2.0;
        }
        if (Math.abs(Math.abs(sweep) - streetAngle) > 0.75) {
            tangentStart = tangentB;
            tangentEnd = tangentA;
        }
        return new CornerFillet(
            filletCenter, tangentStart, tangentEnd, limited, streetAngle, reason);
    }

    /**
     * 在固定铺装并集上替换凹角；圆弧半径上限可参考更长计算环上的同角边长。
     * 保留给测试/调试；正式十字/T 请用 {@link #applyStreetCornerFillets}。
     */
    static FilletPass applyConcaveFillets(
            List<Vec2d> pavementUnion,
            List<Vec2d> computeUnion,
            double requested,
            int arcSegments) {
        int n = pavementUnion.size();
        List<Vec2d> finalRing = new ArrayList<>();
        List<List<Vec2d>> additions = new ArrayList<>();
        List<JunctionRegionGeometry.CurbArc> arcs = new ArrayList<>();
        List<Double> radii = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Vec2d prev = pavementUnion.get((i - 1 + n) % n);
            Vec2d curr = pavementUnion.get(i);
            Vec2d next = pavementUnion.get((i + 1) % n);
            if (!isConcaveVertex(prev, curr, next)) {
                finalRing.add(curr);
                continue;
            }
            CornerTriple longCorner = matchConcaveCorner(computeUnion, curr);
            Vec2d filletPrev = longCorner != null ? longCorner.prev() : prev;
            Vec2d filletNext = longCorner != null ? longCorner.next() : next;
            CornerFillet fillet = computeConcaveFillet(filletPrev, curr, filletNext, requested);
            if (fillet == null) {
                finalRing.add(curr);
                radii.add(0.0);
                continue;
            }
            radii.add(fillet.radius());
            arcs.add(new JunctionRegionGeometry.CurbArc(
                fillet.center(),
                fillet.tangentStart(),
                fillet.tangentEnd(),
                fillet.radius(),
                fillet.cornerAngle(),
                fillet.shrinkReason()));
            additions.add(filletAddition(curr, fillet, arcSegments));
            appendArc(finalRing, fillet, arcSegments);
        }
        return new FilletPass(dedupe(finalRing), List.copyOf(additions), List.copyOf(arcs), List.copyOf(radii));
    }

    private record CornerTriple(Vec2d prev, Vec2d curr, Vec2d next) {
    }

    private static CornerTriple matchConcaveCorner(List<Vec2d> ring, Vec2d corner) {
        if (ring == null || corner == null || ring.size() < 3) {
            return null;
        }
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Vec2d curr = ring.get(i);
            if (curr.distance(corner) > 1e-3) {
                continue;
            }
            Vec2d prev = ring.get((i - 1 + n) % n);
            Vec2d next = ring.get((i + 1) % n);
            if (isConcaveVertex(prev, curr, next)) {
                return new CornerTriple(prev, curr, next);
            }
        }
        return null;
    }

    /** CCW 环：右拐（cross &lt; 0）为凹角。 */
    static boolean isConcaveVertex(Vec2d prev, Vec2d curr, Vec2d next) {
        Vec2d in = curr.subtract(prev);
        Vec2d out = next.subtract(curr);
        if (in.lengthSquared() < 1e-12 || out.lengthSquared() < 1e-12) {
            return false;
        }
        return in.cross(out) < -1e-9;
    }

    static CornerFillet computeConcaveFillet(Vec2d prev, Vec2d curr, Vec2d next, double requestedRadius) {
        Vec2d edgeIn = curr.subtract(prev);
        Vec2d edgeOut = next.subtract(curr);
        if (edgeIn.lengthSquared() < 1e-12 || edgeOut.lengthSquared() < 1e-12) {
            return null;
        }
        Vec2d dirIn = edgeIn.normalize();
        Vec2d dirOut = edgeOut.normalize();
        double turn = Math.atan2(dirIn.cross(dirOut), dirIn.dot(dirOut));
        // 凹角：右转，turn 为负；内角 = π - |turn|？外街角角 = |turn|
        double streetAngle = Math.abs(turn);
        if (streetAngle < MIN_CORNER_ANGLE || streetAngle > MAX_CORNER_ANGLE) {
            return null;
        }

        // 指向铺装外侧（街角一侧）的法向：对 CCW 凹角，边的右侧为外侧
        Vec2d outNormalIn = dirIn.perpendicular().multiply(-1.0);  // right of incoming
        Vec2d outNormalOut = dirOut.perpendicular().multiply(-1.0); // right of outgoing

        double halfPhi = streetAngle * 0.5;
        double tanHalf = Math.tan(halfPhi);
        if (tanHalf < 1e-6) {
            return null;
        }

        double maxByIn = edgeIn.length() * tanHalf * 0.95;
        double maxByOut = edgeOut.length() * tanHalf * 0.95;
        double limited = Math.min(requestedRadius, Math.min(maxByIn, maxByOut));
        String reason = "";
        if (limited + 1e-6 < requestedRadius) {
            reason = "adjacent_edge_too_short";
        }
        if (limited < MIN_RADIUS) {
            return null;
        }

        Vec2d offsetInPoint = curr.add(outNormalIn.multiply(limited));
        Vec2d offsetOutPoint = curr.add(outNormalOut.multiply(limited));
        // 沿边方向的偏移线
        Vec2d filletCenter = lineIntersection(offsetInPoint, dirIn, offsetOutPoint, dirOut);
        if (filletCenter == null) {
            // 平行边：圆心取两法向偏移交点的中点近似
            filletCenter = curr
                .add(outNormalIn.multiply(limited))
                .add(outNormalOut.multiply(limited));
        }

        Vec2d tangentStart = projectToSegmentLine(filletCenter, prev, curr);
        Vec2d tangentEnd = projectToSegmentLine(filletCenter, curr, next);
        if (tangentStart == null || tangentEnd == null) {
            return null;
        }
        if (Math.abs(filletCenter.distance(tangentStart) - limited) > 0.35
                || Math.abs(filletCenter.distance(tangentEnd) - limited) > 0.35) {
            return null;
        }
        // 圆心应在街角外侧：相对 curr→filletCenter 应大致沿内外角平分外侧
        Vec2d toCenter = filletCenter.subtract(curr);
        Vec2d exteriorBisector = outNormalIn.add(outNormalOut);
        if (exteriorBisector.lengthSquared() > 1e-12
                && toCenter.dot(exteriorBisector.normalize()) < 0) {
            return null;
        }

        return new CornerFillet(
            filletCenter, tangentStart, tangentEnd, limited, streetAngle, reason);
    }

    private static List<Vec2d> filletAddition(Vec2d sharpCorner, CornerFillet fillet, int segments) {
        List<Vec2d> poly = new ArrayList<>();
        poly.add(sharpCorner);
        appendArc(poly, fillet, segments);
        return dedupe(poly);
    }

    private static void appendArc(List<Vec2d> out, CornerFillet fillet, int segments) {
        Vec2d center = fillet.center();
        Vec2d start = fillet.tangentStart();
        Vec2d end = fillet.tangentEnd();
        out.add(start);
        double startAngle = Math.atan2(start.y - center.y, start.x - center.x);
        double endAngle = Math.atan2(end.y - center.y, end.x - center.x);
        double sweep = endAngle - startAngle;
        while (sweep <= -Math.PI) {
            sweep += Math.PI * 2.0;
        }
        while (sweep > Math.PI) {
            sweep -= Math.PI * 2.0;
        }
        // 外扩弧应接近 streetAngle；偏差过大则取反向
        if (Math.abs(Math.abs(sweep) - fillet.cornerAngle()) > 0.75) {
            sweep = sweep > 0 ? sweep - Math.PI * 2.0 : sweep + Math.PI * 2.0;
        }
        for (int i = 1; i < segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + sweep * t;
            out.add(new Vec2d(
                center.x + Math.cos(angle) * fillet.radius(),
                center.y + Math.sin(angle) * fillet.radius()));
        }
        out.add(end);
    }

    private static Vec2d projectToSegmentLine(Vec2d point, Vec2d a, Vec2d b) {
        Vec2d ab = b.subtract(a);
        if (ab.lengthSquared() < 1e-12) {
            return null;
        }
        Vec2d unit = ab.normalize();
        double t = point.subtract(a).dot(unit);
        return a.add(unit.multiply(t));
    }

    static double polygonArea(List<Vec2d> ring) {
        if (ring == null || ring.size() < 3) {
            return 0.0;
        }
        double sum = 0.0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = ring.get(i);
            Vec2d b = ring.get((i + 1) % n);
            sum += a.x * b.y - b.x * a.y;
        }
        return Math.abs(sum) * 0.5;
    }

    static boolean strictlyInside(Vec2d point, List<Vec2d> polygon) {
        if (!RoadGeometryUtils.pointInPolygon(point, polygon)) {
            return false;
        }
        // 边上的点不算严格内部
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            if (distanceToSegment(point, polygon.get(i), polygon.get((i + 1) % n)) < 1e-5) {
                return false;
            }
        }
        return true;
    }

    private static double distanceToSegment(Vec2d p, Vec2d a, Vec2d b) {
        Vec2d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-18) {
            return p.distance(a);
        }
        double t = Math.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distance(a.add(ab.multiply(t)));
    }

    private static List<Vec2d> sortCcw(Vec2d center, List<Vec2d> points) {
        List<Vec2d> sorted = new ArrayList<>(points);
        sorted.sort(Comparator
            .comparingDouble((Vec2d p) -> Math.atan2(p.y - center.y, p.x - center.x))
            .thenComparingDouble(p -> p.distance(center)));
        // 确保 CCW
        if (signedArea(sorted) < 0) {
            java.util.Collections.reverse(sorted);
        }
        return sorted;
    }

    private static double signedArea(List<Vec2d> ring) {
        double sum = 0.0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = ring.get(i);
            Vec2d b = ring.get((i + 1) % n);
            sum += a.x * b.y - b.x * a.y;
        }
        return sum * 0.5;
    }

    private static List<Vec2d> dedupe(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<Vec2d> out = new ArrayList<>();
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            boolean dup = false;
            for (Vec2d existing : out) {
                if (existing.distance(point) <= DEDUPE_EPS) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(point);
            }
        }
        return out;
    }

    private static void addIfPresent(List<Vec2d> out, Vec2d point) {
        if (point != null) {
            out.add(point);
        }
    }

    private static List<Vec2d> segmentIntersections(List<Vec2d> polyA, List<Vec2d> polyB) {
        List<Vec2d> hits = new ArrayList<>();
        if (polyA == null || polyB == null || polyA.size() < 2 || polyB.size() < 2) {
            return hits;
        }
        int nA = polyA.size();
        int nB = polyB.size();
        for (int i = 0; i < nA; i++) {
            Vec2d a0 = polyA.get(i);
            Vec2d a1 = polyA.get((i + 1) % nA);
            for (int j = 0; j < nB; j++) {
                Vec2d b0 = polyB.get(j);
                Vec2d b1 = polyB.get((j + 1) % nB);
                Vec2d hit = segmentIntersection(a0, a1, b0, b1);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private static Vec2d segmentIntersection(Vec2d a0, Vec2d a1, Vec2d b0, Vec2d b1) {
        Vec2d dA = a1.subtract(a0);
        Vec2d dB = b1.subtract(b0);
        double cross = dA.x * dB.y - dA.y * dB.x;
        if (Math.abs(cross) < 1e-10) {
            return null;
        }
        Vec2d ab = b0.subtract(a0);
        double t = (ab.x * dB.y - ab.y * dB.x) / cross;
        double u = (ab.x * dA.y - ab.y * dA.x) / cross;
        if (t < -1e-6 || t > 1.0 + 1e-6 || u < -1e-6 || u > 1.0 + 1e-6) {
            return null;
        }
        return a0.add(dA.multiply(t));
    }

    private static Vec2d lineIntersection(Vec2d p1, Vec2d d1, Vec2d p2, Vec2d d2) {
        double cross = d1.x * d2.y - d1.y * d2.x;
        if (Math.abs(cross) < 1e-10) {
            return null;
        }
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double t = (dx * d2.y - dy * d2.x) / cross;
        return new Vec2d(p1.x + d1.x * t, p1.y + d1.y * t);
    }

    record CornerFillet(
            Vec2d center,
            Vec2d tangentStart,
            Vec2d tangentEnd,
            double radius,
            double cornerAngle,
            String shrinkReason) {
    }
}
