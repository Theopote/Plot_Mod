package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 道路几何工具（与 Shape / RoadGenerator 解耦的纯几何运算）
 */
public final class RoadGeometryUtils {
    /**
     * 认领样条时中心线简化容差（格）。越大点越少、曲线越“折”；
     * 生成阶段仍会按 pathSampleDistance 再细分，此处只减少路网存储/示意图上的冗余顶点。
     */
    public static final double ROAD_CENTERLINE_SIMPLIFY_EPSILON = 0.35;

    private RoadGeometryUtils() {
    }

    public static boolean isAdoptablePath(Shape shape) {
        return shape instanceof PolylineShape
            || shape instanceof FreeDrawPath
            || shape instanceof BezierCurveShape
            || shape instanceof LineShape;
    }

    public static Shape findFirstAdoptablePath(List<Shape> shapes) {
        List<Shape> paths = findAdoptablePaths(shapes);
        return paths.isEmpty() ? null : paths.getFirst();
    }

    public static List<Shape> findAdoptablePaths(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<Shape> paths = new ArrayList<>();
        for (Shape shape : shapes) {
            if (isAdoptablePath(shape)) {
                paths.add(shape);
            }
        }
        return paths;
    }

    public static List<Vec2d> extractShapePoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        if (shape instanceof PolylineShape || shape instanceof FreeDrawPath) {
            return shape.getPoints();
        }
        if (shape instanceof BezierCurveShape bezier) {
            return sampleBezierCurve(bezier);
        }
        List<Vec2d> endpoints = shape.getEndpoints();
        return endpoints != null ? new ArrayList<>(endpoints) : List.of();
    }

    /**
     * 将样条离散为道路中心线折点。
     * <p>
     * 路网数据模型只存折线中心线（不存贝塞尔控制点），因此认领时必须离散。
     * 画布侧 {@link BezierCurveShape#getCurvePoints()} 为渲染用密采样（每段可达 8–64 点），
     * 直接入库会让示意图看起来像“一条曲线被切成很多小段”。此处先密采样保证贴合曲线，
     * 再 RDP 简化 + 最大间距补点，使拓扑上仍是<strong>一条边</strong>，顶点数量适合道路用途。
     */
    public static List<Vec2d> sampleBezierCurve(BezierCurveShape curve) {
        if (curve == null) {
            return List.of();
        }
        List<Vec2d> dense = curve.getCurvePoints();
        if (dense.size() < 2) {
            return new ArrayList<>(dense);
        }
        // 只简化、不再二次密化：生成道路时 RoadGenerator.samplePath 会按配置间距细分
        return simplifyPolyline(dense, ROAD_CENTERLINE_SIMPLIFY_EPSILON);
    }

    /**
     * Douglas–Peucker 折线简化：保留端点，去掉偏离弦不超过 {@code epsilon} 的中间点。
     */
    public static List<Vec2d> simplifyPolyline(List<Vec2d> points, double epsilon) {
        if (points == null || points.size() < 3 || epsilon <= 0) {
            return points == null ? List.of() : new ArrayList<>(points);
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyPolylineRecursive(points, 0, points.size() - 1, epsilon, keep);
        List<Vec2d> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i).copy());
            }
        }
        return result;
    }

    private static void simplifyPolylineRecursive(
            List<Vec2d> points, int start, int end, double epsilon, boolean[] keep) {
        if (end <= start + 1) {
            return;
        }
        Vec2d a = points.get(start);
        Vec2d b = points.get(end);
        double maxDist = -1;
        int maxIndex = -1;
        for (int i = start + 1; i < end; i++) {
            double dist = pointToSegmentDistance(points.get(i), a, b);
            if (dist > maxDist) {
                maxDist = dist;
                maxIndex = i;
            }
        }
        if (maxDist > epsilon && maxIndex >= 0) {
            keep[maxIndex] = true;
            simplifyPolylineRecursive(points, start, maxIndex, epsilon, keep);
            simplifyPolylineRecursive(points, maxIndex, end, epsilon, keep);
        }
    }

    private static double pointToSegmentDistance(Vec2d p, Vec2d a, Vec2d b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-18) {
            return p.distance(a);
        }
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = a.x + t * dx;
        double projY = a.y + t * dy;
        double ex = p.x - projX;
        double ey = p.y - projY;
        return Math.sqrt(ex * ex + ey * ey);
    }

    /**
     * 若相邻点间距超过 {@code maxSpacing}，在中间线性插值补点。
     */
    public static List<Vec2d> densifyPolyline(List<Vec2d> points, double maxSpacing) {
        if (points == null || points.size() < 2 || maxSpacing <= 0) {
            return points == null ? List.of() : new ArrayList<>(points);
        }
        List<Vec2d> result = new ArrayList<>();
        result.add(points.getFirst().copy());
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double dist = a.distance(b);
            if (dist > maxSpacing) {
                int steps = (int) Math.ceil(dist / maxSpacing);
                for (int s = 1; s < steps; s++) {
                    double t = (double) s / steps;
                    result.add(a.lerp(b, t));
                }
            }
            result.add(b.copy());
        }
        return result;
    }

    public static double calculatePathLength(List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distance(points.get(i + 1));
        }
        return total;
    }

    /**
     * 沿路径按弧长等间距采样
     */
    public static List<Vec2d> sampleAlongPath(List<Vec2d> pathPoints, double spacing, double skipNearEndsDistance) {
        List<Vec2d> result = new ArrayList<>();
        if (pathPoints == null || pathPoints.size() < 2 || spacing <= 0) {
            return result;
        }

        double totalLength = calculatePathLength(pathPoints);
        if (totalLength <= 2 * skipNearEndsDistance) {
            return result;
        }

        double distance = skipNearEndsDistance;
        while (distance <= totalLength - skipNearEndsDistance) {
            Vec2d point = pointAtDistance(pathPoints, distance);
            if (point != null) {
                result.add(point);
            }
            distance += spacing;
        }
        return result;
    }

    public static Vec2d pointAtDistance(List<Vec2d> points, double targetDistance) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (targetDistance <= 0) {
            return points.getFirst().copy();
        }

        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            double segmentLength = start.distance(end);
            if (segmentLength < 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= targetDistance) {
                double t = (targetDistance - accumulated) / segmentLength;
                return start.lerp(end, t);
            }
            accumulated += segmentLength;
        }
        return points.getLast().copy();
    }

    /**
     * 按归一化弧长（0~1）在折线上插值取点
     */
    public static Vec2d interpolatePolylineByNormalizedDistance(List<Vec2d> polyline, double normalizedDistance) {
        if (polyline == null || polyline.isEmpty()) {
            return new Vec2d(0, 0);
        }
        if (polyline.size() == 1) {
            return polyline.getFirst().copy();
        }
        double clamped = Math.max(0.0, Math.min(1.0, normalizedDistance));
        double totalLength = calculatePathLength(polyline);
        if (totalLength <= 1e-9) {
            return polyline.getFirst().copy();
        }
        Vec2d point = pointAtDistance(polyline, clamped * totalLength);
        return point != null ? point : polyline.getLast().copy();
    }

    /**
     * 在折线上找到距给定点最近的索引位置（用于打断）
     */
    public static int findNearestSegmentIndex(List<Vec2d> points, Vec2d target) {
        if (points == null || points.size() < 2 || target == null) {
            return -1;
        }

        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            Vec2d projected = projectPointOnSegment(start, end, target);
            double distance = projected.distance(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * 路径切向量的左法向（单位向量，用于横断面采样）
     */
    public static Vec2d leftNormal(Vec2d direction) {
        if (direction == null || direction.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = direction.normalize();
        return new Vec2d(-unit.y, unit.x);
    }

    /**
     * 横断面采样偏移量（沿法向，单位：米/格），覆盖 [-halfWidth, +halfWidth]
     */
    public static List<Integer> crossSectionSampleOffsets(double halfWidth) {
        if (halfWidth <= 0) {
            return List.of(0);
        }

        LinkedHashSet<Integer> offsets = new LinkedHashSet<>();
        offsets.add(0);
        offsets.add((int) Math.round(-halfWidth));
        offsets.add((int) Math.round(halfWidth));
        int min = (int) Math.floor(-halfWidth);
        int max = (int) Math.ceil(halfWidth);
        for (int offset = min; offset <= max; offset++) {
            offsets.add(offset);
        }
        return new ArrayList<>(offsets);
    }

    public static Vec2d projectPointOnSegment(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d direction = end.subtract(start);
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < 1e-12) {
            return start.copy();
        }
        double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(direction) / lengthSquared));
        return start.lerp(end, t);
    }

    public static List<List<Vec2d>> splitPolylineAt(List<Vec2d> points, Vec2d splitPoint, double tolerance) {
        if (points == null || points.size() < 2 || splitPoint == null) {
            return singlePart(points);
        }

        if (splitPoint.distance(points.getFirst()) <= tolerance) {
            return singlePart(points);
        }
        if (splitPoint.distance(points.getLast()) <= tolerance) {
            return singlePart(points);
        }

        int segmentIndex = findNearestSegmentIndex(points, splitPoint);
        if (segmentIndex < 0) {
            return singlePart(points);
        }

        Vec2d projected = projectPointOnSegment(
            points.get(segmentIndex),
            points.get(segmentIndex + 1),
            splitPoint
        );

        List<Vec2d> firstPart = new ArrayList<>();
        for (int i = 0; i <= segmentIndex; i++) {
            firstPart.add(points.get(i).copy());
        }
        firstPart.add(projected);

        List<Vec2d> secondPart = new ArrayList<>();
        secondPart.add(projected);
        for (int i = segmentIndex + 1; i < points.size(); i++) {
            secondPart.add(points.get(i).copy());
        }

        List<List<Vec2d>> parts = new ArrayList<>(2);
        parts.add(firstPart);
        parts.add(secondPart);
        return parts;
    }

    private static List<List<Vec2d>> singlePart(List<Vec2d> points) {
        List<List<Vec2d>> parts = new ArrayList<>(1);
        parts.add(copyPoints(points));
        return parts;
    }

    public static boolean pointsNear(Vec2d a, Vec2d b, double tolerance) {
        return a != null && b != null && a.distance(b) <= tolerance;
    }

    /**
     * 将可认领路径按首尾相连关系分组，每组对应一次认领（一条 roadId）。
     * 简单链（无分叉）合并为一条折线；分叉或交汇处各段保持独立。
     */
    public static List<List<Vec2d>> groupConnectedPathsForAdoption(List<Shape> shapes) {
        return groupConnectedPathsForAdoption(shapes, RoadNetworkBuilder.NODE_TOLERANCE);
    }

    public static List<List<Vec2d>> groupConnectedPathsForAdoption(List<Shape> shapes, double tolerance) {
        List<PathFragment> fragments = buildAdoptableFragments(shapes);
        if (fragments.isEmpty()) {
            return List.of();
        }
        if (fragments.size() == 1) {
            return List.of(copyPoints(fragments.getFirst().points));
        }

        assignEndpointClusters(fragments, tolerance);
        List<Set<Integer>> components = buildFragmentComponents(fragments);
        List<List<Vec2d>> groups = new ArrayList<>();
        for (Set<Integer> component : components) {
            Map<Integer, Integer> clusterDegrees = clusterDegrees(component, fragments);
            if (component.size() == 1 || !isSimplePath(clusterDegrees)) {
                for (int index : sortedIndices(component)) {
                    groups.add(copyPoints(fragments.get(index).points));
                }
            } else {
                groups.add(mergeSimpleChain(component, fragments, clusterDegrees));
            }
        }
        return groups;
    }

    private static final class PathFragment {
        final List<Vec2d> points;
        int startCluster = -1;
        int endCluster = -1;

        PathFragment(List<Vec2d> points) {
            this.points = points;
        }
    }

    private static List<PathFragment> buildAdoptableFragments(List<Shape> shapes) {
        List<PathFragment> fragments = new ArrayList<>();
        if (shapes == null) {
            return fragments;
        }
        for (Shape shape : shapes) {
            if (!isAdoptablePath(shape)) {
                continue;
            }
            List<Vec2d> points = extractShapePoints(shape);
            if (points.size() < 2) {
                continue;
            }
            fragments.add(new PathFragment(points));
        }
        return fragments;
    }

    private static void assignEndpointClusters(List<PathFragment> fragments, double tolerance) {
        List<Vec2d> clusterReps = new ArrayList<>();
        for (PathFragment fragment : fragments) {
            fragment.startCluster = findOrCreateCluster(fragment.points.getFirst(), clusterReps, tolerance);
            fragment.endCluster = findOrCreateCluster(fragment.points.getLast(), clusterReps, tolerance);
        }
    }

    private static int findOrCreateCluster(Vec2d point, List<Vec2d> clusterReps, double tolerance) {
        for (int i = 0; i < clusterReps.size(); i++) {
            if (pointsNear(point, clusterReps.get(i), tolerance)) {
                return i;
            }
        }
        clusterReps.add(point.copy());
        return clusterReps.size() - 1;
    }

    private static List<Set<Integer>> buildFragmentComponents(List<PathFragment> fragments) {
        int count = fragments.size();
        int[] parent = new int[count];
        for (int i = 0; i < count; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (fragmentsShareCluster(fragments.get(i), fragments.get(j))) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, Set<Integer>> grouped = new HashMap<>();
        for (int i = 0; i < count; i++) {
            grouped.computeIfAbsent(find(parent, i), ignored -> new LinkedHashSet<>()).add(i);
        }
        return new ArrayList<>(grouped.values());
    }

    private static boolean fragmentsShareCluster(PathFragment left, PathFragment right) {
        return left.startCluster == right.startCluster
            || left.startCluster == right.endCluster
            || left.endCluster == right.startCluster
            || left.endCluster == right.endCluster;
    }

    private static int find(int[] parent, int index) {
        if (parent[index] != index) {
            parent[index] = find(parent, parent[index]);
        }
        return parent[index];
    }

    private static void union(int[] parent, int left, int right) {
        int rootLeft = find(parent, left);
        int rootRight = find(parent, right);
        if (rootLeft != rootRight) {
            parent[rootRight] = rootLeft;
        }
    }

    private static Map<Integer, Integer> clusterDegrees(Set<Integer> component, List<PathFragment> fragments) {
        Map<Integer, Integer> degrees = new HashMap<>();
        for (int index : component) {
            PathFragment fragment = fragments.get(index);
            degrees.merge(fragment.startCluster, 1, Integer::sum);
            degrees.merge(fragment.endCluster, 1, Integer::sum);
        }
        return degrees;
    }

    private static boolean isSimplePath(Map<Integer, Integer> clusterDegrees) {
        for (int degree : clusterDegrees.values()) {
            if (degree > 2) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> sortedIndices(Set<Integer> component) {
        List<Integer> indices = new ArrayList<>(component);
        indices.sort(Integer::compareTo);
        return indices;
    }

    private static List<Vec2d> mergeSimpleChain(
        Set<Integer> component,
        List<PathFragment> fragments,
        Map<Integer, Integer> clusterDegrees
    ) {
        Integer terminalCluster = null;
        for (Map.Entry<Integer, Integer> entry : clusterDegrees.entrySet()) {
            if (entry.getValue() == 1) {
                terminalCluster = entry.getKey();
                break;
            }
        }

        int startFragment = -1;
        boolean forward = true;
        if (terminalCluster != null) {
            for (int index : component) {
                PathFragment fragment = fragments.get(index);
                if (fragment.startCluster == terminalCluster) {
                    startFragment = index;
                    forward = true;
                    break;
                }
                if (fragment.endCluster == terminalCluster) {
                    startFragment = index;
                    forward = false;
                    break;
                }
            }
        } else {
            startFragment = sortedIndices(component).getFirst();
        }

        Set<Integer> remaining = new HashSet<>(component);
        List<Vec2d> merged = new ArrayList<>();
        int currentFragment = startFragment;
        boolean currentForward = forward;
        while (currentFragment >= 0) {
            PathFragment fragment = fragments.get(currentFragment);
            List<Vec2d> oriented = currentForward
                ? fragment.points
                : reverseCopy(fragment.points);
            appendJoined(merged, oriented, RoadNetworkBuilder.NODE_TOLERANCE);
            remaining.remove(currentFragment);

            int exitCluster = currentForward ? fragment.endCluster : fragment.startCluster;
            int nextFragment = -1;
            boolean nextForward = true;
            for (int candidate : remaining) {
                PathFragment next = fragments.get(candidate);
                if (next.startCluster == exitCluster) {
                    nextFragment = candidate;
                    nextForward = true;
                    break;
                }
                if (next.endCluster == exitCluster) {
                    nextFragment = candidate;
                    nextForward = false;
                    break;
                }
            }
            currentFragment = nextFragment;
            currentForward = nextForward;
        }
        return merged;
    }

    private static void appendJoined(List<Vec2d> target, List<Vec2d> segment, double tolerance) {
        if (segment.isEmpty()) {
            return;
        }
        int startIndex = 0;
        if (!target.isEmpty() && pointsNear(target.getLast(), segment.getFirst(), tolerance)) {
            startIndex = 1;
        }
        for (int i = startIndex; i < segment.size(); i++) {
            target.add(segment.get(i).copy());
        }
    }

    private static List<Vec2d> reverseCopy(List<Vec2d> points) {
        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i).copy());
        }
        return reversed;
    }

    /**
     * 从折线某一端出发，沿弧长前进指定距离
     */
    public static Vec2d pointAlongPolylineFrom(Vec2d from, List<Vec2d> points, double maxDistance) {
        if (from == null || points == null || points.size() < 2 || maxDistance <= 0) {
            return from != null ? from.copy() : new Vec2d(0, 0);
        }

        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double segLen = a.distance(b);
            if (segLen < 1e-9) {
                continue;
            }
            if (accumulated + segLen >= maxDistance) {
                double t = (maxDistance - accumulated) / segLen;
                return a.lerp(b, t);
            }
            accumulated += segLen;
        }
        return points.getLast().copy();
    }

    public static boolean pointInPolygon(Vec2d point, List<Vec2d> polygon) {
        if (point == null || polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);
            boolean intersect = ((pi.y > point.y) != (pj.y > point.y))
                && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 1e-9) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 画布坐标转 Minecraft 方块 XZ（Y 由调用方指定）
     */
    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, CoordinateTransformer transformer) {
        if (canvasPos == null) {
            return BlockPos.ORIGIN;
        }
        if (transformer != null) {
            Vec2d worldPos = transformer.canvasToMinecraftWorld(canvasPos);
            if (worldPos != null) {
                return new BlockPos(
                    (int) Math.round(worldPos.x),
                    0,
                    (int) Math.round(worldPos.y));
            }
        }
        return new BlockPos(
            (int) Math.round(canvasPos.x),
            0,
            (int) Math.round(canvasPos.y));
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point.copy());
            }
        }
        return copy;
    }
}
