package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置道路风格目录。AI / UI 通过 styleId 选用完整工程参数包。
 * <p>
 * 几何 preset 与 {@link RoadThemeCatalog} 视觉主题正交：同一横断面可换 Medieval / Cyberpunk 等调色板。
 */
public final class RoadStyleCatalog {
    private RoadStyleCatalog() {
    }

    public static List<RoadStyle> defaultStyles() {
        List<RoadStyle> styles = new ArrayList<>();
        styles.add(path());
        styles.add(villageRoad());
        styles.add(residential());
        styles.add(cityStreet());
        styles.add(avenue());
        styles.add(boulevard());
        styles.add(highway());
        styles.add(mountain());
        styles.add(dirtRoad());
        styles.add(medievalRoad());
        styles.add(cyberpunkStreet());
        styles.add(cityMain());
        styles.add(countryRoad());
        styles.add(industrial());
        styles.add(park());
        return styles;
    }

    /** 3 格步道：最窄人行通道。 */
    public static RoadStyle path() {
        RoadStyle style = base("path");
        style.width = 3;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.maxSlope = 8.0f;
        style.roadMaterial = "minecraft:dirt_path";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    /** 5 格村道：无标线。 */
    public static RoadStyle villageRoad() {
        RoadStyle style = base("village_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.maxSlope = 10.0f;
        style.roadMaterial = "minecraft:coarse_dirt";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
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

    /** 9 格城市街道：人行道 + 路灯。 */
    public static RoadStyle cityStreet() {
        RoadStyle style = base("city_street");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.maxSlope = 8.0f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:smooth_stone";
        style.streetlightSpacing = 12;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        return style;
    }

    /** 13 格林荫大道：中央分隔 + 宽人行道。 */
    public static RoadStyle avenue() {
        RoadStyle style = base("avenue");
        style.width = 8;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.includeMedian = true;
        style.medianWidth = 1;
        style.maxSlope = 6.0f;
        style.roadMaterial = "minecraft:black_concrete";
        style.sidewalkMaterial = "minecraft:stone_bricks";
        style.centerLineStyle = CenterLineStyle.DOUBLE_SOLID.name();
        style.laneDividers = true;
        style.streetlightSpacing = 14;
        return style;
    }

    /** 宽人行道 + 路灯，适合城市景观主轴。 */
    public static RoadStyle boulevard() {
        RoadStyle style = base("boulevard");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeShoulder = false;
        style.maxSlope = 6.0f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:grass_block";
        style.streetlightSpacing = 10;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        return style;
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

    /** 土/砾石乡间路。 */
    public static RoadStyle dirtRoad() {
        RoadStyle style = base("dirt_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.5f;
        style.cutSlopeRatio = 1.0f;
        style.maxSlope = 14.0f;
        style.roadMaterial = "minecraft:dirt";
        style.shoulderMaterial = "minecraft:gravel";
        style.fillSlopeMaterial = "minecraft:coarse_dirt";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    /** 石材中世纪街道。 */
    public static RoadStyle medievalRoad() {
        RoadStyle style = base("medieval_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.maxSlope = 10.0f;
        style.roadMaterial = "minecraft:cobblestone";
        style.sidewalkMaterial = "minecraft:stone_bricks";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        style.themeId = "medieval";
        return style;
    }

    /** 深色路面 + 霓虹标线 + 密集路灯。 */
    public static RoadStyle cyberpunkStreet() {
        RoadStyle style = base("cyberpunk_street");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeBikeLane = true;
        style.bikeLaneWidth = 1;
        style.maxSlope = 8.0f;
        style.roadMaterial = "minecraft:black_concrete";
        style.sidewalkMaterial = "minecraft:cyan_concrete";
        style.bikeLaneMaterial = "minecraft:blue_concrete";
        style.markingMaterial = "minecraft:light_blue_concrete";
        style.streetlightSpacing = 8;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        style.themeId = "cyberpunk";
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
        style.fillSlopeRatio = 0f;
        style.cutSlopeRatio = 0f;
        return style;
    }
}
