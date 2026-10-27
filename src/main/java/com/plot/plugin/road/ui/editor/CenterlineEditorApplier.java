package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.List;
import java.util.Objects;

/** 中心线浮动编辑器：将工作副本中该路的平面几何写回 live。 */
public final class CenterlineEditorApplier {

    private CenterlineEditorApplier() {
    }

    public static boolean hasChanges(RoadNetwork openSnapshot, RoadNetwork workingNetwork, String roadId) {
        Road original = openSnapshot.getRoad(roadId);
        Road working = workingNetwork.getRoad(roadId);
        if (original == null || working == null) {
            return false;
        }
        if (!Objects.equals(original.getOrderedSegmentIds(), working.getOrderedSegmentIds())) {
            return true;
        }
        if (!alignmentsEqual(
            original.getHorizontalAlignment(),
            working.getHorizontalAlignment())) {
            return true;
        }
        for (String edgeId : working.getOrderedSegmentIds()) {
            RoadEdge openEdge = openSnapshot.getEdge(edgeId);
            RoadEdge workEdge = workingNetwork.getEdge(edgeId);
            if (openEdge == null || workEdge == null) {
                return true;
            }
            if (openEdge.getLength() != workEdge.getLength()) {
                return true;
            }
            if (!pointsEqual(openEdge.getCenterlinePoints(), workEdge.getCenterlinePoints())) {
                return true;
            }
            if (!slopeOverridesEqual(openEdge, workEdge)) {
                return true;
            }
        }
        return false;
    }

    public static void applyToLive(RoadNetwork live, RoadNetwork workingNetwork, String roadId) {
        CenterlineGeometrySync.applyRoadFromWorking(live, workingNetwork, roadId);
    }

    private static boolean alignmentsEqual(com.plot.plugin.road.alignment.RoadHorizontalAlignment a,
            com.plot.plugin.road.alignment.RoadHorizontalAlignment b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        var left = a.getElements();
        var right = b.getElements();
        if (a.getOrigin().x != b.getOrigin().x || a.getOrigin().y != b.getOrigin().y
                || a.getStartBearingRadians() != b.getStartBearingRadians()
                || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var x = left.get(i);
            var y = right.get(i);
            if (x.getType() != y.getType() || x.getLength() != y.getLength()
                    || !Objects.equals(x.getRadius(), y.getRadius()) || x.getDirection() != y.getDirection()
                    || !Objects.equals(x.getSpiralParameterA(), y.getSpiralParameterA())) return false;
        }
        return true;
    }

    private static boolean pointsEqual(
            List<com.plot.api.geometry.Vec2d> left,
            List<com.plot.api.geometry.Vec2d> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            com.plot.api.geometry.Vec2d a = left.get(i);
            com.plot.api.geometry.Vec2d b = right.get(i);
            if (a == null || b == null || a.x != b.x || a.y != b.y) {
                return false;
            }
        }
        return true;
    }

    private static boolean slopeOverridesEqual(RoadEdge left, RoadEdge right) {
        return left.getSlopeOverrides().size() == right.getSlopeOverrides().size()
            && left.getSlopeOverrides().equals(right.getSlopeOverrides());
    }
}
