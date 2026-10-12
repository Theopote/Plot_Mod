package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.ui.RoadUiContext;

import java.util.LinkedHashSet;
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
    private boolean dirty;
    private final Set<String> touchedRoadIds = new LinkedHashSet<>();
    private final Set<String> touchedNodeIds = new LinkedHashSet<>();
    private final Set<String> touchedCrossingIds = new LinkedHashSet<>();

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
        return dirty;
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
            dirty = true;
        }
    }

    public void markTouchedNode(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            touchedNodeIds.add(nodeId);
            dirty = true;
        }
    }

    public void markTouchedCrossing(String crossingId) {
        if (crossingId != null && !crossingId.isBlank()) {
            touchedCrossingIds.add(crossingId);
            dirty = true;
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
        dirty = false;
    }

    public void cancel() {
        restore();
    }

    public ConfirmResult confirm(RoadUiContext ctx, RoadNetwork live, RoadSystemConfig config) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(live, "live");
        if (!dirty) {
            return ConfirmResult.NO_CHANGES;
        }
        if (ctx.networkManager().getNetworkRevision() != openRevision) {
            return ConfirmResult.CONFLICT;
        }
        ProfileEditorNetworkApplier.propagateTouchedRoads(
            workingNetwork, touchedRoadIds, config);
        ctx.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
        ProfileEditorNetworkApplier.apply(
            workingNetwork,
            live,
            touchedRoadIds,
            touchedNodeIds,
            touchedCrossingIds);
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
        dirty = false;
        return ConfirmResult.APPLIED;
    }

    /** 传播后可能调整相邻道路，将其加入 touched 集。 */
    public void markPropagationAdjustedRoads(RoadNetwork networkAfterPropagate) {
        if (networkAfterPropagate == null) {
            return;
        }
        for (String roadId : ProfileEditorNetworkApplier.collectChangedRoadIds(openSnapshot, networkAfterPropagate)) {
            touchedRoadIds.add(roadId);
        }
    }
}
