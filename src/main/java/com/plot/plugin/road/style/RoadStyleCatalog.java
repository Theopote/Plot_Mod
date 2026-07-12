package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置道路风格目录。AI / UI 通过 styleId 选用完整工程参数包。
 */
public final class RoadStyleCatalog {
    private RoadStyleCatalog() {
    }

    public static List<RoadStyle> defaultStyles() {
        List<RoadStyle> styles = new ArrayList<>();
        styles.add(cityMain());
        styles.add(residential());
        styles.add(countryRoad());
        styles.add(highway());
        styles.add(industrial());
        styles.add(park());
        styles.add(mountain());
        return styles;
    }

    public static RoadStyle cityMain() {
        RoadStyle style = base("city_main");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeShoulder = false;
        style.maxSlope = 8.0f;
        style.roadMaterial = "minecraft:white_concrete";
        style.sidewalkMaterial = "minecraft:smooth_stone";
        style.streetlightSpacing = 12;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        return style;
    }

    public static RoadStyle residential() {
        RoadStyle style = base("residential");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.maxSlope = 10.0f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:stone";
        style.streetlightSpacing = 16;
        return style;
    }

    /** @deprecated 兼容旧 preset id */
    @Deprecated
    public static RoadStyle citySecondary() {
        RoadStyle style = residential();
        style.id = "city_secondary";
        style.name = "city_secondary";
        return style;
    }

    public static RoadStyle countryRoad() {
        RoadStyle style = base("country_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.5f;
        style.cutSlopeRatio = 1.0f;
        style.maxSlope = 12.0f;
        style.roadMaterial = "minecraft:gravel";
        style.shoulderMaterial = "minecraft:coarse_dirt";
        style.fillSlopeMaterial = "minecraft:coarse_dirt";
        return style;
    }

    public static RoadStyle highway() {
        RoadStyle style = base("highway");
        style.width = 9;
        style.laneCount = 2;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 2;
        style.includeDrainage = true;
        style.includeMedian = true;
        style.medianWidth = 1;
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 2.0f;
        style.cutSlopeRatio = 1.5f;
        style.maxSlope = 6.0f;
        style.roadMaterial = "minecraft:black_concrete";
        style.shoulderMaterial = "material.plot.gravel";
        style.centerLineStyle = CenterLineStyle.DOUBLE_SOLID.name();
        style.laneDividers = true;
        return style;
    }

    public static RoadStyle industrial() {
        RoadStyle style = base("industrial");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 2;
        style.includeDrainage = true;
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.2f;
        style.cutSlopeRatio = 0.8f;
        style.maxSlope = 8.0f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.shoulderMaterial = "minecraft:gravel";
        style.fillSlopeMaterial = "minecraft:gravel";
        return style;
    }

    public static RoadStyle park() {
        RoadStyle style = base("park");
        style.width = 3;
        style.laneCount = 1;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.includeBikeLane = true;
        style.bikeLaneWidth = 1;
        style.maxSlope = 6.0f;
        style.roadMaterial = "minecraft:dirt_path";
        style.sidewalkMaterial = "minecraft:grass_block";
        style.bikeLaneMaterial = "minecraft:light_blue_concrete";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    public static RoadStyle mountain() {
        RoadStyle style = base("mountain");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 2;
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 2.5f;
        style.cutSlopeRatio = 2.0f;
        style.maxSlope = 18.0f;
        style.roadMaterial = "minecraft:gravel";
        style.shoulderMaterial = "minecraft:stone";
        style.fillSlopeMaterial = "minecraft:stone";
        style.cutSlopeMaterial = "minecraft:cobblestone";
        return style;
    }

    public static RoadStyle findById(RoadSystemConfig config, String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return null;
        }
        if (config != null) {
            for (RoadStyle style : config.getStyles()) {
                if (styleId.equals(style.id)) {
                    return style;
                }
            }
        }
        for (RoadStyle style : defaultStyles()) {
            if (styleId.equals(style.id)) {
                return style;
            }
        }
        if ("city_secondary".equals(styleId)) {
            return citySecondary();
        }
        return null;
    }

    public static Map<String, RoadStyle> indexById(List<RoadStyle> styles) {
        Map<String, RoadStyle> index = new LinkedHashMap<>();
        if (styles == null) {
            return index;
        }
        for (RoadStyle style : styles) {
            if (style != null && style.id != null) {
                index.put(style.id, style);
            }
        }
        return index;
    }

    private static RoadStyle base(String id) {
        RoadStyle style = new RoadStyle(id);
        style.laneCount = 0;
        style.hasSidewalk = false;
        style.sidewalkWidth = 0;
        style.includeShoulder = false;
        style.shoulderWidth = 0;
        style.includeBikeLane = false;
        style.includeDrainage = false;
        style.includeMedian = false;
        style.includeSlopeBatter = null;
        return style;
    }
}
