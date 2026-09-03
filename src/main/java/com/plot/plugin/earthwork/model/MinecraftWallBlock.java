package com.plot.plugin.earthwork.model;

/**
 * 挡土墙三种常用方块，给玩家点选而不是先翻方块选择器。
 */
public enum MinecraftWallBlock {
    STONE_BRICKS("minecraft:stone_bricks", "plugin.earthwork.wall.stone_bricks"),
    COBBLESTONE("minecraft:cobblestone", "plugin.earthwork.wall.cobblestone"),
    DEEPSLATE_BRICKS("minecraft:deepslate_bricks", "plugin.earthwork.wall.deepslate_bricks");

    public static final String DEFAULT_BLOCK_ID = STONE_BRICKS.blockId;

    private final String blockId;
    private final String i18nKey;

    MinecraftWallBlock(String blockId, String i18nKey) {
        this.blockId = blockId;
        this.i18nKey = i18nKey;
    }

    public String blockId() {
        return blockId;
    }

    public String i18nKey() {
        return i18nKey;
    }

    public static MinecraftWallBlock fromBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return STONE_BRICKS;
        }
        String id = blockId.trim();
        for (MinecraftWallBlock preset : values()) {
            if (preset.blockId.equals(id)) {
                return preset;
            }
        }
        return STONE_BRICKS;
    }
}
