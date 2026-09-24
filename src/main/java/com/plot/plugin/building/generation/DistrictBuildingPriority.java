package com.plot.plugin.building.generation;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** 片区生成顺序：总高度高的优先；同高时按 id 稳定决胜（先处理者覆盖重叠区）。 */
public final class DistrictBuildingPriority {
    private static final Comparator<BuildingFootprint> GENERATION_ORDER = Comparator
        .comparingInt(DistrictBuildingPriority::totalHeightBlocks).reversed()
        .thenComparing(BuildingFootprint::getId, Comparator.reverseOrder());

    private DistrictBuildingPriority() {
    }

    public static int totalHeightBlocks(BuildingFootprint building) {
        if (building == null) {
            return 0;
        }
        return Math.max(1, building.getFloors()) * Math.max(1, building.getFloorHeight());
    }

    public static List<BuildingFootprint> sortedForGeneration(Collection<BuildingFootprint> buildings) {
        List<BuildingFootprint> ordered = new ArrayList<>();
        if (buildings != null) {
            for (BuildingFootprint building : buildings) {
                if (building != null) {
                    ordered.add(building);
                }
            }
        }
        ordered.sort(GENERATION_ORDER);
        return ordered;
    }

    /**
     * {@code dominant} 是否在重叠区优先于 {@code incoming}（更高，或同高但已先处理）。
     */
    public static boolean dominates(BuildingFootprint dominant, BuildingFootprint incoming) {
        if (dominant == null || incoming == null || dominant == incoming) {
            return false;
        }
        int dominantHeight = totalHeightBlocks(dominant);
        int incomingHeight = totalHeightBlocks(incoming);
        if (dominantHeight > incomingHeight) {
            return true;
        }
        if (dominantHeight < incomingHeight) {
            return false;
        }
        return GENERATION_ORDER.compare(dominant, incoming) < 0;
    }
}
