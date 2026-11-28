package com.plot.plugin.road.centerline;

import com.plot.plugin.road.alignment.CenterlineHorizontalAlignmentSync;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.CenterlineEditOperation;
import com.plot.plugin.road.station.CenterlineEditStationPolicy;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;
import com.plot.plugin.road.station.StationMigrationRefuseException;

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
 *   <tr><td>Split edge</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>preserve HA (no refit)</td></tr>
 *   <tr><td>Merge edge</td><td>{@link CenterlineEditStationPolicy#PRESERVE_STATION}</td><td>preserve HA (no refit)</td></tr>
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
     * 段几何变化：Insert/Delete PI、Fillet。
     *
     * @return false when the edit must be refused (unreliable HA map / remap failure)
     */
    public static boolean afterSegmentGeometryEdit(
            RoadNetwork network,
            CenterlineEditOperation operation,
            String edgeId,
            SegmentGeometrySnapshot before) {
        if (network == null || edgeId == null || before == null) {
            return false;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || edge.getRoadId() == null) {
            return true;
        }
        Road road = network.getRoadForEdge(edge);
        if (road == null) {
            return false;
        }
        // Length-change policy uses instance polyline; station remap uses canonical lengths.
        // With HA present, refuse when domain map is unreliable — instance ratio ≠ canonical authority.
        if (road.getHorizontalAlignment() != null) {
            var map = com.plot.plugin.road.station.RoadStationing.domainMap(network, road);
            if (map == null || !map.isReliable()) {
                return false;
            }
        }
        CenterlineEditStationPolicy policy = operation.resolveStationPolicy(
            before.instanceLength(),
            edge.getLength());
        if (policy == CenterlineEditStationPolicy.REPARAMETERIZE_STATION) {
            double ratio = before.instanceLength() > 1e-9
                ? edge.getLength() / before.instanceLength()
                : 1.0;
            double newCanonicalLength = before.oldSegmentLength() * ratio;
            try {
                policy.applySegmentGeometryEdit(road, before, newCanonicalLength);
            } catch (StationMigrationRefuseException refused) {
                return false;
            }
        }
        syncHorizontalAlignment(network, road);
        return true;
    }

    /**
     * 图拓扑变化、总链长不变：Split edge、Merge edge。
     * Pure topology — do not refit/clear HA; station data and HA chain length stay valid.
     */
    public static void afterGraphTopologyEdit(
            RoadNetwork network,
            CenterlineEditOperation operation,
            String anchorEdgeId) {
        if (operation.defaultStationPolicy() != CenterlineEditStationPolicy.PRESERVE_STATION) {
            throw new IllegalArgumentException("Expected graph edit with PRESERVE_STATION, got " + operation);
        }
        // Intentionally no syncHorizontalAlignment — PRESERVE_STATION pure topology.
    }

    /**
     * 邻近交叉点合并：对每条重连边按段长变化重映射沿桩号工程数据，并同步 HA。
     *
     * @return false if any segment remap must refuse
     */
    public static boolean afterJunctionNodeMerge(
            RoadNetwork network,
            List<JunctionMergeEdgeEdit> edits) {
        if (network == null || edits == null || edits.isEmpty()) {
            return true;
        }
        Set<String> syncedRoads = new HashSet<>();
        for (JunctionMergeEdgeEdit edit : edits) {
            if (edit == null || edit.edgeId() == null) {
                continue;
            }
            Road road = roadForEdge(network, edit.edgeId());
            if (edit.before() != null) {
                if (!afterSegmentGeometryEdit(
                        network,
                        CenterlineEditOperation.MERGE_JUNCTION_NODE,
                        edit.edgeId(),
                        edit.before())) {
                    return false;
                }
                if (road != null) {
                    syncedRoads.add(road.getId());
                }
            } else if (road != null && syncedRoads.add(road.getId())) {
                syncHorizontalAlignment(network, road);
            }
        }
        return true;
    }

    public record JunctionMergeEdgeEdit(String edgeId, SegmentGeometrySnapshot before) {
    }

    /**
     * 单段反向：仅存储方向变化，不重拟合/清除 HA，不物化派生中心线。
     */
    public static void afterReverseEdge(RoadNetwork network, String edgeId) {
        CenterlineEditOperation.REVERSE_EDGE.defaultStationPolicy().applyReverseEdge(network, edgeId);
        // Reverse Edge is storage-only — never refit/clear HA or rematerialize.
    }

    private static void syncHorizontalAlignment(RoadNetwork network, Road road) {
        CenterlineHorizontalAlignmentSync.Outcome outcome =
            CenterlineHorizontalAlignmentSync.syncAfterCenterlineEdit(network, road);
        if (outcome == CenterlineHorizontalAlignmentSync.Outcome.FITTED
                && HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
            HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
        }
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
