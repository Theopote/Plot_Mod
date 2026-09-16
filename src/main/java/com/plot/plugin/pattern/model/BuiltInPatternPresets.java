package com.plot.plugin.pattern.model;

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
}
