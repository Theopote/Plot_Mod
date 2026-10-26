package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;

import java.util.Objects;

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
        return !Objects.equals(
            normalize(original.getVariableCrossSections()),
            normalize(working.getVariableCrossSections()));
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

    private static RoadVariableCrossSections normalize(RoadVariableCrossSections sections) {
        return sections != null ? sections.copy() : null;
    }
}
