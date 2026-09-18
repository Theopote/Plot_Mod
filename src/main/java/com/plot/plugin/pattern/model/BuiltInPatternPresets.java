package com.plot.plugin.pattern.model;

import com.plot.plugin.pattern.image.PatternBuiltinPresetAssets;
import com.plot.plugin.pattern.model.ImagePatternConfig.FitMode;
import com.plot.plugin.pattern.model.ImagePatternConfig.MaterialMatchMode;

import java.util.List;

/**
 * 内置图案预设（代码定义、固定 ID，启动时 upsert，不写入用户 JSON）。
 */
public final class BuiltInPatternPresets {
    public static final String ID_PREFIX = "builtin:";

    private BuiltInPatternPresets() {
    }

    public static List<PatternPreset> all() {
        return List.of(
            procedural(
                "checkerboard_classic",
                "plugin.pattern.preset.builtin.checkerboard_classic.name",
                "plugin.pattern.preset.builtin.checkerboard_classic.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
                    config.setTileSize(2.0);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:stone_bricks"));
                }),
            procedural(
                "stripes_45",
                "plugin.pattern.preset.builtin.stripes_45.name",
                "plugin.pattern.preset.builtin.stripes_45.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.STRIPES);
                    config.setTileSize(1.5);
                    config.setAngleDegrees(45.0);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone"));
                }),
            procedural(
                "rings_stone",
                "plugin.pattern.preset.builtin.rings_stone.name",
                "plugin.pattern.preset.builtin.rings_stone.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:andesite", "minecraft:diorite"));
                }),
            procedural(
                "mosaic_stone",
                "plugin.pattern.preset.builtin.mosaic_stone.name",
                "plugin.pattern.preset.builtin.mosaic_stone.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.MOSAIC);
                    config.setTileSize(1.0);
                    config.setMosaicPrimaryRatio(0.7);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:gravel"));
                }),
            procedural(
                "checkerboard_simple",
                "plugin.pattern.preset.builtin.checkerboard_simple.name",
                "plugin.pattern.preset.builtin.checkerboard_simple.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of("minecraft:white_concrete", "minecraft:black_concrete"));
                }),
            procedural(
                "stripes_triple_vertical",
                "plugin.pattern.preset.builtin.stripes_triple_vertical.name",
                "plugin.pattern.preset.builtin.stripes_triple_vertical.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.STRIPES);
                    config.setTileSize(1.0);
                    config.setAngleDegrees(90.0);
                    config.setMaterials(List.of("minecraft:stone_bricks", "minecraft:brick", "minecraft:sandstone"));
                }),
            procedural(
                "hexagonal_stone",
                "plugin.pattern.preset.builtin.hexagonal_stone.name",
                "plugin.pattern.preset.builtin.hexagonal_stone.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.HEXAGONAL);
                    config.setTileSize(1.5);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:andesite"));
                }),
            procedural(
                "diamond_quartz",
                "plugin.pattern.preset.builtin.diamond_quartz.name",
                "plugin.pattern.preset.builtin.diamond_quartz.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.DIAMOND);
                    config.setTileSize(1.0);
                    config.setAngleDegrees(45.0);
                    config.setDensity(1.2);
                    config.setMaterials(List.of("minecraft:quartz_block", "minecraft:chiseled_quartz_block"));
                }),
            procedural(
                "herringbone_wood",
                "plugin.pattern.preset.builtin.herringbone_wood.name",
                "plugin.pattern.preset.builtin.herringbone_wood.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.HERRINGBONE);
                    config.setTileSize(0.8);
                    config.setAngleDegrees(45.0);
                    config.setDensity(1.0);
                    config.setMaterials(List.of(
                        "minecraft:oak_planks",
                        "minecraft:birch_planks",
                        "minecraft:spruce_planks"));
                }),
            procedural(
                "running_bond_brick",
                "plugin.pattern.preset.builtin.running_bond_brick.name",
                "plugin.pattern.preset.builtin.running_bond_brick.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.RUNNING_BOND);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of("minecraft:bricks", "minecraft:stone_bricks"));
                }),
            procedural(
                "crosshatch_stone",
                "plugin.pattern.preset.builtin.crosshatch_stone.name",
                "plugin.pattern.preset.builtin.crosshatch_stone.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.CROSSHATCH);
                    config.setTileSize(1.5);
                    config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:andesite"));
                }),
            procedural(
                "scatter_flagstone",
                "plugin.pattern.preset.builtin.scatter_flagstone.name",
                "plugin.pattern.preset.builtin.scatter_flagstone.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.SCATTER);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of(
                        "minecraft:stone",
                        "minecraft:cobblestone",
                        "minecraft:gravel",
                        "minecraft:andesite"));
                }),
            procedural(
                "radial_slate",
                "plugin.pattern.preset.builtin.radial_slate.name",
                "plugin.pattern.preset.builtin.radial_slate.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.RADIAL);
                    config.setTileSize(1.2);
                    config.setMaterials(List.of(
                        "minecraft:deepslate",
                        "minecraft:polished_deepslate",
                        "minecraft:deepslate_bricks",
                        "minecraft:deepslate_tiles"));
                }),
            procedural(
                "windmill_terracotta",
                "plugin.pattern.preset.builtin.windmill_terracotta.name",
                "plugin.pattern.preset.builtin.windmill_terracotta.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.WINDMILL);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of(
                        "minecraft:terracotta",
                        "minecraft:white_terracotta",
                        "minecraft:orange_terracotta",
                        "minecraft:yellow_terracotta"));
                }),
            procedural(
                "frame_border",
                "plugin.pattern.preset.builtin.frame_border.name",
                "plugin.pattern.preset.builtin.frame_border.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.FRAME);
                    config.setTileSize(1.0);
                    config.setMaterials(List.of("minecraft:polished_andesite", "minecraft:stone"));
                }),
            procedural(
                "fish_scale_prismarine",
                "plugin.pattern.preset.builtin.fish_scale_prismarine.name",
                "plugin.pattern.preset.builtin.fish_scale_prismarine.description",
                config -> {
                    config.setType(ProceduralPatternConfig.PatternType.FISH_SCALE);
                    config.setTileSize(1.2);
                    config.setDensity(1.0);
                    config.setMaterials(List.of("minecraft:prismarine", "minecraft:dark_prismarine"));
                }),
            image(
                "zebra_crossing",
                "plugin.pattern.preset.builtin.zebra_crossing.name",
                "plugin.pattern.preset.builtin.zebra_crossing.description",
                config -> {
                    config.setImagePath(PatternBuiltinPresetAssets.relativePath("zebra_crossing"));
                    config.setImageWidth(64);
                    config.setImageHeight(16);
                    config.setFitMode(FitMode.STRETCH);
                    config.setMaterialMatchMode(MaterialMatchMode.CUSTOM);
                    config.setPaletteBlocks(List.of(
                        "minecraft:white_concrete",
                        "minecraft:black_concrete"));
                }));
    }

    private static PatternPreset procedural(
            String slug,
            String nameKey,
            String descriptionKey,
            java.util.function.Consumer<ProceduralPatternConfig> configurer) {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        configurer.accept(config);
        PatternPreset preset = new PatternPreset();
        preset.setId(ID_PREFIX + slug);
        preset.setNameKey(nameKey);
        preset.setDescriptionKey(descriptionKey);
        preset.setSource(PatternSource.PROCEDURAL);
        preset.setProceduralConfig(config);
        preset.setBuiltIn(true);
        return preset;
    }

    private static PatternPreset image(
            String slug,
            String nameKey,
            String descriptionKey,
            java.util.function.Consumer<ImagePatternConfig> configurer) {
        ImagePatternConfig config = new ImagePatternConfig();
        configurer.accept(config);
        PatternPreset preset = new PatternPreset();
        preset.setId(ID_PREFIX + slug);
        preset.setNameKey(nameKey);
        preset.setDescriptionKey(descriptionKey);
        preset.setSource(PatternSource.IMAGE);
        preset.setImageConfig(config);
        preset.setBuiltIn(true);
        return preset;
    }
}
