package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 单条边生成时的桩号附属设施上下文。
 */
public record StationFacilityBuildContext(
        RoadNetwork network,
        Road road,
        double segmentStartStation) {

    public static final StationFacilityBuildContext EMPTY = new StationFacilityBuildContext(null, null, 0.0);

    public boolean isActive() {
        return network != null && road != null && StationFacilityResolver.hasStationFacilities(road);
    }

    public double roadEndStation() {
        if (network == null || road == null) {
            return 0.0;
        }
        return RoadStationing.totalLength(network, road);
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
        double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
        if (segmentStart < 0.0) {
            return EMPTY;
        }
        return new StationFacilityBuildContext(network, road, segmentStart);
    }
}
