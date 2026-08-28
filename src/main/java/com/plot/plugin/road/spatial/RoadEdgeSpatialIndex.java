package com.plot.plugin.road.spatial;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.plugin.road.model.RoadEdge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uniform-grid spatial hash for road-edge broad-phase intersection filtering.
 *
 * <p>Pipeline: edge bounding box → grid cells → candidate edge pairs → exact polyline tests
 * in {@link com.plot.plugin.road.RoadNetworkBuilder}.</p>
 */
public final class RoadEdgeSpatialIndex {
    public record CandidatePair(String edgeIdA, String edgeIdB) {
        public CandidatePair {
            if (edgeIdA.compareTo(edgeIdB) >= 0) {
                throw new IllegalArgumentException("edgeIdA must sort before edgeIdB");
            }
        }
    }

    private final double cellSize;
    private final Map<String, BoundingBox> edgeBounds;
    private final Map<Long, List<String>> grid;

    private RoadEdgeSpatialIndex(
            double cellSize,
            Map<String, BoundingBox> edgeBounds,
            Map<Long, List<String>> grid) {
        this.cellSize = cellSize;
        this.edgeBounds = edgeBounds;
        this.grid = grid;
    }

    public static RoadEdgeSpatialIndex build(Collection<RoadEdge> edges, double expansionTolerance) {
        Map<String, BoundingBox> boundsById = new HashMap<>();
        for (RoadEdge edge : edges) {
            if (edge == null || edge.getId() == null) {
                continue;
            }
            BoundingBox bounds = boundsOf(edge, expansionTolerance);
            if (bounds != null) {
                boundsById.put(edge.getId(), bounds);
            }
        }

        double cellSize = resolveCellSize(boundsById.values());
        Map<Long, List<String>> grid = new HashMap<>();
        for (Map.Entry<String, BoundingBox> entry : boundsById.entrySet()) {
            insert(grid, cellSize, entry.getKey(), entry.getValue());
        }
        return new RoadEdgeSpatialIndex(cellSize, boundsById, grid);
    }

    public List<CandidatePair> candidatePairs() {
        List<CandidatePair> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, BoundingBox> entry : edgeBounds.entrySet()) {
            String edgeId = entry.getKey();
            for (String otherId : queryOverlapping(entry.getValue())) {
                if (edgeId.equals(otherId)) {
                    continue;
                }
                String first = edgeId.compareTo(otherId) < 0 ? edgeId : otherId;
                String second = edgeId.compareTo(otherId) < 0 ? otherId : edgeId;
                if (seen.add(first + '\0' + second)) {
                    pairs.add(new CandidatePair(first, second));
                }
            }
        }
        return pairs;
    }

    public int edgeCount() {
        return edgeBounds.size();
    }

    public double cellSize() {
        return cellSize;
    }

    private Set<String> queryOverlapping(BoundingBox queryBox) {
        Set<String> result = new HashSet<>();
        int minCellX = cellIndex(queryBox.getMinX());
        int maxCellX = cellIndex(queryBox.getMaxX());
        int minCellY = cellIndex(queryBox.getMinY());
        int maxCellY = cellIndex(queryBox.getMaxY());
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                List<String> bucket = grid.get(packCellKey(cellX, cellY));
                if (bucket == null) {
                    continue;
                }
                for (String edgeId : bucket) {
                    BoundingBox bounds = edgeBounds.get(edgeId);
                    if (bounds != null && bounds.intersects(queryBox)) {
                        result.add(edgeId);
                    }
                }
            }
        }
        return result;
    }

    private static void insert(
            Map<Long, List<String>> grid,
            double cellSize,
            String edgeId,
            BoundingBox bounds) {
        int minCellX = cellIndex(bounds.getMinX(), cellSize);
        int maxCellX = cellIndex(bounds.getMaxX(), cellSize);
        int minCellY = cellIndex(bounds.getMinY(), cellSize);
        int maxCellY = cellIndex(bounds.getMaxY(), cellSize);
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                grid.computeIfAbsent(packCellKey(cellX, cellY), ignored -> new ArrayList<>())
                    .add(edgeId);
            }
        }
    }

    private int cellIndex(double coordinate) {
        return cellIndex(coordinate, cellSize);
    }

    private static int cellIndex(double coordinate, double cellSize) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static long packCellKey(int cellX, int cellY) {
        return (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
    }

    private static BoundingBox boundsOf(RoadEdge edge, double expansionTolerance) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            Vec2d point = points.getFirst();
            return new BoundingBox(
                point.x - expansionTolerance,
                point.y - expansionTolerance,
                point.x + expansionTolerance,
                point.y + expansionTolerance);
        }
        return BoundingBox.fromPoints(points.toArray(Vec2d[]::new)).expand(expansionTolerance);
    }

    private static double resolveCellSize(Collection<BoundingBox> boxes) {
        if (boxes.isEmpty()) {
            return 16.0;
        }
        BoundingBox total = BoundingBox.merge(boxes.toArray(BoundingBox[]::new));
        double span = Math.max(Math.max(total.getWidth(), total.getHeight()), 1.0);
        int count = boxes.size();
        return Math.max(1.0, span / Math.max(8.0, Math.sqrt(count)));
    }
}
