package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        // 先用切线拓扑求半径/角，再把切点精修到真实条带外缘（变宽/弯曲路缘）。
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
        // 仅当进口条带半宽相对切线 halfWidth 有明显变宽/收窄时才精修，恒宽（含弯曲）走切线解
        FilletPass refined = bandsIndicateVariableWidth(tangents, bandPolygons)
            ? refineFilletsOntoBandCurbs(
                center,
                tangents,
                bandPolygons,
                tangentCore.overlapRegion(),
                filletedTangents.curbArcs(),
                requested,
                10)
            : new FilletPass(List.of(), List.of(), List.of(), List.of());
        List<List<Vec2d>> additions = refined.additions().isEmpty()
            ? filletedTangents.filletAdditions() : refined.additions();
        List<JunctionRegionGeometry.CurbArc> arcs = refined.arcs().isEmpty()
            ? filletedTangents.curbArcs() : refined.arcs();
        List<Double> radii = refined.effectiveRadii().isEmpty()
            ? filletedTangents.effectiveCornerRadii() : refined.effectiveRadii();
        return new JunctionRegionGeometry(
            tangentCore.overlapRegion(),
            pavementUnion,
            pavementUnion,
            additions,
            arcs,
            bounds,
            requested,
            radii);
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

    private static double  distanceToPolygonBoundary(Vec2d point, List<Vec2d> polygon) {
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
        // 允许轻度不等宽（变宽/渠化进口）；过大则交由条带算法处理。
        double meanHalf = 0.5 * (a.halfWidth() + b.halfWidth());
        if (meanHalf <= 1e-9) {
            return null;
        }
        if (Math.abs(a.halfWidth() - b.halfWidth()) > Math.max(1.0, 0.35 * meanHalf)) {
            return null;
        }
        return new StraightRoad(a.outward(), meanHalf);
    }

    private record FilletPass(
            List<Vec2d> finalRing,
            List<List<Vec2d>> additions,
            List<JunctionRegionGeometry.CurbArc> arcs,
            List<Double> effectiveRadii) {
    }

    /** 条带横向跨度相对进口 halfWidth 明显更大或更小 → 存在变宽/收窄。 */
    private static boolean bandsIndicateVariableWidth(
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons) {
        if (tangents == null || bandPolygons == null) {
            return false;
        }
        int n = Math.min(tangents.size(), bandPolygons.size());
        for (int i = 0; i < n; i++) {
            JunctionCurbFilletBuilder.Approach approach = tangents.get(i);
            List<Vec2d> band = bandPolygons.get(i);
            if (approach == null || band == null || band.size() < 3) {
                continue;
            }
            double expected = 2.0 * approach.halfWidth();
            double actual = bandCrossExtent(band, approach.outward());
            // 阈值需大于斜交/弯曲条带相对进口法向的投影膨胀，避免五岔恒宽误触发
            if (actual > expected + 4.0 || (expected > 2.0 && actual < expected - 4.0)) {
                return true;
            }
        }
        return false;
    }

    private static double bandCrossExtent(List<Vec2d> band, Vec2d along) {
        if (band == null || along == null || along.lengthSquared() < 1e-12) {
            return 0.0;
        }
        Vec2d normal = along.normalize().perpendicular();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Vec2d p : band) {
            if (p == null) {
                continue;
            }
            double lat = p.dot(normal);
            min = Math.min(min, lat);
            max = Math.max(max, lat);
        }
        return Double.isFinite(min) && Double.isFinite(max) ? max - min : 0.0;
    }

    /**
     * 将切线近似圆角精修到「该街角所属两条进口的外侧路缘」上。
     * 只在指定外侧路缘折线上吸附，不扫端盖/内部重叠边；变宽与收窄均触发。
     */
    static FilletPass refineFilletsOntoBandCurbs(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons,
            List<Vec2d> overlapCorners,
            List<JunctionRegionGeometry.CurbArc> seedArcs,
            double requested,
            int arcSegments) {
        if (center == null || bandPolygons == null || tangents == null
                || seedArcs == null || seedArcs.isEmpty() || requested < MIN_RADIUS) {
            return new FilletPass(List.of(), List.of(), List.of(), List.of());
        }
        List<List<Vec2d>> additions = new ArrayList<>();
        List<JunctionRegionGeometry.CurbArc> arcs = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        boolean anySnap = false;
        for (JunctionRegionGeometry.CurbArc seed : seedArcs) {
            if (seed == null || seed.radius() < MIN_RADIUS) {
                continue;
            }
            Vec2d overlapSharp = nearestOverlapCorner(overlapCorners, seed);
            StreetCornerBands cornerBands = resolveStreetCornerBands(
                center, overlapSharp, seed, tangents, bandPolygons);
            CornerFillet snapped = null;
            Vec2d sharp = overlapSharp;
            // 仅当种子相对指定外侧路缘明显内偏（变宽）或外偏（收窄）时精修
            if (cornerBands != null && seedNeedsOuterCurbSnap(seed, cornerBands)) {
                snapped = snapFilletOntoOuterCurbs(center, seed, cornerBands);
            }
            double moveTol = Math.max(1.0, 0.2 * seed.radius());
            boolean moved = snapped != null && filletTangentsMoved(seed, snapped, moveTol);
            // moved==false 时不得采纳 bandSharp：旧弧与新锐角会脱节。
            CornerFillet use = moved ? snapped : seedAsFillet(seed);
            if (use == null) {
                continue;
            }
            if (moved) {
                anySnap = true;
                Vec2d bandSharp = outerCurbIntersection(cornerBands, overlapSharp);
                if (bandSharp != null) {
                    sharp = bandSharp;
                }
            }
            if (sharp == null) {
                sharp = seed.sharpCorner() != null ? seed.sharpCorner() : reconstructSharpFromFillet(use);
            }
            radii.add(use.radius());
            arcs.add(new JunctionRegionGeometry.CurbArc(
                use.center(),
                use.tangentStart(),
                use.tangentEnd(),
                use.radius(),
                use.cornerAngle(),
                use.shrinkReason(),
                use.sweepRadians(),
                sharp));
            if (sharp != null) {
                additions.add(filletAddition(sharp, use, Math.max(3, arcSegments)));
            }
        }
        if (!anySnap) {
            return new FilletPass(List.of(), List.of(), List.of(), List.of());
        }
        return new FilletPass(List.of(), List.copyOf(additions), List.copyOf(arcs), List.copyOf(radii));
    }

    private static boolean filletTangentsMoved(
            JunctionRegionGeometry.CurbArc seed,
            CornerFillet snapped,
            double minMove) {
        if (seed == null || snapped == null) {
            return false;
        }
        double d1 = Math.min(
            seed.tangentStart().distance(snapped.tangentStart()),
            seed.tangentStart().distance(snapped.tangentEnd()));
        double d2 = Math.min(
            seed.tangentEnd().distance(snapped.tangentStart()),
            seed.tangentEnd().distance(snapped.tangentEnd()));
        return Math.max(d1, d2) >= minMove;
    }

    private record StreetCornerBands(
            JunctionCurbFilletBuilder.Approach approachA,
            JunctionCurbFilletBuilder.Approach approachB,
            List<Vec2d> outerCurbA,
            List<Vec2d> outerCurbB,
            List<Vec2d> bandA,
            List<Vec2d> bandB) {
    }

    /**
     * 由街角锐点确定两条进口，并各自提取面向草地的外侧路缘折线（排除端盖）。
     */
    private static StreetCornerBands resolveStreetCornerBands(
            Vec2d center,
            Vec2d corner,
            JunctionRegionGeometry.CurbArc seed,
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons) {
        Vec2d sharp = corner != null ? corner : (seed != null ? seed.center() : null);
        if (center == null || sharp == null || tangents == null || bandPolygons == null) {
            return null;
        }
        int[] pair = nearestApproachPair(center, sharp, tangents);
        if (pair == null) {
            return null;
        }
        JunctionCurbFilletBuilder.Approach a = tangents.get(pair[0]);
        JunctionCurbFilletBuilder.Approach b = tangents.get(pair[1]);
        List<Vec2d> bandA = bandForApproach(pair[0], tangents, bandPolygons);
        List<Vec2d> bandB = bandForApproach(pair[1], tangents, bandPolygons);
        if (a == null || b == null || bandA == null || bandB == null) {
            return null;
        }
        List<Vec2d> curbA = extractOuterCurbPolyline(center, sharp, a, bandA);
        List<Vec2d> curbB = extractOuterCurbPolyline(center, sharp, b, bandB);
        if (curbA.size() < 2 || curbB.size() < 2) {
            return null;
        }
        return new StreetCornerBands(a, b, curbA, curbB, bandA, bandB);
    }

    private static int[] nearestApproachPair(
            Vec2d center,
            Vec2d corner,
            List<JunctionCurbFilletBuilder.Approach> tangents) {
        if (tangents == null || tangents.size() < 2) {
            return null;
        }
        Vec2d radial = corner.subtract(center);
        if (radial.lengthSquared() < 1e-12) {
            return null;
        }
        radial = radial.normalize();
        int bestI = -1;
        int bestJ = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < tangents.size(); i++) {
            for (int j = i + 1; j < tangents.size(); j++) {
                JunctionCurbFilletBuilder.Approach a = tangents.get(i);
                JunctionCurbFilletBuilder.Approach b = tangents.get(j);
                if (a == null || b == null) {
                    continue;
                }
                double sideA = Math.signum(radial.dot(a.leftNormal()));
                double sideB = Math.signum(radial.dot(b.leftNormal()));
                if (Math.abs(sideA) < 1e-9 || Math.abs(sideB) < 1e-9) {
                    continue;
                }
                Vec2d outA = a.leftNormal().multiply(sideA);
                Vec2d outB = b.leftNormal().multiply(sideB);
                double score = outA.dot(radial) + outB.dot(radial);
                if (score > bestScore) {
                    bestScore = score;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        return bestI >= 0 ? new int[]{bestI, bestJ} : null;
    }

    private static List<Vec2d> bandForApproach(
            int approachIndex,
            List<JunctionCurbFilletBuilder.Approach> tangents,
            List<List<Vec2d>> bandPolygons) {
        if (approachIndex < 0 || approachIndex >= tangents.size()) {
            return null;
        }
        if (approachIndex < bandPolygons.size() && bandPolygons.get(approachIndex) != null) {
            return bandPolygons.get(approachIndex);
        }
        JunctionCurbFilletBuilder.Approach approach = tangents.get(approachIndex);
        if (approach == null || approach.outward().lengthSquared() < 1e-12) {
            return null;
        }
        Vec2d dir = approach.outward().normalize();
        List<Vec2d> best = null;
        double bestDot = -2.0;
        for (List<Vec2d> band : bandPolygons) {
            if (band == null || band.size() < 3) {
                continue;
            }
            Vec2d c = polygonCentroid(band);
            if (c == null || c.lengthSquared() < 1e-12) {
                continue;
            }
            double dot = c.normalize().dot(dir);
            if (dot > bestDot) {
                bestDot = dot;
                best = band;
            }
        }
        return best;
    }

    private static Vec2d polygonCentroid(List<Vec2d> ring) {
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (Vec2d p : ring) {
            if (p == null) {
                continue;
            }
            sx += p.x;
            sy += p.y;
            n++;
        }
        return n == 0 ? null : new Vec2d(sx / n, sy / n);
    }

    /**
     * 提取面向街角草地的外侧路缘折线；跳过与进口方向近乎垂直的端盖边。
     */
    private static List<Vec2d> extractOuterCurbPolyline(
            Vec2d center,
            Vec2d corner,
            JunctionCurbFilletBuilder.Approach approach,
            List<Vec2d> band) {
        if (band == null || band.size() < 2 || approach == null) {
            return List.of();
        }
        Vec2d radial = corner.subtract(center);
        if (radial.lengthSquared() < 1e-12) {
            return List.of();
        }
        radial = radial.normalize();
        double side = Math.signum(radial.dot(approach.leftNormal()));
        if (Math.abs(side) < 1e-9) {
            side = 1.0;
        }
        Vec2d desiredOut = approach.leftNormal().multiply(side).normalize();
        Vec2d along = approach.outward().normalize();
        List<Vec2d> curb = new ArrayList<>();
        int n = band.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = band.get(i);
            Vec2d b = band.get((i + 1) % n);
            if (a == null || b == null) {
                continue;
            }
            Vec2d ab = b.subtract(a);
            double len2 = ab.lengthSquared();
            if (len2 < 1e-12) {
                continue;
            }
            Vec2d tangent = ab.normalize();
            // 端盖：近似垂直于进口方向
            if (Math.abs(tangent.dot(along)) < 0.35) {
                continue;
            }
            Vec2d mid = new Vec2d(0.5 * (a.x + b.x), 0.5 * (a.y + b.y));
            Vec2d outward = tangent.perpendicular();
            if (outward.dot(mid.subtract(center)) < 0.0) {
                outward = outward.multiply(-1.0);
            }
            if (outward.normalize().dot(desiredOut) < 0.25) {
                continue;
            }
            if (curb.isEmpty() || curb.getLast().distance(a) > 1e-4) {
                curb.add(a);
            }
            curb.add(b);
        }
        return curb.size() >= 2 ? curb : List.of();
    }

    /**
     * 种子切点是否已落在该街角两条指定外侧路缘上；任一偏离超过半宽比例容差则需精修。
     * 按切点各自对应路缘（direct / swap 较优配对）分别判断，避免贴错邻缘却被判“已对齐”。
     */
    private static boolean seedNeedsOuterCurbSnap(
            JunctionRegionGeometry.CurbArc seed,
            StreetCornerBands bands) {
        if (seed == null || bands == null
                || bands.approachA() == null || bands.approachB() == null) {
            return false;
        }
        double tol = Math.max(
            1.0,
            0.25 * Math.max(bands.approachA().halfWidth(), bands.approachB().halfWidth()));
        BoundaryHit directA = closestHitOnPolyline(seed.tangentStart(), bands.outerCurbA(), 0);
        BoundaryHit directB = closestHitOnPolyline(seed.tangentEnd(), bands.outerCurbB(), 1);
        BoundaryHit swapA = closestHitOnPolyline(seed.tangentStart(), bands.outerCurbB(), 1);
        BoundaryHit swapB = closestHitOnPolyline(seed.tangentEnd(), bands.outerCurbA(), 0);
        double directCost = hitPairCost(seed.tangentStart(), seed.tangentEnd(), directA, directB);
        double swapCost = hitPairCost(seed.tangentStart(), seed.tangentEnd(), swapA, swapB);
        boolean useDirect = directCost <= swapCost;
        BoundaryHit hitStart = useDirect ? directA : swapA;
        BoundaryHit hitEnd = useDirect ? directB : swapB;
        if (hitStart == null || hitEnd == null) {
            return true;
        }
        // 用配对后的指定外侧路缘分别判定，勿对两条路缘取 min。
        double toStart = useDirect
            ? distanceToPolyline(seed.tangentStart(), bands.outerCurbA())
            : distanceToPolyline(seed.tangentStart(), bands.outerCurbB());
        double toEnd = useDirect
            ? distanceToPolyline(seed.tangentEnd(), bands.outerCurbB())
            : distanceToPolyline(seed.tangentEnd(), bands.outerCurbA());
        return toStart > tol || toEnd > tol;
    }

    /**
     * 仅在两条指定外侧路缘上吸附种子切点，再按外法向偏置求圆心。
     */
    static CornerFillet snapFilletOntoOuterCurbs(
            Vec2d center,
            JunctionRegionGeometry.CurbArc seed,
            StreetCornerBands bands) {
        if (center == null || seed == null || bands == null) {
            return null;
        }
        BoundaryHit directA = closestHitOnPolyline(seed.tangentStart(), bands.outerCurbA(), 0);
        BoundaryHit directB = closestHitOnPolyline(seed.tangentEnd(), bands.outerCurbB(), 1);
        BoundaryHit swapA = closestHitOnPolyline(seed.tangentStart(), bands.outerCurbB(), 1);
        BoundaryHit swapB = closestHitOnPolyline(seed.tangentEnd(), bands.outerCurbA(), 0);
        double directCost = hitPairCost(seed.tangentStart(), seed.tangentEnd(), directA, directB);
        double swapCost = hitPairCost(seed.tangentStart(), seed.tangentEnd(), swapA, swapB);
        BoundaryHit hitA;
        BoundaryHit hitB;
        if (directCost <= swapCost) {
            hitA = directA;
            hitB = directB;
        } else {
            hitA = swapA;
            hitB = swapB;
        }
        if (hitA == null || hitB == null) {
            return null;
        }
        Vec2d outA = hitA.outwardNormal(center);
        Vec2d outB = hitB.outwardNormal(center);
        if (outA == null || outB == null) {
            return null;
        }
        // 按新切点几何夹紧半径：R ≤ setback·tan(φ/2)，避免变宽进口超出后退预算。
        double normalCos = Math.clamp(outA.dot(outB), -1.0, 1.0);
        double grassAngle = Math.PI - Math.acos(normalCos);
        if (grassAngle < MIN_CORNER_ANGLE || grassAngle > MAX_CORNER_ANGLE) {
            grassAngle = seed.cornerAngle() > 0.0 ? seed.cornerAngle() : Math.PI * 0.5;
        }
        double halfPhi = 0.5 * grassAngle;
        double tanHalf = Math.tan(halfPhi);
        Vec2d curbSharp = outerCurbIntersection(bands, seed.center());
        double setbackA = curbSharp != null
            ? hitA.point().distance(curbSharp)
            : hitA.point().distance(center);
        double setbackB = curbSharp != null
            ? hitB.point().distance(curbSharp)
            : hitB.point().distance(center);
        double setbackBudget = Math.max(1e-6, Math.min(setbackA, setbackB));
        double radius = seed.radius();
        if (tanHalf > 1e-6) {
            radius = Math.min(radius, setbackBudget * tanHalf);
        }
        if (radius < MIN_RADIUS) {
            return null;
        }
        Vec2d offsetA = hitA.point().add(outA.multiply(radius));
        Vec2d offsetB = hitB.point().add(outB.multiply(radius));
        Vec2d filletCenter = lineIntersection(offsetA, hitA.tangent(), offsetB, hitB.tangent());
        if (filletCenter == null) {
            filletCenter = new Vec2d(
                0.5 * (offsetA.x + offsetB.x),
                0.5 * (offsetA.y + offsetB.y));
        }
        if (filletCenter.distance(center) + 1e-6 < hitA.point().distance(center)
                && filletCenter.distance(center) + 1e-6 < hitB.point().distance(center)) {
            return null;
        }
        Vec2d tangentA = projectToSegmentLine(
            filletCenter, hitA.point(), hitA.point().add(hitA.tangent()));
        Vec2d tangentB = projectToSegmentLine(
            filletCenter, hitB.point(), hitB.point().add(hitB.tangent()));
        if (tangentA == null || tangentB == null) {
            return null;
        }
        if (Math.abs(filletCenter.distance(tangentA) - radius) > 0.55
                || Math.abs(filletCenter.distance(tangentB) - radius) > 0.55) {
            return null;
        }
        if (distanceToPolyline(tangentA, bands.outerCurbA()) > 0.85
                && distanceToPolyline(tangentA, bands.outerCurbB()) > 0.85) {
            return null;
        }
        if (distanceToPolyline(tangentB, bands.outerCurbA()) > 0.85
                && distanceToPolyline(tangentB, bands.outerCurbB()) > 0.85) {
            return null;
        }
        double sweep = FilletArcSweep.resolveShort(filletCenter, tangentA, tangentB);
        return new CornerFillet(
            filletCenter, tangentA, tangentB, radius, grassAngle, "band_curb", sweep);
    }

    private static Vec2d outerCurbIntersection(StreetCornerBands bands, Vec2d near) {
        if (bands == null) {
            return null;
        }
        Vec2d best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Vec2d hit : openPolylineIntersections(bands.outerCurbA(), bands.outerCurbB())) {
            if (hit == null) {
                continue;
            }
            double d = near != null ? hit.distance(near) : hit.lengthSquared();
            if (d < bestDist) {
                bestDist = d;
                best = hit;
            }
        }
        return bestDist < 6.0 ? best : null;
    }

    private static List<Vec2d> openPolylineIntersections(List<Vec2d> a, List<Vec2d> b) {
        List<Vec2d> hits = new ArrayList<>();
        if (a == null || b == null || a.size() < 2 || b.size() < 2) {
            return hits;
        }
        for (int i = 0; i < a.size() - 1; i++) {
            for (int j = 0; j < b.size() - 1; j++) {
                Vec2d hit = segmentIntersection(a.get(i), a.get(i + 1), b.get(j), b.get(j + 1));
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private record BoundaryHit(Vec2d point, Vec2d tangent, int bandIndex) {
        Vec2d outwardNormal(Vec2d center) {
            if (tangent == null || tangent.lengthSquared() < 1e-12 || point == null) {
                return null;
            }
            Vec2d n = tangent.normalize().perpendicular();
            if (center != null && n.dot(point.subtract(center)) < 0.0) {
                n = n.multiply(-1.0);
            }
            return n.normalize();
        }
    }

    private static BoundaryHit closestHitOnPolyline(Vec2d query, List<Vec2d> polyline, int id) {
        if (query == null || polyline == null || polyline.size() < 2) {
            return null;
        }
        BoundaryHit best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d a = polyline.get(i);
            Vec2d c = polyline.get(i + 1);
            if (a == null || c == null) {
                continue;
            }
            Vec2d ab = c.subtract(a);
            double len2 = ab.lengthSquared();
            if (len2 < 1e-12) {
                continue;
            }
            double t = Math.clamp(query.subtract(a).dot(ab) / len2, 0.0, 1.0);
            Vec2d foot = a.add(ab.multiply(t));
            double d = query.distance(foot);
            if (d < bestDist) {
                bestDist = d;
                best = new BoundaryHit(foot, ab.normalize(), id);
            }
        }
        return best;
    }

    private static double hitPairCost(Vec2d q0, Vec2d q1, BoundaryHit h0, BoundaryHit h1) {
        if (h0 == null || h1 == null || q0 == null || q1 == null) {
            return Double.POSITIVE_INFINITY;
        }
        return q0.distance(h0.point()) + q1.distance(h1.point());
    }

    private static double distanceToPolyline(Vec2d point, List<Vec2d> polyline) {
        BoundaryHit hit = closestHitOnPolyline(point, polyline, 0);
        return hit == null ? Double.POSITIVE_INFINITY : point.distance(hit.point());
    }

    private static Vec2d nearestOverlapCorner(
            List<Vec2d> overlapCorners,
            JunctionRegionGeometry.CurbArc seed) {
        if (overlapCorners == null || seed == null || seed.center() == null) {
            return null;
        }
        Vec2d best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Vec2d corner : overlapCorners) {
            if (corner == null) {
                continue;
            }
            double d = corner.distance(seed.center());
            if (d < bestDist) {
                bestDist = d;
                best = corner;
            }
        }
        return best;
    }

    private static CornerFillet seedAsFillet(JunctionRegionGeometry.CurbArc seed) {
        if (seed == null) {
            return null;
        }
        return new CornerFillet(
            seed.center(),
            seed.tangentStart(),
            seed.tangentEnd(),
            seed.radius(),
            seed.cornerAngle(),
            seed.shrinkReason() != null ? seed.shrinkReason() : "",
            seed.sweepRadians());
    }

    private static Vec2d reconstructSharpFromFillet(CornerFillet fillet) {
        if (fillet == null || fillet.center() == null || fillet.radius() <= 1e-6) {
            return null;
        }
        double phi = fillet.cornerAngle();
        double sinHalf = Math.sin(phi * 0.5);
        if (sinHalf < 1e-6) {
            return null;
        }
        double startAngle = Math.atan2(
            fillet.tangentStart().y - fillet.center().y,
            fillet.tangentStart().x - fillet.center().x);
        Vec2d mid = FilletArcSweep.pointOnArc(
            fillet.center(), fillet.radius(), startAngle + fillet.sweepRadians() * 0.5);
        Vec2d fromCenter = mid.subtract(fillet.center());
        if (fromCenter.lengthSquared() < 1e-12) {
            return null;
        }
        return fillet.center().add(fromCenter.multiply(1.0 / sinHalf));
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
                fillet.shrinkReason(),
                fillet.sweepRadians(),
                corner));
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
        // 画布尺度下半宽/半径可变大：固定 0.75 会漏掉重叠核角，倒圆外形织不进 fillPolygon
        double matchTol = 0.75;
        for (JunctionRegionGeometry.CurbArc arc : arcs) {
            if (arc != null && arc.radius() > matchTol) {
                matchTol = Math.max(matchTol, arc.radius() * 0.15);
            }
        }
        List<Vec2d> out = new ArrayList<>();
        Set<JunctionRegionGeometry.CurbArc> usedArcs = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Vec2d curr = union.get(i);
            if (!isNearAny(curr, overlapCorners, matchTol)) {
                out.add(curr);
                continue;
            }
            JunctionRegionGeometry.CurbArc best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (JunctionRegionGeometry.CurbArc arc : arcs) {
                if (arc == null || arc.radius() < MIN_RADIUS || arc.center() == null
                        || usedArcs.contains(arc)) {
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
            usedArcs.add(best);
            // 环走向：上一顶点更接近哪个切点，决定弧采样方向，避免 CCW 环上半数角回折。
            Vec2d prev = out.isEmpty() ? union.get((i - 1 + n) % n) : out.getLast();
            boolean reverse = shouldReverseArcForRing(prev, best);
            Vec2d arcStart = reverse ? best.tangentEnd() : best.tangentStart();
            Vec2d arcEnd = reverse ? best.tangentStart() : best.tangentEnd();
            double sweep = FilletArcSweep.resolveShort(best.center(), arcStart, arcEnd);
            CornerFillet fillet = new CornerFillet(
                best.center(), arcStart, arcEnd,
                best.radius(), best.cornerAngle(),
                best.shrinkReason() != null ? best.shrinkReason() : "",
                sweep);
            appendArcFollowingRing(out, prev, fillet, Math.max(3, arcSegments));
        }
        List<Vec2d> ring = dedupe(out);
        return ring.size() >= 3 ? ring : union;
    }

    /** 上一顶点更靠近 tangentEnd 时，应按 End→Start 插入弧以跟随环走向。 */
    private static boolean shouldReverseArcForRing(Vec2d prev, JunctionRegionGeometry.CurbArc arc) {
        if (prev == null || arc == null
                || arc.tangentStart() == null || arc.tangentEnd() == null) {
            return false;
        }
        return prev.distance(arc.tangentEnd()) + 1e-9 < prev.distance(arc.tangentStart());
    }

    /**
     * 沿环追加弧：若上一顶点已是弧起点则跳过首点；否则整段写入，避免漏掉入口切点。
     */
    private static void appendArcFollowingRing(
            List<Vec2d> out,
            Vec2d prev,
            CornerFillet fillet,
            int segments) {
        FilletArcSweep.ListSamples samples = FilletArcSweep.sample(
            fillet.center(),
            fillet.tangentStart(),
            fillet.tangentEnd(),
            fillet.radius(),
            fillet.sweepRadians(),
            segments);
        List<Vec2d> points = samples.points();
        if (points.isEmpty()) {
            return;
        }
        int start = 0;
        if (prev != null && prev.distance(points.getFirst()) <= 1e-6) {
            start = 1;
        }
        for (int i = start; i < points.size(); i++) {
            out.add(points.get(i));
        }
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

    /**
     * 沿路缘从街角向外的可用切点后退预算（用于限制 t = R/tan(φ/2)）。
     * <p>
     * 仅用重叠核投影时，锐角草地 tan(φ/2)&lt;1，有效 R 会卡在核边长平台期——滑条继续加大却无可见变化。
     * 预算随请求半径加长：补铺落在街角草地口袋，不拉长锐角并集路臂。
     */
    private static double streetCornerMaxSetback(
            Vec2d center,
            StraightRoad roadA,
            StraightRoad roadB,
            List<Vec2d> overlapCorners,
            double requested) {
        double fromOverlap = pavementExtentFromOverlap(center, roadA, roadB, overlapCorners);
        double widthSpan = Math.max(roadA.halfWidth(), roadB.halfWidth());
        double armBudget = Math.max(fromOverlap * 0.85, widthSpan + 1.0);
        // 随 R 增长，避免锐角 limited=armBudget*tan(φ/2) 平台；正交时仍约可容纳 setback≈R
        return armBudget + Math.max(0.0, requested);
    }

    /**
     * 重叠核角点处的路缘圆角。
     * <p>
     * {@code outA/outB} 是条带外法向，二者夹角 α；真正的街角草地楔形是两路缘夹角
     * {@code φ = π - α}。圆心沿草地角平分线、距角点 {@code R/sin(φ/2)}，使到两路缘的
     * 垂距均为 R；切点后退 {@code R/tan(φ/2)}。
     * <p>
     * 若误把 α 当 φ，锐角草地几乎不外扩（弧贴在交点），钝角草地却把弧甩到路臂远处——
     * 正是预览里「绿弧围着交叉点、外围看不到倒圆」的根因。
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
        double normalCos = Math.clamp(outA.dot(outB), -1.0, 1.0);
        double normalAngle = Math.acos(normalCos);
        // 路缘草地楔形 = 两法向补角
        double grassAngle = Math.PI - normalAngle;
        if (grassAngle < MIN_CORNER_ANGLE || grassAngle > MAX_CORNER_ANGLE) {
            return null;
        }
        double halfPhi = grassAngle * 0.5;
        double sinHalf = Math.sin(halfPhi);
        double tanHalf = Math.tan(halfPhi);
        if (sinHalf < 1e-6 || tanHalf < 1e-6) {
            return null;
        }
        // 后退 t = R/tan(φ/2) ≤ maxSetback
        double limited = Math.min(requested, Math.max(0.0, maxSetback) * tanHalf * 0.95);
        String reason = "";
        if (limited + 1e-6 < requested) {
            reason = "adjacent_edge_too_short";
        }
        if (limited < MIN_RADIUS) {
            return null;
        }

        Vec2d bisector = outA.add(outB);
        if (bisector.lengthSquared() < 1e-12) {
            return null;
        }
        bisector = bisector.normalize();
        // 必须指向交叉点外侧（草地），且落在两法向正半平面交（街角口袋）
        if (bisector.dot(radial.normalize()) <= 0.0) {
            bisector = bisector.multiply(-1.0);
        }
        double dist = limited / sinHalf;
        Vec2d filletCenter = corner.add(bisector.multiply(dist));
        if (filletCenter.distance(center) + 1e-6 < corner.distance(center)) {
            return null;
        }
        if (filletCenter.subtract(corner).dot(outA) <= 0.0
                || filletCenter.subtract(corner).dot(outB) <= 0.0) {
            return null;
        }

        Vec2d edgeAPoint = center.add(outA.multiply(roadA.halfWidth()));
        Vec2d edgeBPoint = center.add(outB.multiply(roadB.halfWidth()));
        Vec2d tangentA = projectToSegmentLine(filletCenter, edgeAPoint, edgeAPoint.add(roadA.direction()));
        Vec2d tangentB = projectToSegmentLine(filletCenter, edgeBPoint, edgeBPoint.add(roadB.direction()));
        if (tangentA == null || tangentB == null) {
            return null;
        }
        if (Math.abs(filletCenter.distance(tangentA) - limited) > 0.35
                || Math.abs(filletCenter.distance(tangentB) - limited) > 0.35) {
            return null;
        }
        double setbackLimit = limited / tanHalf + 1.0;
        if (corner.distance(tangentA) > setbackLimit
                || corner.distance(tangentB) > setbackLimit) {
            return null;
        }

        // 切点顺序任意：appendArc / 预览采样一律走短弧，中点会落在锐点与圆心之间（外扩）
        double sweep = FilletArcSweep.resolveShort(filletCenter, tangentA, tangentB);
        return new CornerFillet(
            filletCenter, tangentA, tangentB, limited, grassAngle, reason, sweep);
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
                fillet.shrinkReason(),
                fillet.sweepRadians(),
                curr));
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

        double sweep = FilletArcSweep.resolveShort(filletCenter, tangentStart, tangentEnd);
        return new CornerFillet(
            filletCenter, tangentStart, tangentEnd, limited, streetAngle, reason, sweep);
    }

    private static List<Vec2d> filletAddition(Vec2d sharpCorner, CornerFillet fillet, int segments) {
        List<Vec2d> poly = new ArrayList<>();
        poly.add(sharpCorner);
        appendArc(poly, fillet, segments);
        return dedupe(poly);
    }

    private static void appendArc(List<Vec2d> out, CornerFillet fillet, int segments) {
        FilletArcSweep.ListSamples samples = FilletArcSweep.sample(
            fillet.center(),
            fillet.tangentStart(),
            fillet.tangentEnd(),
            fillet.radius(),
            fillet.sweepRadians(),
            segments);
        List<Vec2d> points = samples.points();
        for (int i = out.isEmpty() ? 0 : 1; i < points.size(); i++) {
            out.add(points.get(i));
        }
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
            String shrinkReason,
            double sweepRadians) {
        CornerFillet {
            shrinkReason = shrinkReason != null ? shrinkReason : "";
            if (!Double.isFinite(sweepRadians) || Math.abs(sweepRadians) < 1e-12) {
                sweepRadians = FilletArcSweep.resolveShort(center, tangentStart, tangentEnd);
            } else if (Math.abs(sweepRadians) > Math.PI + 1e-9) {
                sweepRadians = FilletArcSweep.resolveShort(center, tangentStart, tangentEnd);
            }
        }
    }
}
