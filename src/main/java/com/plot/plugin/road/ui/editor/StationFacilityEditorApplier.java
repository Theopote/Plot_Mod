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
        applyToLive(live, workingNetwork, roadId, java.util.Set.of(roadId));
    }

    /**
     * 将工作副本中 {@code sourceRoadId} 的桩号设施复制到 live 上全部 {@code targetRoadIds}。
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
        RoadStationFacilities source = workingRoad.getStationFacilities();
        java.util.Set<String> targets = targetRoadIds == null || targetRoadIds.isEmpty()
            ? java.util.Set.of(sourceRoadId)
            : targetRoadIds;
        for (String targetId : targets) {
            Road liveRoad = live.getRoad(targetId);
            if (liveRoad == null) {
                continue;
            }
            liveRoad.setStationFacilities(source != null ? source.copy() : null);
        }
    }

}
