package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.PowerLineFootprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置杆塔预设目录。
 */
public final class PoleDesignCatalog {
    public static final String SIMPLE_WOOD_POLE_ID = "preset/simple_wood_pole";
    public static final String LATTICE_STEEL_TOWER_ID = "preset/lattice_steel_tower";
    public static final String MODERN_STEEL_POLE_ID = "preset/modern_steel_pole";

    public static final String TAPERED_LATTICE_TOWER_ID = "preset/tapered_lattice_tower";
    public static final String URBAN_CONCRETE_POLE_ID = "preset/urban_concrete_pole";
    public static final String FANTASY_COPPER_POLE_ID = "preset/fantasy_copper_pole";

    private PoleDesignCatalog() {
    }

    public static List<PoleDesign> defaultDesigns() {
        List<PoleDesign> designs = new ArrayList<>();
        designs.add(simpleWoodPole());
        designs.add(latticeSteelTower());
        designs.add(modernSteelPole());
        designs.add(taperedLatticeTower());
        designs.add(urbanConcretePole());
        designs.add(fantasyCopperPole());
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
        return design;
    }

    public static boolean isBuiltinId(String id) {
        return id != null && id.startsWith("preset/");
    }

    public static MaterialMix defaultPoleMaterial() {
        return MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
    }
}
