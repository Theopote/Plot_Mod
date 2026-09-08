package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置线路风格包目录。 */
public final class PowerLineStylePackCatalog {
    private PowerLineStylePackCatalog() {
    }

    /** 装饰向风格（默认展示）。 */
    public static List<PowerLineStylePack> decorativePacks() {
        List<PowerLineStylePack> packs = new ArrayList<>();
        packs.add(classicWood());
        packs.add(doubleWood());
        packs.add(urbanConcrete());
        packs.add(simpleSteel());
        packs.add(modernUtility());
        packs.add(classicLattice());
        packs.add(heavyLattice());
        packs.add(compactLattice());
        packs.add(oldEuropean());
        packs.add(japaneseStreet());
        packs.add(fantasyCopper());
        packs.add(steampunkBrass());
        packs.add(wastelandWind());
        packs.add(abandoned());
        packs.add(suburbanLamp());
        packs.add(rustic());
        return packs;
    }

    /** 工程/自适应塔型（折叠在高级区）。 */
    public static List<PowerLineStylePack> engineeringPacks() {
        List<PowerLineStylePack> packs = new ArrayList<>();
        packs.add(smartTowers());
        packs.add(taperedTower());
        packs.add(modernHvGlass());
        return packs;
    }

    public static List<PowerLineStylePack> defaultPacks() {
        List<PowerLineStylePack> packs = new ArrayList<>(decorativePacks());
        packs.addAll(engineeringPacks());
        return packs;
    }

    public static Map<String, PowerLineStylePack> indexById() {
        Map<String, PowerLineStylePack> indexed = new LinkedHashMap<>();
        for (PowerLineStylePack pack : defaultPacks()) {
            indexed.put(pack.getId(), pack);
        }
        return indexed;
    }

    public static PowerLineStylePack find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }

    public static PowerLineStylePack detect(PowerLineFootprint line) {
        if (line == null) {
            return null;
        }
        PowerLineStylePack byId = find(line.getStylePackId());
        if (byId != null && byId.matches(line)) {
            return byId;
        }
        for (PowerLineStylePack pack : defaultPacks()) {
            if (pack.matchesConfiguration(line)) {
                return pack;
            }
        }
        return null;
    }

    public static PowerLineStylePack classicWood() {
        return new PowerLineStylePack(
            PowerLineStylePack.RUSTIC_WOOD_ID,
            "plugin.powerline.style.pack.classic_wood",
            PowerLineStylePack.StylePreviewKind.WOOD,
            null,
            PoleDesignCatalog.SIMPLE_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    /** @deprecated 使用 {@link #classicWood()} */
    @Deprecated
    public static PowerLineStylePack rusticWood() {
        return classicWood();
    }

    public static PowerLineStylePack doubleWood() {
        return new PowerLineStylePack(
            PowerLineStylePack.DOUBLE_WOOD_ID,
            "plugin.powerline.style.pack.double_wood",
            PowerLineStylePack.StylePreviewKind.DOUBLE_WOOD,
            null,
            PoleDesignCatalog.DOUBLE_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack urbanConcrete() {
        return new PowerLineStylePack(
            PowerLineStylePack.URBAN_CONCRETE_ID,
            "plugin.powerline.style.pack.urban_concrete",
            PowerLineStylePack.StylePreviewKind.URBAN,
            null,
            PoleDesignCatalog.URBAN_CONCRETE_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:stone_bricks"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
    }

    public static PowerLineStylePack simpleSteel() {
        return new PowerLineStylePack(
            PowerLineStylePack.INDUSTRIAL_STEEL_ID,
            "plugin.powerline.style.pack.simple_steel",
            PowerLineStylePack.StylePreviewKind.STEEL_POLE,
            null,
            PoleDesignCatalog.MODERN_STEEL_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
    }

    /** @deprecated 使用 {@link #simpleSteel()} */
    @Deprecated
    public static PowerLineStylePack industrialSteel() {
        return simpleSteel();
    }

    public static PowerLineStylePack modernUtility() {
        return new PowerLineStylePack(
            PowerLineStylePack.MODERN_UTILITY_ID,
            "plugin.powerline.style.pack.modern_utility",
            PowerLineStylePack.StylePreviewKind.MODERN_UTILITY,
            null,
            PoleDesignCatalog.MODERN_UTILITY_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.STRAIGHT);
    }

    public static PowerLineStylePack compactLattice() {
        return new PowerLineStylePack(
            PowerLineStylePack.COMPACT_LATTICE_ID,
            "plugin.powerline.style.pack.compact_lattice",
            PowerLineStylePack.StylePreviewKind.LATTICE_POLE,
            null,
            PoleDesignCatalog.LATTICE_STEEL_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack classicLattice() {
        return new PowerLineStylePack(
            PowerLineStylePack.CLASSIC_LATTICE_ID,
            "plugin.powerline.style.pack.classic_lattice",
            PowerLineStylePack.StylePreviewKind.LATTICE,
            TowerFamily.STANDARD_LATTICE_3_PHASE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack heavyLattice() {
        return new PowerLineStylePack(
            PowerLineStylePack.HEAVY_LATTICE_ID,
            "plugin.powerline.style.pack.heavy_lattice",
            PowerLineStylePack.StylePreviewKind.HEAVY_LATTICE,
            null,
            PoleDesignCatalog.HEAVY_LATTICE_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack smartTowers() {
        return new PowerLineStylePack(
            PowerLineStylePack.SMART_TOWERS_ID,
            "plugin.powerline.style.pack.smart_towers",
            PowerLineStylePack.StylePreviewKind.ADAPTIVE,
            TowerFamily.GRADED_LATTICE_3_PHASE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.STRAIGHT);
    }

    public static PowerLineStylePack taperedTower() {
        return new PowerLineStylePack(
            PowerLineStylePack.TAPERED_TOWER_ID,
            "plugin.powerline.style.pack.tapered_tower",
            PowerLineStylePack.StylePreviewKind.TAPERED,
            null,
            PoleDesignCatalog.TAPERED_LATTICE_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
    }

    public static PowerLineStylePack fantasyCopper() {
        return new PowerLineStylePack(
            PowerLineStylePack.FANTASY_COPPER_ID,
            "plugin.powerline.style.pack.fantasy_copper",
            PowerLineStylePack.StylePreviewKind.COPPER,
            null,
            PoleDesignCatalog.FANTASY_COPPER_POLE_ID,
            MaterialMix.single("minecraft:lightning_rod"),
            MaterialMix.single("minecraft:copper_block"),
            MaterialMix.single("minecraft:lightning_rod"),
            PowerLineUiPresets.WireSag.LOOSE);
    }

    public static PowerLineStylePack japaneseStreet() {
        return new PowerLineStylePack(
            PowerLineStylePack.JAPANESE_STREET_ID,
            "plugin.powerline.style.pack.japanese_street",
            PowerLineStylePack.StylePreviewKind.JAPANESE,
            null,
            PoleDesignCatalog.JAPANESE_STREET_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:dark_oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
    }

    public static PowerLineStylePack wastelandWind() {
        return new PowerLineStylePack(
            PowerLineStylePack.WASTELAND_WIND_ID,
            "plugin.powerline.style.pack.wasteland_wind",
            PowerLineStylePack.StylePreviewKind.WASTELAND_WIND,
            null,
            PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:weathered_copper"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LOOSE);
    }

    public static PowerLineStylePack oldEuropean() {
        return new PowerLineStylePack(
            PowerLineStylePack.OLD_EUROPEAN_ID,
            "plugin.powerline.style.pack.old_european",
            PowerLineStylePack.StylePreviewKind.OLD_EUROPEAN,
            null,
            PoleDesignCatalog.OLD_EUROPEAN_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:birch_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack steampunkBrass() {
        return new PowerLineStylePack(
            PowerLineStylePack.STEAMPUNK_BRASS_ID,
            "plugin.powerline.style.pack.steampunk_brass",
            PowerLineStylePack.StylePreviewKind.STEAMPUNK,
            null,
            PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:copper_block"),
            MaterialMix.single("minecraft:lightning_rod"),
            PowerLineUiPresets.WireSag.NATURAL);
    }

    public static PowerLineStylePack modernHvGlass() {
        return new PowerLineStylePack(
            PowerLineStylePack.MODERN_HV_GLASS_ID,
            "plugin.powerline.style.pack.modern_hv_glass",
            PowerLineStylePack.StylePreviewKind.MODERN_HV_GLASS,
            null,
            PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:light_blue_stained_glass"),
            PowerLineUiPresets.WireSag.STRAIGHT);
    }

    public static PowerLineStylePack suburbanLamp() {
        return new PowerLineStylePack(
            PowerLineStylePack.SUBURBAN_LAMP_ID,
            "plugin.powerline.style.pack.suburban_lamp",
            PowerLineStylePack.StylePreviewKind.SUBURBAN_LAMP,
            null,
            PoleDesignCatalog.SUBURBAN_LAMP_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
    }

    public static PowerLineStylePack abandoned() {
        return new PowerLineStylePack(
            PowerLineStylePack.ABANDONED_ID,
            "plugin.powerline.style.pack.abandoned",
            PowerLineStylePack.StylePreviewKind.ABANDONED,
            null,
            PoleDesignCatalog.ABANDONED_POLE_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:mossy_cobblestone"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LOOSE);
    }

    public static PowerLineStylePack rustic() {
        return new PowerLineStylePack(
            PowerLineStylePack.RUSTIC_ID,
            "plugin.powerline.style.pack.rustic",
            PowerLineStylePack.StylePreviewKind.RUSTIC,
            null,
            PoleDesignCatalog.RUSTIC_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:spruce_fence"),
            MaterialMix.single("minecraft:vine"),
            PowerLineUiPresets.WireSag.LOOSE);
    }
}
