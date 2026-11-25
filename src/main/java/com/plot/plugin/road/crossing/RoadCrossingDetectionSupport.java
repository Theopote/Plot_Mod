package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadLoopSeamService;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Shared plan-centerline construction for crossing / stack detectors:
 * exact partial-slice clip, LOOP physical geometry, AABB cache, discontinuity guard.
 */
final class RoadCrossingDetectionSupport {
    static final double INTERSECTION_EPSILON = 1e-5;

    private RoadCrossingDetectionSupport() {
    }

    enum StationSource {
        /** Unrotated instance polyline; LOOP seam applied once when converting. */
        INSTANCE_PHYSICAL,
        /** HA / design samples already in canonical station domain — never re-rotate. */
        HA_CANONICAL
    }

    static final class DetectionSession {
        private final RoadNetwork network;
        private final Map<String, List<PlanCenterlineSample>> samplesByRoadId = new HashMap<>();
        private final Map<String, CachedRoadGeometry> geometryByRoadId = new HashMap<>();
        private final Map<String, Optional<RoadLoopSeamService.SeamProjection>> seamByRoadId = new HashMap<>();

        DetectionSession(RoadNetwork network) {
            this.network = network;
        }

        RoadNetwork network() {
            return network;
        }

        Map<String, List<PlanCenterlineSample>> samplesByRoadId() {
            return samplesByRoadId;
        }

        CachedRoadGeometry geometryFor(Road road) {
            if (road == null || road.getId() == null) {
                return CachedRoadGeometry.invalid();
            }
            return geometryByRoadId.computeIfAbsent(road.getId(), id -> buildGeometry(road));
        }

        List<Vec2d> planCenterline(Road road) {
            return geometryFor(road).points();
        }

        List<PlanCenterlineSample> samplesFor(Road road) {
            return samplesByRoadId.computeIfAbsent(
                road.getId(), id -> RoadPlanGeometry.resolveRoadCenterlineSamples(network, road));
        }

        /**
         * Convert a detection hit into current canonical chainage using the geometry's
         * authoritative station source (instance arc length vs HA sample stations).
         */
        OptionalDouble toCanonicalChainage(
                Road road,
                CachedRoadGeometry geometry,
                int segmentIndex,
                Vec2d hit,
                double detectionChainage) {
            if (road == null || geometry == null || !geometry.valid() || !Double.isFinite(detectionChainage)) {
                return OptionalDouble.empty();
            }
            if (!RoadStationing.isStationable(network, road)) {
                return OptionalDouble.empty();
            }
            if (geometry.stationSource() == StationSource.HA_CANONICAL) {
                return interpolateHaStation(geometry.samples(), segmentIndex, hit);
            }
            double physical = detectionChainage;
            if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
                RoadLoopSeamService.SeamProjection seam = seamFor(road);
                if (seam != null) {
                    double loopLength = RoadStationing.instanceLength(network, road);
                    physical = RoadLoopSeamService.rotateLoopStation(
                        detectionChainage, seam.chainStation(), loopLength);
                }
            }
            return OptionalDouble.of(RoadStationing.toCanonicalChainage(network, road, physical));
        }

        RoadLoopSeamService.SeamProjection seamFor(Road road) {
            if (road == null || road.getId() == null) {
                return null;
            }
            return seamByRoadId.computeIfAbsent(road.getId(), id -> {
                if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
                    return Optional.empty();
                }
                RoadLoopSeam seam = road.getLoopSeam();
                if (seam == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(RoadLoopSeamService.resolveFromSeam(network, road, seam));
            }).orElse(null);
        }

        private CachedRoadGeometry buildGeometry(Road road) {
            if (!RoadStationing.isStationable(network, road)) {
                return CachedRoadGeometry.invalid();
            }
            if (RoadPlanGeometry.hasDesignAlignment(network, road)) {
                List<PlanCenterlineSample> samples = samplesFor(road);
                return CachedRoadGeometry.ofHa(samples);
            }
            return instancePlanGeometry(road);
        }

        private CachedRoadGeometry instancePlanGeometry(Road road) {
            List<OrientedRoadSegment> segments = detectionSegments(network, road);
            if (segments.isEmpty()) {
                return CachedRoadGeometry.invalid();
            }
            List<Vec2d> merged = new ArrayList<>();
            for (OrientedRoadSegment segment : segments) {
                List<Vec2d> points = orientedSegmentPoints(network, segment);
                if (points.isEmpty()) {
                    return CachedRoadGeometry.invalid();
                }
                if (!appendJoined(merged, points)) {
                    return CachedRoadGeometry.invalid();
                }
            }
            return CachedRoadGeometry.ofInstance(List.copyOf(merged));
        }
    }

    record CachedRoadGeometry(
            List<Vec2d> points,
            double[] aabb,
            boolean valid,
            StationSource stationSource,
            List<PlanCenterlineSample> samples,
            PolylineShape polyline) {

        static CachedRoadGeometry invalid() {
            return new CachedRoadGeometry(
                List.of(), null, false, StationSource.INSTANCE_PHYSICAL, List.of(), null);
        }

        static CachedRoadGeometry ofInstance(List<Vec2d> points) {
            if (points == null || points.size() < 2) {
                return invalid();
            }
            List<Vec2d> copy = List.copyOf(points);
            return new CachedRoadGeometry(
                copy,
                RoadGeometryUtils.polylineAabb(copy),
                true,
                StationSource.INSTANCE_PHYSICAL,
                List.of(),
                new PolylineShape(copy, false));
        }

        static CachedRoadGeometry ofHa(List<PlanCenterlineSample> samples) {
            if (samples == null || samples.size() < 2) {
                return invalid();
            }
            List<PlanCenterlineSample> sampleCopy = List.copyOf(samples);
            List<Vec2d> points = sampleCopy.stream()
                .map(PlanCenterlineSample::position)
                .map(Vec2d::copy)
                .toList();
            return new CachedRoadGeometry(
                points,
                RoadGeometryUtils.polylineAabb(points),
                true,
                StationSource.HA_CANONICAL,
                sampleCopy,
                new PolylineShape(points, false));
        }
    }

    static OptionalDouble interpolateHaStation(
            List<PlanCenterlineSample> samples,
            int segmentIndex,
            Vec2d hit) {
        if (samples == null || hit == null || segmentIndex < 0 || segmentIndex + 1 >= samples.size()) {
            return OptionalDouble.empty();
        }
        PlanCenterlineSample start = samples.get(segmentIndex);
        PlanCenterlineSample end = samples.get(segmentIndex + 1);
        if (start == null || end == null || start.position() == null || end.position() == null) {
            return OptionalDouble.empty();
        }
        double segmentLength = start.position().distance(end.position());
        if (segmentLength <= INTERSECTION_EPSILON) {
            return OptionalDouble.of(start.canonicalStation());
        }
        double t = Math.clamp(start.position().distance(hit) / segmentLength, 0.0, 1.0);
        return OptionalDouble.of(
            start.canonicalStation() + t * (end.canonicalStation() - start.canonicalStation()));
    }

    static List<OrientedRoadSegment> detectionSegments(RoadNetwork network, Road road) {
        if (road != null && road.getTopologyMode() == RoadTopologyMode.LOOP) {
            return RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        }
        return RoadStationing.orientedSegments(network, road);
    }

    static List<Vec2d> orientedSegmentPoints(RoadNetwork network, OrientedRoadSegment segment) {
        RoadEdge edge = network.getEdge(segment.edgeId());
        if (edge == null) {
            return List.of();
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return List.of();
        }
        if (!segment.isPartialSlice()) {
            return segment.forward() ? List.copyOf(points) : reverseCopy(points);
        }
        List<Vec2d> clipped = RoadGeometryUtils.clipPolylineByArcLength(
            points, segment.geometryChainStart(), segment.geometryChainEnd());
        if (clipped.isEmpty()) {
            return List.of();
        }
        return segment.forward() ? clipped : reverseCopy(clipped);
    }

    /**
     * Append segment vertices onto {@code target}. Returns false when a geometric gap would
     * invent a phantom chord between discontinuous pieces.
     */
    static boolean appendJoined(List<Vec2d> target, List<Vec2d> segment) {
        if (segment == null || segment.isEmpty()) {
            return true;
        }
        if (target.isEmpty()) {
            for (Vec2d point : segment) {
                target.add(point.copy());
            }
            return true;
        }
        Vec2d last = target.getLast();
        Vec2d first = segment.getFirst();
        if (last.distance(first) > INTERSECTION_EPSILON) {
            return false;
        }
        for (int i = 1; i < segment.size(); i++) {
            target.add(segment.get(i).copy());
        }
        return true;
    }

    static boolean isPhysicalOpenEndpoint(RoadNetwork network, Road road, Vec2d point) {
        if (point == null || road == null) {
            return false;
        }
        if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
            return false;
        }
        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        if (segments.isEmpty()) {
            return false;
        }
        return isNearNode(network, segments.getFirst().entryNodeId(), point)
            || isNearNode(network, segments.getLast().exitNodeId(), point);
    }

    static boolean aabbMayIntersect(CachedRoadGeometry a, CachedRoadGeometry b) {
        return a != null
            && b != null
            && a.valid()
            && b.valid()
            && RoadGeometryUtils.aabbOverlap(a.aabb(), b.aabb(), INTERSECTION_EPSILON);
    }

    private static boolean isNearNode(RoadNetwork network, String nodeId, Vec2d point) {
        if (network == null || nodeId == null || point == null) {
            return false;
        }
        RoadNode node = network.getNode(nodeId);
        return node != null
            && node.getPosition() != null
            && RoadGeometryUtils.pointsNear(node.getPosition(), point, INTERSECTION_EPSILON);
    }

    private static List<Vec2d> reverseCopy(List<Vec2d> points) {
        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i).copy());
        }
        return reversed;
    }
}
