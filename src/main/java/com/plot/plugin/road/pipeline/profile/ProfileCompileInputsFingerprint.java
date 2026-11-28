package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Content fingerprint of all inputs that affect voxel compilation.
 * Window working-copy edits do not bump live network revision, so this must be used
 * instead of revision alone for preview cache validity.
 */
public record ProfileCompileInputsFingerprint(
        String roadId,
        int geometryHash,
        int alignmentHash,
        int intersectionHash,
        int constraintHash,
        RoadVerticalMode verticalMode,
        Double flatElevation,
        float maxSlope,
        long terrainRevision,
        int worldBoundsHash,
        int algorithmVersion,
        int optionsHash) {

    public static ProfileCompileInputsFingerprint of(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment,
            List<RoadProfileIntersection> intersections,
            VoxelProfileConstraints constraints,
            RoadElevationBounds elevationBounds,
            long terrainRevision,
            RoadSystemConfig config,
            ProfileCompileOptions options) {
        Objects.requireNonNull(road, "road");
        ProfileCompileOptions effective = options != null ? options : ProfileCompileOptions.defaults();
        FlatVerticalIntent flatIntent = road.getFlatVerticalIntent();
        Double flatY = flatIntent != null ? flatIntent.getBaseElevation() : null;
        Float maxSlope = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : (config != null ? config.getMaxSlope() : null);
        return new ProfileCompileInputsFingerprint(
            road.getId() != null ? road.getId() : "",
            previewGeometryHash(network, road),
            alignmentHash(alignment),
            intersectionHash(intersections),
            constraints != null ? constraints.contentHash() : 0,
            road.getVerticalMode(),
            flatY,
            maxSlope != null ? maxSlope : 0f,
            terrainRevision,
            worldBoundsHash(elevationBounds),
            effective.algorithmVersion(),
            Objects.hash(
                road.getEffectiveProfileSource(),
                road.getEditedGeometrySignature(),
                effective.minEffectiveRunBlocks(),
                effective.maxReversalSpanBlocks(),
                effective.algorithmVersion()));
    }

    /**
     * Physical-walk geometry signature for EDITED profile binding.
     * Hashes a collinear-simplified chain-walk polyline so densify-only PI inserts stay stable
     * while equal-length shape changes (e.g. opposite bend) invalidate. Ignores storage
     * {@code forward} so Reverse Edge alone does not invalidate.
     */
    public static int geometryHash(RoadNetwork network, Road road) {
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return 0;
        }
        int hash = Double.hashCode(RoadStationing.canonicalLength(network, road));
        hash = 31 * hash + Objects.hashCode(road.getDesignEntryNodeId());
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            hash = 31 * hash + Objects.hash(segment.edgeId(), segment.length());
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge != null && edge.getCenterlinePoints() != null) {
                List<Vec2d> walk = chainWalkPoints(edge.getCenterlinePoints(), segment.forward());
                for (Vec2d point : simplifyCollinear(walk)) {
                    hash = 31 * hash + Objects.hash(
                        Math.rint(point.x * 1e6) / 1e6,
                        Math.rint(point.y * 1e6) / 1e6);
                }
            }
            RoadNode entry = network.getNode(segment.entryNodeId());
            RoadNode exit = network.getNode(segment.exitNodeId());
            hash = 31 * hash + nodeHash(entry) + 17 * nodeHash(exit);
        }
        // Include HA design elements so EDITED binding invalidates when HA changed but
        // instance polyline has not yet been rematerialized (same fields as preview).
        hash = 31 * hash + Objects.hashCode(road.getTopologyMode());
        var alignment = road.getHorizontalAlignment();
        if (alignment != null && !alignment.isEmpty()) {
            Vec2d origin = alignment.getOrigin();
            hash = 31 * hash + Objects.hash(origin.x, origin.y, alignment.getStartBearingRadians());
            for (var element : alignment.getElements()) {
                hash = 31 * hash + Objects.hash(element.getType(), element.getLength(),
                    element.getRadius(), element.getDirection(), element.getSpiralParameterA());
            }
        }
        return hash;
    }

    private static List<Vec2d> chainWalkPoints(List<Vec2d> points, boolean forward) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (forward) {
            return List.copyOf(points);
        }
        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i));
        }
        return reversed;
    }

    /** Drop near-collinear densify vertices; keep corners that change heading. */
    static List<Vec2d> simplifyCollinear(List<Vec2d> points) {
        if (points == null || points.size() <= 2) {
            return points == null ? List.of() : List.copyOf(points);
        }
        final double eps = 1e-4;
        List<Vec2d> simplified = new ArrayList<>();
        simplified.add(points.getFirst());
        for (int i = 1; i < points.size() - 1; i++) {
            Vec2d prev = simplified.getLast();
            Vec2d cur = points.get(i);
            Vec2d next = points.get(i + 1);
            if (prev == null || cur == null || next == null) {
                continue;
            }
            double ax = cur.x - prev.x;
            double ay = cur.y - prev.y;
            double bx = next.x - cur.x;
            double by = next.y - cur.y;
            double cross = ax * by - ay * bx;
            double dot = ax * bx + ay * by;
            double scale = Math.hypot(ax, ay) * Math.hypot(bx, by);
            // Collinear densify may drop; 180° turn-backs (dot <= 0) must stay.
            if (scale <= eps
                    || Math.abs(cross) > eps * Math.max(1.0, scale)
                    || dot <= eps * Math.max(1.0, scale)) {
                simplified.add(cur);
            }
        }
        simplified.add(points.getLast());
        return simplified;
    }

    /**
     * Preview cache signature. Delegates to {@link #geometryHash}, which already includes
     * collinear-simplified polyline walk plus HA element fields (avoid double-folding).
     */
    private static int previewGeometryHash(RoadNetwork network, Road road) {
        return geometryHash(network, road);
    }

    static int alignmentHash(RoadVerticalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            hash = 31 * hash + Objects.hash(
                pvi.getStation(),
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint());
        }
        return hash;
    }

    static int intersectionHash(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (RoadProfileIntersection intersection : intersections) {
            hash = 31 * hash + Objects.hash(
                intersection.nodeId(),
                intersection.currentRoadId(),
                intersection.otherRoadId(),
                intersection.roadStation(),
                intersection.currentRoadElevation(),
                intersection.otherRoadElevation(),
                intersection.gradeSeparated(),
                intersection.currentRoadElevated(),
                intersection.clearance());
        }
        return hash;
    }

    static int worldBoundsHash(RoadElevationBounds bounds) {
        if (bounds == null) {
            return 0;
        }
        return Objects.hash(bounds.minY(), bounds.maxY());
    }

    private static int nodeHash(RoadNode node) {
        if (node == null) {
            return 0;
        }
        Vec2d position = node.getPosition();
        return Objects.hash(node.getId(), position != null ? position.x : 0.0, position != null ? position.y : 0.0);
    }
}
