package com.plot.plugin.road.lamp;

import java.util.List;

import static com.plot.plugin.road.lamp.RoadLampStyle.Detail.*;
import static com.plot.plugin.road.lamp.RoadLampStyle.Layout.*;
import static com.plot.plugin.road.lamp.RoadLampStyle.Light.*;
import static com.plot.plugin.road.lamp.RoadLampStyle.Material.*;
import static com.plot.plugin.road.lamp.RoadLampStyle.Mount.*;

public final class RoadLampPresets {
    public record Preset(String id, RoadLampStyle style) {
    }

    public static final List<Preset> ALL = List.of(
        preset("modern_single", "Modern Single", 6, METAL, false, SINGLE, TOP, 1, 1, SEA),
        preset("urban_cantilever", "Urban Cantilever", 9, METAL, true, INWARD, TOP, 3, 1, SEA, URBAN),
        preset("classic_twin", "Classic Twin", 7, STONE, true, TWIN, TOP, 2, 1, LANTERN),
        preset("wooden_hanging_twin", "Wooden Hanging Twin", 9, WOOD, true, TWIN, CHAIN, 2, 2, LANTERN, TIMBER),
        preset("cross_four", "Four-way Plaza", 8, COPPER, true, CROSS, TOP, 2, 1, AMBER),
        preset("triple_center", "Three-head Garden", 7, METAL, true, TRIPLE, TOP, 1, 1, SOUL),
        preset("pavilion_twin", "Pavilion Twin", 8, DARK_WOOD, true, TWIN, HOOD, 2, 1, LANTERN, EAVES),
        preset("stone_heritage", "Stone Heritage", 6, STONE, true, INWARD, TOP, 1, 1, AMBER, HERITAGE)
    );

    private RoadLampPresets() {
    }

    private static Preset preset(String id, String name, int height, RoadLampStyle.Material material,
                                 boolean plinth, RoadLampStyle.Layout layout, RoadLampStyle.Mount mount,
                                 int reach, int chain, RoadLampStyle.Light light) {
        return preset(id, name, height, material, plinth, layout, mount, reach, chain, light, NONE);
    }

    private static Preset preset(String id, String name, int height, RoadLampStyle.Material material,
                                 boolean plinth, RoadLampStyle.Layout layout, RoadLampStyle.Mount mount,
                                 int reach, int chain, RoadLampStyle.Light light, RoadLampStyle.Detail detail) {
        return new Preset(id, new RoadLampStyle(name, height, material, plinth,
            List.of(new RoadLampStyle.Level(height - 1, layout, mount, reach, chain, light)), detail));
    }
}
