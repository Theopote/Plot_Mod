package com.plot.plugin.road.lamp;

import java.util.List;
import java.util.Objects;

/** Local +X points toward the carriageway. Y=0 is the first block above the pavement. */
public record RoadLampStyle(String name, int height, Material material, boolean plinth, List<Level> levels, Detail detail) {
    public RoadLampStyle {
        name = Objects.requireNonNull(name).strip();
        Objects.requireNonNull(material);
        levels = List.copyOf(levels);
        detail = detail == null ? Detail.NONE : detail;
        if (name.isEmpty() || name.length() > 80 || height < 5 || height > 24
                || levels.isEmpty() || levels.size() > 3 || levels.getLast().y != height - 1) {
            throw new IllegalArgumentException("Invalid lamp dimensions or name");
        }
        int previousY = 0;
        for (Level level : levels) {
            int lowest = level.mount == Mount.CHAIN ? level.y - level.chain - 1
                : level.mount == Mount.HOOD ? level.y - 1 : level.y;
            if (lowest < 4 || lowest <= previousY || level.y >= height) {
                throw new IllegalArgumentException("Lamp levels overlap or exceed the pole");
            }
            if ((level.layout == Layout.SINGLE || level.layout == Layout.TRIPLE) && level.y != height - 1) {
                throw new IllegalArgumentException("A center light must cap the pole");
            }
            previousY = level.y + 1;
        }
    }

    public RoadLampStyle(String name, int height, Material material, boolean plinth, List<Level> levels) {
        this(name, height, material, plinth, levels, Detail.NONE);
    }

    public enum Detail { NONE, URBAN, TIMBER, EAVES, HERITAGE }

    public enum Layout { SINGLE, INWARD, OUTWARD, TWIN, TRIPLE, CROSS }
    public enum Mount { TOP, CHAIN, HOOD }
    /**
     * Street-lamp fixture choices: placeable light-emitting blocks for the current game version.
     * Enum names for the original four fixtures are stable for JSON persistence.
     */
    public enum Light {
        LANTERN("lantern", Kind.HANGING_LANTERN),
        SOUL("soul_lantern", Kind.HANGING_LANTERN),
        COPPER_LANTERN("copper_lantern", Kind.HANGING_LANTERN),
        EXPOSED_COPPER_LANTERN("exposed_copper_lantern", Kind.HANGING_LANTERN),
        WEATHERED_COPPER_LANTERN("weathered_copper_lantern", Kind.HANGING_LANTERN),
        OXIDIZED_COPPER_LANTERN("oxidized_copper_lantern", Kind.HANGING_LANTERN),
        WAXED_COPPER_LANTERN("waxed_copper_lantern", Kind.HANGING_LANTERN),
        WAXED_EXPOSED_COPPER_LANTERN("waxed_exposed_copper_lantern", Kind.HANGING_LANTERN),
        WAXED_WEATHERED_COPPER_LANTERN("waxed_weathered_copper_lantern", Kind.HANGING_LANTERN),
        WAXED_OXIDIZED_COPPER_LANTERN("waxed_oxidized_copper_lantern", Kind.HANGING_LANTERN),
        SEA("sea_lantern", Kind.PLAIN),
        GLOWSTONE("glowstone", Kind.PLAIN),
        SHROOMLIGHT("shroomlight", Kind.PLAIN),
        AMBER("ochre_froglight", Kind.PLAIN),
        VERDANT_FROGLIGHT("verdant_froglight", Kind.PLAIN),
        PEARLESCENT_FROGLIGHT("pearlescent_froglight", Kind.PLAIN),
        JACK_O_LANTERN("jack_o_lantern", Kind.PLAIN),
        REDSTONE_LAMP("redstone_lamp", Kind.LIT),
        END_ROD("end_rod", Kind.END_ROD),
        TORCH("torch", Kind.PLAIN),
        SOUL_TORCH("soul_torch", Kind.PLAIN),
        COPPER_TORCH("copper_torch", Kind.PLAIN),
        CAMPFIRE("campfire", Kind.LIT),
        SOUL_CAMPFIRE("soul_campfire", Kind.LIT),
        BEACON("beacon", Kind.PLAIN),
        CRYING_OBSIDIAN("crying_obsidian", Kind.PLAIN),
        MAGMA_BLOCK("magma_block", Kind.PLAIN),
        SCULK_CATALYST("sculk_catalyst", Kind.PLAIN),
        ENCHANTING_TABLE("enchanting_table", Kind.PLAIN),
        ENDER_CHEST("ender_chest", Kind.PLAIN),
        COPPER_BULB("copper_bulb", Kind.LIT),
        EXPOSED_COPPER_BULB("exposed_copper_bulb", Kind.LIT),
        WEATHERED_COPPER_BULB("weathered_copper_bulb", Kind.LIT),
        OXIDIZED_COPPER_BULB("oxidized_copper_bulb", Kind.LIT),
        WAXED_COPPER_BULB("waxed_copper_bulb", Kind.LIT),
        WAXED_EXPOSED_COPPER_BULB("waxed_exposed_copper_bulb", Kind.LIT),
        WAXED_WEATHERED_COPPER_BULB("waxed_weathered_copper_bulb", Kind.LIT),
        WAXED_OXIDIZED_COPPER_BULB("waxed_oxidized_copper_bulb", Kind.LIT),
        CANDLE("candle", Kind.LIT_CANDLE),
        WHITE_CANDLE("white_candle", Kind.LIT_CANDLE),
        LIGHT_GRAY_CANDLE("light_gray_candle", Kind.LIT_CANDLE),
        GRAY_CANDLE("gray_candle", Kind.LIT_CANDLE),
        BLACK_CANDLE("black_candle", Kind.LIT_CANDLE),
        BROWN_CANDLE("brown_candle", Kind.LIT_CANDLE),
        RED_CANDLE("red_candle", Kind.LIT_CANDLE),
        ORANGE_CANDLE("orange_candle", Kind.LIT_CANDLE),
        YELLOW_CANDLE("yellow_candle", Kind.LIT_CANDLE),
        LIME_CANDLE("lime_candle", Kind.LIT_CANDLE),
        GREEN_CANDLE("green_candle", Kind.LIT_CANDLE),
        CYAN_CANDLE("cyan_candle", Kind.LIT_CANDLE),
        LIGHT_BLUE_CANDLE("light_blue_candle", Kind.LIT_CANDLE),
        BLUE_CANDLE("blue_candle", Kind.LIT_CANDLE),
        PURPLE_CANDLE("purple_candle", Kind.LIT_CANDLE),
        MAGENTA_CANDLE("magenta_candle", Kind.LIT_CANDLE),
        PINK_CANDLE("pink_candle", Kind.LIT_CANDLE),
        FIREFLY_BUSH("firefly_bush", Kind.PLAIN),
        LIGHT("light", Kind.LIGHT_BLOCK),
        RESPAWN_ANCHOR("respawn_anchor", Kind.RESPAWN_ANCHOR);

        private enum Kind {
            PLAIN, HANGING_LANTERN, LIT, LIT_CANDLE, END_ROD, LIGHT_BLOCK, RESPAWN_ANCHOR
        }

        private final String id;
        private final Kind kind;

        Light(String path, Kind kind) {
            this.id = "minecraft:" + path;
            this.kind = kind;
        }

        /** Unprefixed block id used for display and registry checks. */
        public String blockId() {
            return id;
        }

        String block(boolean hanging) {
            return switch (kind) {
                case HANGING_LANTERN -> hanging ? id + "[hanging=true]" : id;
                case LIT -> id + "[lit=true]";
                case LIT_CANDLE -> id + "[lit=true,candles=1]";
                case END_ROD -> id + "[facing=up]";
                case LIGHT_BLOCK -> id + "[level=15]";
                case RESPAWN_ANCHOR -> id + "[charges=4]";
                case PLAIN -> id;
            };
        }
    }

    public enum Material {
        METAL("polished_deepslate", "polished_deepslate_wall", "smooth_stone"),
        STONE("stone_bricks", "stone_brick_wall", "chiseled_stone_bricks"),
        WOOD("stripped_spruce_wood", "spruce_fence", "spruce_planks"),
        DARK_WOOD("dark_oak_planks", "dark_oak_fence", "spruce_planks"),
        COPPER("waxed_cut_copper", "polished_deepslate_wall", "waxed_chiseled_copper");

        final String beam;
        final String pole;
        final String trim;
        Material(String beam, String pole, String trim) {
            this.beam = "minecraft:" + beam;
            this.pole = "minecraft:" + pole;
            this.trim = "minecraft:" + trim;
        }
    }

    public record Level(int y, Layout layout, Mount mount, int reach, int chain, Light light) {
        public Level {
            Objects.requireNonNull(layout);
            Objects.requireNonNull(mount);
            Objects.requireNonNull(light);
            if (reach < 1 || reach > 4 || chain < 1 || chain > 3
                    || ((layout == Layout.SINGLE || layout == Layout.TRIPLE) && mount != Mount.TOP)) {
                throw new IllegalArgumentException("Invalid lamp level");
            }
        }
    }
}
