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
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
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

    public void setElevationBounds(RoadElevationBounds bounds) {
        elevationBounds = bounds != null ? bounds : RoadWorldElevationBounds.fallback();
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
        double station = draft.draftAlignment().getPvis().get(pviIndex).getStation();
        RoadVerticalAlignment updated = VerticalProfileControlPoints.move(
            draft.draftAlignment(),
            pviIndex,
            station,
            elevationBounds.clamp(elevation),
            RoadStationing.canonicalLength(network, road));
        draft.setDraftAlignment(updated);
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
        draft.updateIntersectionDraft(index, target, elevationBounds.clamp(elevation));
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
        applyDraftToNetwork(workingNetwork, road, config);
        if (windowSession != null) {
            windowSession.markTouched(road);
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
