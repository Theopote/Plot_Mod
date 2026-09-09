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

/** 内置线路风格预设目录。 */
public final class PowerLineStylePresetCatalog {
    private PowerLineStylePresetCatalog() {
    }

    public static List<PowerLineStylePreset> decorativePresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(classicWood());
        presets.add(doubleWood());
        presets.add(urbanConcrete());
        presets.add(simpleSteel());
        presets.add(modernUtility());
        presets.add(classicLattice());
        presets.add(heavyLattice());
        presets.add(compactLattice());
        presets.add(oldEuropean());
        presets.add(japaneseStreet());
        presets.add(fantasyCopper());
        presets.add(steampunkBrass());
        presets.add(wastelandWind());
        presets.add(abandoned());
        presets.add(suburbanLamp());
        presets.add(rustic());
        return presets;
    }

    public static List<PowerLineStylePreset> engineeringPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(smartTowers());
        presets.add(taperedTower());
        presets.add(modernHvGlass());
        return presets;
    }

    public static List<PowerLineStylePreset> defaultPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>(decorativePresets());
        presets.addAll(engineeringPresets());
        return presets;
    }

    public static Map<String, PowerLineStylePreset> indexById() {
        Map<String, PowerLineStylePreset> indexed = new LinkedHashMap<>();
        for (PowerLineStylePreset preset : defaultPresets()) {
            indexed.put(preset.getId(), preset);
        }
        return indexed;
    }

    public static PowerLineStylePreset find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }

    public static PowerLineStylePreset detect(PowerLineFootprint line) {
        if (line == null) {
            return null;
        }
        PowerLineStylePreset active = activePreset(line);
        if (active != null) {
            return active;
        }
        for (PowerLineStylePreset preset : defaultPresets()) {
            if (preset.matchesBundle(line)) {
                return preset;
            }
        }
        return null;
    }

    /** 当前仍与 footprint 配置一致的已选预设（需有 {@code stylePackId}，持久化字段待 rename）。 */
    public static PowerLineStylePreset activePreset(PowerLineFootprint line) {
        if (line == null || line.getStylePackId() == null) {
            return null;
        }
        PowerLineStylePreset preset = find(line.getStylePackId());
        if (preset != null && preset.matchesBundle(line)) {
            return preset;
        }
        return null;
    }

    /** 手动改动后若与预设不一致，清除 {@code stylePackId} 以进入「自定义」状态。 */
    public static void clearStylePresetIfDrifted(PowerLineFootprint line) {
        if (line == null || line.getStylePackId() == null) {
            return;
        }
        PowerLineStylePreset preset = find(line.getStylePackId());
        if (preset == null || !preset.matchesBundle(line)) {
            line.setStylePackId(null);
        }
    }

    public static PowerLineStylePreset classicWood() {
        return preset(
            PowerLineStylePreset.RUSTIC_WOOD_ID,
            "plugin.powerline.style.pack.classic_wood",
            PowerLineStylePreset.StylePreviewKind.WOOD,
            null,
            PoleDesignCatalog.SIMPLE_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            PoleSpacingProfile.streetWood());
    }

    /** @deprecated 使用 {@link #classicWood()} */
    @Deprecated
    public static PowerLineStylePreset rusticWood() {
        return classicWood();
    }

    public static PowerLineStylePreset doubleWood() {
        return preset(
            PowerLineStylePreset.DOUBLE_WOOD_ID,
            "plugin.powerline.style.pack.double_wood",
            PowerLineStylePreset.StylePreviewKind.DOUBLE_WOOD,
            null,
            PoleDesignCatalog.DOUBLE_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(20, 35, 50));
    }

    public static PowerLineStylePreset urbanConcrete() {
        return preset(
            PowerLineStylePreset.URBAN_CONCRETE_ID,
            "plugin.powerline.style.pack.urban_concrete",
            PowerLineStylePreset.StylePreviewKind.URBAN,
            null,
            PoleDesignCatalog.URBAN_CONCRETE_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:stone_bricks"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            new PoleSpacingProfile(20, 40, 60));
    }

    public static PowerLineStylePreset simpleSteel() {
        return preset(
            PowerLineStylePreset.INDUSTRIAL_STEEL_ID,
            "plugin.powerline.style.pack.simple_steel",
            PowerLineStylePreset.StylePreviewKind.STEEL_POLE,
            null,
            PoleDesignCatalog.MODERN_STEEL_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            new PoleSpacingProfile(30, 60, 90));
    }

    /** @deprecated 使用 {@link #simpleSteel()} */
    @Deprecated
    public static PowerLineStylePreset industrialSteel() {
        return simpleSteel();
    }

    public static PowerLineStylePreset modernUtility() {
        return preset(
            PowerLineStylePreset.MODERN_UTILITY_ID,
            "plugin.powerline.style.pack.modern_utility",
            PowerLineStylePreset.StylePreviewKind.MODERN_UTILITY,
            null,
            PoleDesignCatalog.MODERN_UTILITY_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.STRAIGHT,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(25, 45, 65));
    }

    public static PowerLineStylePreset compactLattice() {
        return preset(
            PowerLineStylePreset.COMPACT_LATTICE_ID,
            "plugin.powerline.style.pack.compact_lattice",
            PowerLineStylePreset.StylePreviewKind.LATTICE_POLE,
            null,
            PoleDesignCatalog.LATTICE_STEEL_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(50, 90, 140));
    }

    public static PowerLineStylePreset classicLattice() {
        return preset(
            PowerLineStylePreset.CLASSIC_LATTICE_ID,
            "plugin.powerline.style.pack.classic_lattice",
            PowerLineStylePreset.StylePreviewKind.LATTICE,
            TowerFamily.STANDARD_LATTICE_3_PHASE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(100, 200, 300));
    }

    public static PowerLineStylePreset heavyLattice() {
        return preset(
            PowerLineStylePreset.HEAVY_LATTICE_ID,
            "plugin.powerline.style.pack.heavy_lattice",
            PowerLineStylePreset.StylePreviewKind.HEAVY_LATTICE,
            null,
            PoleDesignCatalog.HEAVY_LATTICE_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(70, 130, 200));
    }

    public static PowerLineStylePreset smartTowers() {
        return preset(
            PowerLineStylePreset.SMART_TOWERS_ID,
            "plugin.powerline.style.pack.smart_towers",
            PowerLineStylePreset.StylePreviewKind.ADAPTIVE,
            TowerFamily.GRADED_LATTICE_3_PHASE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.STRAIGHT,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(100, 200, 300));
    }

    public static PowerLineStylePreset taperedTower() {
        return preset(
            PowerLineStylePreset.TAPERED_TOWER_ID,
            "plugin.powerline.style.pack.tapered_tower",
            PowerLineStylePreset.StylePreviewKind.TAPERED,
            null,
            PoleDesignCatalog.TAPERED_LATTICE_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(70, 130, 200));
    }

    public static PowerLineStylePreset fantasyCopper() {
        return preset(
            PowerLineStylePreset.FANTASY_COPPER_ID,
            "plugin.powerline.style.pack.fantasy_copper",
            PowerLineStylePreset.StylePreviewKind.COPPER,
            null,
            PoleDesignCatalog.FANTASY_COPPER_POLE_ID,
            MaterialMix.single("minecraft:lightning_rod"),
            MaterialMix.single("minecraft:copper_block"),
            MaterialMix.single("minecraft:lightning_rod"),
            PowerLineUiPresets.WireSag.LOOSE,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            new PoleSpacingProfile(30, 60, 90));
    }

    public static PowerLineStylePreset japaneseStreet() {
        return preset(
            PowerLineStylePreset.JAPANESE_STREET_ID,
            "plugin.powerline.style.pack.japanese_street",
            PowerLineStylePreset.StylePreviewKind.JAPANESE,
            null,
            PoleDesignCatalog.JAPANESE_STREET_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:dark_oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(15, 30, 45));
    }

    public static PowerLineStylePreset wastelandWind() {
        return preset(
            PowerLineStylePreset.WASTELAND_WIND_ID,
            "plugin.powerline.style.pack.wasteland_wind",
            PowerLineStylePreset.StylePreviewKind.WASTELAND_WIND,
            null,
            PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:weathered_copper"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LOOSE,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            new PoleSpacingProfile(30, 60, 90));
    }

    public static PowerLineStylePreset oldEuropean() {
        return preset(
            PowerLineStylePreset.OLD_EUROPEAN_ID,
            "plugin.powerline.style.pack.old_european",
            PowerLineStylePreset.StylePreviewKind.OLD_EUROPEAN,
            null,
            PoleDesignCatalog.OLD_EUROPEAN_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:birch_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            PoleSpacingProfile.streetWood());
    }

    public static PowerLineStylePreset steampunkBrass() {
        return preset(
            PowerLineStylePreset.STEAMPUNK_BRASS_ID,
            "plugin.powerline.style.pack.steampunk_brass",
            PowerLineStylePreset.StylePreviewKind.STEAMPUNK,
            null,
            PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:copper_block"),
            MaterialMix.single("minecraft:lightning_rod"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(70, 130, 200));
    }

    public static PowerLineStylePreset modernHvGlass() {
        return preset(
            PowerLineStylePreset.MODERN_HV_GLASS_ID,
            "plugin.powerline.style.pack.modern_hv_glass",
            PowerLineStylePreset.StylePreviewKind.MODERN_HV_GLASS,
            null,
            PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:light_blue_stained_glass"),
            PowerLineUiPresets.WireSag.STRAIGHT,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(100, 200, 300));
    }

    public static PowerLineStylePreset suburbanLamp() {
        return preset(
            PowerLineStylePreset.SUBURBAN_LAMP_ID,
            "plugin.powerline.style.pack.suburban_lamp",
            PowerLineStylePreset.StylePreviewKind.SUBURBAN_LAMP,
            null,
            PoleDesignCatalog.SUBURBAN_LAMP_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            new PoleSpacingProfile(30, 60, 90));
    }

    public static PowerLineStylePreset abandoned() {
        return preset(
            PowerLineStylePreset.ABANDONED_ID,
            "plugin.powerline.style.pack.abandoned",
            PowerLineStylePreset.StylePreviewKind.ABANDONED,
            null,
            PoleDesignCatalog.ABANDONED_POLE_ID,
            MaterialMix.single("minecraft:chain"),
            MaterialMix.single("minecraft:mossy_cobblestone"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LOOSE,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            PoleSpacingProfile.streetWood());
    }

    public static PowerLineStylePreset rustic() {
        return preset(
            PowerLineStylePreset.RUSTIC_ID,
            "plugin.powerline.style.pack.rustic",
            PowerLineStylePreset.StylePreviewKind.RUSTIC,
            null,
            PoleDesignCatalog.RUSTIC_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:spruce_fence"),
            MaterialMix.single("minecraft:vine"),
            PowerLineUiPresets.WireSag.LOOSE,
            PowerLineStylePreset.ConductorLayout.SINGLE,
            PoleSpacingProfile.streetWood());
    }

    private static PowerLineStylePreset preset(
            String id,
            String labelKey,
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix groundWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            PowerLineStylePreset.ConductorLayout conductorLayout,
            PoleSpacingProfile spacingProfile) {
        return new PowerLineStylePreset(
            id,
            labelKey,
            previewKind,
            towerFamilyId,
            poleDesignId,
            wireMaterial,
            poleMaterial,
            groundWireMaterial,
            sagPreset,
            conductorLayout,
            spacingProfile);
    }
}
