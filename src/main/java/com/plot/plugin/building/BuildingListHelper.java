package com.plot.plugin.building;

import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 建筑列表排序（概览 Tab 使用）。
 */
public final class BuildingListHelper {

    public enum SortMode {
        INSERTION("plugin.building.sort.insertion"),
        AREA_ASC("plugin.building.sort.area_asc"),
        AREA_DESC("plugin.building.sort.area_desc"),
        FLOORS_ASC("plugin.building.sort.floors_asc"),
        FLOORS_DESC("plugin.building.sort.floors_desc"),
        NAME("plugin.building.sort.name");

        private final String i18nKey;

        SortMode(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String label() {
            return PlotI18n.tr(i18nKey);
        }
    }

    private BuildingListHelper() {
    }

    public static List<BuildingFootprint> sorted(BuildingProject project, SortMode mode) {
        return sorted(project, mode, WorldProjectionSnapshot.UNKNOWN);
    }

    public static List<BuildingFootprint> sorted(
            BuildingProject project,
            SortMode mode,
            WorldProjectionSnapshot projection) {
        if (project == null || mode == null) {
            return List.of();
        }
        List<BuildingFootprint> buildings = new ArrayList<>(project.getBuildings().values());
        if (mode == SortMode.INSERTION) {
            return buildings;
        }
        WorldProjectionSnapshot effectiveProjection = projection != null
            ? projection
            : WorldProjectionSnapshot.UNKNOWN;
        Comparator<BuildingFootprint> comparator = switch (mode) {
            case AREA_ASC -> Comparator.comparingInt(
                building -> BuildingBlockCountCache.blockCount(building, effectiveProjection));
            case AREA_DESC -> Comparator.<BuildingFootprint>comparingInt(
                building -> BuildingBlockCountCache.blockCount(building, effectiveProjection)).reversed();
            case FLOORS_ASC -> Comparator.comparingInt(BuildingFootprint::getFloors);
            case FLOORS_DESC -> Comparator.comparingInt(BuildingFootprint::getFloors).reversed();
            case NAME -> Comparator.comparing(
                building -> building.getName() != null ? building.getName() : "",
                String.CASE_INSENSITIVE_ORDER);
            case INSERTION -> Comparator.comparingInt(building -> 0);
        };
        buildings.sort(comparator);
        return buildings;
    }
}
