package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.geometry.BoundingBox;

import java.util.*;

/**
 * 吸附计算专用的 R-tree 空间索引。
 * 与 {@link com.plot.core.spatial.SpatialIndex} 不同，仅服务于 SnapCalculator 的邻近查询。
 */
public class SnapSpatialIndex {
    private static final int MAX_ENTRIES = 16;
    private final Node root;

    public SnapSpatialIndex() {
        root = new Node(null);
    }

    public void insert(Shape shape) {
        BoundingBox bounds = shape.getBoundingBox();
        if (bounds == null) return;

        root.insert(new Entry(bounds, shape));
    }

    public List<Shape> queryNearby(Vec2d point, double radius) {
        List<Shape> result = new ArrayList<>();
        BoundingBox searchBox = new BoundingBox(
                new Vec2d(point.x - radius, point.y - radius),
                new Vec2d(point.x + radius, point.y + radius)
        );
        root.search(searchBox, result);
        return result;
    }

    private static class Node {
        final int level;
        final List<Entry> entries = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        BoundingBox bounds;

        Node(Node parent) {
            this.level = parent == null ? 0 : parent.level + 1;
            this.bounds = new BoundingBox(
                new Vec2d(Double.MAX_VALUE, Double.MAX_VALUE),
                new Vec2d(Double.MIN_VALUE, Double.MIN_VALUE)
            );
        }

        void insert(Entry entry) {
            if (level == 0) {
                entries.add(entry);
                bounds = bounds.union(entry.bounds);
                if (entries.size() > MAX_ENTRIES) {
                    splitNode();
                }
            } else {
                Node child = chooseSubtree(entry.bounds);
                child.insert(entry);
                bounds = bounds.union(entry.bounds);
            }
        }

        Node chooseSubtree(BoundingBox entryBounds) {
            if (children.isEmpty()) {
                children.add(new Node(this));
            }

            Node bestChild = null;
            double minEnlargement = Double.MAX_VALUE;

            for (Node child : children) {
                double currentArea = child.bounds.getWidth() * child.bounds.getHeight();
                BoundingBox unionBox = child.bounds.union(entryBounds);
                double newArea = unionBox.getWidth() * unionBox.getHeight();
                double enlargement = newArea - currentArea;
                if (enlargement < minEnlargement) {
                    minEnlargement = enlargement;
                    bestChild = child;
                }
            }

            return bestChild;
        }

        void search(BoundingBox searchBox, List<Shape> result) {
            if (!bounds.intersects(searchBox)) {
                return;
            }

            if (level == 0) {
                for (Entry entry : entries) {
                    if (entry.bounds.intersects(searchBox)) {
                        result.add(entry.shape);
                    }
                }
            } else {
                for (Node child : children) {
                    child.search(searchBox, result);
                }
            }
        }

        void splitNode() {
            if (level == 0 && entries.size() > MAX_ENTRIES) {
                Vec2d boxCenter = bounds.getCenter();
                double midX = boxCenter.x;
                double midY = boxCenter.y;

                List<Node> newChildren = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    newChildren.add(new Node(this));
                }

                List<Entry> oldEntries = new ArrayList<>(entries);
                entries.clear();

                for (Entry entry : oldEntries) {
                    Vec2d entryCenter = entry.bounds.getCenter();
                    int index = (entryCenter.x > midX ? 1 : 0) + (entryCenter.y > midY ? 2 : 0);
                    newChildren.get(index).entries.add(entry);
                    newChildren.get(index).bounds =
                            newChildren.get(index).bounds.union(entry.bounds);
                }

                children.addAll(newChildren);
            }
        }
    }

    private static class Entry {
        final BoundingBox bounds;
        final Shape shape;

        Entry(BoundingBox bounds, Shape shape) {
            this.bounds = bounds;
            this.shape = shape;
        }
    }
}
