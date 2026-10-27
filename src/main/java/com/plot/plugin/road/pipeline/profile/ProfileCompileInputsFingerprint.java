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
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalMode;

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

    /** Existing persisted edited-profile bindings use this signature; keep its format stable. */
    public static int geometryHash(RoadNetwork network, Road road) {
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return 0;
        }
        int hash = Double.hashCode(RoadStationing.canonicalLength(network, road));
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            hash = 31 * hash + Objects.hash(segment.edgeId(), segment.forward(), segment.length());
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            hash = 31 * hash + nodeHash(start) + 17 * nodeHash(end);
        }
        return hash;
    }

    private static int previewGeometryHash(RoadNetwork network, Road road) {
        int hash = geometryHash(network, road);
        if (road == null || !RoadStationing.isStationable(network, road)) return hash;
        hash = 31 * hash + Objects.hashCode(road.getTopologyMode());
        var alignment = road.getHorizontalAlignment();
        if (alignment != null && !alignment.isEmpty()) {
            Vec2d origin = alignment.getOrigin();
            hash = 31 * hash + Objects.hash(origin.x, origin.y, alignment.getStartBearingRadians());
            for (var element : alignment.getElements()) {
                hash = 31 * hash + Objects.hash(element.getType(), element.getLength(),
                    element.getRadius(), element.getDirection(), element.getSpiralParameterA());
            }
        } else {
            for (var segment : RoadStationing.orientedSegments(network, road)) {
                RoadEdge edge = network.getEdge(segment.edgeId());
                if (edge == null || edge.getCenterlinePoints() == null) continue;
                for (Vec2d point : edge.getCenterlinePoints()) {
                    hash = 31 * hash + Objects.hash(point.x, point.y);
                }
            }
        }
        return hash;
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
