package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonBoolean;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 片区重叠检测：2D footprint 相交对 + 体素合并冲突计数。
 * <p>
 * 不阻止生成（仍 later-wins），仅提供 warning 数据。
 */
public final class DistrictOverlapAnalyzer {
    private DistrictOverlapAnalyzer() {
    }

    public record OverlapPair(
            String buildingIdA,
            String buildingNameA,
            String buildingIdB,
            String buildingNameB) {
    }

    public record OverlapReport(
            List<OverlapPair> overlappingBuildingPairs,
            int conflictingBlockCount) {

        public boolean hasOverlap() {
            return !overlappingBuildingPairs.isEmpty() || conflictingBlockCount > 0;
        }

        public int pairCount() {
            return overlappingBuildingPairs.size();
        }
    }

    /**
     * 对成功生成的建筑做 footprint 两两相交检测（O(n²)，片区规模可接受）。
     */
    public static List<OverlapPair> findFootprintOverlapPairs(Collection<BuildingFootprint> buildings) {
        List<BuildingFootprint> list = new ArrayList<>();
        if (buildings != null) {
            for (BuildingFootprint building : buildings) {
                if (building != null
                        && building.getOuterPoints() != null
                        && building.getOuterPoints().size() >= 3) {
                    list.add(building);
                }
            }
        }
        List<OverlapPair> pairs = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BuildingFootprint a = list.get(i);
            List<Vec2d> aPts = a.getOuterPoints();
            for (int j = i + 1; j < list.size(); j++) {
                BuildingFootprint b = list.get(j);
                if (PolygonBoolean.intersects(aPts, b.getOuterPoints())) {
                    pairs.add(new OverlapPair(
                        a.getId(), a.getName(),
                        b.getId(), b.getName()));
                }
            }
        }
        return pairs;
    }

    /**
     * 统计合并时被后栋覆盖的方块数，并补充因体素冲突产生的建筑对。
     */
    public static int countConflictingBlocks(
            Map<BlockPos, String> previousOwners,
            Map<BlockPos, ? extends Object> incomingRecords,
            String incomingBuildingId,
            Set<String> conflictPairKeys,
            List<OverlapPair> voxelPairs,
            Map<String, String> idToName) {
        if (incomingRecords == null || incomingRecords.isEmpty() || incomingBuildingId == null) {
            return 0;
        }
        int conflicts = 0;
        for (BlockPos pos : incomingRecords.keySet()) {
            if (pos == null) {
                continue;
            }
            String previousId = previousOwners.get(pos);
            if (previousId != null && !previousId.equals(incomingBuildingId)) {
                conflicts++;
                String key = pairKey(previousId, incomingBuildingId);
                if (conflictPairKeys.add(key)) {
                    voxelPairs.add(new OverlapPair(
                        previousId,
                        idToName.getOrDefault(previousId, previousId),
                        incomingBuildingId,
                        idToName.getOrDefault(incomingBuildingId, incomingBuildingId)));
                }
            }
            previousOwners.put(pos, incomingBuildingId);
        }
        return conflicts;
    }

    public static String pairKey(String idA, String idB) {
        if (idA.compareTo(idB) <= 0) {
            return idA + "|" + idB;
        }
        return idB + "|" + idA;
    }

    public static List<OverlapPair> mergeUniquePairs(
            List<OverlapPair> footprintPairs,
            List<OverlapPair> voxelPairs) {
        Set<String> seen = new LinkedHashSet<>();
        List<OverlapPair> merged = new ArrayList<>();
        for (OverlapPair pair : footprintPairs) {
            if (pair == null) {
                continue;
            }
            if (seen.add(pairKey(pair.buildingIdA(), pair.buildingIdB()))) {
                merged.add(pair);
            }
        }
        for (OverlapPair pair : voxelPairs) {
            if (pair == null) {
                continue;
            }
            if (seen.add(pairKey(pair.buildingIdA(), pair.buildingIdB()))) {
                merged.add(pair);
            }
        }
        return merged;
    }
}
