package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.style.PowerLineStylePreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置杆塔预设目录。
 */
public final class PoleDesignCatalog {
    public static final String SIMPLE_WOOD_POLE_ID = "preset/simple_wood_pole";
    public static final String DOUBLE_WOOD_POLE_ID = "preset/double_wood_pole";
    public static final String LATTICE_STEEL_TOWER_ID = "preset/lattice_steel_tower";
    public static final String HEAVY_LATTICE_TOWER_ID = "preset/heavy_lattice_tower";
    public static final String MODERN_STEEL_POLE_ID = "preset/modern_steel_pole";

    public static final String TAPERED_LATTICE_TOWER_ID = "preset/tapered_lattice_tower";
    public static final String URBAN_CONCRETE_POLE_ID = "preset/urban_concrete_pole";
    public static final String FANTASY_COPPER_POLE_ID = "preset/fantasy_copper_pole";
    public static final String JAPANESE_STREET_POLE_ID = "preset/japanese_street_pole";
    public static final String WASTELAND_WIND_TURBINE_ID = "preset/wasteland_wind_turbine";
    public static final String OLD_EUROPEAN_POLE_ID = "preset/old_european_pole";
    public static final String STEAMPUNK_BRASS_TOWER_ID = "preset/steampunk_brass_tower";
    public static final String MODERN_HV_GLASS_TOWER_ID = "preset/modern_hv_glass_tower";
    public static final String SUBURBAN_LAMP_POLE_ID = "preset/suburban_lamp_pole";
    public static final String MODERN_UTILITY_POLE_ID = "preset/modern_utility_pole";
    public static final String ABANDONED_POLE_ID = "preset/abandoned_pole";
    public static final String RUSTIC_WOOD_POLE_ID = "preset/rustic_wood_pole";

    private PoleDesignCatalog() {
    }

    public static List<PoleDesign> defaultDesigns() {
        List<PoleDesign> designs = new ArrayList<>();
        designs.add(simpleWoodPole());
        designs.add(doubleWoodPole());
        designs.add(latticeSteelTower());
        designs.add(heavyLatticeTower());
        designs.add(modernSteelPole());
        designs.add(taperedLatticeTower());
        designs.add(urbanConcretePole());
        designs.add(fantasyCopperPole());
        designs.add(japaneseStreetPole());
        designs.add(wastelandWindTurbine());
        designs.add(oldEuropeanPole());
        designs.add(steampunkBrassTower());
        designs.add(modernHvGlassTower());
        designs.add(suburbanLampPole());
        designs.add(modernUtilityPole());
        designs.add(abandonedPole());
        designs.add(rusticWoodPole());
        return designs;
    }

    public static Map<String, PoleDesign> indexById() {
        Map<String, PoleDesign> indexed = new LinkedHashMap<>();
        for (PoleDesign design : defaultDesigns()) {
            indexed.put(design.getId(), design);
        }
        return indexed;
    }

    public static PoleDesign findBuiltin(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }

    public static PoleDesign simpleWoodPole() {
        PoleDesign design = new PoleDesign(SIMPLE_WOOD_POLE_ID, "Simple Wood Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:oak_fence")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:oak_slab"));
        crossarm.setCrossarmLength(5);
        layers.add(crossarm);
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 按风格预设为杆塔设计配置导线挂点。 */
    static void wireConductorLayout(PoleDesign design, PowerLineStylePreset.ConductorLayout layout) {
        if (design == null || layout == null || design.hasEnabledAttachments()) {
            return;
        }
        double offset = Math.max(1.0, design.totalHeight() - 1.0);
        switch (layout) {
            case THREE_PHASE_HORIZONTAL -> design.setAttachments(
                ConductorAttachmentPresets.threePhaseHorizontal(offset, -2.0, 0.0, 2.0));
            case THREE_PHASE_VERTICAL -> design.setAttachments(
                ConductorAttachmentPresets.threePhaseVertical(offset));
            default -> design.setAttachments(ConductorAttachmentPresets.singleConductor(offset));
        }
    }

    /** 双横担木杆：经典 H 型配电杆。 */
    public static PoleDesign doubleWoodPole() {
        PoleDesign design = new PoleDesign(DOUBLE_WOOD_POLE_ID, "Double Wood Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            5,
            MaterialMix.single("minecraft:oak_fence")));
        PoleLayer lowerArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:oak_slab"));
        lowerArm.setCrossarmLength(4);
        layers.add(lowerArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:oak_fence")));
        PoleLayer upperArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:oak_slab"));
        upperArm.setCrossarmLength(5);
        layers.add(upperArm);
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    public static PoleDesign latticeSteelTower() {
        PoleDesign design = new PoleDesign(LATTICE_STEEL_TOWER_ID, "Lattice Steel Tower");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:iron_bars")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:iron_block")));
        PoleLayer lowerArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        lowerArm.setCrossarmLength(7);
        layers.add(lowerArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            3,
            MaterialMix.single("minecraft:iron_bars")));
        PoleLayer upperArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        upperArm.setCrossarmLength(5);
        layers.add(upperArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:lantern")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    /** 重型格构塔：更高、更宽横担，适合醒目输电走廊。 */
    public static PoleDesign heavyLatticeTower() {
        PoleDesign design = new PoleDesign(HEAVY_LATTICE_TOWER_ID, "Heavy Lattice Tower");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            5,
            MaterialMix.single("minecraft:iron_block")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:iron_bars")));
        PoleLayer lowerArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        lowerArm.setCrossarmLength(9);
        layers.add(lowerArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:iron_block")));
        PoleLayer upperArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        upperArm.setCrossarmLength(7);
        layers.add(upperArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            3,
            MaterialMix.single("minecraft:iron_bars")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:iron_block")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    public static PoleDesign taperedLatticeTower() {
        return TowerStructurePresets.taperedLatticePoleDesign(
            TAPERED_LATTICE_TOWER_ID,
            "Tapered Lattice Tower");
    }

    public static PoleDesign modernSteelPole() {
        PoleDesign design = new PoleDesign(MODERN_STEEL_POLE_ID, "Modern Steel Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            10,
            MaterialMix.single("minecraft:iron_block")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:glowstone")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    public static PoleDesign urbanConcretePole() {
        PoleDesign design = new PoleDesign(URBAN_CONCRETE_POLE_ID, "Urban Concrete Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            9,
            MaterialMix.single("minecraft:stone_bricks")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:smooth_stone_slab"));
        crossarm.setCrossarmLength(5);
        layers.add(crossarm);
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    public static PoleDesign fantasyCopperPole() {
        PoleDesign design = new PoleDesign(FANTASY_COPPER_POLE_ID, "Fantasy Copper Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            9,
            MaterialMix.single("minecraft:copper_block")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:lightning_rod"));
        crossarm.setCrossarmLength(6);
        layers.add(crossarm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:amethyst_cluster")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 日式街区电杆：深色木杆 + 双层横担 + 灯笼。 */
    public static PoleDesign japaneseStreetPole() {
        PoleDesign design = new PoleDesign(JAPANESE_STREET_POLE_ID, "Japanese Street Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            6,
            MaterialMix.single("minecraft:dark_oak_fence")));
        PoleLayer lowerArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:dark_oak_slab"));
        lowerArm.setCrossarmLength(4);
        layers.add(lowerArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            3,
            MaterialMix.single("minecraft:dark_oak_fence")));
        PoleLayer upperArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:dark_oak_slab"));
        upperArm.setCrossarmLength(5);
        layers.add(upperArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:lantern")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    /** 废土风电塔：锈蚀塔身 + 长桨叶横担。 */
    public static PoleDesign wastelandWindTurbine() {
        PoleDesign design = new PoleDesign(WASTELAND_WIND_TURBINE_ID, "Wasteland Wind Turbine");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            12,
            MaterialMix.single("minecraft:weathered_copper")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:oxidized_copper")));
        PoleLayer blade = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:orange_terracotta"));
        blade.setCrossarmLength(9);
        layers.add(blade);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:iron_trapdoor")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 老式欧洲电线杆：浅色木杆 + 宽横担 + 瓷瓶装饰帽。 */
    public static PoleDesign oldEuropeanPole() {
        PoleDesign design = new PoleDesign(OLD_EUROPEAN_POLE_ID, "Old European Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            9,
            MaterialMix.single("minecraft:birch_fence")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:birch_slab"));
        crossarm.setCrossarmLength(6);
        layers.add(crossarm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:quartz_block")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 蒸汽朋克铜塔：铜柱 + 黄铜横担 + 齿轮顶饰。 */
    public static PoleDesign steampunkBrassTower() {
        PoleDesign design = new PoleDesign(STEAMPUNK_BRASS_TOWER_ID, "Steampunk Brass Tower");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            5,
            MaterialMix.single("minecraft:copper_block")));
        PoleLayer brassArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:gold_block"));
        brassArm.setCrossarmLength(6);
        layers.add(brassArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:cut_copper")));
        PoleLayer rodArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:lightning_rod"));
        rodArm.setCrossarmLength(7);
        layers.add(rodArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:gold_block")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    /** 现代高压塔：钢构塔身 + 玻璃绝缘子横担。 */
    public static PoleDesign modernHvGlassTower() {
        PoleDesign design = new PoleDesign(MODERN_HV_GLASS_TOWER_ID, "Modern HV Glass Tower");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            10,
            MaterialMix.single("minecraft:iron_block")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            5,
            MaterialMix.single("minecraft:iron_bars")));
        PoleLayer glassArm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:sea_lantern"));
        glassArm.setCrossarmLength(8);
        layers.add(glassArm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:light_blue_stained_glass")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    /** 郊区路灯线：铸铁杆 + 暖色灯头（街区氛围）。 */
    public static PoleDesign suburbanLampPole() {
        PoleDesign design = new PoleDesign(SUBURBAN_LAMP_POLE_ID, "Suburban Lamp Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            9,
            MaterialMix.single("minecraft:iron_bars")));
        PoleLayer arm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        arm.setCrossarmLength(5);
        layers.add(arm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:soul_lantern")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 现代配电杆：钢杆 + 变压器箱 + 短横担。 */
    public static PoleDesign modernUtilityPole() {
        PoleDesign design = new PoleDesign(MODERN_UTILITY_POLE_ID, "Modern Utility Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:iron_bars")));
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            2,
            MaterialMix.single("minecraft:iron_block")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:iron_bars"));
        crossarm.setCrossarmLength(4);
        layers.add(crossarm);
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL);
        return design;
    }

    /** 废弃电杆：风化木杆 + 歪斜短横担。 */
    public static PoleDesign abandonedPole() {
        PoleDesign design = new PoleDesign(ABANDONED_POLE_ID, "Abandoned Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            7,
            MaterialMix.single("minecraft:mossy_cobblestone")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:spruce_slab"));
        crossarm.setCrossarmLength(3);
        layers.add(crossarm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:cobweb")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    /** 粗朴木杆：云杉 + 苔石横担，林间野趣。 */
    public static PoleDesign rusticWoodPole() {
        PoleDesign design = new PoleDesign(RUSTIC_WOOD_POLE_ID, "Rustic Wood Pole");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:spruce_fence")));
        PoleLayer crossarm = new PoleLayer(
            PoleLayer.Shape.CROSSARM,
            1,
            MaterialMix.single("minecraft:mossy_cobblestone_slab"));
        crossarm.setCrossarmLength(5);
        layers.add(crossarm);
        layers.add(new PoleLayer(
            PoleLayer.Shape.CAP,
            1,
            MaterialMix.single("minecraft:vine")));
        design.setLayers(layers);
        wireConductorLayout(design, PowerLineStylePreset.ConductorLayout.SINGLE);
        return design;
    }

    public static boolean isBuiltinId(String id) {
        return id != null && id.startsWith("preset/");
    }

}
