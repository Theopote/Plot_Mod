package com.plot.plugin.road.alignment;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.Optional;

/**
 * centerline 编辑后同步设计平面线形：能拟合则 refit，否则清除 HA。
 */
public final class CenterlineHorizontalAlignmentSync {

    public enum Outcome {
        UNCHANGED,
        FITTED,
        INVALIDATED
    }

    private CenterlineHorizontalAlignmentSync() {
    }

    public static Outcome syncAfterCenterlineEdit(RoadNetwork network, Road road) {
        if (road == null || road.getHorizontalAlignment() == null) {
            return Outcome.UNCHANGED;
        }
        if (!RoadStationing.isStationable(network, road)) {
            road.setHorizontalAlignment(null);
            return Outcome.INVALIDATED;
        }
        Optional<RoadHorizontalAlignment> fitted = HorizontalAlignmentPolylineFitter.fit(network, road);
        if (fitted.isPresent() && !fitted.get().isEmpty()) {
            road.setHorizontalAlignment(fitted.get());
            HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road);
            return Outcome.FITTED;
        }
        road.setHorizontalAlignment(null);
        return Outcome.INVALIDATED;
    }

    public static Outcome syncAfterCenterlineEdit(RoadNetwork network, String edgeId) {
        if (network == null || edgeId == null || edgeId.isBlank()) {
            return Outcome.UNCHANGED;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || edge.getRoadId() == null) {
            return Outcome.UNCHANGED;
        }
        Road road = network.getRoadForEdge(edge);
        return syncAfterCenterlineEdit(network, road);
    }
}
