package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;

import java.util.Objects;

/** 沿线设施浮动编辑器写回。 */
public final class StationFacilityEditorApplier {

    private StationFacilityEditorApplier() {
    }

    public static boolean hasChanges(RoadNetwork openSnapshot, RoadNetwork workingNetwork, String roadId) {
        Road original = openSnapshot.getRoad(roadId);
        Road working = workingNetwork.getRoad(roadId);
        if (original == null || working == null) {
            return false;
        }
        return !Objects.equals(
            normalize(original.getStationFacilities()),
            normalize(working.getStationFacilities()));
    }

    public static void applyToLive(RoadNetwork live, RoadNetwork workingNetwork, String roadId) {
        Road liveRoad = live.getRoad(roadId);
        Road workingRoad = workingNetwork.getRoad(roadId);
        if (liveRoad == null || workingRoad == null) {
            return;
        }
        RoadStationFacilities source = workingRoad.getStationFacilities();
        liveRoad.setStationFacilities(source != null ? source.copy() : null);
    }

    private static RoadStationFacilities normalize(RoadStationFacilities facilities) {
        return facilities != null ? facilities.copy() : null;
    }
}
