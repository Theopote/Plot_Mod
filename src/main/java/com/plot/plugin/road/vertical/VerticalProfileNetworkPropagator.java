package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Propagates a changed junction elevation and repairs adjacent grades across connected roads. */
public final class VerticalProfileNetworkPropagator {
    public record RoadResult(String roadId, String nodeId, boolean changed, boolean fullyResolved) { }
    public record Result(List<RoadResult> roads) {
        public long adjustedRoadCount() { return roads.stream().filter(RoadResult::changed).count(); }
        public long unresolvedRoadCount() { return roads.stream().filter(r -> !r.fullyResolved()).count(); }
    }

    private VerticalProfileNetworkPropagator() { }

    public static Result propagate(
            RoadNetwork network,
            Road sourceRoad,
            ToDoubleFunction<Road> maxGradeResolver) {
        if (network == null || sourceRoad == null || maxGradeResolver == null) {
            return new Result(List.of());
        }
        VerticalAlignmentJunctionSynchronizer.synchronize(network, sourceRoad);
        List<RoadResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> sourceJunction
                : VerticalAlignmentJunctionSynchronizer.junctionStations(network, sourceRoad).entrySet()) {
            RoadNode node = network.getNode(sourceJunction.getKey());
            if (node == null || node.getManualElevation() == null) continue;
            for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
                Road road = network.getRoad(roadId);
                if (road == null || road.getVerticalMode() != RoadVerticalMode.MANUAL_PROFILE
                        || road.getVerticalAlignment() == null) continue;
                int synchronizedPvis = VerticalAlignmentJunctionSynchronizer
                    .applySharedJunctionConstraints(network, road);
                Double station = VerticalAlignmentJunctionSynchronizer
                    .junctionStations(network, road).get(node.getId());
                if (station == null) continue;
                int anchor = VerticalAlignmentJunctionSynchronizer.matchingPviIndex(
                    road.getVerticalAlignment().getPvis(), station);
                if (anchor < 0) continue;
                VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                    road.getVerticalAlignment(), anchor, RoadStationing.canonicalLength(network, road),
                    maxGradeResolver.applyAsDouble(road),
                    index -> index > 0 && index < road.getVerticalAlignment().pviCount() - 1
                        && !VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
                            network, road,
                            road.getVerticalAlignment().getPvis().get(index).getStation()));
                if (fixed.changed()) road.setVerticalAlignment(fixed.alignment());
                results.add(new RoadResult(
                    roadId, node.getId(), synchronizedPvis > 0 || fixed.changed(), fixed.fullyResolved()));
            }
        }
        return new Result(List.copyOf(results));
    }
}
