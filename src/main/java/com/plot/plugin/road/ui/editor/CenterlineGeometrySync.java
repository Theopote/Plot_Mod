package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将工作副本中某条逻辑道路的平面几何与沿桩号工程数据同步到 live 路网。
 * <p>
 * 纵断面（VA）须与 {@link com.plot.plugin.road.station.CenterlineEditStationPolicy} 在工作副本上的
 * 变换结果一并写回，否则几何反向/重参数化后 live 仍保留旧桩号标高，体素预览与生成会偏离。
 * Phase 2.4.2 also syncs design-direction binding, seam, profile authority, and cross-section.
 */
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

        // 先挂接/更新目标分段，避免删光旧分段时 live Road 被 removeEdge 连带移除。
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
                // 端点变更必须同步节点邻接表，不可只改边字段。
                live.updateEdgeEndpoints(
                    edgeId, workingEdge.getStartNodeId(), workingEdge.getEndNodeId());
                liveEdge.setCenterlinePoints(copyPoints(workingEdge.getCenterlinePoints()));
                liveEdge.setSlopeOverrides(workingEdge.getSlopeOverrides());
            }
        }

        for (String edgeId : new ArrayList<>(liveRoad.getOrderedSegmentIds())) {
            if (!targetSet.contains(edgeId)) {
                live.removeEdge(edgeId);
            }
        }

        liveRoad.reorderSegments(List.copyOf(targetSegments));
        liveRoad.setDesignEntryNodeId(workingRoad.getDesignEntryNodeId());
        liveRoad.setDesignEntryEdgeId(workingRoad.getDesignEntryEdgeId());
        liveRoad.setLoopSeam(workingRoad.getLoopSeam());
        liveRoad.setCrossSection(
            workingRoad.getCrossSection() != null ? workingRoad.getCrossSection().copy() : null);
        if (workingRoad.getHorizontalAlignment() != null) {
            liveRoad.setHorizontalAlignment(workingRoad.getHorizontalAlignment().copy());
        } else {
            liveRoad.setHorizontalAlignment(null);
        }
        liveRoad.setStationFacilities(workingRoad.getStationFacilities());
        liveRoad.setVariableCrossSections(workingRoad.getVariableCrossSections());
        if (workingRoad.getVerticalAlignment() != null) {
            liveRoad.setVerticalAlignment(workingRoad.getVerticalAlignment().copy());
        } else {
            liveRoad.setVerticalAlignment(null);
        }
        liveRoad.setProfileSource(workingRoad.getStoredProfileSource());
        liveRoad.setEditedGeometrySignature(workingRoad.getEditedGeometrySignature());
        // D01: keep verified reverse-migration crossing identity authoritative on floating apply.
        copyCrossingRegistryForRoad(live, working, roadId);
    }

    private static void copyCrossingRegistryForRoad(
            RoadNetwork live,
            RoadNetwork working,
            String roadId) {
        if (live == null || working == null || roadId == null || roadId.isBlank()) {
            return;
        }
        Set<String> relevantCrossingIds = new HashSet<>();
        for (RoadCrossing crossing : working.getCrossings().values()) {
            if (crossing == null) {
                continue;
            }
            if (roadId.equals(crossing.roadAId()) || roadId.equals(crossing.roadBId())) {
                live.registerCrossing(crossing);
                relevantCrossingIds.add(crossing.id());
            }
        }
        for (RoadCrossing crossing : List.copyOf(live.getCrossings().values())) {
            if (crossing == null) {
                continue;
            }
            if ((roadId.equals(crossing.roadAId()) || roadId.equals(crossing.roadBId()))
                    && !relevantCrossingIds.contains(crossing.id())
                    && working.getCrossing(crossing.id()) == null) {
                live.removeCrossing(crossing.id());
            }
        }
        Set<String> relevantStacks = new HashSet<>();
        for (RoadStackCrossing stack : working.getStackCrossings().values()) {
            if (stack != null && roadId.equals(stack.roadId())) {
                live.registerStackCrossing(stack);
                relevantStacks.add(stack.id());
            }
        }
        for (RoadStackCrossing stack : List.copyOf(live.getStackCrossings().values())) {
            if (stack != null
                    && roadId.equals(stack.roadId())
                    && !relevantStacks.contains(stack.id())
                    && working.getStackCrossing(stack.id()) == null) {
                live.removeStackCrossing(stack.id());
            }
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
