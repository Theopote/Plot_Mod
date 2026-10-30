package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.station.RoadStationing;

import java.util.Set;

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
        applyToLive(live, workingNetwork, roadId, Set.of(roadId), BatchStationMappingMode.PROPORTIONAL);
    }

    /**
     * 将工作副本中 {@code sourceRoadId} 的可变断面映射到 live 上全部 {@code targetRoadIds}。
     * 比例映射在源/目标无有效长度时跳过该目标（不猜测桩号）。
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
        RoadVariableCrossSections source = workingRoad.getVariableCrossSections();
        Set<String> targets = targetRoadIds == null || targetRoadIds.isEmpty()
            ? Set.of(sourceRoadId)
            : targetRoadIds;
        BatchStationMappingMode mode = mappingMode != null
            ? mappingMode
            : BatchStationMappingMode.PROPORTIONAL;
        double sourceLength = RoadStationing.canonicalLength(workingNetwork, workingRoad);
        int applied = 0;
        int skipped = 0;
        int dropped = 0;
        int clamped = 0;
        for (String targetId : targets) {
            Road liveRoad = live.getRoad(targetId);
            if (liveRoad == null) {
                skipped++;
                continue;
            }
            if (source == null) {
                liveRoad.setVariableCrossSections(null);
                applied++;
                continue;
            }
            double targetLength = RoadStationing.canonicalLength(live, liveRoad);
            BatchStationDataMapper.MapResult<RoadVariableCrossSections> mapped =
                BatchStationDataMapper.mapVariableCrossSections(source, sourceLength, targetLength, mode);
            if (mapped.rejected()) {
                skipped++;
                dropped += mapped.droppedCount();
                continue;
            }
            liveRoad.setVariableCrossSections(mapped.data());
            applied++;
            dropped += mapped.droppedCount();
            clamped += mapped.clampedCount();
        }
        return new BatchApplyReport(applied, skipped, dropped, clamped);
    }

    public static BatchApplyReport preview(
            RoadNetwork network,
            Road sourceRoad,
            RoadVariableCrossSections source,
            Set<String> targetRoadIds,
            BatchStationMappingMode mappingMode) {
        if (network == null || sourceRoad == null || source == null || targetRoadIds == null) {
            return BatchApplyReport.empty();
        }
        BatchStationMappingMode mode = mappingMode != null
            ? mappingMode
            : BatchStationMappingMode.PROPORTIONAL;
        double sourceLength = RoadStationing.canonicalLength(network, sourceRoad);
        int skipped = 0;
        int dropped = 0;
        int clamped = 0;
        int applied = 0;
        for (String targetId : targetRoadIds) {
            if (targetId.equals(sourceRoad.getId())) {
                continue;
            }
            Road target = network.getRoad(targetId);
            if (target == null) {
                skipped++;
                continue;
            }
            double targetLength = RoadStationing.canonicalLength(network, target);
            var mapped = BatchStationDataMapper.mapVariableCrossSections(
                source, sourceLength, targetLength, mode);
            if (mapped.rejected()) {
                skipped++;
                dropped += mapped.droppedCount();
            } else {
                applied++;
                dropped += mapped.droppedCount();
                clamped += mapped.clampedCount();
            }
        }
        return new BatchApplyReport(applied, skipped, dropped, clamped);
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
