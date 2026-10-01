package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.ArrayList;
import java.util.List;

/** 纵断面编辑器内存草稿，拖动期间不写入 RoadNetwork。 */
final class ProfileEditDraft {

    enum Kind {
        NONE,
        PVI,
        CURVE,
        INTERSECTION
    }

    private Kind kind = Kind.NONE;
    private RoadVerticalAlignment draftAlignment;
    private boolean dirty;
    private List<RoadProfileIntersection> draftIntersections = List.of();
    private int intersectionIndex = -1;
    private RoadProfileIntersectionDragEditor.DragTarget intersectionTarget;

    Kind kind() {
        return kind;
    }

    boolean isActive() {
        return kind != Kind.NONE;
    }

    boolean isDirty() {
        return dirty;
    }

    RoadVerticalAlignment draftAlignment() {
        return draftAlignment;
    }

    List<RoadProfileIntersection> draftIntersections() {
        return draftIntersections;
    }

    void beginAlignmentEdit(Road road, Kind editKind) {
        kind = editKind;
        dirty = false;
        draftAlignment = copyAlignment(road.getVerticalAlignment());
        draftIntersections = List.of();
        intersectionIndex = -1;
    }

    void beginIntersectionEdit(List<RoadProfileIntersection> intersections) {
        kind = Kind.INTERSECTION;
        dirty = false;
        draftAlignment = null;
        draftIntersections = new ArrayList<>(intersections);
        intersectionIndex = -1;
    }

    void setDraftAlignment(RoadVerticalAlignment alignment) {
        draftAlignment = alignment;
        dirty = true;
    }

    void updateIntersectionDraft(
            int index,
            RoadProfileIntersectionDragEditor.DragTarget target,
            double elevation) {
        if (index < 0 || index >= draftIntersections.size()) {
            return;
        }
        intersectionIndex = index;
        intersectionTarget = target;
        RoadProfileIntersection original = draftIntersections.get(index);
        double current = original.currentRoadElevation();
        double other = original.otherRoadElevation();
        if (target == RoadProfileIntersectionDragEditor.DragTarget.SHARED) {
            current = elevation;
            other = elevation;
        } else if (target == RoadProfileIntersectionDragEditor.DragTarget.OTHER) {
            other = elevation;
        } else {
            current = elevation;
        }
        draftIntersections.set(index, new RoadProfileIntersection(
            original.nodeId(),
            original.currentRoadId(),
            original.otherRoadId(),
            original.otherRoadLabel(),
            original.localDistance(),
            original.roadStation(),
            current,
            other,
            original.otherCrossSection(),
            original.gradeSeparated(),
            original.currentRoadElevated(),
            original.clearance(),
            original.steepGradeWarning()));
        dirty = true;
    }

    void clear() {
        kind = Kind.NONE;
        draftAlignment = null;
        draftIntersections = List.of();
        dirty = false;
        intersectionIndex = -1;
        intersectionTarget = null;
    }

    int intersectionIndex() {
        return intersectionIndex;
    }

    RoadProfileIntersectionDragEditor.DragTarget intersectionTarget() {
        return intersectionTarget;
    }

    private static RoadVerticalAlignment copyAlignment(RoadVerticalAlignment source) {
        if (source == null) {
            return new RoadVerticalAlignment();
        }
        return new RoadVerticalAlignment(source.getPvis());
    }
}
