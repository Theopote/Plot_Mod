package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;

import com.plot.plugin.road.model.facility.StationFacilityRun;

import java.util.List;
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
        var left = original.getStationFacilities() != null ? original.getStationFacilities().sortedRuns()
            : List.<StationFacilityRun>of();
        var right = working.getStationFacilities() != null ? working.getStationFacilities().sortedRuns()
            : List.<StationFacilityRun>of();
        if (left.size() != right.size()) return true;
        for (int i = 0; i < left.size(); i++) {
            var a = left.get(i);
            var b = right.get(i);
            if (Double.compare(a.getStartStation(), b.getStartStation()) != 0
                    || !Objects.equals(a.getEndStation(), b.getEndStation())
                    || a.getKind() != b.getKind() || a.getSide() != b.getSide()
                    || !Objects.equals(a.getMaterial(), b.getMaterial())
                    || !Objects.equals(a.getHeight(), b.getHeight())) return true;
        }
        return false;
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

}
