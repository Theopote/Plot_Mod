package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.model.section.StationCrossSection;

import java.util.List;

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
        var left = original.getVariableCrossSections() != null
            ? original.getVariableCrossSections().getStations() : List.<StationCrossSection>of();
        var right = working.getVariableCrossSections() != null
            ? working.getVariableCrossSections().getStations() : List.<StationCrossSection>of();
        if (left.size() != right.size()) return true;
        for (int i = 0; i < left.size(); i++) {
            if (Double.compare(left.get(i).getStation(), right.get(i).getStation()) != 0
                    || !RoadCrossSectionEngineeringEquality.equals(left.get(i).getCrossSection(), right.get(i).getCrossSection())) return true;
        }
        return false;
    }

    public static void applyToLive(RoadNetwork live, RoadNetwork workingNetwork, String roadId) {
        Road liveRoad = live.getRoad(roadId);
        Road workingRoad = workingNetwork.getRoad(roadId);
        if (liveRoad == null || workingRoad == null) {
            return;
        }
        RoadVariableCrossSections source = workingRoad.getVariableCrossSections();
        liveRoad.setVariableCrossSections(source != null ? source.copy() : null);
    }

}
