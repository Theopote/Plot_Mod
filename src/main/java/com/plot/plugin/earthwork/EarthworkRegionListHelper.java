package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 土方区域列表排序（概览 Tab 使用）。
 */
public final class EarthworkRegionListHelper {

    public enum SortMode {
        INSERTION("plugin.earthwork.sort.insertion"),
        AREA_ASC("plugin.earthwork.sort.area_asc"),
        AREA_DESC("plugin.earthwork.sort.area_desc"),
        NAME("plugin.earthwork.sort.name");

        private final String i18nKey;

        SortMode(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String label() {
            return PlotI18n.tr(i18nKey);
        }
    }

    private EarthworkRegionListHelper() {
    }

    public static List<GradingRegion> sorted(EarthworkProject project, SortMode mode) {
        if (project == null || mode == null) {
            return List.of();
        }
        List<GradingRegion> regions = new ArrayList<>(project.getRegions().values());
        if (mode == SortMode.INSERTION) {
            return regions;
        }
        Comparator<GradingRegion> comparator = switch (mode) {
            case AREA_ASC -> Comparator.comparingDouble(GradingRegion::computeArea);
            case AREA_DESC -> Comparator.comparingDouble(GradingRegion::computeArea).reversed();
            case NAME -> Comparator.comparing(
                region -> region.getName() != null ? region.getName() : "",
                String.CASE_INSENSITIVE_ORDER);
            case INSERTION -> Comparator.comparingInt(region -> 0);
        };
        regions.sort(comparator);
        return regions;
    }
}
