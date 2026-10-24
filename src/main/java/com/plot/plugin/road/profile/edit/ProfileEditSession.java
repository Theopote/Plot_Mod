package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatProfileCompiler;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 纵断面编辑 Draft→Commit 会话：拖动期间只改内存草稿，提交时一次性写回路网。 */
public final class ProfileEditSession {

    private final ProfileEditDraft draft = new ProfileEditDraft();
    private int activeCurvePviIndex = -1;
    private RoadElevationBounds elevationBounds = RoadWorldElevationBounds.fallback();
    private RoadNetwork previewNetwork;
    private long draftRevision;
    private long previewRevision = -1;
    private RoadProfileConstraintSynchronizer.Result constraintResult =
        new RoadProfileConstraintSynchronizer.Result(java.util.Set.of(), java.util.Set.of());

    public RoadProfileConstraintSynchronizer.Result constraintResult() {
        return constraintResult;
    }

    /** Disposable, cached network for live crossing overlays; cancellation cannot leak a drag. */
    public RoadNetwork previewNetwork(RoadNetwork working, Road road, RoadSystemConfig config) {
        if (!draft.isActive() || !draft.isDirty()) return working;
        if (previewNetwork == null || previewRevision != draftRevision) {
            previewNetwork = working.snapshot();
            Road previewRoad = previewNetwork.getRoad(road.getId());
            applyAndSynchronize(previewNetwork, previewRoad, config);
            previewRevision = draftRevision;
        }
        return previewNetwork;
    }

    public void setElevationBounds(RoadElevationBounds bounds) {
        RoadElevationBounds next = bounds != null ? bounds : RoadWorldElevationBounds.fallback();
        if (!next.equals(elevationBounds)) previewNetwork = null;
        elevationBounds = next;
    }

    public RoadElevationBounds elevationBounds() {
        return elevationBounds;
    }

    public boolean isActive() {
        return draft.isActive();
    }

    public boolean isDirty() {
        return draft.isDirty();
    }

    public void cancelEdit() {
        draft.clear();
        activeCurvePviIndex = -1;
        previewNetwork = null;
        draftRevision++;
    }

    public void beginPviEdit(Road road) {
        Objects.requireNonNull(road, "road");
        if (draft.isActive() && draft.kind() != ProfileEditDraft.Kind.PVI) {
            return;
        }
        if (!draft.isActive()) {
            draft.beginAlignmentEdit(road, ProfileEditDraft.Kind.PVI);
        }
    }

    public void beginNumericEdit(Road road) {
        beginPviEdit(road);
    }

    public void updatePviElevation(RoadNetwork network, Road road, int pviIndex, double elevation) {
        if (road == null || draft.draftAlignment() == null || pviIndex < 0
                || pviIndex >= draft.draftAlignment().pviCount()) {
            return;
        }
        double length = RoadStationing.canonicalLength(network, road);
        RoadVerticalAlignment updated = com.plot.plugin.road.vertical.VerticalProfileDesignRules.slopeAllowed(length)
            ? VerticalProfileControlPoints.withElevation(draft.draftAlignment(), pviIndex, elevationBounds.clamp(elevation), road)
            : com.plot.plugin.road.vertical.VerticalProfileDesignRules.flatAlignment(length, elevationBounds.clamp(elevation));
        draft.setDraftAlignment(updated);
        draftRevision++;
    }

    public void beginCurveEdit(Road road, int pviIndex) {
        Objects.requireNonNull(road, "road");
        if (!draft.isActive()) {
            draft.beginAlignmentEdit(road, ProfileEditDraft.Kind.CURVE);
        }
        activeCurvePviIndex = pviIndex;
    }

    public void beginIntersectionEdit(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        draft.beginIntersectionEdit(intersections);
    }

    public void updatePviDrag(
            RoadNetwork network,
            Road road,
            ProfileControlPoint draggedPoint,
            int pviIndex,
            double requestedStation,
            double elevation) {
        if (road == null || draggedPoint == null || draft.draftAlignment() == null) {
            return;
        }
        elevation = elevationBounds.clamp(elevation);
        RoadVerticalAlignment updated;
        if (road.getTopologyMode() == RoadTopologyMode.LOOP
                && (draggedPoint.role() == ProfilePointRole.LOOP_SEAM_START
                    || draggedPoint.role() == ProfilePointRole.LOOP_SEAM_END
                    || draggedPoint.role() == ProfilePointRole.START_ENDPOINT
                    || draggedPoint.role() == ProfilePointRole.END_ENDPOINT)) {
            updated = VerticalProfileControlPoints.withElevation(
                draft.draftAlignment(), pviIndex, elevation, road);
        } else {
            updated = VerticalProfileControlPoints.move(
                draft.draftAlignment(),
                pviIndex,
                requestedStation,
                elevation,
                RoadStationing.canonicalLength(network, road));
        }
        draft.setDraftAlignment(updated);
        draftRevision++;
    }

    public void updateCurveLength(double curveLength) {
        if (activeCurvePviIndex < 0 || draft.draftAlignment() == null) {
            return;
        }
        draft.setDraftAlignment(VerticalProfileControlPoints.withCurveLength(
            draft.draftAlignment(), activeCurvePviIndex, curveLength));
        draftRevision++;
    }

    public void updateIntersectionDrag(
            int index,
            RoadProfileIntersectionDragEditor.DragTarget target,
            double elevation) {
        draft.updateIntersectionDraft(index, target, elevationBounds.clamp(elevation));
        draftRevision++;
    }

    /**
     * 水平道路：拖动任一交点时，整条设计线同步抬升/降低，并预览所有交点 Y 联动。
     */
    public void updateFlatRoadWholeLineDrag(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            int index,
            RoadProfileIntersectionDragEditor.DragTarget target,
            double elevation,
            List<RoadProfileIntersection> intersections) {
        if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT || intersections == null) {
            updateIntersectionDrag(index, target, elevation);
            return;
        }
        if (!draft.isActive() || draft.kind() != ProfileEditDraft.Kind.INTERSECTION) {
            beginIntersectionEdit(intersections);
        }
        double clamped = elevationBounds.clamp(elevation);
        double maxGrade = road.getEffectiveMaxSlope(config);
        FlatVerticalIntent previewIntent = new FlatVerticalIntent(clamped);
        RoadVerticalAlignment preview = FlatProfileCompiler.compile(
            network, road, previewIntent, maxGrade);
        draft.setDraftAlignment(preview);
        if (target == RoadProfileIntersectionDragEditor.DragTarget.OTHER) {
            draft.updateIntersectionDraft(index, target, clamped);
        } else {
            for (int i = 0; i < draft.draftIntersections().size(); i++) {
                draft.updateIntersectionDraft(
                    i,
                    RoadProfileIntersectionDragEditor.DragTarget.CURRENT,
                    clamped);
            }
            draft.forceIntersectionTarget(index, target);
        }
        draftRevision++;
    }

    public RoadVerticalAlignment effectiveAlignment(Road road) {
        if (draft.draftAlignment() != null) {
            return draft.draftAlignment();
        }
        return road != null ? road.getVerticalAlignment() : null;
    }

    public Optional<VerticalAlignmentProfileOverlay> effectiveDesignOverlay(
            RoadNetwork network,
            Road road) {
        if (draft.draftAlignment() != null) {
            return VerticalAlignmentProfileOverlay.forAlignment(network, road, draft.draftAlignment());
        }
        Optional<VerticalAlignmentProfileOverlay> active =
            VerticalAlignmentProfileOverlay.forRoad(network, road);
        if (active.isPresent()) {
            return active;
        }
        // FIT_TERRAIN 下保留的 dormant VA：打开编辑器时显示绿线，确认前不参与生成。
        return VerticalAlignmentProfileOverlay.forAlignment(
            network, road, road != null ? road.getVerticalAlignment() : null);
    }

    public List<RoadProfileIntersection> effectiveIntersections(List<RoadProfileIntersection> base) {
        if (draft.kind() == ProfileEditDraft.Kind.INTERSECTION && !draft.draftIntersections().isEmpty()) {
            return draft.draftIntersections();
        }
        return base != null ? base : List.of();
    }

    public void commitEdit(
            RoadNetwork workingNetwork,
            Road road,
            RoadSystemConfig config,
            ProfileEditorWindowSession windowSession,
            Runnable propagatePrimary,
            Runnable afterCommit) {
        if (!draft.isActive()) {
            return;
        }
        if (!draft.isDirty()) {
            cancelEdit();
            return;
        }
        applyAndSynchronize(workingNetwork, road, config);
        if (windowSession != null) {
            windowSession.markTouched(road);
            for (String changedId : constraintResult.changedRoadIds()) {
                windowSession.markTouched(workingNetwork.getRoad(changedId));
            }
            if (draft.kind() == ProfileEditDraft.Kind.INTERSECTION) {
                markIntersectionTouched(windowSession, workingNetwork, road);
            }
        }
        if (propagatePrimary != null) {
            propagatePrimary.run();
        }
        if (afterCommit != null) {
            afterCommit.run();
        }
        cancelEdit();
    }

    private void applyAndSynchronize(RoadNetwork network, Road road, RoadSystemConfig config) {
        Road driver = road;
        if (draft.kind() == ProfileEditDraft.Kind.INTERSECTION
                && draft.intersectionTarget() == RoadProfileIntersectionDragEditor.DragTarget.OTHER
                && draft.intersectionIndex() >= 0) {
            Road other = network.getRoad(draft.draftIntersections().get(draft.intersectionIndex()).otherRoadId());
            if (other != null) driver = other;
        }
        RoadVerticalAlignment before = driver.getVerticalAlignment();
        applyDraftToNetwork(network, road, config);
        constraintResult = RoadProfileConstraintSynchronizer.synchronize(
            network, driver, before, config, elevationBounds);
    }

    private void markIntersectionTouched(
            ProfileEditorWindowSession windowSession,
            RoadNetwork network,
            Road road) {
        int index = draft.intersectionIndex();
        if (index < 0 || index >= draft.draftIntersections().size()) {
            return;
        }
        RoadProfileIntersection intersection = draft.draftIntersections().get(index);
        windowSession.markTouchedIntersection(intersection);
        Road otherRoad = network.getRoad(intersection.otherRoadId());
        if (otherRoad != null) {
            windowSession.markTouched(otherRoad);
        }
    }

    private void applyDraftToNetwork(RoadNetwork network, Road road, RoadSystemConfig config) {
        switch (draft.kind()) {
            case PVI, CURVE -> {
                RoadVerticalAlignment before = road.getVerticalAlignment();
                road.setVerticalAlignment(draft.draftAlignment());
                if (draft.kind() == ProfileEditDraft.Kind.CURVE) {
                    RoadProfileConstraintSynchronizer.preserveCrossingElevations(network, road, before, config);
                }
                com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
            }
            case INTERSECTION -> commitIntersectionDraft(network, road, config);
            case NONE -> {
            }
        }
    }

    private void commitIntersectionDraft(RoadNetwork network, Road road, RoadSystemConfig config) {
        int index = draft.intersectionIndex();
        if (index < 0 || index >= draft.draftIntersections().size()) {
            return;
        }
        RoadProfileIntersection patched = draft.draftIntersections().get(index);
        RoadProfileIntersectionDragEditor.DragTarget target = draft.intersectionTarget();
        if (target == null) {
            return;
        }
        double requested = switch (target) {
            case OTHER -> patched.otherRoadElevation();
            case SHARED -> patched.currentRoadElevation();
            case CURRENT -> patched.currentRoadElevation();
        };
        RoadProfileIntersectionDragEditor.applyDraggedElevation(
            network, road, patched, target, requested, config);
    }
}
