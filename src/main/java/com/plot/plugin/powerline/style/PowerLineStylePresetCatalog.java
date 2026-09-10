package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.design.ConductorArrangement;
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

    public static List<StyleCategory> galleryCategories() {
        return List.of(
            StyleCategory.UTILITY,
            StyleCategory.TRANSMISSION,
            StyleCategory.INDUSTRIAL,
            StyleCategory.FANTASY);
    }

    public static List<PowerLineStylePreset> utilityPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(classicWood());
        presets.add(doubleWood());
        presets.add(urbanConcrete());
        presets.add(simpleSteel());
        presets.add(modernUtility());
        presets.add(oldEuropean());
        presets.add(japaneseStreet());
        presets.add(suburbanLamp());
        presets.add(abandoned());
        presets.add(rustic());
        return presets;
    }

    public static List<PowerLineStylePreset> transmissionPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(compactLattice());
        presets.add(classicLattice());
        presets.add(tripleArmTower());
        presets.add(cupTower());
        presets.add(heavyLattice());
        presets.add(taperedTower());
        presets.add(smartTowers());
        presets.add(modernHvGlass());
        return presets;
    }

    public static List<PowerLineStylePreset> industrialPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(industrialPortal());
        presets.add(heavyDoubleCircuit());
        presets.add(megaLattice());
        presets.add(monsterPylon());
        return presets;
    }

    public static List<PowerLineStylePreset> fantasyPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        presets.add(fantasyCopper());
        presets.add(steampunkBrass());
        presets.add(wastelandWind());
        return presets;
    }

    public static List<PowerLineStylePreset> presetsByCategory(StyleCategory category) {
        if (category == null) {
            return List.of();
        }
        return switch (category) {
            case UTILITY -> utilityPresets();
            case TRANSMISSION -> transmissionPresets();
            case INDUSTRIAL -> industrialPresets();
            case FANTASY -> fantasyPresets();
        };
    }

    /** @deprecated use {@link #utilityPresets()} etc. */
    @Deprecated
    public static List<PowerLineStylePreset> decorativePresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>(utilityPresets());
        presets.addAll(transmissionPresets());
        presets.addAll(fantasyPresets());
        return presets;
    }

    /** @deprecated use {@link #transmissionPresets()} */
    @Deprecated
    public static List<PowerLineStylePreset> engineeringPresets() {
        return transmissionPresets().stream()
            .filter(p -> PowerLineStylePreset.SMART_TOWERS_ID.equals(p.getId())
                || PowerLineStylePreset.TAPERED_TOWER_ID.equals(p.getId())
                || PowerLineStylePreset.MODERN_HV_GLASS_ID.equals(p.getId()))
            .toList();
    }

    /** @deprecated use {@link #industrialPresets()} */
    @Deprecated
    public static List<PowerLineStylePreset> industrialMegaPresets() {
        return industrialPresets();
    }

    public static List<PowerLineStylePreset> defaultPresets() {
        List<PowerLineStylePreset> presets = new ArrayList<>();
        for (StyleCategory category : galleryCategories()) {
            presets.addAll(presetsByCategory(category));
        }
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

    /** 当前 base preset（按 {@code stylePresetId}，微调后仍保留）。 */
    public static PowerLineStylePreset activePreset(PowerLineFootprint line) {
        return PowerLineStyleEditor.basePreset(line);
    }

    /** footprint 配置是否仍与 base preset 默认 bundle 完全一致。 */
    public static boolean matchesBaseBundle(PowerLineFootprint line) {
        PowerLineStyleInstance instance = PowerLineStyleInstance.of(line);
        return instance != null && instance.matchesBaseDefinition();
    }

    public static PowerLineStylePreset classicWood() {
        return preset(
            StyleCategory.UTILITY,
            PowerLineStylePreset.RUSTIC_WOOD_ID,
            "plugin.powerline.style.pack.classic_wood",
            PowerLineStylePreset.StylePreviewKind.WOOD,
            null,
            PoleDesignCatalog.SIMPLE_WOOD_POLE_ID,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:oak_fence"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            ConductorArrangement.single(),
            PoleSpacingProfile.streetWood());
    }

    public static PowerLineStylePreset doubleWood() {
        return preset(
            StyleCategory.UTILITY,
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
            StyleCategory.UTILITY,
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
            StyleCategory.UTILITY,
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

    public static PowerLineStylePreset modernUtility() {
        return preset(
            StyleCategory.UTILITY,
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
            StyleCategory.TRANSMISSION,
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
            StyleCategory.TRANSMISSION,
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

    public static PowerLineStylePreset megaLattice() {
        return preset(
            StyleCategory.INDUSTRIAL,
            PowerLineStylePreset.MEGA_LATTICE_ID,
            "plugin.powerline.style.pack.mega_lattice",
            PowerLineStylePreset.StylePreviewKind.MEGA_LATTICE,
            TowerFamily.MEGA_LATTICE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            ConductorArrangement.megaIndustrialBundled(),
            new PoleSpacingProfile(80, 150, 220));
    }

    public static PowerLineStylePreset heavyDoubleCircuit() {
        return preset(
            StyleCategory.INDUSTRIAL,
            PowerLineStylePreset.HEAVY_DOUBLE_CIRCUIT_ID,
            "plugin.powerline.style.pack.heavy_double_circuit",
            PowerLineStylePreset.StylePreviewKind.HEAVY_DOUBLE_CIRCUIT,
            TowerFamily.HEAVY_DOUBLE_CIRCUIT_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.LIGHT,
            ConductorArrangement.doubleCircuitDrum(),
            new PoleSpacingProfile(90, 160, 240));
    }

    public static PowerLineStylePreset industrialPortal() {
        return preset(
            StyleCategory.INDUSTRIAL,
            PowerLineStylePreset.INDUSTRIAL_PORTAL_ID,
            "plugin.powerline.style.pack.industrial_portal",
            PowerLineStylePreset.StylePreviewKind.INDUSTRIAL_PORTAL,
            TowerFamily.INDUSTRIAL_PORTAL_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.STRAIGHT,
            ConductorArrangement.heavyDoubleCircuit(),
            new PoleSpacingProfile(100, 180, 260));
    }

    public static PowerLineStylePreset monsterPylon() {
        return preset(
            StyleCategory.INDUSTRIAL,
            PowerLineStylePreset.MONSTER_PYLON_ID,
            "plugin.powerline.style.pack.monster_pylon",
            PowerLineStylePreset.StylePreviewKind.MONSTER_PYLON,
            TowerFamily.MONSTER_PYLON_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            ConductorArrangement.monsterQuadCircuit(),
            new PoleSpacingProfile(110, 200, 300));
    }

    public static PowerLineStylePreset heavyLattice() {
        return preset(
            StyleCategory.TRANSMISSION,
            PowerLineStylePreset.HEAVY_LATTICE_ID,
            "plugin.powerline.style.pack.heavy_lattice",
            PowerLineStylePreset.StylePreviewKind.HEAVY_LATTICE,
            TowerFamily.HEAVY_TRANSMISSION_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(70, 130, 200));
    }

    public static PowerLineStylePreset tripleArmTower() {
        return preset(
            StyleCategory.TRANSMISSION,
            PowerLineStylePreset.TRIPLE_ARM_TOWER_ID,
            "plugin.powerline.style.pack.triple_arm_tower",
            PowerLineStylePreset.StylePreviewKind.TRIPLE_ARM,
            TowerFamily.TRIPLE_ARM_3_PHASE_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(90, 160, 240));
    }

    public static PowerLineStylePreset cupTower() {
        return preset(
            StyleCategory.TRANSMISSION,
            PowerLineStylePreset.CUP_TOWER_STYLE_ID,
            "plugin.powerline.style.pack.cup_tower",
            PowerLineStylePreset.StylePreviewKind.CUP_TOWER,
            TowerFamily.CUP_TOWER_ID,
            null,
            MaterialMix.single("minecraft:iron_bars"),
            MaterialMix.single("minecraft:iron_block"),
            MaterialMix.single("minecraft:chain"),
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL,
            new PoleSpacingProfile(80, 140, 210));
    }

    public static PowerLineStylePreset smartTowers() {
        return preset(
            StyleCategory.TRANSMISSION,
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
            StyleCategory.TRANSMISSION,
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
            StyleCategory.FANTASY,
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
            StyleCategory.UTILITY,
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
            StyleCategory.FANTASY,
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
            StyleCategory.UTILITY,
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
            StyleCategory.FANTASY,
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
            StyleCategory.TRANSMISSION,
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
            StyleCategory.UTILITY,
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
            StyleCategory.UTILITY,
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
            StyleCategory.UTILITY,
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
            StyleCategory category,
            String id,
            String labelKey,
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            PowerLineStylePreset.ConductorLayout conductorLayout,
            PoleSpacingProfile spacingProfile) {
        return preset(
            category,
            id,
            labelKey,
            previewKind,
            towerFamilyId,
            poleDesignId,
            wireMaterial,
            poleMaterial,
            topWireMaterial,
            sagPreset,
            ConductorArrangement.fromLegacyLayout(conductorLayout),
            spacingProfile);
    }

    private static PowerLineStylePreset preset(
            StyleCategory category,
            String id,
            String labelKey,
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            ConductorArrangement conductorArrangement,
            PoleSpacingProfile spacingProfile) {
        PowerLineStyleDefinition definition = new PowerLineStyleDefinition(
            previewKind,
            towerFamilyId,
            poleDesignId,
            wireMaterial,
            poleMaterial,
            topWireMaterial,
            sagPreset,
            defaultMaxSagDepth(previewKind),
            conductorArrangement,
            spacingProfile);
        return new PowerLineStylePreset(id, labelKey, category, definition);
    }

    /**
     * 风格默认最大下垂深度（视觉控制，非工程规范）。
     * Wood 12 / Japanese 10 / Transmission 16 / Mega 24 / Monster 32。
     */
    static double defaultMaxSagDepth(PowerLineStylePreset.StylePreviewKind previewKind) {
        if (previewKind == null) {
            return PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        }
        return switch (previewKind) {
            case JAPANESE, SUBURBAN_LAMP -> 10.0;
            case MONSTER_PYLON -> 32.0;
            case MEGA_LATTICE, HEAVY_DOUBLE_CIRCUIT, INDUSTRIAL_PORTAL -> 24.0;
            case LATTICE, HEAVY_LATTICE, TRIPLE_ARM, CUP_TOWER, LATTICE_POLE, TAPERED, ADAPTIVE, MODERN_HV_GLASS, STEAMPUNK
                -> 16.0;
            default -> PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        };
    }
}
