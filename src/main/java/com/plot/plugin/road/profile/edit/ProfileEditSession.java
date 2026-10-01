package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 纵断面编辑 Draft→Commit 会话：拖动期间只改内存草稿，提交时一次性写回路网。 */
public final class ProfileEditSession {

    private final ProfileEditDraft draft = new ProfileEditDraft();
    private int activeCurvePviIndex = -1;

    public boolean isActive() {
        return draft.isActive();
    }

    public boolean isDirty() {
        return draft.isDirty();
    }

    public void cancelEdit() {
        draft.clear();
        activeCurvePviIndex = -1;
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
    }

    public void updateCurveLength(double curveLength) {
        if (activeCurvePviIndex < 0 || draft.draftAlignment() == null) {
            return;
        }
        draft.setDraftAlignment(VerticalProfileControlPoints.withCurveLength(
            draft.draftAlignment(), activeCurvePviIndex, curveLength));
    }

    public void updateIntersectionDrag(
            int index,
            RoadProfileIntersectionDragEditor.DragTarget target,
            double elevation) {
        draft.updateIntersectionDraft(index, target, elevation);
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
        return VerticalAlignmentProfileOverlay.forRoad(network, road);
    }

    public List<RoadProfileIntersection> effectiveIntersections(List<RoadProfileIntersection> base) {
        if (draft.kind() == ProfileEditDraft.Kind.INTERSECTION && !draft.draftIntersections().isEmpty()) {
            return draft.draftIntersections();
        }
        return base != null ? base : List.of();
    }

    public void commitEdit(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable propagatePrimary,
            Runnable afterCommit) {
        if (!draft.isActive()) {
            return;
        }
        if (!draft.isDirty()) {
            cancelEdit();
            return;
        }
        ctx.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
        applyDraftToNetwork(network, road, config);
        if (propagatePrimary != null) {
            propagatePrimary.run();
        }
        ctx.previewManager().markBuildPreviewStalePreservingProfile();
        ctx.finishNetworkEdit();
        if (afterCommit != null) {
            afterCommit.run();
        }
        cancelEdit();
    }

    private void applyDraftToNetwork(RoadNetwork network, Road road, RoadSystemConfig config) {
        switch (draft.kind()) {
            case PVI, CURVE -> {
                road.setVerticalAlignment(draft.draftAlignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
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
