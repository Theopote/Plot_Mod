package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;

/**
 * 单条边生成时的桩号附属设施上下文。
 */
public record StationFacilityBuildContext(
        RoadNetwork network,
        Road road,
        OrientedRoadSegment oriented,
        List<OrientedRoadSegment> slices) {

    public static final StationFacilityBuildContext EMPTY =
        new StationFacilityBuildContext(null, null, null, List.of());

    public StationFacilityBuildContext {
        slices = slices != null ? List.copyOf(slices) : List.of();
    }

    public StationFacilityBuildContext(RoadNetwork network, Road road, OrientedRoadSegment oriented) {
        this(network, road, oriented, oriented != null ? List.of(oriented) : List.of());
    }

    public boolean isActive() {
        return network != null && road != null && oriented != null && StationFacilityResolver.hasStationFacilities(road);
    }

    public String edgeId() {
        return oriented != null ? oriented.edgeId() : null;
    }

    public double segmentStartStation() {
        return oriented != null ? oriented.startStation() : 0.0;
    }

    public double roadEndStation() {
        if (network == null || road == null) {
            return 0.0;
        }
        return RoadStationing.canonicalLength(network, road);
    }

    /**
     * Canonical station at geometry-local. Fail-closed NaN when network resolve fails
     * (no silent instance fallback).
     */
    public double chainageAtGeometryLocal(double geometryLocalDistance) {
        if (network != null && road != null && edgeId() != null) {
            var canonical = RoadStationing.resolveCanonicalStationAtEdgeLocal(
                network, road, edgeId(), geometryLocalDistance);
            return canonical.isPresent() ? canonical.getAsDouble() : Double.NaN;
        }
        OrientedRoadSegment slice = sliceContaining(geometryLocalDistance);
        if (slice != null) {
            return slice.roadStationAtGeometryLocal(geometryLocalDistance);
        }
        return oriented != null
            ? oriented.roadStationAtGeometryLocal(geometryLocalDistance)
            : geometryLocalDistance;
    }

    public OrientedRoadSegment sliceContaining(double geometryLocalDistance) {
        for (OrientedRoadSegment slice : slices) {
            if (slice.containsGeometryLocal(geometryLocalDistance)) {
                return slice;
            }
        }
        if (slices.size() == 1) {
            return slices.getFirst();
        }
        return oriented;
    }

    public static StationFacilityBuildContext forEdge(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null) {
            return EMPTY;
        }
        String roadId = edge.getRoadId();
        if (roadId == null) {
            return EMPTY;
        }
        Road road = network.getRoad(roadId);
        if (road == null || !StationFacilityResolver.hasStationFacilities(road)) {
            return EMPTY;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return EMPTY;
        }
        List<OrientedRoadSegment> slices =
            RoadStationing.orientedSegmentsForEdge(network, road, edge.getId());
        if (slices.isEmpty()) {
            return EMPTY;
        }
        return new StationFacilityBuildContext(network, road, slices.getFirst(), slices);
    }
}
