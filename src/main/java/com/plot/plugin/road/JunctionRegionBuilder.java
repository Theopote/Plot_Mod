package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 两条直线道路的平交路口区域几何：重叠核区 + 铺装并集 + 凹角路缘圆角。
 * <p>
 * 不从交叉点外推 plaza，而是用两条道路的局部铺装条带求交/并，再在并集外环凹角补弧。
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

        // 最终铺装臂长由重叠平行四边形投影决定（不随 R 变化），避免锐角斜交裁掉核区角点
        double pavementExtent = Double.isFinite(extent) && extent > 0
            ? extent
            : pavementExtentFromOverlap(center, roadA, roadB, overlap);
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

        List<Vec2d> computeUnion = computeExtent > pavementExtent + 1e-6
            ? buildUnionCross(
                center,
                buildStripRectangle(center, roadA, computeExtent),
                buildStripRectangle(center, roadB, computeExtent),
                overlap)
            : pavementUnion;
        // 在长边界上求圆弧，再把补弧接到固定铺装并集的凹角上
        FilletPass fillets = applyConcaveFillets(
            pavementUnion, computeUnion, requested, Math.max(3, arcSegments));
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
     * 从多条进口配对成两条贯通直线道路。
     * 首版仅接受四条有效进口（两组近似反向且半宽一致）；两臂节点不得扩张成四臂十字。
     */
    public static JunctionRegionGeometry buildFromApproaches(
            Vec2d center,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            double cornerRadius) {
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
        // 禁止两臂自动扩张为四臂完整道路（立交下层弯折等）
        if (valid.size() != 4) {
            return JunctionRegionGeometry.empty();
        }
        valid.sort(Comparator.comparingDouble(a -> Math.atan2(a.outward().y, a.outward().x)));
        StraightRoad roadA = pairOpposite(valid.get(0), valid.get(2));
        StraightRoad roadB = pairOpposite(valid.get(1), valid.get(3));
        if (roadA == null || roadB == null) {
            return JunctionRegionGeometry.empty();
        }
        return build(center, roadA, roadB, cornerRadius);
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
        for (Vec2d vertex : overlap) {
            candidates.add(vertex);
        }
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
     * 在固定铺装并集上替换凹角；圆弧半径上限可参考更长计算环上的同角边长。
     */
    private static FilletPass applyConcaveFillets(
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
        double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
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
