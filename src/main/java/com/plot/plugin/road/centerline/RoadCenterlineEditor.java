package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.alignment.HorizontalAlignmentElementType;
import com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator;
import com.plot.plugin.road.crossing.RoadCrossingRegistrySync;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.station.CenterlineEditOperation;
import com.plot.plugin.road.station.CenterlineEditStationPolicy;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadDesignDirectionAuthority;
import com.plot.plugin.road.station.RoadLoopSeamService;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.RoadStationMirroring;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 道路中心线编辑：插入 PI、圆角、拆分/合并、反向。
 * <p>
 * v1 直接操作 {@link RoadEdge} 折线中心线。Phase 2 工程数据联动见
 * {@link CenterlinePhase2ConsistencyPolicy}。
 */
public final class RoadCenterlineEditor {

    private static final double ENDPOINT_CLEARANCE = 0.5;
    private static final double MIN_FILLET_RADIUS = 0.5;
    /** 仅作异常值防护；真实上限由折点两侧腿长与夹角决定。 */
    private static final double MAX_FILLET_RADIUS = 1_000_000.0;

    private RoadCenterlineEditor() {
    }

    public static CenterlineEditResult insertPiAtLocalDistance(
            RoadNetwork network,
            String edgeId,
            double localDistance) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Optional<List<Vec2d>> updated = RoadGeometryUtils.insertVertexAtDistance(
            edge.getCenterlinePoints(),
            localDistance,
            ENDPOINT_CLEARANCE
        );
        if (updated.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE);
        }
        SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
        if (before == null && edge.getRoadId() != null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }
        return commitSegmentGeometryEdit(
            network, edgeId, edge.getRoadId(), before, updated.get(), CenterlineEditOperation.INSERT_PI);
    }

    public static CenterlineEditResult splitAtLocalDistance(
            RoadNetwork network,
            String edgeId,
            double localDistance) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Vec2d splitPoint = RoadGeometryUtils.pointAtDistance(edge.getCenterlinePoints(), localDistance);
        if (splitPoint == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE);
        }
        // Atomic: topology + lineage + Crossing + EDITED on working copy only (Q04).
        RoadNetwork working = network.snapshot();
        RoadEdge workingEdge = working.getEdge(edgeId);
        if (workingEdge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Road roadBefore = workingEdge.getRoadId() != null ? working.getRoad(workingEdge.getRoadId()) : null;
        String priorEntryEdge = roadBefore != null ? roadBefore.getDesignEntryEdgeId() : null;
        String priorEntryNode = roadBefore != null ? roadBefore.getDesignEntryNodeId() : null;
        RoadLoopSeam priorSeam = roadBefore != null && roadBefore.getLoopSeam() != null
            ? roadBefore.getLoopSeam() : null;
        Optional<OrientedRoadSegment> priorOriented = roadBefore != null
            ? RoadStationing.orientedSegment(working, roadBefore, edgeId)
            : Optional.empty();
        boolean priorEditedValid = roadBefore != null
            && ResolvedProfileAuthority.hasValidEditedProfile(working, roadBefore);
        String roadId = workingEdge.getRoadId();
        Set<String> roadIds = roadId != null ? Set.of(roadId) : Set.of();
        RoadCrossingOwnershipMigrator.CaptureResult crossingCapture = roadIds.isEmpty()
            ? new RoadCrossingOwnershipMigrator.CaptureResult(List.of(), false)
            : RoadCrossingOwnershipMigrator.captureForRoads(working, roadIds);
        if (crossingCapture.hadUnresolved()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
        }

        Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(working)
            .splitEdgeAtLocalDistance(edgeId, localDistance);
        if (split.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.SPLIT_FAILED);
        }
        RoadGraphEdits.SplitResult result = split.get();
        migrateLineageAfterEdgeSplit(
            working, result, priorEntryEdge, priorEntryNode, priorSeam, priorOriented.orElse(null));
        if (!crossingCapture.isEmpty()) {
            RoadCrossingOwnershipMigrator.CaptureResult remapped =
                RoadCrossingOwnershipMigrator.remapAfterEdgeSplit(
                    crossingCapture,
                    result.oldEdgeId(),
                    result.firstEdgeId(),
                    result.secondEdgeId(),
                    result.splitGeometryLocal());
            RoadCrossingOwnershipMigrator.ApplyResult apply =
                RoadCrossingOwnershipMigrator.reapply(working, remapped);
            if (!apply.complete()) {
                return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
            }
        }
        if (roadId != null) {
            Road roadAfter = working.getRoad(roadId);
            if (roadAfter != null
                    && !RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                        working, roadAfter, priorEditedValid)) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
        }
        network.replaceContentsFrom(working);
        return finishEdit(
            network,
            result.firstEdgeId(),
            CenterlineEditResult.split(result.firstEdgeId(), result.secondEdgeId(), result.nodeId()),
            CenterlineEditOperation.SPLIT_EDGE);
    }

    public static CenterlineEditResult splitAtRoadStation(
            RoadNetwork network,
            Road road,
            String edgeId,
            double roadStation) {
        if (road == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        return RoadStationing.resolve(network, road, roadStation)
            .filter(segment -> edgeId.equals(segment.segmentId()))
            .map(segment -> splitAtLocalDistance(network, edgeId, segment.localDistance()))
            .orElseGet(() -> CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE));
    }

    /**
     * 删除分段折线的内部折点（PI）；不可删除起终点。
     */
    public static CenterlineEditResult removePiAtVertex(
            RoadNetwork network,
            String edgeId,
            int vertexIndex) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points == null || points.size() <= 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_VERTEX);
        }
        SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
        if (before == null && edge.getRoadId() != null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }
        List<Vec2d> updated = new ArrayList<>(points.size() - 1);
        for (int i = 0; i < points.size(); i++) {
            if (i == vertexIndex) {
                continue;
            }
            Vec2d point = points.get(i);
            updated.add(point != null ? point.copy() : null);
        }
        return commitSegmentGeometryEdit(
            network, edgeId, edge.getRoadId(), before, updated, CenterlineEditOperation.DELETE_PI);
    }

    /**
     * 圆角局部弧预览：切点 → 弧 → 切点（不含折线其余部分）。
     */
    public static List<Vec2d> previewFilletArc(List<Vec2d> points, int vertexIndex, double radius) {
        return RoadCenterlineFillet.filletLocalArc(points, vertexIndex, radius);
    }

    /**
     * 指定折点在当前腿长下可构造的最大圆角半径（画布域）；不可圆角时返回 0。
     */
    public static double maxFeasibleFilletRadius(List<Vec2d> points, int vertexIndex) {
        return RoadCenterlineFillet.maxFeasibleRadius(points, vertexIndex);
    }

    public static CenterlineEditResult filletVertex(
            RoadNetwork network,
            String edgeId,
            int vertexIndex,
            double radius) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        if (radius < MIN_FILLET_RADIUS || radius > MAX_FILLET_RADIUS) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_RADIUS);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_VERTEX);
        }
        List<Vec2d> filleted = RoadCenterlineFillet.filletVertex(points, vertexIndex, radius);
        if (filleted == null || filleted.size() < 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_RADIUS);
        }
        SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
        if (before == null && edge.getRoadId() != null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }
        return commitSegmentGeometryEdit(
            network, edgeId, edge.getRoadId(), before, filleted, CenterlineEditOperation.FILLET);
    }

    public static CenterlineEditResult mergeThroughNode(RoadNetwork network, String nodeId) {
        if (network == null || nodeId == null || nodeId.isBlank()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.NODE_NOT_FOUND);
        }
        RoadNode liveThrough = network.getNode(nodeId);
        if (liveThrough == null || liveThrough.getDegree() != 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        // Atomic: topology + lineage + Crossing + EDITED on working copy only (Q04).
        RoadNetwork working = network.snapshot();
        RoadNode through = working.getNode(nodeId);
        if (through == null || through.getDegree() != 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        List<String> connected = List.copyOf(through.getConnectedEdgeIds());
        if (connected.size() != 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        RoadEdge previewA = working.getEdge(connected.get(0));
        RoadEdge previewB = working.getEdge(connected.get(1));
        String roadId = previewA != null && previewA.getRoadId() != null
            ? previewA.getRoadId()
            : (previewB != null ? previewB.getRoadId() : null);
        Road roadBefore = roadId != null ? working.getRoad(roadId) : null;
        String priorEntryEdge = roadBefore != null ? roadBefore.getDesignEntryEdgeId() : null;
        String priorEntryNode = roadBefore != null ? roadBefore.getDesignEntryNodeId() : null;
        RoadLoopSeam priorSeam = roadBefore != null ? roadBefore.getLoopSeam() : null;
        // LOOP virtual seam at the deleted design-entry node: convert to explicit seam on merge
        // (do not shift zero by moving entry to the far endpoint).
        boolean convertVirtualSeam = roadBefore != null
            && roadBefore.getTopologyMode() == RoadTopologyMode.LOOP
            && priorEntryNode != null
            && priorEntryNode.equals(nodeId)
            && (priorSeam == null || !RoadLoopSeamService.isSeamResolvable(working, roadBefore));
        Vec2d virtualSeamOrigin = null;
        String seamPriorEntryEdge = priorEntryEdge;
        if (convertVirtualSeam) {
            if (through.getPosition() == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
            }
            virtualSeamOrigin = through.getPosition().copy();
            // Keep priorEntryNode as deleted id only for lineage; seam carries physical zero.
        } else if (priorEntryNode != null && priorEntryNode.equals(nodeId)) {
            String rebuildNode = survivingEndpointForDeletedEntry(
                previewA, previewB, nodeId, priorEntryEdge);
            if (rebuildNode == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
            }
            priorEntryNode = rebuildNode;
        }

        boolean priorEditedValid = roadBefore != null
            && ResolvedProfileAuthority.hasValidEditedProfile(working, roadBefore);
        Set<String> roadIds = roadId != null ? Set.of(roadId) : Set.of();
        RoadCrossingOwnershipMigrator.CaptureResult crossingCapture = roadIds.isEmpty()
            ? new RoadCrossingOwnershipMigrator.CaptureResult(List.of(), false)
            : RoadCrossingOwnershipMigrator.captureForRoads(working, roadIds);
        if (crossingCapture.hadUnresolved()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
        }

        Optional<RoadGraphEdits.MergeResult> merged = RoadGraphEdits.of(working).mergeThroughNode(nodeId);
        if (merged.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        RoadGraphEdits.MergeResult result = merged.get();
        migrateLineageAfterEdgeMerge(working, result, priorEntryEdge, priorEntryNode, priorSeam);
        if (virtualSeamOrigin != null) {
            if (!placeExplicitSeamAtMergeJoin(working, result, virtualSeamOrigin, seamPriorEntryEdge)) {
                return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
            }
        }
        if (!crossingCapture.isEmpty()) {
            RoadCrossingOwnershipMigrator.CaptureResult remapped =
                RoadCrossingOwnershipMigrator.remapAfterEdgeMerge(
                    crossingCapture,
                    result.firstOldEdgeId(),
                    result.secondOldEdgeId(),
                    result.mergedEdgeId(),
                    result.secondLocalOffset(),
                    result.firstOldLength(),
                    result.secondOldLength(),
                    result.firstReversed(),
                    result.secondReversed());
            RoadCrossingOwnershipMigrator.ApplyResult apply =
                RoadCrossingOwnershipMigrator.reapply(working, remapped);
            if (!apply.complete()) {
                return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
            }
        }
        if (roadId != null) {
            Road roadAfter = working.getRoad(roadId);
            if (roadAfter != null
                    && !RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                        working, roadAfter, priorEditedValid)) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
        }
        network.replaceContentsFrom(working);
        return finishEdit(
            network,
            result.mergedEdgeId(),
            CenterlineEditResult.merged(result.mergedEdgeId()),
            CenterlineEditOperation.MERGE_EDGE);
    }

    /**
     * Place an explicit LOOP seam at the former through-node (join) on the merged edge so the
     * physical station zero is preserved when the virtual design-entry node is deleted.
     * Design entry follows prior station-growth direction — never default storage start (Q05).
     */
    private static boolean placeExplicitSeamAtMergeJoin(
            RoadNetwork network,
            RoadGraphEdits.MergeResult merge,
            Vec2d origin,
            String priorEntryEdgeId) {
        if (network == null || merge == null || origin == null || merge.mergedEdgeId() == null) {
            return false;
        }
        RoadEdge merged = network.getEdge(merge.mergedEdgeId());
        if (merged == null || merged.getRoadId() == null || merged.getLength() <= 1e-9) {
            return false;
        }
        Road road = network.getRoad(merged.getRoadId());
        if (road == null) {
            return false;
        }
        double joinLocal = Math.max(0.0, Math.min(merged.getLength(), merge.secondLocalOffset()));
        double frac = joinLocal / merged.getLength();
        road.setLoopSeam(RoadLoopSeam.onSegment(origin.copy(), merged.getId(), frac));
        road.setDesignEntryEdgeId(merged.getId());
        String entryNode = designEntryNodeAfterVirtualSeamMerge(merged, merge, priorEntryEdgeId);
        if (entryNode == null) {
            return false;
        }
        road.setDesignEntryNodeId(entryNode);
        return RoadLoopSeamService.isSeamResolvable(network, road);
    }

    /**
     * Pick the merged-edge endpoint so oriented walk leaves the seam in the same direction the
     * prior design entry edge grew from the deleted through-node.
     */
    private static String designEntryNodeAfterVirtualSeamMerge(
            RoadEdge merged,
            RoadGraphEdits.MergeResult merge,
            String priorEntryEdgeId) {
        if (merged == null || merge == null) {
            return null;
        }
        // Merged storage: start --firstOld--> join --secondOld--> end.
        // Prior entry at join walking into secondOld → grow toward end → entry at start.
        // Prior entry at join walking into firstOld → grow toward start → entry at end.
        if (priorEntryEdgeId != null && priorEntryEdgeId.equals(merge.firstOldEdgeId())) {
            return merged.getEndNodeId();
        }
        if (priorEntryEdgeId != null && priorEntryEdgeId.equals(merge.secondOldEdgeId())) {
            return merged.getStartNodeId();
        }
        // No prior edge id: prefer forward merge orientation (non-reversed second).
        if (merge.secondReversed() && !merge.firstReversed()) {
            return merged.getEndNodeId();
        }
        return merged.getStartNodeId();
    }

    /**
     * When merge deletes the design-entry node, keep the far endpoint of the entry edge.
     */
    private static String survivingEndpointForDeletedEntry(
            RoadEdge edgeA,
            RoadEdge edgeB,
            String throughNodeId,
            String priorEntryEdgeId) {
        RoadEdge entryEdge = null;
        if (priorEntryEdgeId != null) {
            if (edgeA != null && priorEntryEdgeId.equals(edgeA.getId())) {
                entryEdge = edgeA;
            } else if (edgeB != null && priorEntryEdgeId.equals(edgeB.getId())) {
                entryEdge = edgeB;
            }
        }
        if (entryEdge == null) {
            return null;
        }
        if (throughNodeId.equals(entryEdge.getStartNodeId())) {
            return entryEdge.getEndNodeId();
        }
        if (throughNodeId.equals(entryEdge.getEndNodeId())) {
            return entryEdge.getStartNodeId();
        }
        return null;
    }

    /** Remap seam hint + design entry after Split Edge using pre-captured lineage. */
    static void migrateLineageAfterEdgeSplit(
            RoadNetwork network,
            RoadGraphEdits.SplitResult split,
            String priorEntryEdge,
            String priorEntryNode,
            RoadLoopSeam priorSeam,
            OrientedRoadSegment priorOriented) {
        if (network == null || split == null || split.oldEdgeId() == null) {
            return;
        }
        RoadEdge first = network.getEdge(split.firstEdgeId());
        RoadEdge second = network.getEdge(split.secondEdgeId());
        if (first == null || second == null || first.getRoadId() == null) {
            return;
        }
        Road road = network.getRoad(first.getRoadId());
        if (road == null) {
            return;
        }
        if (split.oldEdgeId().equals(priorEntryEdge)) {
            String entryNode = priorEntryNode;
            if (entryNode == null && priorOriented != null) {
                entryNode = priorOriented.entryNodeId();
            }
            // Never use second.getStartNodeId() (always the new split node) as design entry.
            if (entryNode != null && entryNode.equals(second.getEndNodeId())) {
                road.setDesignEntryEdgeId(second.getId());
                road.setDesignEntryNodeId(second.getEndNodeId());
            } else if (entryNode != null && entryNode.equals(first.getStartNodeId())) {
                road.setDesignEntryEdgeId(first.getId());
                road.setDesignEntryNodeId(first.getStartNodeId());
            } else if (priorOriented != null && !priorOriented.forward()) {
                // Reverse storage: design walk enters at old storage end → second child.
                road.setDesignEntryEdgeId(second.getId());
                road.setDesignEntryNodeId(second.getEndNodeId());
            } else {
                road.setDesignEntryEdgeId(first.getId());
                road.setDesignEntryNodeId(
                    entryNode != null ? entryNode : first.getStartNodeId());
            }
        }
        RoadLoopSeam seam = priorSeam != null ? priorSeam : road.getLoopSeam();
        if (seam == null || seam.segmentHintId() == null
                || !seam.segmentHintId().equals(split.oldEdgeId())
                || seam.localFraction() == null
                || !Double.isFinite(split.splitGeometryLocal())) {
            return;
        }
        double oldLength = first.getLength() + second.getLength();
        if (oldLength <= 1e-9) {
            return;
        }
        double seamLocal = seam.localFraction() * oldLength;
        Vec2d seamPos = seam.position() != null ? seam.position().copy() : null;
        if (seamLocal <= split.splitGeometryLocal() + 1e-9) {
            double frac = first.getLength() > 1e-9
                ? Math.max(0.0, Math.min(1.0, seamLocal / first.getLength()))
                : 0.0;
            road.setLoopSeam(RoadLoopSeam.onSegment(seamPos, first.getId(), frac));
        } else {
            double localOnSecond = seamLocal - split.splitGeometryLocal();
            double frac = second.getLength() > 1e-9
                ? Math.max(0.0, Math.min(1.0, localOnSecond / second.getLength()))
                : 0.0;
            road.setLoopSeam(RoadLoopSeam.onSegment(seamPos, second.getId(), frac));
        }
    }

    /** Remap seam hint + design entry after Merge Edge using reverse-aware lineage. */
    static void migrateLineageAfterEdgeMerge(
            RoadNetwork network,
            RoadGraphEdits.MergeResult merge,
            String priorEntryEdge,
            String priorEntryNode,
            RoadLoopSeam priorSeam) {
        if (network == null || merge == null || merge.mergedEdgeId() == null) {
            return;
        }
        RoadEdge merged = network.getEdge(merge.mergedEdgeId());
        if (merged == null || merged.getRoadId() == null) {
            return;
        }
        Road road = network.getRoad(merged.getRoadId());
        if (road == null) {
            return;
        }
        if (merge.firstOldEdgeId().equals(priorEntryEdge)
                || merge.secondOldEdgeId().equals(priorEntryEdge)) {
            road.setDesignEntryEdgeId(merged.getId());
            if (priorEntryNode != null
                    && (priorEntryNode.equals(merged.getStartNodeId())
                        || priorEntryNode.equals(merged.getEndNodeId()))) {
                road.setDesignEntryNodeId(priorEntryNode);
            } else if (priorEntryNode != null) {
                road.setDesignEntryNodeId(priorEntryNode);
            } else {
                road.setDesignEntryNodeId(merged.getStartNodeId());
            }
        }
        RoadLoopSeam seam = priorSeam != null ? priorSeam : road.getLoopSeam();
        if (seam == null || seam.segmentHintId() == null || seam.localFraction() == null) {
            return;
        }
        String hint = seam.segmentHintId();
        if (!hint.equals(merge.firstOldEdgeId()) && !hint.equals(merge.secondOldEdgeId())) {
            return;
        }
        double mergedLength = merged.getLength();
        if (mergedLength <= 1e-9) {
            return;
        }
        double local;
        if (hint.equals(merge.firstOldEdgeId())) {
            double oldLen = merge.firstOldLength() > 1e-9
                ? merge.firstOldLength() : Math.max(merge.secondLocalOffset(), 0.0);
            double dOld = seam.localFraction() * oldLen;
            if (merge.firstReversed()) {
                dOld = oldLen - dOld;
            }
            local = dOld;
        } else {
            double oldLen = merge.secondOldLength() > 1e-9
                ? merge.secondOldLength()
                : Math.max(0.0, mergedLength - merge.secondLocalOffset());
            double dOld = seam.localFraction() * oldLen;
            if (merge.secondReversed()) {
                dOld = oldLen - dOld;
            }
            local = merge.secondLocalOffset() + dOld;
        }
        road.setLoopSeam(RoadLoopSeam.onSegment(
            seam.position() != null ? seam.position().copy() : null,
            merged.getId(),
            Math.max(0.0, Math.min(1.0, local / mergedLength))));
    }

    public static CenterlineEditResult reverseEdge(RoadNetwork network, String edgeId) {
        RoadEdge liveEdge = network != null ? network.getEdge(edgeId) : null;
        if (liveEdge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Set<String> roadIds = liveEdge.getRoadId() != null ? Set.of(liveEdge.getRoadId()) : Set.of();

        // Working-copy transform: failures leave live untouched.
        RoadNetwork working = network.snapshot();
        RoadCrossingOwnershipMigrator.CaptureResult crossingCapture =
            RoadCrossingOwnershipMigrator.captureForRoads(working, roadIds);
        if (crossingCapture.hadUnresolved()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
        }

        CenterlineEditResult result = reverseEdgeGeometry(working, edgeId);
        if (!result.isSuccess()) {
            return result;
        }
        if (!crossingCapture.isEmpty()) {
            RoadCrossingOwnershipMigrator.CaptureResult remapped =
                RoadCrossingOwnershipMigrator.remapLocalDistancesAfterStorageReverse(
                    working, crossingCapture, Set.of(edgeId));
            RoadCrossingOwnershipMigrator.ApplyResult apply =
                RoadCrossingOwnershipMigrator.reapply(working, remapped);
            if (!apply.complete()) {
                return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
            }
        }
        CenterlinePhase2ConsistencyPolicy.afterReverseEdge(working, edgeId);

        String roadId = liveEdge.getRoadId();
        if (roadId != null) {
            com.plot.plugin.road.ui.editor.CenterlineEditorApplier.applyToLive(network, working, roadId);
        } else {
            // Orphan edge: copy geometry endpoints only.
            RoadEdge workingEdge = working.getEdge(edgeId);
            if (workingEdge != null) {
                network.updateEdgeEndpoints(
                    edgeId, workingEdge.getStartNodeId(), workingEdge.getEndNodeId());
                liveEdge.setCenterlinePoints(workingEdge.getCenterlinePoints());
                liveEdge.setSlopeOverrides(workingEdge.getSlopeOverrides());
            }
        }
        RoadCrossingRegistrySync.copyForRoads(network, working, roadIds);
        return CenterlineEditResult.success();
    }

    static CenterlineEditResult reverseEdgeGeometry(RoadNetwork network, String edgeId) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }

        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i).copy());
        }
        edge.setCenterlinePoints(reversed);

        String oldStart = edge.getStartNodeId();
        String oldEnd = edge.getEndNodeId();
        edge.setStartNodeId(oldEnd);
        edge.setEndNodeId(oldStart);
        edge.setSlopeOverrides(reverseSlopeOverrides(edge.getSlopeOverrides(), edge.getLength()));
        mirrorSeamLocalFractionOnEdge(network, edge);
        return CenterlineEditResult.success();
    }

    public static CenterlineEditResult reverseRoad(RoadNetwork network, Road road) {
        if (network == null || road == null || network.getRoad(road.getId()) == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        // Read-only live checks before snapshot — do not ensureBound on live.
        if (!RoadStationing.isStationable(network, road)) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_STATIONABLE);
        }
        List<String> ordered = RoadSegmentOrdering.orderedSegmentIds(network, road);
        if (ordered.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }

        // Transform on an isolated snapshot; preflight/bind only on working.
        RoadNetwork working = network.snapshot();
        Road workingRoad = working.getRoad(road.getId());
        if (workingRoad == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        CenterlineEditResult preflight = preflightReverseRoad(working, workingRoad);
        if (!preflight.isSuccess()) {
            return preflight;
        }

        boolean priorEditedValid = ResolvedProfileAuthority.hasValidEditedProfile(working, workingRoad);
        RoadCrossingOwnershipMigrator.CaptureResult crossingCapture =
            RoadCrossingOwnershipMigrator.captureForRoads(working, Set.of(workingRoad.getId()));
        if (crossingCapture.hadUnresolved()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
        }
        double lengthBefore = RoadStationing.canonicalLength(working, workingRoad);
        List<PhysicalSample> samplesBefore = samplePhysicalState(working, workingRoad);
        if (lengthBefore > 0.0 && samplesBefore.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }
        Set<String> reversedEdges = new HashSet<>(ordered);
        RoadCrossSection oldDefault = workingRoad.getCrossSection() != null
            ? workingRoad.getCrossSection().copy()
            : new RoadCrossSection();
        RoadVariableCrossSections oldVcs = workingRoad.getVariableCrossSections();

        for (String edgeId : ordered) {
            CenterlineEditResult result = reverseEdgeGeometry(working, edgeId);
            if (!result.isSuccess()) {
                return result;
            }
        }

        List<String> reversedOrder = new ArrayList<>(ordered);
        java.util.Collections.reverse(reversedOrder);
        workingRoad.reorderSegments(reversedOrder);
        RoadDesignDirectionAuthority.flipAfterReverseRoad(working, workingRoad);

        double totalLength = RoadStationing.canonicalLength(working, workingRoad);
        if (workingRoad.getHorizontalAlignment() != null) {
            workingRoad.setHorizontalAlignment(reverseHorizontalAlignment(workingRoad.getHorizontalAlignment()));
        }
        if (workingRoad.getVerticalAlignment() != null) {
            RoadVerticalAlignment reversedVertical = RoadStationMirroring.mirrorVerticalAlignment(
                workingRoad.getVerticalAlignment(),
                totalLength
            );
            if (reversedVertical == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
            workingRoad.setVerticalAlignment(reversedVertical);
        }

        // D02: mirror VCS from pre-mirror default, then set mirrored default once.
        if (oldVcs != null && !oldVcs.isEmpty()) {
            workingRoad.setVariableCrossSections(
                RoadStationMirroring.mirrorVariableCrossSections(oldVcs, oldDefault, totalLength));
        }
        if (workingRoad.getStationFacilities() != null && !workingRoad.getStationFacilities().isEmpty()) {
            workingRoad.setStationFacilities(
                RoadStationMirroring.mirrorStationFacilities(workingRoad.getStationFacilities(), totalLength));
        }
        workingRoad.setCrossSection(oldDefault.mirroredForReverseDirection());

        if (!crossingCapture.isEmpty()) {
            RoadCrossingOwnershipMigrator.CaptureResult remapped =
                RoadCrossingOwnershipMigrator.remapLocalDistancesAfterStorageReverse(
                    working, crossingCapture, reversedEdges);
            RoadCrossingOwnershipMigrator.ApplyResult apply =
                RoadCrossingOwnershipMigrator.reapply(working, remapped);
            if (!apply.complete()) {
                return CenterlineEditResult.failure(CenterlineEditStatus.CROSSING_MIGRATION_FAILED);
            }
        }

        if (!physicalStateEquivalent(samplesBefore, lengthBefore, working, workingRoad, totalLength)) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }

        // D05: rebind EDITED only when the prior binding was already valid.
        if (priorEditedValid) {
            workingRoad.setProfileSource(ProfileSource.EDITED);
            workingRoad.setEditedGeometrySignature(
                ProfileCompileInputsFingerprint.geometryHash(working, workingRoad));
        }

        com.plot.plugin.road.ui.editor.CenterlineEditorApplier.applyToLive(network, working, road.getId());
        RoadCrossingRegistrySync.copyForRoads(network, working, Set.of(road.getId()));
        return CenterlineEditResult.success();
    }

    private record PhysicalSample(
            double station,
            double elevation,
            Vec2d haWorld,
            Vec2d instanceWorld) {
    }

    private static List<PhysicalSample> samplePhysicalState(RoadNetwork network, Road road) {
        List<PhysicalSample> samples = new ArrayList<>();
        double length = RoadStationing.canonicalLength(network, road);
        if (!(length > 0.0)) {
            return samples;
        }
        boolean evaluableVa = VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment());
        for (double s : sampleStationsForReverseVerify(network, road, length)) {
            double elevation = evaluableVa
                ? VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), s).orElse(Double.NaN)
                : Double.NaN;
            Vec2d haWorld = RoadStationing.pointAtStation(network, road, s).orElse(null);
            Vec2d instanceWorld = RoadStationing.instancePointAtStation(network, road, s).orElse(null);
            samples.add(new PhysicalSample(
                s,
                elevation,
                haWorld != null ? haWorld.copy() : null,
                instanceWorld != null ? instanceWorld.copy() : null));
        }
        return samples;
    }

    /**
     * Bounded verify stations: endpoints, edge boundaries, polyline/HA corners, uniform stride.
     */
    private static List<Double> sampleStationsForReverseVerify(
            RoadNetwork network,
            Road road,
            double length) {
        TreeSet<Double> stations = new TreeSet<>();
        stations.add(0.0);
        stations.add(length);
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            double start = RoadStationing.toCanonicalChainage(network, road, segment.startStation());
            double end = RoadStationing.toCanonicalChainage(network, road, segment.endStation());
            if (Double.isFinite(start)) {
                stations.add(clampStation(start, length));
            }
            if (Double.isFinite(end)) {
                stations.add(clampStation(end, length));
            }
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null || edge.getCenterlinePoints() == null) {
                continue;
            }
            double walked = 0.0;
            List<Vec2d> points = edge.getCenterlinePoints();
            int vertexBudget = 32;
            for (int i = 0; i < points.size() && vertexBudget > 0; i++) {
                if (i > 0 && points.get(i - 1) != null && points.get(i) != null) {
                    walked += points.get(i - 1).distance(points.get(i));
                }
                double chainLocal = segment.chainLocalFromGeometryLocal(walked);
                double canonical = RoadStationing.toCanonicalChainage(
                    network, road, segment.startStation() + chainLocal);
                if (Double.isFinite(canonical)) {
                    stations.add(clampStation(canonical, length));
                    vertexBudget--;
                }
            }
        }
        RoadHorizontalAlignment ha = road.getHorizontalAlignment();
        if (ha != null && !ha.isEmpty()) {
            double nativeWalked = 0.0;
            for (HorizontalAlignmentElement element : ha.getElements()) {
                nativeWalked += element.getLength();
                double canonical = RoadPlanGeometry.canonicalFromHaNative(
                    network, road, nativeWalked, length);
                if (Double.isFinite(canonical)) {
                    stations.add(clampStation(canonical, length));
                }
            }
        }
        double stride = Math.max(2.0, length / 32.0);
        for (double s = stride; s < length - 1e-9; s += stride) {
            stations.add(clampStation(s, length));
        }
        return List.copyOf(stations);
    }

    private static double clampStation(double station, double length) {
        if (!(length > 0.0)) {
            return 0.0;
        }
        if (station <= 0.0) {
            return 0.0;
        }
        if (station >= length) {
            return length;
        }
        return station;
    }

    private static boolean physicalStateEquivalent(
            List<PhysicalSample> before,
            double lengthBefore,
            RoadNetwork network,
            Road road,
            double totalLength) {
        if (!(totalLength > 0.0) || !(lengthBefore > 0.0)) {
            return false;
        }
        if (Math.abs(lengthBefore - totalLength) > 1e-3) {
            return false;
        }
        if (before.isEmpty()) {
            return false;
        }
        boolean evaluableVa = VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment());
        for (PhysicalSample sample : before) {
            if (sample.haWorld() == null || sample.instanceWorld() == null) {
                return false;
            }
            double mirroredStation = totalLength - sample.station();
            Vec2d afterHa = RoadStationing.pointAtStation(network, road, mirroredStation).orElse(null);
            Vec2d afterInstance = RoadStationing.instancePointAtStation(network, road, mirroredStation)
                .orElse(null);
            // Each domain must preserve its own physical identity under s' = L - s.
            if (afterHa == null || afterHa.distance(sample.haWorld()) > 1e-2) {
                return false;
            }
            if (afterInstance == null || afterInstance.distance(sample.instanceWorld()) > 1e-2) {
                return false;
            }
            if (evaluableVa) {
                if (!Double.isFinite(sample.elevation())) {
                    return false;
                }
                double actual = VerticalAlignmentGeometry.elevationAt(
                    road.getVerticalAlignment(), mirroredStation).orElse(Double.NaN);
                if (!Double.isFinite(actual) || Math.abs(sample.elevation() - actual) > 1e-3) {
                    return false;
                }
            }
        }
        return true;
    }

    static CenterlineEditResult preflightReverseRoad(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_STATIONABLE);
        }
        RoadHorizontalAlignment ha = road.getHorizontalAlignment();
        if (ha != null) {
            for (HorizontalAlignmentElement element : ha.getElements()) {
                if (element.getType() == HorizontalAlignmentElementType.SPIRAL) {
                    return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
                }
            }
        }
        if (road.getVerticalAlignment() != null) {
            if (!road.getVerticalAlignment().hasStrictlyIncreasingStorageOrder()) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
            var sorted = road.getVerticalAlignment().sortedPvis();
            if (!sorted.isEmpty() && Math.abs(sorted.getFirst().getStation()) > 1e-3) {
                // Incomplete VA (non-zero start) — refuse rather than shift-domain mirror.
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
        }
        RoadDesignDirectionAuthority.ensureBound(network, road);
        if (road.getTopologyMode() == com.plot.plugin.road.model.RoadTopologyMode.LOOP
                && road.getOrderedSegmentIds().size() == 1) {
            // Single-edge self-loop cannot express opposite circulation via entry-edge flip.
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_STATIONABLE);
        }
        return CenterlineEditResult.success();
    }

    /** Seam localFraction is geometry-storage domain; reverse storage → f' = 1 - f. */
    static void mirrorSeamLocalFractionOnEdge(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null || edge.getRoadId() == null) {
            return;
        }
        Road road = network.getRoad(edge.getRoadId());
        if (road == null || road.getLoopSeam() == null) {
            return;
        }
        RoadLoopSeam seam = road.getLoopSeam();
        if (seam.segmentHintId() == null || !seam.segmentHintId().equals(edge.getId())) {
            return;
        }
        if (seam.localFraction() == null) {
            return;
        }
        road.setLoopSeam(RoadLoopSeam.onSegment(
            seam.position() != null ? seam.position().copy() : null,
            seam.segmentHintId(),
            1.0 - seam.localFraction()));
    }

    public static boolean canMergeAtNode(RoadNetwork network, String nodeId) {
        if (network == null || nodeId == null) {
            return false;
        }
        var node = network.getNode(nodeId);
        if (node == null || node.getDegree() != 2) {
            return false;
        }
        List<String> edgeIds = List.copyOf(node.getConnectedEdgeIds());
        if (edgeIds.size() != 2) {
            return false;
        }
        RoadEdge edgeA = network.getEdge(edgeIds.get(0));
        RoadEdge edgeB = network.getEdge(edgeIds.get(1));
        if (edgeA == null || edgeB == null) {
            return false;
        }
        String roadA = edgeA.getRoadId();
        String roadB = edgeB.getRoadId();
        return roadA != null && roadA.equals(roadB);
    }

    private static List<RoadEdge.SlopeOverride> reverseSlopeOverrides(
            List<RoadEdge.SlopeOverride> overrides,
            double length) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }
        List<RoadEdge.SlopeOverride> reversed = new ArrayList<>();
        for (RoadEdge.SlopeOverride override : overrides) {
            reversed.add(new RoadEdge.SlopeOverride(
                length - override.endDistance,
                length - override.startDistance,
                override.maxSlope
            ));
        }
        return reversed;
    }

    static RoadHorizontalAlignment reverseHorizontalAlignment(RoadHorizontalAlignment alignment) {
        return HorizontalAlignmentGeometry.reverse(alignment);
    }

    static RoadVerticalAlignment reverseVerticalAlignment(
            RoadVerticalAlignment alignment,
            double totalLength) {
        if (alignment == null || alignment.isEmpty()) {
            return alignment != null ? alignment.copy() : null;
        }
        if (!alignment.hasStrictlyIncreasingStorageOrder()) {
            return null;
        }
        return RoadStationMirroring.mirrorVerticalAlignment(alignment, totalLength);
    }

    private static double normalizeAngle(double radians) {
        double angle = radians;
        while (angle <= -Math.PI) {
            angle += 2.0 * Math.PI;
        }
        while (angle > Math.PI) {
            angle -= 2.0 * Math.PI;
        }
        return angle;
    }

    /**
     * Mutate geometry on a working copy; refuse leaves live network unchanged (Q06).
     */
    private static CenterlineEditResult commitSegmentGeometryEdit(
            RoadNetwork network,
            String edgeId,
            String roadId,
            SegmentGeometrySnapshot before,
            List<Vec2d> newPoints,
            CenterlineEditOperation operation) {
        if (roadId != null && !roadId.isBlank()) {
            RoadNetwork working = network.snapshot();
            RoadEdge workingEdge = working.getEdge(edgeId);
            if (workingEdge == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
            }
            workingEdge.setCenterlinePoints(newPoints);
            if (before != null
                    && !CenterlinePhase2ConsistencyPolicy.afterSegmentGeometryEdit(
                        working, operation, edgeId, before)) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
            com.plot.plugin.road.ui.editor.CenterlineEditorApplier.applyToLive(network, working, roadId);
            return CenterlineEditResult.success();
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        edge.setCenterlinePoints(newPoints);
        return CenterlineEditResult.success();
    }

    private static CenterlineEditResult finishEdit(
            RoadNetwork network,
            String edgeId,
            CenterlineEditResult result,
            CenterlineEditOperation operation) {
        return finishEdit(network, edgeId, null, result, operation);
    }

    private static CenterlineEditResult finishEdit(
            RoadNetwork network,
            String edgeId,
            SegmentGeometrySnapshot before,
            CenterlineEditResult result,
            CenterlineEditOperation operation) {
        if (result.isSuccess()) {
            if (before != null) {
                if (!CenterlinePhase2ConsistencyPolicy.afterSegmentGeometryEdit(
                        network, operation, edgeId, before)) {
                    return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
                }
            } else if (operation.defaultStationPolicy() == CenterlineEditStationPolicy.PRESERVE_STATION) {
                CenterlinePhase2ConsistencyPolicy.afterGraphTopologyEdit(network, operation, edgeId);
            }
        }
        return result;
    }
}
