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
    public enum Light {
        LANTERN("lantern"), SOUL("soul_lantern"), SEA("sea_lantern"), AMBER("ochre_froglight");
        private final String id;
        Light(String id) { this.id = "minecraft:" + id; }
        String block(boolean hanging) {
            return (this == LANTERN || this == SOUL) && hanging ? id + "[hanging=true]" : id;
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
