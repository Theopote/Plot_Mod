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

    public enum Detail {
        NONE, URBAN, TIMBER, EAVES, HERITAGE;

        /**
         * Whether any level currently satisfies this detail's geometry rules
         * (URBAN needs arm reach ≥ 2; TIMBER needs CHAIN; EAVES needs HOOD).
         */
        public boolean isVisiblyActive(List<Level> levels) {
            if (levels == null || levels.isEmpty()) {
                return this == NONE || this == HERITAGE;
            }
            return switch (this) {
                case NONE, HERITAGE -> true;
                case URBAN -> levels.stream()
                    .anyMatch(level -> level.layout() != Layout.SINGLE && level.reach() > 1);
                case TIMBER -> levels.stream().anyMatch(level -> level.mount() == Mount.CHAIN);
                case EAVES -> levels.stream().anyMatch(level -> level.mount() == Mount.HOOD);
            };
        }
    }

    public enum Layout { SINGLE, INWARD, OUTWARD, TWIN, TRIPLE, CROSS }
    public enum Mount { TOP, CHAIN, HOOD }

    /** Grouping for the fixture combo (search + sections). */
    public enum CatalogGroup { COMMON, COPPER, OTHER }
    /**
     * Street-lamp fixture choices: placeable light-emitting blocks for the current game version.
     * Enum names for the original four fixtures are stable for JSON persistence.
     * <p>
     * Fixtures that need a solid block below (torches, candles, campfires) are TOP-only;
     * CHAIN/HOOD require {@link #supportsSuspended()}. Atypical or unsafe lights
     * (respawn anchor, firefly bush, enchanting table, ender chest) are omitted.
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
        SEA("sea_lantern", Kind.FULL_BLOCK),
        GLOWSTONE("glowstone", Kind.FULL_BLOCK),
        SHROOMLIGHT("shroomlight", Kind.FULL_BLOCK),
        AMBER("ochre_froglight", Kind.FULL_BLOCK),
        VERDANT_FROGLIGHT("verdant_froglight", Kind.FULL_BLOCK),
        PEARLESCENT_FROGLIGHT("pearlescent_froglight", Kind.FULL_BLOCK),
        JACK_O_LANTERN("jack_o_lantern", Kind.FULL_BLOCK),
        REDSTONE_LAMP("redstone_lamp", Kind.LIT_FULL),
        END_ROD("end_rod", Kind.END_ROD),
        TORCH("torch", Kind.GROUND),
        SOUL_TORCH("soul_torch", Kind.GROUND),
        COPPER_TORCH("copper_torch", Kind.GROUND),
        CAMPFIRE("campfire", Kind.LIT_GROUND),
        SOUL_CAMPFIRE("soul_campfire", Kind.LIT_GROUND),
        BEACON("beacon", Kind.FULL_BLOCK),
        CRYING_OBSIDIAN("crying_obsidian", Kind.FULL_BLOCK),
        MAGMA_BLOCK("magma_block", Kind.FULL_BLOCK),
        SCULK_CATALYST("sculk_catalyst", Kind.FULL_BLOCK),
        COPPER_BULB("copper_bulb", Kind.LIT_FULL),
        EXPOSED_COPPER_BULB("exposed_copper_bulb", Kind.LIT_FULL),
        WEATHERED_COPPER_BULB("weathered_copper_bulb", Kind.LIT_FULL),
        OXIDIZED_COPPER_BULB("oxidized_copper_bulb", Kind.LIT_FULL),
        WAXED_COPPER_BULB("waxed_copper_bulb", Kind.LIT_FULL),
        WAXED_EXPOSED_COPPER_BULB("waxed_exposed_copper_bulb", Kind.LIT_FULL),
        WAXED_WEATHERED_COPPER_BULB("waxed_weathered_copper_bulb", Kind.LIT_FULL),
        WAXED_OXIDIZED_COPPER_BULB("waxed_oxidized_copper_bulb", Kind.LIT_FULL),
        LIGHT("light", Kind.LIGHT_BLOCK),
        // 保留枚举名以兼容旧存档；不再出现在灯具面板中。
        CANDLE("candle", Kind.LIT_CANDLE, false),
        WHITE_CANDLE("white_candle", Kind.LIT_CANDLE, false),
        LIGHT_GRAY_CANDLE("light_gray_candle", Kind.LIT_CANDLE, false),
        GRAY_CANDLE("gray_candle", Kind.LIT_CANDLE, false),
        BLACK_CANDLE("black_candle", Kind.LIT_CANDLE, false),
        BROWN_CANDLE("brown_candle", Kind.LIT_CANDLE, false),
        RED_CANDLE("red_candle", Kind.LIT_CANDLE, false),
        ORANGE_CANDLE("orange_candle", Kind.LIT_CANDLE, false),
        YELLOW_CANDLE("yellow_candle", Kind.LIT_CANDLE, false),
        LIME_CANDLE("lime_candle", Kind.LIT_CANDLE, false),
        GREEN_CANDLE("green_candle", Kind.LIT_CANDLE, false),
        CYAN_CANDLE("cyan_candle", Kind.LIT_CANDLE, false),
        LIGHT_BLUE_CANDLE("light_blue_candle", Kind.LIT_CANDLE, false),
        BLUE_CANDLE("blue_candle", Kind.LIT_CANDLE, false),
        PURPLE_CANDLE("purple_candle", Kind.LIT_CANDLE, false),
        MAGENTA_CANDLE("magenta_candle", Kind.LIT_CANDLE, false),
        PINK_CANDLE("pink_candle", Kind.LIT_CANDLE, false),
        FIREFLY_BUSH("firefly_bush", Kind.GROUND, false),
        ENCHANTING_TABLE("enchanting_table", Kind.FULL_BLOCK, false),
        ENDER_CHEST("ender_chest", Kind.FULL_BLOCK, false),
        RESPAWN_ANCHOR("respawn_anchor", Kind.FULL_BLOCK, false);

        private enum Kind {
            /** Lanterns that can sit or hang from the block above. */
            HANGING_LANTERN,
            /** Full blocks that remain when setblock'd in air. */
            FULL_BLOCK,
            /** Full blocks that need lit=true. */
            LIT_FULL,
            /** Needs solid support below; TOP mount only. */
            GROUND,
            /** Campfire-like; lit + TOP only. */
            LIT_GROUND,
            /** Candles; lit + TOP only. */
            LIT_CANDLE,
            /** End rod; facing attaches to the adjacent support. */
            END_ROD,
            /** Invisible light block; valid anywhere. */
            LIGHT_BLOCK
        }

        private final String id;
        private final Kind kind;
        private final boolean catalog;

        Light(String path, Kind kind) {
            this(path, kind, true);
        }

        Light(String path, Kind kind, boolean catalog) {
            this.id = "minecraft:" + path;
            this.kind = kind;
            this.catalog = catalog;
        }

        public String blockId() {
            return id;
        }

        /** Whether the designer dropdown should offer this fixture. */
        public boolean inCatalog() {
            return catalog;
        }

        /** Designer combo section for catalog fixtures. */
        public CatalogGroup catalogGroup() {
            if (name().contains("COPPER_LANTERN") || name().contains("COPPER_BULB")) {
                return CatalogGroup.COPPER;
            }
            return switch (this) {
                case LANTERN, SOUL, SEA, GLOWSTONE, SHROOMLIGHT, AMBER, VERDANT_FROGLIGHT,
                     PEARLESCENT_FROGLIGHT, END_ROD, REDSTONE_LAMP, JACK_O_LANTERN -> CatalogGroup.COMMON;
                case TORCH, SOUL_TORCH, COPPER_TORCH, CAMPFIRE, SOUL_CAMPFIRE, BEACON,
                     CRYING_OBSIDIAN, MAGMA_BLOCK, SCULK_CATALYST, LIGHT -> CatalogGroup.OTHER;
                default -> CatalogGroup.OTHER;
            };
        }

        /**
         * Whether this fixture can be placed under CHAIN/HOOD without popping off
         * (no solid block below; support comes from above or the block is free-standing).
         */
        public boolean supportsSuspended() {
            return switch (kind) {
                case HANGING_LANTERN, FULL_BLOCK, LIT_FULL, END_ROD, LIGHT_BLOCK -> true;
                case GROUND, LIT_GROUND, LIT_CANDLE -> false;
            };
        }

        public boolean supportsMount(Mount mount) {
            return mount == Mount.TOP || supportsSuspended();
        }

        String block(boolean hanging) {
            // 已下架但仍可能出现在旧存档中的重生锚：降级为无充能，避免主世界误触爆炸。
            if (this == RESPAWN_ANCHOR) {
                return id + "[charges=0]";
            }
            return switch (kind) {
                case HANGING_LANTERN -> hanging ? id + "[hanging=true]" : id;
                case LIT_FULL, LIT_GROUND -> id + "[lit=true]";
                case LIT_CANDLE -> id + "[lit=true,candles=1]";
                // Suspended: attach to the block above; top-mounted: stand on the arm tip.
                case END_ROD -> id + (hanging ? "[facing=down]" : "[facing=up]");
                case LIGHT_BLOCK -> id + "[level=15]";
                case FULL_BLOCK, GROUND -> id;
            };
        }
    }

    public enum Material {
        METAL("polished_deepslate", "polished_deepslate_wall", "smooth_stone", "polished_deepslate_slab"),
        STONE("stone_bricks", "stone_brick_wall", "chiseled_stone_bricks", "stone_brick_slab"),
        WOOD("stripped_spruce_wood", "spruce_fence", "spruce_planks", "spruce_slab"),
        DARK_WOOD("dark_oak_planks", "dark_oak_fence", "spruce_planks", "dark_oak_slab"),
        COPPER("waxed_cut_copper", "polished_deepslate_wall", "waxed_chiseled_copper", "waxed_cut_copper_slab");

        final String beam;
        final String pole;
        final String trim;
        final String slab;

        Material(String beam, String pole, String trim, String slab) {
            this.beam = "minecraft:" + beam;
            this.pole = "minecraft:" + pole;
            this.trim = "minecraft:" + trim;
            this.slab = "minecraft:" + slab;
        }

        String slabTop() {
            return slab + "[type=top]";
        }

        String slabBottom() {
            return slab + "[type=bottom]";
        }
    }

    public record Level(int y, Layout layout, Mount mount, int reach, int chain, Light light) {
        public Level {
            Objects.requireNonNull(layout);
            Objects.requireNonNull(mount);
            Objects.requireNonNull(light);
            // 旧存档若把火把等顶置灯具配在悬吊上，回退为可悬挂的灯笼，避免世界里掉落。
            if (!light.supportsMount(mount)) {
                light = Light.LANTERN;
            }
            if (reach < 1 || reach > 4 || chain < 1 || chain > 3
                    || ((layout == Layout.SINGLE || layout == Layout.TRIPLE) && mount != Mount.TOP)) {
                throw new IllegalArgumentException("Invalid lamp level");
            }
        }
    }
}
