package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 绘制过程中的几何快照，用于撤销/重做。
 */
public final class DrawingGeometrySnapshot {
    public enum Kind {
        POLYLINE,
        PEN,
        BEZIER_EDIT
    }

    public record PathNodeSnapshot(
            double anchorX, double anchorY,
            double controlPrevX, double controlPrevY,
            double controlNextX, double controlNextY,
            PathNode.NodeType type) {
        static PathNodeSnapshot from(PathNode node) {
            Vec2d anchor = node.getAnchor();
            Vec2d prev = node.getControlPrev();
            Vec2d next = node.getControlNext();
            return new PathNodeSnapshot(
                    anchor.x, anchor.y,
                    prev.x, prev.y,
                    next.x, next.y,
                    node.getType());
        }

        PathNode toPathNode() {
            PathNode node = new PathNode(new Vec2d(anchorX, anchorY));
            node.restoreState(
                    new Vec2d(anchorX, anchorY),
                    new Vec2d(controlPrevX, controlPrevY),
                    new Vec2d(controlNextX, controlNextY),
                    type);
            return node;
        }
    }

    private final Kind kind;
    private final List<Vec2d> points;
    private final List<PathNodeSnapshot> pathNodes;
    private final List<Boolean> curveSegments;

    private DrawingGeometrySnapshot(
            Kind kind,
            List<Vec2d> points,
            List<PathNodeSnapshot> pathNodes,
            List<Boolean> curveSegments) {
        this.kind = kind;
        this.points = copyPoints(points);
        this.pathNodes = pathNodes == null ? List.of() : List.copyOf(pathNodes);
        this.curveSegments = curveSegments == null ? List.of() : List.copyOf(curveSegments);
    }

    public static DrawingGeometrySnapshot polyline(List<Vec2d> points) {
        return new DrawingGeometrySnapshot(Kind.POLYLINE, points, List.of(), List.of());
    }

    public static DrawingGeometrySnapshot pen(List<PathNode> nodes) {
        List<PathNodeSnapshot> snapshots = new ArrayList<>();
        if (nodes != null) {
            for (PathNode node : nodes) {
                snapshots.add(PathNodeSnapshot.from(node));
            }
        }
        return new DrawingGeometrySnapshot(Kind.PEN, List.of(), snapshots, List.of());
    }

    public static DrawingGeometrySnapshot bezierEdit(List<Vec2d> points, List<Boolean> curveSegments) {
        return new DrawingGeometrySnapshot(Kind.BEZIER_EDIT, points, List.of(), curveSegments);
    }

    public Kind getKind() {
        return kind;
    }

    public List<Vec2d> getPoints() {
        return copyPoints(points);
    }

    public List<PathNodeSnapshot> getPathNodes() {
        return pathNodes;
    }

    public List<Boolean> getCurveSegments() {
        return curveSegments;
    }

    public boolean sameGeometryAs(DrawingGeometrySnapshot other) {
        if (other == null || kind != other.kind) {
            return false;
        }
        return switch (kind) {
            case POLYLINE, BEZIER_EDIT -> pointsEqual(points, other.points)
                    && (kind != Kind.BEZIER_EDIT || curveSegments.equals(other.curveSegments));
            case PEN -> pathNodesEqual(pathNodes, other.pathNodes);
        };
    }

    private static boolean pointsEqual(List<Vec2d> a, List<Vec2d> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Vec2d p1 = a.get(i);
            Vec2d p2 = b.get(i);
            if (p1 == null || p2 == null) {
                if (p1 != p2) {
                    return false;
                }
                continue;
            }
            if (p1.distance(p2) > 1e-6) {
                return false;
            }
        }
        return true;
    }

    private static boolean pathNodesEqual(List<PathNodeSnapshot> a, List<PathNodeSnapshot> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Vec2d> copies = new ArrayList<>(source.size());
        for (Vec2d point : source) {
            copies.add(point == null ? null : new Vec2d(point.x, point.y));
        }
        return List.copyOf(copies);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DrawingGeometrySnapshot other && sameGeometryAs(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, points, pathNodes, curveSegments);
    }
}
