package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置道路视觉主题。与 {@link RoadStyleCatalog} 几何 preset 正交：同一 7 m 城市路可换 Modern / Medieval / Cyberpunk 等调色板。
 */
public final class RoadThemeCatalog {

    public static final String MODERN_ID = "modern";

    private RoadThemeCatalog() {
    }

    public static List<RoadTheme> defaultThemes() {
        List<RoadTheme> themes = new ArrayList<>();
        themes.add(modern());
        themes.add(medieval());
        themes.add(japanese());
        themes.add(industrial());
        themes.add(fantasy());
        themes.add(cyberpunk());
        themes.add(rural());
        themes.add(desert());
        themes.add(snow());
        themes.add(nether());
        return themes;
    }

    /** 现代默认：不覆盖 preset 内建材质。 */
    public static RoadTheme modern() {
        RoadTheme theme = new RoadTheme(MODERN_ID);
        theme.streetlightBlock = "minecraft:lantern";
        return theme;
    }

    public static RoadTheme medieval() {
        RoadTheme theme = new RoadTheme("medieval");
        theme.roadMaterial = "minecraft:cobblestone";
        theme.sidewalkMaterial = "minecraft:stone_bricks";
        theme.shoulderMaterial = "minecraft:gravel";
        theme.markingMaterial = "minecraft:smooth_stone";
        theme.fillSlopeMaterial = "minecraft:cobblestone";
        theme.cutSlopeMaterial = "minecraft:stone";
        theme.streetlightBlock = "minecraft:torch";
        return theme;
    }

    public static RoadTheme japanese() {
        RoadTheme theme = new RoadTheme("japanese");
        theme.roadMaterial = "minecraft:polished_andesite";
        theme.sidewalkMaterial = "minecraft:stone_bricks";
        theme.shoulderMaterial = "minecraft:gravel";
        theme.markingMaterial = "minecraft:white_concrete";
        theme.fillSlopeMaterial = "minecraft:coarse_dirt";
        theme.cutSlopeMaterial = "minecraft:andesite";
        theme.streetlightBlock = "minecraft:lantern";
        return theme;
    }

    public static RoadTheme industrial() {
        RoadTheme theme = new RoadTheme("industrial");
        theme.roadMaterial = "minecraft:gray_concrete";
        theme.sidewalkMaterial = "minecraft:iron_block";
        theme.shoulderMaterial = "material.plot.gravel";
        theme.markingMaterial = "minecraft:yellow_concrete";
        theme.fillSlopeMaterial = "minecraft:gravel";
        theme.cutSlopeMaterial = "minecraft:deepslate_bricks";
        theme.streetlightBlock = "minecraft:iron_bars";
        return theme;
    }

    public static RoadTheme fantasy() {
        RoadTheme theme = new RoadTheme("fantasy");
        theme.roadMaterial = "minecraft:mossy_cobblestone";
        theme.sidewalkMaterial = "minecraft:moss_block";
        theme.shoulderMaterial = "minecraft:grass_block";
        theme.markingMaterial = "minecraft:glowstone";
        theme.fillSlopeMaterial = "minecraft:grass_block";
        theme.cutSlopeMaterial = "minecraft:rooted_dirt";
        theme.streetlightBlock = "minecraft:glowstone";
        return theme;
    }

    public static RoadTheme cyberpunk() {
        RoadTheme theme = new RoadTheme("cyberpunk");
        theme.roadMaterial = "minecraft:black_concrete";
        theme.sidewalkMaterial = "minecraft:cyan_concrete";
        theme.shoulderMaterial = "minecraft:gray_concrete";
        theme.markingMaterial = "minecraft:light_blue_concrete";
        theme.bikeLaneMaterial = "minecraft:blue_concrete";
        theme.fillSlopeMaterial = "minecraft:deepslate";
        theme.cutSlopeMaterial = "minecraft:polished_deepslate";
        theme.streetlightBlock = "minecraft:sea_lantern";
        return theme;
    }

    public static RoadTheme rural() {
        RoadTheme theme = new RoadTheme("rural");
        theme.roadMaterial = "minecraft:dirt_path";
        theme.sidewalkMaterial = "minecraft:coarse_dirt";
        theme.shoulderMaterial = "minecraft:grass_block";
        theme.markingMaterial = "minecraft:packed_mud";
        theme.fillSlopeMaterial = "minecraft:coarse_dirt";
        theme.cutSlopeMaterial = "minecraft:dirt";
        theme.streetlightBlock = "minecraft:oak_fence";
        return theme;
    }

    public static RoadTheme desert() {
        RoadTheme theme = new RoadTheme("desert");
        theme.roadMaterial = "minecraft:smooth_sandstone";
        theme.sidewalkMaterial = "minecraft:sandstone";
        theme.shoulderMaterial = "minecraft:sand";
        theme.markingMaterial = "minecraft:cut_sandstone";
        theme.fillSlopeMaterial = "minecraft:sand";
        theme.cutSlopeMaterial = "minecraft:red_sandstone";
        theme.streetlightBlock = "minecraft:soul_lantern";
        return theme;
    }

    public static RoadTheme snow() {
        RoadTheme theme = new RoadTheme("snow");
        theme.roadMaterial = "minecraft:packed_ice";
        theme.sidewalkMaterial = "minecraft:snow_block";
        theme.shoulderMaterial = "minecraft:powder_snow";
        theme.markingMaterial = "minecraft:blue_ice";
        theme.fillSlopeMaterial = "minecraft:snow_block";
        theme.cutSlopeMaterial = "minecraft:ice";
        theme.streetlightBlock = "minecraft:soul_lantern";
        return theme;
    }

    public static RoadTheme nether() {
        RoadTheme theme = new RoadTheme("nether");
        theme.roadMaterial = "minecraft:blackstone";
        theme.sidewalkMaterial = "minecraft:polished_blackstone_bricks";
        theme.shoulderMaterial = "minecraft:basalt";
        theme.markingMaterial = "minecraft:crimson_planks";
        theme.fillSlopeMaterial = "minecraft:netherrack";
        theme.cutSlopeMaterial = "minecraft:blackstone";
        theme.streetlightBlock = "minecraft:shroomlight";
        return theme;
    }

    public static String defaultStreetlightBlock() {
        return modern().streetlightBlock;
    }

    public static RoadTheme findById(String themeId) {
        if (themeId == null || themeId.isBlank() || MODERN_ID.equals(themeId)) {
            return modern();
        }
        for (RoadTheme theme : defaultThemes()) {
            if (themeId.equals(theme.id)) {
                return theme;
            }
        }
        return null;
    }

    public static Map<String, RoadTheme> indexById() {
        Map<String, RoadTheme> index = new LinkedHashMap<>();
        for (RoadTheme theme : defaultThemes()) {
            if (theme.id != null) {
                index.put(theme.id, theme);
            }
        }
        return index;
    }

    /**
     * 将主题调色板叠加到 style 副本上，供预览与生成使用。
     */
    public static RoadStyle applyTheme(String themeId, RoadStyle style) {
        if (style == null) {
            return null;
        }
        RoadTheme theme = findById(themeId);
        if (theme == null || MODERN_ID.equals(theme.id)) {
            return style.copy();
        }
        RoadStyle themed = style.copy();
        theme.applyPalette(themed);
        themed.themeId = theme.id;
        return themed;
    }

    public static void applyThemeToConfig(String themeId, RoadSystemConfig config) {
        if (config == null) {
            return;
        }
        RoadTheme theme = findById(themeId);
        if (theme != null && !MODERN_ID.equals(theme.id)) {
            theme.applyToConfig(config);
        }
    }

    /**
     * 无 preset 的自定义道路：将主题调色板覆盖到道路横断面材质字段。
     */
    public static void overlayThemeOnRoad(String themeId, Road road, RoadSystemConfig config) {
        if (road == null || config == null) {
            return;
        }
        RoadTheme theme = findById(themeId);
        if (theme == null || MODERN_ID.equals(theme.id)) {
            return;
        }
        RoadStyle scratch = new RoadStyle();
        var resolved = road.getCrossSection().resolve(config);
        scratch.roadMaterial = resolved.carriagewayMaterial.getPrimaryMaterial();
        scratch.sidewalkMaterial = resolved.sidewalkMaterial;
        scratch.shoulderMaterial = resolved.shoulderMaterial;
        scratch.bikeLaneMaterial = resolved.bikeLaneMaterial;
        scratch.markingMaterial = resolved.markingMaterial;
        scratch.fillSlopeMaterial = resolved.fillSlopeMaterial;
        scratch.cutSlopeMaterial = resolved.cutSlopeMaterial;
        scratch.streetlightBlock = resolved.streetlightBlock;
        theme.applyPalette(scratch);

        var section = road.getCrossSection();
        if (scratch.roadMaterial != null) {
            section.getCarriageway().setMaterial(
                com.plot.core.material.MaterialMix.single(scratch.roadMaterial));
        }
        if (scratch.sidewalkMaterial != null) {
            section.getSidewalk().setMaterial(scratch.sidewalkMaterial);
        }
        if (scratch.shoulderMaterial != null) {
            section.getShoulder().setMaterial(scratch.shoulderMaterial);
        }
        if (scratch.bikeLaneMaterial != null) {
            section.getBikeLane().setMaterial(scratch.bikeLaneMaterial);
        }
        if (scratch.markingMaterial != null) {
            section.getMarkings().setMaterial(scratch.markingMaterial);
        }
        if (scratch.fillSlopeMaterial != null) {
            section.getSlopeBatter().setFillMaterial(scratch.fillSlopeMaterial);
        }
        if (scratch.cutSlopeMaterial != null) {
            section.getSlopeBatter().setCutMaterial(scratch.cutSlopeMaterial);
        }
        if (scratch.streetlightBlock != null) {
            section.getStreetFurniture().setStreetlightBlock(scratch.streetlightBlock);
        }
    }
}
