package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将工作副本中某条逻辑道路的平面几何同步到 live 路网。 */
final class CenterlineGeometrySync {

    private CenterlineGeometrySync() {
    }

    static void applyRoadFromWorking(RoadNetwork live, RoadNetwork working, String roadId) {
        Road liveRoad = live.getRoad(roadId);
        Road workingRoad = working.getRoad(roadId);
        if (liveRoad == null || workingRoad == null) {
            return;
        }
        List<String> targetSegments = workingRoad.getOrderedSegmentIds();
        Set<String> targetSet = new LinkedHashSet<>(targetSegments);

        for (String edgeId : new ArrayList<>(liveRoad.getOrderedSegmentIds())) {
            if (!targetSet.contains(edgeId)) {
                live.removeEdge(edgeId);
            }
        }

        for (String edgeId : targetSegments) {
            RoadEdge workingEdge = working.getEdge(edgeId);
            if (workingEdge == null) {
                continue;
            }
            ensureNode(live, working, workingEdge.getStartNodeId());
            ensureNode(live, working, workingEdge.getEndNodeId());
            RoadEdge liveEdge = live.getEdge(edgeId);
            if (liveEdge == null) {
                RoadEdge copy = new RoadEdge(
                    edgeId,
                    workingEdge.getStartNodeId(),
                    workingEdge.getEndNodeId(),
                    copyPoints(workingEdge.getCenterlinePoints()),
                    roadId,
                    workingEdge.getSlopeOverrides(),
                    workingEdge.getSourceRoadId());
                live.attachExistingEdge(copy);
            } else {
                liveEdge.setStartNodeId(workingEdge.getStartNodeId());
                liveEdge.setEndNodeId(workingEdge.getEndNodeId());
                liveEdge.setCenterlinePoints(copyPoints(workingEdge.getCenterlinePoints()));
                liveEdge.setSlopeOverrides(workingEdge.getSlopeOverrides());
            }
        }

        liveRoad.reorderSegments(List.copyOf(targetSegments));
        if (workingRoad.getHorizontalAlignment() != null) {
            liveRoad.setHorizontalAlignment(workingRoad.getHorizontalAlignment().copy());
        } else {
            liveRoad.setHorizontalAlignment(null);
        }
    }

    private static void ensureNode(RoadNetwork live, RoadNetwork working, String nodeId) {
        if (live.getNode(nodeId) != null) {
            return;
        }
        RoadNode source = working.getNode(nodeId);
        if (source == null) {
            return;
        }
        RoadNode copy = new RoadNode(
            source.getId(),
            source.getPosition(),
            source.getManualElevation(),
            source.getCornerRadius(),
            new LinkedHashSet<>());
        copy.setManualElevation(source.getManualElevation(), source.getManualElevationOrigin());
        live.attachExistingNode(copy);
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            copy.add(point != null ? point.copy() : null);
        }
        return copy;
    }
}
