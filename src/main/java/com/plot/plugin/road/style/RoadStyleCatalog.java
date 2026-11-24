package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置道路风格目录。AI / UI 通过 styleId 选用完整工程参数包。
 * <p>
 * 几何 preset 与 {@link RoadThemeCatalog} 视觉主题正交：同一横断面可换 Medieval / Cyberpunk 等调色板。
 * <p>
 * 对称约定：双向道路打包目标宽减去中心线占格后，应能被车道数整除，避免左右行驶宽不对称。
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
        RoadStyle style = softNone("path");
        style.width = 3;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.roadMaterial = "minecraft:dirt_path";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    /** 5 格村道：无标线。 */
    public static RoadStyle villageRoad() {
        RoadStyle style = softNone("village_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.roadMaterial = "minecraft:coarse_dirt";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    public static RoadStyle residential() {
        RoadStyle style = urbanWall("residential");
        style.width = 6;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:stone";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        style.streetlightSpacing = 16;
        style.lampPresetId = "modern_single";
        return style;
    }

    /** 城市街道：2 车道 3+1+3，人行道 1，单虚线。 */
    public static RoadStyle cityStreet() {
        RoadStyle style = urbanWall("city_street");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:smooth_stone";
        style.streetlightSpacing = 12;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        style.lampPresetId = "modern_single";
        return style;
    }

    /** 林荫大道：车行 12（5+2+5）+ 中央分隔 + 宽人行；总硬质约 16。 */
    public static RoadStyle avenue() {
        RoadStyle style = urbanWall("avenue");
        style.width = 12;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeShoulder = false;
        style.includeMedian = true;
        style.medianWidth = 1;
        style.roadMaterial = "minecraft:black_concrete";
        style.sidewalkMaterial = "minecraft:stone_bricks";
        style.centerLineStyle = CenterLineStyle.DOUBLE_SOLID.name();
        style.laneDividers = true;
        style.streetlightSpacing = 14;
        style.wallMaterial = "minecraft:stone_bricks";
        style.lampPresetId = "urban_cantilever";
        return style;
    }

    /** 景观大道：2 车道 4+1+4，骑行道 + 宽人行 + 密集路灯。 */
    public static RoadStyle boulevard() {
        RoadStyle style = urbanWall("boulevard");
        style.width = 9;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeShoulder = false;
        style.includeBikeLane = true;
        style.bikeLaneWidth = 1;
        style.bikeLaneMaterial = "minecraft:light_blue_concrete";
        style.roadMaterial = "minecraft:gray_concrete";
        style.sidewalkMaterial = "minecraft:smooth_stone";
        style.streetlightSpacing = 10;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        style.lampPresetId = "triple_center";
        return style;
    }

    /** 城市主干道：4 车道（3×4 + 双黄 2 + 侧缝 2 = 16），双黄 + 分道线。 */
    public static RoadStyle cityMain() {
        RoadStyle style = urbanWall("city_main");
        style.width = 16;
        style.laneCount = 4;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeShoulder = false;
        style.roadMaterial = "minecraft:black_concrete";
        style.sidewalkMaterial = "minecraft:smooth_stone";
        style.streetlightSpacing = 12;
        style.centerLineStyle = CenterLineStyle.DOUBLE_SOLID.name();
        style.laneDividers = true;
        style.lampPresetId = "urban_cantilever";
        return style;
    }

    /** 乡村硬化路：双车道 3+1+3，石子肩，与土质 dirt_road 区分。 */
    public static RoadStyle countryRoad() {
        RoadStyle style = base("country_road");
        style.width = 7;
        style.laneCount = 2;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.5f;
        style.cutSlopeRatio = 1.0f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.shoulderMaterial = "minecraft:andesite";
        style.fillSlopeMaterial = "minecraft:coarse_dirt";
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        style.laneDividers = false;
        return style;
    }

    /** 高速公路：4 车道（16）+ 中央分隔 + 2 格硬路肩。 */
    public static RoadStyle highway() {
        RoadStyle style = base("highway");
        style.width = 16;
        style.laneCount = 4;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 2;
        style.includeMedian = true;
        style.medianWidth = 1;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 2.0f;
        style.cutSlopeRatio = 1.5f;
        style.roadMaterial = "minecraft:black_concrete";
        style.shoulderMaterial = "minecraft:stone";
        style.centerLineStyle = CenterLineStyle.DOUBLE_SOLID.name();
        style.laneDividers = true;
        return style;
    }

    /**
     * 单线铁路路基参数包（已退出内置预设库，仍供交汇/标线测试与程序化造路使用）。
     * 道床 + 两侧道砟肩 + 路堤边坡；中心线铺轨枕 + {@code minecraft:rail}。
     */
    public static RoadStyle railway() {
        RoadStyle style = base("railway");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.includeMedian = false;
        style.includeBikeLane = false;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.5f;
        style.cutSlopeRatio = 1.0f;
        style.roadMaterial = "minecraft:polished_andesite";
        style.shoulderMaterial = "minecraft:cobbled_deepslate";
        style.fillSlopeMaterial = "minecraft:cobbled_deepslate";
        style.cutSlopeMaterial = "minecraft:stone";
        style.markingMaterial = "minecraft:rail";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.RAIL.name();
        style.streetlightSpacing = null;
        return style;
    }

    public static RoadStyle industrial() {
        RoadStyle style = base("industrial");
        style.width = 8;
        style.laneCount = 2;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 2;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.2f;
        style.cutSlopeRatio = 0.8f;
        style.roadMaterial = "minecraft:gray_concrete";
        style.shoulderMaterial = "minecraft:coarse_dirt";
        style.fillSlopeMaterial = "minecraft:coarse_dirt";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    public static RoadStyle park() {
        RoadStyle style = softNone("park");
        style.width = 4;
        style.laneCount = 1;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.includeShoulder = false;
        style.includeBikeLane = false;
        style.roadMaterial = "minecraft:dirt_path";
        style.sidewalkMaterial = "minecraft:grass_block";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    public static RoadStyle mountain() {
        RoadStyle style = base("mountain");
        style.width = 6;
        style.laneCount = 2;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 2.5f;
        style.cutSlopeRatio = 2.0f;
        style.roadMaterial = "minecraft:coarse_dirt";
        style.shoulderMaterial = "minecraft:stone";
        style.fillSlopeMaterial = "minecraft:stone";
        style.cutSlopeMaterial = "minecraft:cobblestone";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    /** 土质无标线乡间小路（与硬化 country_road 区分）。 */
    public static RoadStyle dirtRoad() {
        RoadStyle style = base("dirt_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = false;
        style.includeShoulder = true;
        style.shoulderWidth = 1;
        style.roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
        style.includeSlopeBatter = true;
        style.fillSlopeRatio = 1.5f;
        style.cutSlopeRatio = 1.0f;
        style.roadMaterial = "minecraft:dirt";
        style.shoulderMaterial = "minecraft:coarse_dirt";
        style.fillSlopeMaterial = "minecraft:coarse_dirt";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        return style;
    }

    /** 石材中世纪街道。 */
    public static RoadStyle medievalRoad() {
        RoadStyle style = urbanWall("medieval_road");
        style.width = 5;
        style.laneCount = 1;
        style.hasSidewalk = true;
        style.sidewalkWidth = 1;
        style.roadMaterial = "minecraft:cobblestone";
        style.sidewalkMaterial = "minecraft:stone_bricks";
        style.laneDividers = false;
        style.centerLineStyle = CenterLineStyle.NONE.name();
        style.themeId = "medieval";
        style.wallMaterial = "minecraft:cobblestone";
        style.streetlightSpacing = 14;
        style.lampPresetId = "stone_heritage";
        return style;
    }

    /** 深色路面 + 霓虹标线 + 骑行道 + 密集路灯（2 车道 4+1+4）。 */
    public static RoadStyle cyberpunkStreet() {
        RoadStyle style = urbanWall("cyberpunk_street");
        style.width = 9;
        style.laneCount = 2;
        style.hasSidewalk = true;
        style.sidewalkWidth = 2;
        style.includeBikeLane = true;
        style.bikeLaneWidth = 1;
        style.roadMaterial = "minecraft:black_concrete";
        style.sidewalkMaterial = "minecraft:cyan_concrete";
        style.bikeLaneMaterial = "minecraft:blue_concrete";
        style.markingMaterial = "minecraft:light_blue_concrete";
        style.streetlightSpacing = 8;
        style.centerLineStyle = CenterLineStyle.SINGLE_DASHED.name();
        style.themeId = "cyberpunk";
        style.wallMaterial = "minecraft:cyan_concrete";
        style.lampPresetId = "urban_cantilever";
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
        style.includeMedian = false;
        style.includeSlopeBatter = null;
        style.roadsideEdgeMode = null;
        style.fillSlopeRatio = 0f;
        style.cutSlopeRatio = 0f;
        return style;
    }

    /** 城市硬质路：默认挡土墙收边。 */
    private static RoadStyle urbanWall(String id) {
        RoadStyle style = base(id);
        style.roadsideEdgeMode = RoadsideEdgeMode.RETAINING_WALL.name();
        style.includeSlopeBatter = false;
        style.wallMaterial = "minecraft:stone_bricks";
        return style;
    }

    /** 软质步道：无边坡、无挡土墙。 */
    private static RoadStyle softNone(String id) {
        RoadStyle style = base(id);
        style.roadsideEdgeMode = RoadsideEdgeMode.NONE.name();
        style.includeSlopeBatter = false;
        return style;
    }
}
