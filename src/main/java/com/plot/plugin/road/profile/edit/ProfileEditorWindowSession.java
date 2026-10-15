package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.ui.RoadUiContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
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

    public boolean isDirty() {
        return ProfileEditorNetworkApplier.hasVerticalStateChanges(openSnapshot, workingNetwork);
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
        if (!isDirty()) {
            return ConfirmResult.NO_CHANGES;
        }
        if (ctx.networkManager().getNetworkRevision() != openRevision) {
            return ConfirmResult.CONFLICT;
        }
        Set<String> propagateSeeds = new LinkedHashSet<>(
            ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, workingNetwork));
        propagateSeeds.addAll(touchedRoadIds);
        ProfileEditorNetworkApplier.propagateTouchedRoads(
            workingNetwork, propagateSeeds, config);

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

    /** 传播后可能调整相邻道路，将其加入 touched 集（供编辑期中间传播使用）。 */
    public void markPropagationAdjustedRoads(RoadNetwork networkAfterPropagate) {
        if (networkAfterPropagate == null) {
            return;
        }
        touchedRoadIds.addAll(ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, networkAfterPropagate));
    }
}
