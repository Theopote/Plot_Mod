package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 真正的道路路缘圆角：在相邻进口硬路缘交点处增加转弯圆弧铺装，
 * 而不是对路口铺装多边形的外凸顶点做统一倒角裁剪。
 * <p>
 * 正交十字路口时，四处圆弧位于道路矩形并集的凹角，向原铺装之外扩展。
 */
public final class JunctionCurbFilletBuilder {
    private static final double MIN_RADIUS = 0.15;
    private static final double MIN_CORNER_ANGLE = 0.15;
    private static final double MAX_CORNER_ANGLE = Math.PI - 0.15;

    private JunctionCurbFilletBuilder() {
    }

    /** 一条进口：中心线向外方向 + 硬表面半宽 + 可选边 id（切点归属）。 */
    public record Approach(Vec2d outward, double halfWidth, String edgeId) {
        public Approach(Vec2d outward, double halfWidth) {
            this(outward, halfWidth, null);
        }

        public Approach {
            if (outward == null) {
                outward = new Vec2d(0, 0);
            } else if (outward.lengthSquared() > 1e-12) {
                outward = outward.normalize();
            }
            halfWidth = Math.max(0.0, halfWidth);
            if (edgeId != null && edgeId.isBlank()) {
                edgeId = null;
            }
        }

        public Vec2d leftNormal() {
            return outward.perpendicular();
        }
    }

    public static JunctionGeometryResult build(
            Vec2d center,
            List<Approach> approaches,
            double junctionRadius,
            double cornerRadius) {
        return build(center, approaches, junctionRadius, cornerRadius, 10);
    }

    public static JunctionGeometryResult build(
            Vec2d center,
            List<Approach> approaches,
            double junctionRadius,
            double cornerRadius,
            int arcSegments) {
        // ≥3 臂：2 臂会在背向外侧生成凸角圆角并漏掉真实凹口。
        if (center == null || approaches == null || approaches.size() < 3) {
            return JunctionGeometryResult.empty();
        }
        List<Approach> sorted = sortApproaches(approaches);
        if (sorted.size() < 3) {
            return JunctionGeometryResult.empty();
        }
        double plaza = Math.max(junctionRadius, maxHalfWidth(sorted) + 1.0);
        List<Vec2d> boundaries = mouthBoundaryPoints(center, sorted, plaza);
        double requested = Math.max(0.0, cornerRadius);

        List<CurbFillet> fillets = new ArrayList<>();
        double minEffective = Double.POSITIVE_INFINITY;
        int count = sorted.size();
        for (int i = 0; i < count; i++) {
            CurbFillet fillet = requested > 1e-6
                ? computeFillet(center, sorted.get(i), sorted.get((i + 1) % count), requested, plaza)
                : null;
            fillets.add(fillet);
            if (fillet != null) {
                minEffective = Math.min(minEffective, fillet.radius());
            }
        }

        List<Vec2d> sharp = traceBoundary(center, sorted, plaza, null, 0);
        List<Vec2d> fill = requested <= 1e-6
            ? sharp
            : traceBoundary(center, sorted, plaza, fillets, Math.max(3, arcSegments));
        if (fill.size() < 3) {
            fill = sharp;
        }

        List<JunctionGeometryResult.FilletCorner> meta = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CurbFillet fillet = fillets.get(i);
            if (fillet == null) {
                continue;
            }
            // computeFillet(a,b) 产出 tangentStart∈b、tangentEnd∈a；owner 必须与切点同侧。
            Approach approachA = sorted.get(i);
            Approach approachB = sorted.get((i + 1) % count);
            meta.add(new JunctionGeometryResult.FilletCorner(
                fillet.center(),
                fillet.tangentStart(),
                fillet.tangentEnd(),
                fillet.radius(),
                fillet.cornerAngle(),
                fillet.sweepRadians(),
                null,
                approachB.edgeId(),
                approachA.edgeId()));
        }
        double effective = meta.isEmpty() ? 0.0 : minEffective;
        return new JunctionGeometryResult(fill, sharp, boundaries, requested, effective, meta);
    }

    private static List<Approach> sortApproaches(List<Approach> approaches) {
        List<Approach> sorted = new ArrayList<>();
        for (Approach approach : approaches) {
            if (approach == null || approach.outward().lengthSquared() < 1e-12 || approach.halfWidth() <= 1e-9) {
                continue;
            }
            sorted.add(approach);
        }
        sorted.sort(Comparator.comparingDouble(a -> Math.atan2(a.outward().y, a.outward().x)));
        return sorted;
    }

    private static double maxHalfWidth(List<Approach> approaches) {
        double max = 0.0;
        for (Approach approach : approaches) {
            max = Math.max(max, approach.halfWidth());
        }
        return max;
    }

    private static List<Vec2d> mouthBoundaryPoints(Vec2d center, List<Approach> approaches, double plaza) {
        List<Vec2d> points = new ArrayList<>();
        for (Approach approach : approaches) {
            Vec2d mouth = center.add(approach.outward().multiply(plaza));
            Vec2d leftN = approach.leftNormal();
            points.add(mouth.add(leftN.multiply(approach.halfWidth())));
            points.add(mouth.subtract(leftN.multiply(approach.halfWidth())));
        }
        return points;
    }

    /**
     * 沿各进口嘴部与街角走一圈（CCW）。有圆角时在凹角插入外扩圆弧。
     */
    private static List<Vec2d> traceBoundary(
            Vec2d center,
            List<Approach> approaches,
            double plaza,
            List<CurbFillet> fillets,
            int arcSegments) {
        List<Vec2d> ring = new ArrayList<>();
        int count = approaches.size();
        for (int i = 0; i < count; i++) {
            Approach approach = approaches.get(i);
            Vec2d u = approach.outward();
            Vec2d leftN = approach.leftNormal();
            Vec2d mouth = center.add(u.multiply(plaza));
            Vec2d mouthLeft = mouth.add(leftN.multiply(approach.halfWidth()));
            Vec2d mouthRight = mouth.subtract(leftN.multiply(approach.halfWidth()));

            // 进口左侧街角（与上一条进口之间）
            CurbFillet leftCorner = fillets != null ? fillets.get((i - 1 + count) % count) : null;
            if (leftCorner != null) {
                appendArc(ring, leftCorner, arcSegments);
            } else {
                Vec2d sharp = curbIntersection(
                    center, approaches.get((i - 1 + count) % count), approach);
                if (sharp != null) {
                    ring.add(sharp);
                }
            }
            ring.add(mouthLeft);
            ring.add(mouthRight);
        }
        return finalizeRing(ring, center);
    }

    private static List<Vec2d> finalizeRing(List<Vec2d> ring, Vec2d center) {
        List<Vec2d> deduped = RoadJunctionGeometry.dedupeNearbyVertices(ring);
        if (deduped.size() < 3) {
            return deduped;
        }
        List<Vec2d> sorted = RoadJunctionGeometry.sortVerticesByPolarAngle(deduped, center);
        if (!RoadJunctionGeometry.isSimplePolygon(sorted)) {
            List<Vec2d> hull = RoadJunctionGeometry.convexHull(deduped);
            if (hull.size() >= 3) {
                sorted = RoadJunctionGeometry.sortVerticesByPolarAngle(hull, center);
            }
        }
        return sorted;
    }

    /** 相邻进口 A→B（CCW）之间的路缘交点：A 左侧路缘 × B 右侧路缘。 */
    static Vec2d curbIntersection(Vec2d center, Approach a, Approach b) {
        if (center == null || a == null || b == null) {
            return null;
        }
        Vec2d aPoint = center.add(a.leftNormal().multiply(a.halfWidth()));
        Vec2d bPoint = center.subtract(b.leftNormal().multiply(b.halfWidth()));
        return lineIntersection(aPoint, a.outward(), bPoint, b.outward());
    }

    /**
     * 凹形街角路缘圆角：圆心在铺装外侧，圆弧向街角扩展铺装。
     */
    static CurbFillet computeFillet(
            Vec2d center,
            Approach a,
            Approach b,
            double requestedRadius,
            double plaza) {
        Vec2d corner = curbIntersection(center, a, b);
        if (corner == null || requestedRadius < MIN_RADIUS) {
            return null;
        }
        // 街角外侧法向：与 JunctionRegionBuilder.streetCornerFillet 一致，
        // 草地楔形 φ = π − 法向夹角；不得一律用 π − 进口夹角（斜交会算反）。
        Vec2d outA = a.leftNormal();
        Vec2d outB = b.leftNormal().multiply(-1.0);
        double normalCos = clamp(outA.dot(outB), -1.0, 1.0);
        double normalAngle = Math.acos(normalCos);
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
        double mouthClearance = Math.max(0.75, plaza - Math.max(a.halfWidth(), b.halfWidth()) * 0.25);
        // 随请求 R 加长后退预算，避免锐角有效半径卡在 plaza 导出的平台期
        double setbackBudget = mouthClearance + Math.max(0.0, requestedRadius);
        // 后退 t = R/tan(φ/2) ≤ setbackBudget
        double radius = Math.min(requestedRadius, setbackBudget * tanHalf);
        if (radius < MIN_RADIUS) {
            return null;
        }

        Vec2d bisector = outA.add(outB);
        if (bisector.lengthSquared() < 1e-12) {
            return null;
        }
        bisector = bisector.normalize();
        Vec2d fromCorner = corner.subtract(center);
        if (fromCorner.lengthSquared() > 1e-12 && bisector.dot(fromCorner.normalize()) <= 0.0) {
            bisector = bisector.multiply(-1.0);
        }
        // 圆心在草地角平分线上，距角点 R/sin(φ/2)，保证到两路缘垂距为 R
        Vec2d filletCenter = corner.add(bisector.multiply(radius / sinHalf));
        if (filletCenter.distance(center) < corner.distance(center) - 1e-6) {
            return null;
        }
        if (filletCenter.subtract(corner).dot(outA) <= 0.0
                || filletCenter.subtract(corner).dot(outB) <= 0.0) {
            return null;
        }

        Vec2d tangentOnB = projectToLine(
            filletCenter,
            center.subtract(b.leftNormal().multiply(b.halfWidth())),
            b.outward());
        Vec2d tangentOnA = projectToLine(
            filletCenter,
            center.add(a.leftNormal().multiply(a.halfWidth())),
            a.outward());
        if (tangentOnB == null || tangentOnA == null) {
            return null;
        }
        if (Math.abs(filletCenter.distance(tangentOnB) - radius) > 0.2
                || Math.abs(filletCenter.distance(tangentOnA) - radius) > 0.2) {
            return null;
        }
        double sweep = FilletArcSweep.resolveShort(filletCenter, tangentOnB, tangentOnA);
        return new CurbFillet(filletCenter, tangentOnB, tangentOnA, radius, grassAngle, sweep);
    }

    private static void appendArc(List<Vec2d> out, CurbFillet fillet, int segments) {
        FilletArcSweep.ListSamples samples = FilletArcSweep.sample(
            fillet.center(),
            fillet.tangentStart(),
            fillet.tangentEnd(),
            fillet.radius(),
            fillet.sweepRadians(),
            segments);
        // sample 已含起终切点；边界环上起点可能已写入，跳过重复首点
        List<Vec2d> points = samples.points();
        for (int i = out.isEmpty() ? 0 : 1; i < points.size(); i++) {
            out.add(points.get(i));
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

    private static Vec2d projectToLine(Vec2d point, Vec2d linePoint, Vec2d lineDir) {
        if (lineDir.lengthSquared() < 1e-12) {
            return null;
        }
        Vec2d unit = lineDir.normalize();
        double t = point.subtract(linePoint).dot(unit);
        return linePoint.add(unit.multiply(t));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record CurbFillet(
            Vec2d center,
            Vec2d tangentStart,
            Vec2d tangentEnd,
            double radius,
            double cornerAngle,
            double sweepRadians) {
        CurbFillet {
            sweepRadians = Double.isFinite(sweepRadians) ? sweepRadians : 0.0;
        }
    }
}
