package com.plot.plugin.road.centerline;

import com.plot.plugin.road.alignment.CenterlineHorizontalAlignmentSync;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.CenterlineEditOperation;
import com.plot.plugin.road.station.CenterlineEditStationPolicy;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;
import com.plot.plugin.road.station.RoadStationMirroring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 中心线编辑 → Phase 2 工程数据一致性规则（统一入口）。
 * <p>
 * 沿桩号策略见 {@link CenterlineEditStationPolicy} 与 {@link CenterlineEditOperation}；
 * HA 在折线变化后 refit 或清除（ADR 0007）。
 * <p>
 * <h2>操作 → Station Policy</h2>
 * <table>
 *   <tr><th>操作</th><th>Station policy</th><th>HA</th></tr>
 *   <tr><td>Insert PI（段长不变）</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Fillet</td><td>{@link CenterlineEditStationPolicy#REPARAMETERIZE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Split edge</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Merge edge</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Merge junction node</td><td>{@link CenterlineEditStationPolicy#REPARAMETERIZE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Split road</td><td>{@link CenterlineEditStationPolicy#PARTITION_AND_RESET_TAIL}</td><td>clear + refit</td></tr>
 *   <tr><td>Merge road</td><td>{@link CenterlineEditStationPolicy#OFFSET_BY_HEAD_LENGTH}</td><td>clear + refit</td></tr>
 *   <tr><td>Reverse edge</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>refit / clear</td></tr>
 *   <tr><td>Reverse road</td><td>{@link CenterlineEditStationPolicy#MIRROR_FULL_ROAD}</td><td>explicit reverse</td></tr>
 * </table>
 */
public final class CenterlinePhase2ConsistencyPolicy {

    private CenterlinePhase2ConsistencyPolicy() {
    }

    /**
     * 段几何变化：Insert PI、Fillet。
     */
    public static void afterSegmentGeometryEdit(
            RoadNetwork network,
            CenterlineEditOperation operation,
            String edgeId,
            SegmentGeometrySnapshot before) {
        if (network == null || edgeId == null || before == null) {
            return;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || edge.getRoadId() == null) {
            return;
        }
        Road road = network.getRoadForEdge(edge);
        if (road == null) {
            return;
        }
        CenterlineEditStationPolicy policy = operation.resolveStationPolicy(
            before.oldSegmentLength(),
            edge.getLength());
        if (policy == CenterlineEditStationPolicy.REPARAMETERIZE_STATION) {
            policy.applySegmentGeometryEdit(road, before, edge.getLength());
        }
        syncHorizontalAlignment(network, road);
    }

    /**
     * 图拓扑变化、总链长不变：Split edge、Merge edge。
     */
    public static void afterGraphTopologyEdit(
            RoadNetwork network,
            CenterlineEditOperation operation,
            String anchorEdgeId) {
        if (operation.defaultStationPolicy() != CenterlineEditStationPolicy.PRESERVE_STATION) {
            throw new IllegalArgumentException("Expected graph edit with PRESERVE_STATION, got " + operation);
        }
        Road road = roadForEdge(network, anchorEdgeId);
        if (road != null) {
            syncHorizontalAlignment(network, road);
        }
    }

    /**
     * 邻近交叉点合并：对每条重连边按段长变化重映射沿桩号工程数据，并同步 HA。
     */
    public static void afterJunctionNodeMerge(
            RoadNetwork network,
            List<JunctionMergeEdgeEdit> edits) {
        if (network == null || edits == null || edits.isEmpty()) {
            return;
        }
        Set<String> syncedRoads = new HashSet<>();
        for (JunctionMergeEdgeEdit edit : edits) {
            if (edit == null || edit.edgeId() == null) {
                continue;
            }
            Road road = roadForEdge(network, edit.edgeId());
            if (edit.before() != null) {
                afterSegmentGeometryEdit(
                    network,
                    CenterlineEditOperation.MERGE_JUNCTION_NODE,
                    edit.edgeId(),
                    edit.before());
                if (road != null) {
                    syncedRoads.add(road.getId());
                }
            } else if (road != null && syncedRoads.add(road.getId())) {
                syncHorizontalAlignment(network, road);
            }
        }
    }

    public record JunctionMergeEdgeEdit(String edgeId, SegmentGeometrySnapshot before) {
    }

    /**
     * 单段反向。
     */
    public static void afterReverseEdge(RoadNetwork network, String edgeId) {
        CenterlineEditOperation.REVERSE_EDGE.defaultStationPolicy().applyReverseEdge(network, edgeId);
        Road road = roadForEdge(network, edgeId);
        if (road != null) {
            syncHorizontalAlignment(network, road);
        }
    }

    private static void syncHorizontalAlignment(RoadNetwork network, Road road) {
        CenterlineHorizontalAlignmentSync.syncAfterCenterlineEdit(network, road);
    }

    private static Road roadForEdge(RoadNetwork network, String edgeId) {
        if (network == null || edgeId == null || edgeId.isBlank()) {
            return null;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || edge.getRoadId() == null) {
            return null;
        }
        return network.getRoadForEdge(edge);
    }
}
