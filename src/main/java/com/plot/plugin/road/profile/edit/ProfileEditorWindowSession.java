package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.crossing.RoadStackCrossingRef;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.profile.RoadProfileChartAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.EditedProfileRebindValidator;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 纵断面编辑器窗口级事务：打开时捕获快照，编辑期间写入工作副本，确认时一次性写回 live。
 */
public final class ProfileEditorWindowSession {

    public enum ConfirmResult {
        /** 无改动，可直接关闭 */
        NO_CHANGES,
        /** 已写回 live */
        APPLIED,
        /** Crossing constraints could not be satisfied; keep the editable transaction open. */
        CONSTRAINT_CONFLICT,
        /** live 在外部被修改，拒绝覆盖 */
        CONFLICT
    }

    private final RoadNetwork openSnapshot;
    private final long openRevision;
    private RoadNetwork workingNetwork;
    private final Set<String> touchedRoadIds = new LinkedHashSet<>();
    private final Set<String> touchedNodeIds = new LinkedHashSet<>();
    private final Set<String> touchedCrossingIds = new LinkedHashSet<>();
    private final Map<String, VoxelProfilePreviewSlot> voxelPreviews = new LinkedHashMap<>();
    private final ProfileEditorUndoHistory undoHistory = new ProfileEditorUndoHistory();
    private Set<String> unresolvedConstraints = Set.of();

    public Set<String> unresolvedConstraints() {
        return unresolvedConstraints;
    }

    private ProfileEditorWindowSession(RoadNetwork openSnapshot, long openRevision) {
        this.openSnapshot = Objects.requireNonNull(openSnapshot, "openSnapshot");
        this.openRevision = openRevision;
        this.workingNetwork = openSnapshot.snapshot();
    }

    public static ProfileEditorWindowSession open(RoadNetwork live, long openRevision) {
        Objects.requireNonNull(live, "live");
        RoadNetwork snapshot = live.snapshot();
        return new ProfileEditorWindowSession(snapshot, openRevision);
    }

    public RoadNetwork openSnapshot() {
        return openSnapshot;
    }

    public long openRevision() {
        return openRevision;
    }

    public RoadNetwork editingNetwork() {
        return workingNetwork;
    }

    /** Adopt existing terrain samples as derived editor data, without making the window dirty. */
    public void seedSampledProfiles(RoadSystemConfig config, Map<String, RoadGenerationResult> edgeResults) {
        for (Road road : workingNetwork.getRoads().values()) {
            seedRoadEditorAlignment(road, config, edgeResults, null);
        }
        syncCrossingAnchorsToSnapshot(config);
    }

    /**
     * Ensure the working copy has an evaluable alignment for editor display.
     * Does not mark the window dirty.
     */
    public void ensureRoadEditorAlignment(
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            RoadProfileChartData fallbackChart) {
        if (road == null || config == null) {
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            float maxGrade = road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope();
            FlatVerticalIntentSupport.syncCompiledAlignment(workingNetwork, road, maxGrade);
            return;
        }
        if (VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            return;
        }
        if (seedRoadEditorAlignment(road, config, edgeResults, fallbackChart)) {
            syncCrossingAnchorsToSnapshot(config);
        }
    }

    /** @return true when a new evaluable alignment was written to the working copy */
    public boolean seedRoadEditorAlignment(
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            RoadProfileChartData fallbackChart) {
        if (road == null || config == null) {
            return false;
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return false;
        }
        if (VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            return false;
        }
        Optional<RoadProfileChartData> chart = RoadProfileChartAssembler.assemble(
            workingNetwork, road, config, edgeResults);
        if (chart.isEmpty() && fallbackChart != null && fallbackChart.hasProfileData()) {
            chart = Optional.of(fallbackChart);
        }
        if (chart.isPresent() && applyChartToRoadAlignment(road, chart.get())) {
            return true;
        }
        if (fallbackChart != null && fallbackChart.hasProfileData()) {
            return bootstrapEndpointAlignmentFromChart(road, fallbackChart);
        }
        return false;
    }

    private boolean applyChartToRoadAlignment(Road road, RoadProfileChartData chart) {
        List<PointOfVerticalIntersection> points = buildPvisFromChart(chart);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(points);
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return bootstrapEndpointAlignmentFromChart(road, chart);
        }
        applyAlignmentToWorkingAndSnapshot(road, alignment);
        return true;
    }

    private static List<PointOfVerticalIntersection> buildPvisFromChart(RoadProfileChartData chart) {
        var points = new ArrayList<PointOfVerticalIntersection>();
        for (int i = 0; i < chart.stations().size(); i++) {
            double elevation = chart.previewElevationAt(chart.stations().get(i));
            if (Double.isFinite(elevation)) {
                double station = chart.stations().get(i);
                while (points.size() >= 2) {
                    var a = points.get(points.size() - 2);
                    var b = points.getLast();
                    double expected = a.getElevation() + (elevation - a.getElevation())
                        * (b.getStation() - a.getStation()) / (station - a.getStation());
                    if (Math.abs(expected - b.getElevation()) > 1e-8) {
                        break;
                    }
                    points.removeLast();
                }
                points.add(PointOfVerticalIntersection.of(station, elevation));
            }
        }
        return points;
    }

    private boolean bootstrapEndpointAlignmentFromChart(Road road, RoadProfileChartData chart) {
        if (!RoadStationing.isStationable(workingNetwork, road) || !chart.hasProfileData()) {
            return false;
        }
        double length = RoadStationing.canonicalLength(workingNetwork, road);
        if (!Double.isFinite(length) || length <= 1e-6) {
            return false;
        }
        double start = chart.previewElevationAt(0.0);
        double end = chart.previewElevationAt(length);
        if (!Double.isFinite(start)) {
            start = chart.groundElevationAt(0.0);
        }
        if (!Double.isFinite(end)) {
            end = chart.groundElevationAt(length);
        }
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            return false;
        }
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, start),
            PointOfVerticalIntersection.of(length, end)));
        applyAlignmentToWorkingAndSnapshot(road, alignment);
        return true;
    }

    private void applyAlignmentToWorkingAndSnapshot(Road road, RoadVerticalAlignment alignment) {
        RoadVerticalMode mode = road.getVerticalMode();
        road.setVerticalAlignment(alignment);
        road.setVerticalMode(mode);
        Road snapshotRoad = openSnapshot.getRoad(road.getId());
        if (snapshotRoad != null) {
            snapshotRoad.setVerticalAlignment(alignment.copy());
            snapshotRoad.setVerticalMode(mode);
        }
    }

    private void syncCrossingAnchorsToSnapshot(RoadSystemConfig config) {
        var anchored = com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer
            .seedCrossingAnchors(workingNetwork, config);
        for (String roadId : anchored) {
            Road derived = workingNetwork.getRoad(roadId);
            Road original = openSnapshot.getRoad(roadId);
            if (derived == null || original == null) {
                continue;
            }
            original.setVerticalAlignment(derived.getVerticalAlignment().copy());
            original.setVerticalMode(derived.getVerticalMode());
        }
    }

    public boolean isDirty() {
        return ProfileEditorNetworkApplier.hasVerticalStateChanges(openSnapshot, workingNetwork);
    }

    /** 确认保存时可能写入 live 路网的道路数量（含平交联动）。 */
    public int estimateConfirmAffectRoadCount() {
        if (!isDirty()) {
            return 0;
        }
        LinkedHashSet<String> affected = new LinkedHashSet<>(touchedRoadIds);
        affected.addAll(ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, workingNetwork));
        return affected.size();
    }

    public Set<String> touchedRoadIds() {
        return Set.copyOf(touchedRoadIds);
    }

    public Set<String> touchedNodeIds() {
        return Set.copyOf(touchedNodeIds);
    }

    public Set<String> touchedCrossingIds() {
        return Set.copyOf(touchedCrossingIds);
    }

    public void markTouched(Road road) {
        if (road != null && road.getId() != null) {
            touchedRoadIds.add(road.getId());
        }
    }

    public void markTouchedNode(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            touchedNodeIds.add(nodeId);
        }
    }

    public void markTouchedCrossing(String crossingId) {
        if (crossingId != null && !crossingId.isBlank()) {
            touchedCrossingIds.add(crossingId);
        }
    }

    public void markTouchedIntersection(RoadProfileIntersection intersection) {
        if (intersection == null) {
            return;
        }
        if (intersection.otherRoadId() != null) {
            touchedRoadIds.add(intersection.otherRoadId());
        }
        if (RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
            markTouchedCrossing(RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
        } else if (RoadStackCrossingRef.isStackCrossingRef(intersection.nodeId())) {
            markTouchedCrossing(RoadStackCrossingRef.stackCrossingIdFromRef(intersection.nodeId()));
        } else {
            markTouchedNode(intersection.nodeId());
        }
    }

    public void restore() {
        workingNetwork = openSnapshot.snapshot();
        touchedRoadIds.clear();
        touchedNodeIds.clear();
        touchedCrossingIds.clear();
        undoHistory.clear();
        invalidateAllVoxelPreviews();
    }

    public void cancel() {
        workingNetwork = openSnapshot.snapshot();
        touchedRoadIds.clear();
        touchedNodeIds.clear();
        touchedCrossingIds.clear();
        undoHistory.clear();
        clearVoxelPreviews();
    }

    public void recordUndoPoint() {
        undoHistory.record(workingNetwork);
    }

    public boolean canUndo() {
        return undoHistory.canUndo();
    }

    public boolean canRedo() {
        return undoHistory.canRedo();
    }

    public void undo() {
        workingNetwork = undoHistory.undo(workingNetwork);
        invalidateAllVoxelPreviews();
    }

    public void redo() {
        workingNetwork = undoHistory.redo(workingNetwork);
        invalidateAllVoxelPreviews();
    }

    public boolean hasUnconfirmedEditsForRoad(String roadId) {
        if (roadId == null || roadId.isBlank() || !isDirty()) {
            return false;
        }
        if (touchedRoadIds.contains(roadId)) {
            return true;
        }
        return ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, workingNetwork)
            .contains(roadId);
    }

    public VoxelProfilePreviewSlot voxelPreview(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return new VoxelProfilePreviewSlot();
        }
        return voxelPreviews.computeIfAbsent(roadId, ignored -> new VoxelProfilePreviewSlot());
    }

    public void markVoxelPreviewStale(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        VoxelProfilePreviewSlot slot = voxelPreviews.get(roadId);
        if (slot != null) {
            slot.markStale();
        } else {
            VoxelProfilePreviewSlot created = new VoxelProfilePreviewSlot();
            created.markStale();
            voxelPreviews.put(roadId, created);
        }
    }

    public void markAllVoxelPreviewsStale() {
        for (VoxelProfilePreviewSlot slot : voxelPreviews.values()) {
            slot.markStale();
        }
    }

    public void storeVoxelPreview(
            String roadId,
            VoxelProfilePreviewKey key,
            VoxelProfileCompileResult result) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        VoxelProfilePreviewSlot slot = voxelPreview(roadId);
        if (result == null || !result.feasible() || !result.hasSamples()) {
            slot.markFailed(key, result != null ? result.failureReason() : "empty result");
            return;
        }
        slot.markReady(key, result);
    }

    public void invalidateAllVoxelPreviews() {
        for (VoxelProfilePreviewSlot slot : voxelPreviews.values()) {
            slot.markStale();
        }
    }

    public void clearVoxelPreviews() {
        voxelPreviews.clear();
    }

    public ConfirmResult confirm(RoadUiContext ctx, RoadNetwork live, RoadSystemConfig config) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(live, "live");
        if (ctx.networkManager().getNetworkRevision() != openRevision) {
            return ConfirmResult.CONFLICT;
        }
        ConfirmResult rebindOnly = tryConfirmStaleProfileRebind(ctx, live, config);
        if (rebindOnly != null) {
            return rebindOnly;
        }
        if (!isDirty()) {
            return ConfirmResult.NO_CHANGES;
        }
        Set<String> changedRoadIds = ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, workingNetwork);
        Set<String> propagateSeeds = new LinkedHashSet<>(changedRoadIds);
        propagateSeeds.addAll(touchedRoadIds);
        unresolvedConstraints = com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.validate(
            workingNetwork, propagateSeeds, config);
        // Normal editor gestures have already linked the working copy. For callers
        // that directly edited one road, synchronize once. Never choose an arbitrary
        // winner between several conflicting edited profiles at confirmation time.
        if (!unresolvedConstraints.isEmpty() && changedRoadIds.size() == 1) {
            ProfileEditorNetworkApplier.propagateTouchedRoads(workingNetwork, changedRoadIds, config);
            unresolvedConstraints = com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.validate(
                workingNetwork, propagateSeeds, config);
        }
        if (!unresolvedConstraints.isEmpty()) {
            return ConfirmResult.CONSTRAINT_CONFLICT;
        }

        ProfileEditorNetworkApplier.VerticalStateDiff diff =
            ProfileEditorNetworkApplier.collectChangedEntities(openSnapshot, workingNetwork);
        if (!diff.hasAnyChanges()) {
            return ConfirmResult.NO_CHANGES;
        }

        ctx.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
        ProfileEditorNetworkApplier.apply(
            workingNetwork,
            live,
            diff.roadIds(),
            diff.nodeIds(),
            diff.crossingIds());
        for (String roadId : diff.roadIds()) {
            com.plot.plugin.road.model.Road road = live.getRoad(roadId);
            if (road != null) {
                com.plot.plugin.road.vertical.ResolvedProfileAuthority.markEditedProfileConfirmed(
                    live, road);
            }
        }
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
        touchedRoadIds.clear();
        touchedNodeIds.clear();
        touchedCrossingIds.clear();
        undoHistory.clear();
        return ConfirmResult.APPLIED;
    }

    /**
     * 曲线数值未变但几何签名过期时，允许用户仅重新确认绑定，无需拖动 PVI。
     */
    private ConfirmResult tryConfirmStaleProfileRebind(
            RoadUiContext ctx,
            RoadNetwork live,
            RoadSystemConfig config) {
        if (isDirty()) {
            return null;
        }
        Set<String> rebindRoadIds = new LinkedHashSet<>();
        for (String roadId : touchedRoadIds) {
            com.plot.plugin.road.model.Road workingRoad = workingNetwork.getRoad(roadId);
            com.plot.plugin.road.model.Road liveRoad = live.getRoad(roadId);
            if (workingRoad == null || liveRoad == null) {
                continue;
            }
            if (!com.plot.plugin.road.vertical.ResolvedProfileAuthority.hasStoredEditedCurve(workingRoad)) {
                continue;
            }
            if (com.plot.plugin.road.vertical.ResolvedProfileAuthority.canActivateEditedProfile(
                    workingNetwork, workingRoad)) {
                continue;
            }
            rebindRoadIds.add(roadId);
        }
        if (rebindRoadIds.isEmpty()) {
            return null;
        }
        for (String roadId : rebindRoadIds) {
            com.plot.plugin.road.model.Road workingRoad = workingNetwork.getRoad(roadId);
            if (EditedProfileRebindValidator.validate(workingNetwork, workingRoad, config) != null) {
                return ConfirmResult.CONSTRAINT_CONFLICT;
            }
        }
        ctx.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
        for (String roadId : rebindRoadIds) {
            com.plot.plugin.road.model.Road road = live.getRoad(roadId);
            if (road != null) {
                com.plot.plugin.road.vertical.ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);
            }
        }
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
        return ConfirmResult.APPLIED;
    }

    /** 传播后可能调整相邻道路，将其加入 touched 集（供编辑期中间传播使用）。 */
    public void markPropagationAdjustedRoads(RoadNetwork networkAfterPropagate) {
        if (networkAfterPropagate == null) {
            return;
        }
        touchedRoadIds.addAll(ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, networkAfterPropagate));
    }
}
