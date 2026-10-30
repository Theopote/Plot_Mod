package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;

/** 可变横断面浮动编辑器写回。 */
public final class VariableCrossSectionEditorApplier {

    private VariableCrossSectionEditorApplier() {
    }

    public static boolean hasChanges(RoadNetwork openSnapshot, RoadNetwork workingNetwork, String roadId) {
        Road original = openSnapshot.getRoad(roadId);
        Road working = workingNetwork.getRoad(roadId);
        if (original == null || working == null) {
            return false;
        }
        RoadVariableCrossSections leftSections = original.getVariableCrossSections();
        RoadVariableCrossSections rightSections = working.getVariableCrossSections();
        if (leftSections == null && rightSections == null) {
            return false;
        }
        if (leftSections == null || rightSections == null) {
            return true;
        }
        if (leftSections.getTransitionMode() != rightSections.getTransitionMode()) {
            return true;
        }
        var left = leftSections.getStations();
        var right = rightSections.getStations();
        if (left.size() != right.size()) return true;
        for (int i = 0; i < left.size(); i++) {
            if (Double.compare(left.get(i).getStation(), right.get(i).getStation()) != 0
                    || !RoadCrossSectionEngineeringEquality.equals(left.get(i).getCrossSection(), right.get(i).getCrossSection())) return true;
        }
        return false;
    }

    public static void applyToLive(RoadNetwork live, RoadNetwork workingNetwork, String roadId) {
        applyToLive(live, workingNetwork, roadId, java.util.Set.of(roadId));
    }

    /**
     * 将工作副本中 {@code sourceRoadId} 的可变断面复制到 live 上全部 {@code targetRoadIds}。
     */
    public static void applyToLive(
            RoadNetwork live,
            RoadNetwork workingNetwork,
            String sourceRoadId,
            java.util.Set<String> targetRoadIds) {
        Road workingRoad = workingNetwork.getRoad(sourceRoadId);
        if (live == null || workingRoad == null) {
            return;
        }
        RoadVariableCrossSections source = workingRoad.getVariableCrossSections();
        java.util.Set<String> targets = targetRoadIds == null || targetRoadIds.isEmpty()
            ? java.util.Set.of(sourceRoadId)
            : targetRoadIds;
        for (String targetId : targets) {
            Road liveRoad = live.getRoad(targetId);
            if (liveRoad == null) {
                continue;
            }
            liveRoad.setVariableCrossSections(source != null ? source.copy() : null);
        }
    }

}
