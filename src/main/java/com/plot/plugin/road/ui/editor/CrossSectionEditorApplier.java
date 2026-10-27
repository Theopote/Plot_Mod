package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import java.util.Objects;

/**
 * 横断面浮动编辑器：工作副本与 live 路网的比较与写回。
 */
public final class CrossSectionEditorApplier {

    private CrossSectionEditorApplier() {
    }

    public static boolean hasChanges(
            RoadNetwork openSnapshot,
            RoadNetwork workingNetwork,
            String roadId,
            RoadSystemConfig config) {
        Road original = openSnapshot.getRoad(roadId);
        Road working = workingNetwork.getRoad(roadId);
        if (original == null || working == null) {
            return false;
        }
        return !RoadCrossSectionEngineeringEquality.equals(original.getCrossSection(), working.getCrossSection())
            || !Objects.equals(original.getMaxSlope(), working.getMaxSlope());
    }

    public static void applyToLive(
            RoadNetwork live,
            RoadNetwork workingNetwork,
            String roadId,
            RoadSystemConfig config) {
        Road liveRoad = live.getRoad(roadId);
        Road workingRoad = workingNetwork.getRoad(roadId);
        if (liveRoad == null || workingRoad == null) {
            return;
        }
        // Copy explicit overrides, including null inheritance, rather than freezing resolved defaults.
        liveRoad.setCrossSection(workingRoad.getCrossSection().copy());
        liveRoad.setMaxSlope(workingRoad.getMaxSlope());
    }
}
