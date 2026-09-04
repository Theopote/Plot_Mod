package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Straight Skeleton / 距离场屋顶的测试不变量（拓扑 + 高度场）。
 */
final class StraightSkeletonInvariants {
    private StraightSkeletonInvariants() {
    }

    static void assertSuccessful(StraightSkeleton.Result skeleton) {
        if (!skeleton.success()) {
            throw new AssertionError("skeleton.success expected true");
        }
        if (skeleton.polygon().size() < 3) {
            throw new AssertionError("normalized polygon too small");
        }
        if (skeleton.maxSkeletalTime() <= 0.0) {
            throw new AssertionError("maxSkeletalTime must be > 0");
        }
        if (skeleton.nodes().isEmpty()) {
            throw new AssertionError("expected at least one skeleton node");
        }
    }

    /** 节点在多边形内；边端点合法；无自环。 */
    static void assertTopology(StraightSkeleton.Result skeleton) {
        assertSuccessful(skeleton);
        Map<Integer, StraightSkeleton.SkeletonNode> byId = new HashMap<>();
        for (StraightSkeleton.SkeletonNode node : skeleton.nodes()) {
            if (byId.put(node.id(), node) != null) {
                throw new AssertionError("duplicate node id " + node.id());
            }
            if (!PolygonBoolean.contains(skeleton.polygon(), node.point())) {
                throw new AssertionError("node outside polygon: " + node.point());
            }
            if (node.time() <= 0.0) {
                throw new AssertionError("node time must be > 0: " + node);
            }
            double expected = skeleton.skeletalTime(node.point());
            if (Math.abs(expected - node.time()) > 0.75) {
                throw new AssertionError("node time disagrees with distance field: "
                    + node.time() + " vs " + expected);
            }
        }
        for (StraightSkeleton.SkeletonEdge edge : skeleton.edges()) {
            if (edge.startId() == edge.endId()) {
                throw new AssertionError("self-loop edge " + edge);
            }
            if (!byId.containsKey(edge.startId()) || !byId.containsKey(edge.endId())) {
                throw new AssertionError("edge references missing node: " + edge);
            }
        }
    }

    /**
     * 屋脊连通：若存在多个节点且彼此距离 ≤ {@code connectRadius}，
     * 则由 edges 诱导的图应对这些节点连通（无孤立柱）。
     */
    static void assertRidgeConnected(StraightSkeleton.Result skeleton, double connectRadius) {
        assertTopology(skeleton);
        List<StraightSkeleton.SkeletonNode> nodes = skeleton.nodes();
        if (nodes.size() <= 1) {
            return;
        }

        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (StraightSkeleton.SkeletonNode node : nodes) {
            adj.put(node.id(), new HashSet<>());
        }
        for (StraightSkeleton.SkeletonEdge edge : skeleton.edges()) {
            adj.get(edge.startId()).add(edge.endId());
            adj.get(edge.endId()).add(edge.startId());
        }

        // 只要求「在 connectRadius 内有邻居」的节点进入同一连通分量
        List<Integer> clustered = new ArrayList<>();
        for (StraightSkeleton.SkeletonNode a : nodes) {
            boolean hasNeighbor = false;
            for (StraightSkeleton.SkeletonNode b : nodes) {
                if (a.id() == b.id()) {
                    continue;
                }
                if (a.point().distance(b.point()) <= connectRadius) {
                    hasNeighbor = true;
                    break;
                }
            }
            if (hasNeighbor) {
                clustered.add(a.id());
            }
        }
        if (clustered.size() <= 1) {
            return;
        }

        int start = clustered.getFirst();
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : adj.getOrDefault(current, Set.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (int id : clustered) {
            if (!visited.contains(id)) {
                throw new AssertionError("ridge graph disconnected: node " + id
                    + " isolated while within " + connectRadius + " of another node");
            }
        }
    }

    /**
     * 距离场 1-Lipschitz：|t(a)-t(b)| ≤ |a-b| + ε（禁止反坡突变）。
     */
    static void assertDistanceFieldLipschitz(StraightSkeleton.Result skeleton, double cellStep) {
        assertSuccessful(skeleton);
        List<Vec2d> cells = PolygonRasterizer.collectCellCenters(skeleton.polygon());
        double neighborLimit = cellStep * 1.5;
        for (int i = 0; i < cells.size(); i++) {
            Vec2d a = cells.get(i);
            if (!PolygonBoolean.contains(skeleton.polygon(), a)) {
                continue;
            }
            double ta = skeleton.skeletalTime(a);
            for (int j = i + 1; j < cells.size(); j++) {
                Vec2d b = cells.get(j);
                double dist = a.distance(b);
                if (dist > neighborLimit || dist < 1e-9) {
                    continue;
                }
                if (!PolygonBoolean.contains(skeleton.polygon(), b)) {
                    continue;
                }
                double tb = skeleton.skeletalTime(b);
                double limit = dist + 0.05;
                if (Math.abs(ta - tb) > limit) {
                    throw new AssertionError("reverse/steep slope between " + a + " and " + b
                        + ": |Δt|=" + Math.abs(ta - tb) + " > " + limit);
                }
            }
        }
    }

    /**
     * L / 凹角：靠近凹角内侧的 skeletal time 应明显低于宽臂中心（谷线区域更低）。
     */
    static void assertValleyLowerThanArmCenters(
            StraightSkeleton.Result skeleton,
            Vec2d valleyProbe,
            Vec2d armA,
            Vec2d armB) {
        assertSuccessful(skeleton);
        double tv = skeleton.skeletalTime(valleyProbe);
        double ta = skeleton.skeletalTime(armA);
        double tb = skeleton.skeletalTime(armB);
        if (tv >= ta - 0.25 || tv >= tb - 0.25) {
            throw new AssertionError("valley not lower than arms: valley=" + tv
                + " armA=" + ta + " armB=" + tb);
        }
    }

    /** 旋转后 maxTime 与对应点 time 一致。 */
    static void assertRotationConsistent(List<Vec2d> polygon, double degrees) {
        StraightSkeleton.Result original = StraightSkeleton.compute(polygon);
        assertSuccessful(original);
        List<Vec2d> rotated = rotate(polygon, degrees);
        StraightSkeleton.Result after = StraightSkeleton.compute(rotated);
        assertSuccessful(after);
        if (Math.abs(original.maxSkeletalTime() - after.maxSkeletalTime()) > 0.75) {
            throw new AssertionError("maxTime changed under rotation: "
                + original.maxSkeletalTime() + " vs " + after.maxSkeletalTime());
        }
        Vec2d probe = interiorProbe(original);
        Vec2d rotatedProbe = rotatePoint(probe, centroid(polygon), degrees);
        double t0 = original.skeletalTime(probe);
        double t1 = after.skeletalTime(rotatedProbe);
        if (Math.abs(t0 - t1) > 0.85) {
            throw new AssertionError("skeletalTime changed under rotation at probe: "
                + t0 + " vs " + t1);
        }
    }

    /** CW / CCW 输入应得到一致的距离场。 */
    static void assertWindingConsistent(List<Vec2d> polygon) {
        StraightSkeleton.Result ccw = StraightSkeleton.compute(polygon);
        List<Vec2d> reversed = new ArrayList<>(polygon.size());
        for (int i = polygon.size() - 1; i >= 0; i--) {
            reversed.add(polygon.get(i).copy());
        }
        StraightSkeleton.Result cw = StraightSkeleton.compute(reversed);
        assertSuccessful(ccw);
        assertSuccessful(cw);
        if (Math.abs(ccw.maxSkeletalTime() - cw.maxSkeletalTime()) > 1e-6) {
            throw new AssertionError("maxTime differs by winding: "
                + ccw.maxSkeletalTime() + " vs " + cw.maxSkeletalTime());
        }
        Vec2d probe = interiorProbe(ccw);
        if (Math.abs(ccw.skeletalTime(probe) - cw.skeletalTime(probe)) > 1e-6) {
            throw new AssertionError("skeletalTime differs by winding at " + probe);
        }
    }

    /**
     * HIP 高度场：rise = floor(time/pitch)；无孤立柱（rise 不高于邻域 max+1）；
     * 高区 4-连通且无「被包围的 rise=0 洞」。
     */
    static void assertHipHeightFieldInvariants(List<Vec2d> polygon, int pitch) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(polygon);
        assertSuccessful(skeleton);
        int p = Math.max(1, pitch);
        List<Vec2d> cells = PolygonRasterizer.collectCellCenters(skeleton.polygon());
        Map<Long, Integer> riseByKey = new HashMap<>();
        Map<Long, Vec2d> cellByKey = new HashMap<>();
        for (Vec2d cell : cells) {
            if (!PolygonBoolean.contains(skeleton.polygon(), cell)) {
                continue;
            }
            int rise = (int) Math.floor(skeleton.skeletalTime(cell) / p);
            long key = cellKey(cell);
            riseByKey.put(key, rise);
            cellByKey.put(key, cell);
        }
        if (riseByKey.isEmpty()) {
            throw new AssertionError("no interior roof cells");
        }

        int maxRise = riseByKey.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (skeleton.maxSkeletalTime() < p) {
            // 窄足迹：坡屋顶本应降级；高度场全 0 即可
            if (maxRise != 0) {
                throw new AssertionError("expected flat height field when maxTime < pitch");
            }
            return;
        }
        if (maxRise < 1) {
            throw new AssertionError("expected positive hip rise somewhere");
        }

        // 无孤立柱：任意 rise>0 格，其 4-邻至少有一个 rise >= rise-1
        for (Map.Entry<Long, Integer> entry : riseByKey.entrySet()) {
            int rise = entry.getValue();
            if (rise <= 0) {
                continue;
            }
            Vec2d cell = cellByKey.get(entry.getKey());
            int bestNeighbor = -1;
            for (Vec2d n : orthogonalNeighbors(cell)) {
                Integer nr = riseByKey.get(cellKey(n));
                if (nr != null) {
                    bestNeighbor = Math.max(bestNeighbor, nr);
                }
            }
            if (bestNeighbor >= 0 && rise > bestNeighbor + 1) {
                throw new AssertionError("isolated pillar at " + cell
                    + " rise=" + rise + " neighborMax=" + bestNeighbor);
            }
        }

        // 无屋顶孔洞：rise==0 的内部格不应被 rise>0 的 4-邻完全包围（且自身不在边界）
        for (Map.Entry<Long, Integer> entry : riseByKey.entrySet()) {
            if (entry.getValue() != 0) {
                continue;
            }
            Vec2d cell = cellByKey.get(entry.getKey());
            int highNeighbors = 0;
            int knownNeighbors = 0;
            for (Vec2d n : orthogonalNeighbors(cell)) {
                Integer nr = riseByKey.get(cellKey(n));
                if (nr == null) {
                    continue;
                }
                knownNeighbors++;
                if (nr > 0) {
                    highNeighbors++;
                }
            }
            if (knownNeighbors == 4 && highNeighbors == 4) {
                throw new AssertionError("roof hole (rise=0 enclosed by roof) at " + cell);
            }
        }
    }

    private static Vec2d interiorProbe(StraightSkeleton.Result skeleton) {
        Vec2d centroid = centroid(skeleton.polygon());
        if (PolygonBoolean.contains(skeleton.polygon(), centroid)) {
            return centroid;
        }
        return skeleton.nodes().getFirst().point();
    }

    private static Vec2d centroid(List<Vec2d> polygon) {
        double sx = 0;
        double sy = 0;
        for (Vec2d p : polygon) {
            sx += p.x;
            sy += p.y;
        }
        return new Vec2d(sx / polygon.size(), sy / polygon.size());
    }

    private static List<Vec2d> rotate(List<Vec2d> polygon, double degrees) {
        Vec2d c = centroid(polygon);
        List<Vec2d> out = new ArrayList<>(polygon.size());
        for (Vec2d p : polygon) {
            out.add(rotatePoint(p, c, degrees));
        }
        return out;
    }

    private static Vec2d rotatePoint(Vec2d point, Vec2d center, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        return new Vec2d(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos);
    }

    private static long cellKey(Vec2d cell) {
        int x = (int) Math.floor(cell.x);
        int y = (int) Math.floor(cell.y);
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static List<Vec2d> orthogonalNeighbors(Vec2d cell) {
        return List.of(
            new Vec2d(cell.x + 1, cell.y),
            new Vec2d(cell.x - 1, cell.y),
            new Vec2d(cell.x, cell.y + 1),
            new Vec2d(cell.x, cell.y - 1)
        );
    }
}
