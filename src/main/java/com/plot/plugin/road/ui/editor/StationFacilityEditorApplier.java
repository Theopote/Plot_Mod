package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        applyToLive(live, workingNetwork, roadId, Set.of(roadId), BatchStationMappingMode.PROPORTIONAL);
    }

    /**
     * 将工作副本中 {@code sourceRoadId} 的桩号设施映射到 live 上全部 {@code targetRoadIds}。
     * LEFT/RIGHT 随各路前进方向解释，不做世界坐标翻转。
     */
    public static BatchApplyReport applyToLive(
            RoadNetwork live,
            RoadNetwork workingNetwork,
            String sourceRoadId,
            Set<String> targetRoadIds,
            BatchStationMappingMode mappingMode) {
        Road workingRoad = workingNetwork.getRoad(sourceRoadId);
        if (live == null || workingRoad == null) {
            return BatchApplyReport.empty();
        }
        RoadStationFacilities source = workingRoad.getStationFacilities();
        Set<String> targets = targetRoadIds == null || targetRoadIds.isEmpty()
            ? Set.of(sourceRoadId)
            : targetRoadIds;
        BatchStationMappingMode mode = mappingMode != null
            ? mappingMode
            : BatchStationMappingMode.PROPORTIONAL;
        double sourceLength = RoadStationing.canonicalLength(workingNetwork, workingRoad);
        int applied = 0;
        int dropped = 0;
        int clamped = 0;
        for (String targetId : targets) {
            Road liveRoad = live.getRoad(targetId);
            if (liveRoad == null) {
                continue;
            }
            if (source == null) {
                liveRoad.setStationFacilities(null);
                applied++;
                continue;
            }
            double targetLength = RoadStationing.canonicalLength(live, liveRoad);
            BatchStationDataMapper.MapResult<RoadStationFacilities> mapped =
                BatchStationDataMapper.mapStationFacilities(source, sourceLength, targetLength, mode);
            liveRoad.setStationFacilities(mapped.data());
            applied++;
            dropped += mapped.droppedCount();
            clamped += mapped.clampedCount();
        }
        return new BatchApplyReport(applied, dropped, clamped);
    }

    /** @deprecated 请显式传入 {@link BatchStationMappingMode} */
    @Deprecated
    public static void applyToLive(
            RoadNetwork live,
            RoadNetwork workingNetwork,
            String sourceRoadId,
            Set<String> targetRoadIds) {
        applyToLive(live, workingNetwork, sourceRoadId, targetRoadIds, BatchStationMappingMode.PROPORTIONAL);
    }
}
