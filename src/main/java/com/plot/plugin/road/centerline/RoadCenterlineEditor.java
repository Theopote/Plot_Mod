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
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.station.CenterlineEditOperation;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadDesignDirectionAuthority;
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
        edge.setCenterlinePoints(updated.get());
        return finishEdit(
            network,
            edgeId,
            before,
            CenterlineEditResult.success(),
            CenterlineEditOperation.INSERT_PI);
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
        // Local-arc split: keep segment index; never re-pick nearest segment by plane coords.
        Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network)
            .splitEdgeAtLocalDistance(edgeId, localDistance);
        if (split.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.SPLIT_FAILED);
        }
        RoadGraphEdits.SplitResult result = split.get();
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
        List<Vec2d> updated = new ArrayList<>(points.size() - 1);
        for (int i = 0; i < points.size(); i++) {
            if (i == vertexIndex) {
                continue;
            }
            Vec2d point = points.get(i);
            updated.add(point != null ? point.copy() : null);
        }
        edge.setCenterlinePoints(updated);
        return finishEdit(
            network,
            edgeId,
            before,
            CenterlineEditResult.success(),
            CenterlineEditOperation.DELETE_PI);
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
        edge.setCenterlinePoints(filleted);
        return finishEdit(
            network,
            edgeId,
            before,
            CenterlineEditResult.success(),
            CenterlineEditOperation.FILLET);
    }

    public static CenterlineEditResult mergeThroughNode(RoadNetwork network, String nodeId) {
        if (network == null || nodeId == null || nodeId.isBlank()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.NODE_NOT_FOUND);
        }
        Optional<String> merged = RoadGraphEdits.of(network).mergeThroughNode(nodeId);
        if (merged.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        return finishEdit(network, merged.get(), CenterlineEditResult.merged(merged.get()), CenterlineEditOperation.MERGE_EDGE);
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
        if (alignment == null || alignment.isEmpty()) {
            return alignment != null ? alignment.copy() : null;
        }
        double total = alignment.totalLength();
        var endPose = HorizontalAlignmentGeometry.poseAt(alignment, total).orElse(null);
        if (endPose == null) {
            return alignment.copy();
        }
        List<HorizontalAlignmentElement> reversed = new ArrayList<>();
        List<HorizontalAlignmentElement> elements = alignment.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            reversed.add(reverseHorizontalElement(elements.get(i)));
        }
        RoadHorizontalAlignment reversedAlignment = new RoadHorizontalAlignment(
            new Vec2d(endPose.x(), endPose.y()),
            normalizeAngle(endPose.bearingRadians() + Math.PI),
            reversed
        );
        return reversedAlignment;
    }

    private static HorizontalAlignmentElement reverseHorizontalElement(HorizontalAlignmentElement element) {
        return switch (element.getType()) {
            case TANGENT -> HorizontalAlignmentElement.tangent(element.getLength());
            case CIRCULAR_ARC -> HorizontalAlignmentElement.circularArc(
                element.getLength(),
                element.getRadius(),
                element.getDirection() == TurnDirection.LEFT ? TurnDirection.RIGHT : TurnDirection.LEFT
            );
            case SPIRAL -> HorizontalAlignmentElement.spiral(
                element.getLength(),
                element.getSpiralParameterA()
            );
        };
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
                CenterlinePhase2ConsistencyPolicy.afterSegmentGeometryEdit(
                    network,
                    operation,
                    edgeId,
                    before);
            } else {
                CenterlinePhase2ConsistencyPolicy.afterGraphTopologyEdit(network, operation, edgeId);
            }
        }
        return result;
    }
}
