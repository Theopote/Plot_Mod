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

    public static List<PowerLineStylePack> defaultPacks() {
        List<PowerLineStylePack> packs = new ArrayList<>();
        packs.add(rusticWood());
        packs.add(urbanConcrete());
        packs.add(industrialSteel());
        packs.add(compactLattice());
        packs.add(classicLattice());
        packs.add(smartTowers());
        packs.add(taperedTower());
        packs.add(fantasyCopper());
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

    public static PowerLineStylePack rusticWood() {
        return new PowerLineStylePack(
            PowerLineStylePack.RUSTIC_WOOD_ID,
            "plugin.powerline.style.pack.rustic_wood",
            PowerLineStylePack.StylePreviewKind.WOOD,
            null,
            PoleDesignCatalog.SIMPLE_WOOD_POLE_ID,
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

    public static PowerLineStylePack industrialSteel() {
        return new PowerLineStylePack(
            PowerLineStylePack.INDUSTRIAL_STEEL_ID,
            "plugin.powerline.style.pack.industrial_steel",
            PowerLineStylePack.StylePreviewKind.STEEL_POLE,
            null,
            PoleDesignCatalog.MODERN_STEEL_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT);
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
}
