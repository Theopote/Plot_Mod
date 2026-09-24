package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
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
 * 不阻止生成（重叠区由更高建筑优先合并），仅提供 warning 数据。
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

    public record ClosePair(
            String buildingIdA,
            String buildingNameA,
            String buildingIdB,
            String buildingNameB,
            double gapBlocks) {
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
     * 检测轮廓未相交但间距过近的建筑对（重叠不算问题）。
     */
    public static List<ClosePair> findTooClosePairs(Collection<BuildingFootprint> buildings, double maxGapBlocks) {
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
        List<ClosePair> pairs = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BuildingFootprint a = list.get(i);
            List<Vec2d> aPts = a.getOuterPoints();
            for (int j = i + 1; j < list.size(); j++) {
                BuildingFootprint b = list.get(j);
                List<Vec2d> bPts = b.getOuterPoints();
                if (PolygonBoolean.intersects(aPts, bPts)) {
                    continue;
                }
                double gap = minDistanceBetweenPolygons(aPts, bPts);
                if (gap <= maxGapBlocks) {
                    pairs.add(new ClosePair(
                        a.getId(), a.getName(),
                        b.getId(), b.getName(),
                        gap));
                }
            }
        }
        return pairs;
    }

    private static double minDistanceBetweenPolygons(List<Vec2d> a, List<Vec2d> b) {
        double min = Double.MAX_VALUE;
        for (Vec2d point : a) {
            min = Math.min(min, minDistancePointToPolygon(point, b));
        }
        for (Vec2d point : b) {
            min = Math.min(min, minDistancePointToPolygon(point, a));
        }
        return min;
    }

    private static double minDistancePointToPolygon(Vec2d point, List<Vec2d> polygon) {
        double min = Double.MAX_VALUE;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % n);
            min = Math.min(min, GeometryUtils.pointToSegmentDistance(point, start, end));
        }
        return min;
    }

    /**
     * 统计合并时被后栋覆盖的方块数，并补充因体素冲突产生的建筑对。
     */
    public static int countConflictingBlocks(
            Map<BlockPos, String> previousOwners,
            Map<BlockPos, ?> incomingRecords,
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

    /**
     * 参与重叠（footprint 或体素冲突）的不重复建筑数量。
     */
    public static int countDistinctBuildings(List<OverlapPair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return 0;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (OverlapPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            if (pair.buildingIdA() != null) {
                ids.add(pair.buildingIdA());
            }
            if (pair.buildingIdB() != null) {
                ids.add(pair.buildingIdB());
            }
        }
        return ids.size();
    }
}
