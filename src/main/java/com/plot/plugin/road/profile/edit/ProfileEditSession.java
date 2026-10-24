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
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

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
            if (isFlatWholeLineDraft(previewRoad)) {
                applyFlatWholeLinePreview(previewNetwork, previewRoad, config);
            } else {
                applyAndSynchronize(previewNetwork, previewRoad, config);
            }
            previewRevision = draftRevision;
        }
        return previewNetwork;
    }

    private boolean isFlatWholeLineDraft(Road road) {
        if (road == null
                || road.getVerticalMode() != RoadVerticalMode.FLAT
                || draft.draftAlignment() == null) {
            return false;
        }
        if (draft.kind() == ProfileEditDraft.Kind.PVI) {
            return true;
        }
        return draft.kind() == ProfileEditDraft.Kind.INTERSECTION
            && draft.intersectionTarget() != RoadProfileIntersectionDragEditor.DragTarget.OTHER;
    }

    private void applyFlatWholeLinePreview(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        RoadVerticalAlignment preview = draft.draftAlignment();
        if (preview == null || preview.isEmpty()) {
            applyAndSynchronize(network, road, config);
            return;
        }
        double base = preview.getPvis().getFirst().getElevation();
        road.setVerticalAlignment(preview);
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        if (intent != null) {
            intent.setBaseElevation(base);
            intent.clearIntersectionOverrides();
        }
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

    /** 水平道路整体抬升编辑：有交点走交点草稿，无端点交点时走 PVI 草稿。 */
    public void beginFlatWholeLineEdit(
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections) {
        if (intersections != null && !intersections.isEmpty()) {
            beginIntersectionEdit(intersections);
            return;
        }
        Objects.requireNonNull(road, "road");
        if (!draft.isActive()) {
            draft.beginAlignmentEdit(road, ProfileEditDraft.Kind.PVI);
        }
        double length = RoadStationing.canonicalLength(network, road);
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        double base = intent != null ? intent.getBaseElevation() : 64.0;
        draft.setDraftAlignment(VerticalProfileDesignRules.flatAlignment(length, base));
        draftRevision++;
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
        if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT) {
            updateIntersectionDrag(index, target, elevation);
            return;
        }
        List<RoadProfileIntersection> working =
            intersections != null ? intersections : List.of();
        if (!draft.isActive()) {
            beginFlatWholeLineEdit(network, road, working);
        }
        double clamped = elevationBounds.clamp(elevation);
        double roadLength = RoadStationing.canonicalLength(network, road);
        RoadVerticalAlignment preview = VerticalProfileDesignRules.flatAlignment(roadLength, clamped);
        draft.setDraftAlignment(preview);
        if (target == RoadProfileIntersectionDragEditor.DragTarget.OTHER) {
            if (index >= 0 && index < draft.draftIntersections().size()) {
                draft.updateIntersectionDraft(index, target, clamped);
            }
        } else if (!draft.draftIntersections().isEmpty()) {
            for (int i = 0; i < draft.draftIntersections().size(); i++) {
                RoadProfileIntersection original = draft.draftIntersections().get(i);
                RoadProfileIntersectionDragEditor.DragTarget perTarget =
                    original.gradeSeparated()
                        ? RoadProfileIntersectionDragEditor.DragTarget.CURRENT
                        : RoadProfileIntersectionDragEditor.DragTarget.SHARED;
                draft.updateIntersectionDraft(i, perTarget, clamped);
            }
            int resolvedIndex = Math.min(Math.max(index, 0), draft.draftIntersections().size() - 1);
            RoadProfileIntersection resolved = draft.draftIntersections().get(resolvedIndex);
            draft.forceIntersectionTarget(
                resolvedIndex,
                resolved.gradeSeparated()
                    ? RoadProfileIntersectionDragEditor.DragTarget.CURRENT
                    : RoadProfileIntersectionDragEditor.DragTarget.SHARED);
        }
        draftRevision++;
    }

    public RoadVerticalAlignment effectiveAlignment(Road road) {
        return effectiveAlignment(null, road);
    }

    public RoadVerticalAlignment effectiveAlignment(RoadNetwork network, Road road) {
        if (draft.draftAlignment() != null) {
            return draft.draftAlignment();
        }
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT && network != null) {
            return FlatVerticalIntentSupport.strictEditorAlignment(network, road);
        }
        return road != null ? road.getVerticalAlignment() : null;
    }

    public Optional<VerticalAlignmentProfileOverlay> effectiveDesignOverlay(
            RoadNetwork network,
            Road road) {
        if (draft.draftAlignment() != null) {
            return VerticalAlignmentProfileOverlay.forAlignment(network, road, draft.draftAlignment());
        }
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT && network != null) {
            RoadVerticalAlignment flat = FlatVerticalIntentSupport.strictEditorAlignment(network, road);
            return VerticalAlignmentProfileOverlay.forAlignment(network, road, flat);
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
        return effectiveIntersections(null, null, base);
    }

    public List<RoadProfileIntersection> effectiveIntersections(
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> base) {
        if (draft.kind() == ProfileEditDraft.Kind.INTERSECTION && !draft.draftIntersections().isEmpty()) {
            return draft.draftIntersections();
        }
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT && network != null) {
            return FlatVerticalIntentSupport.alignIntersectionsForEditor(network, road, base);
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
                if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                    markFlatIntersectionsTouched(windowSession, workingNetwork);
                } else {
                    markIntersectionTouched(windowSession, workingNetwork, road);
                }
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

    private void markFlatIntersectionsTouched(
            ProfileEditorWindowSession windowSession,
            RoadNetwork network) {
        for (RoadProfileIntersection intersection : draft.draftIntersections()) {
            windowSession.markTouchedIntersection(intersection);
            Road otherRoad = network.getRoad(intersection.otherRoadId());
            if (otherRoad != null) {
                windowSession.markTouched(otherRoad);
            }
        }
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
                if (road.getVerticalMode() == RoadVerticalMode.FLAT && draft.draftAlignment() != null) {
                    double base = draft.draftAlignment().getPvis().getFirst().getElevation();
                    FlatVerticalIntentSupport.setWholeRoadElevation(
                        network, road, base, road.getEffectiveMaxSlope(config));
                } else {
                    RoadVerticalAlignment before = road.getVerticalAlignment();
                    road.setVerticalAlignment(draft.draftAlignment());
                    if (draft.kind() == ProfileEditDraft.Kind.CURVE) {
                        RoadProfileConstraintSynchronizer.preserveCrossingElevations(
                            network, road, before, config);
                    }
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
        if (road.getVerticalMode() == RoadVerticalMode.FLAT
                && target != RoadProfileIntersectionDragEditor.DragTarget.OTHER) {
            FlatVerticalIntentSupport.setWholeRoadElevation(
                network,
                road,
                requested,
                road.getEffectiveMaxSlope(config));
            for (RoadProfileIntersection intersection : draft.draftIntersections()) {
                RoadProfileIntersectionDragEditor.DragTarget applyTarget =
                    intersection.gradeSeparated()
                        ? target
                        : RoadProfileIntersectionDragEditor.DragTarget.SHARED;
                double elevation = applyTarget
                        == RoadProfileIntersectionDragEditor.DragTarget.OTHER
                    ? intersection.otherRoadElevation()
                    : requested;
                RoadProfileIntersectionDragEditor.applyDraggedElevation(
                    network, road, intersection, applyTarget, elevation, config);
            }
            return;
        }
        RoadProfileIntersectionDragEditor.applyDraggedElevation(
            network, road, patched, target, requested, config);
    }
}
