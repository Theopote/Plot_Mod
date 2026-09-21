package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 塔体分项材质 override 的解析与应用。 */
public final class TowerMaterialOverrideSupport {
    private TowerMaterialOverrideSupport() {
    }

    public static boolean supportsTowerMaterialTune(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        return line.hasParametricTowerConfig() || line.hasTowerFamily();
    }

    public static void applyTo(PoleDesign design, PowerLineFootprint line) {
        if (design == null || line == null) {
            return;
        }
        applyColumnMaterial(design, line.getPoleMaterial());
        if (!design.hasTowerStructure()) {
            return;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        MaterialMix poleMaterial = line.getPoleMaterial();
        TowerMaterialApplyMode mode = line.getTowerMaterialApplyMode();
        if (poleMaterial != null) {
            structure.setPrimaryMaterial(poleMaterial.copy());
            if (mode.syncsAllTowerMaterials()) {
                structure.setBraceMaterial(poleMaterial.copy());
                applyArmMaterials(structure, poleMaterial, poleMaterial);
            }
        }
        if (!mode.syncsAllTowerMaterials()) {
            MaterialMix braceOverride = line.getBraceMaterialOverride();
            if (braceOverride != null) {
                structure.setBraceMaterial(braceOverride.copy());
            }
            MaterialMix armChordOverride = line.getArmChordMaterialOverride();
            if (armChordOverride != null) {
                for (TowerArm arm : structure.getArms()) {
                    arm.setMaterial(armChordOverride.copy());
                }
            }
            MaterialMix armBraceOverride = line.getArmBraceMaterialOverride();
            if (armBraceOverride != null) {
                for (TowerArm arm : structure.getArms()) {
                    arm.setBraceMaterial(armBraceOverride.copy());
                }
            }
        }
    }

    public static MaterialMix displayBraceMaterial(PoleDesign design, PowerLineFootprint line) {
        MaterialMix override = line != null ? line.getBraceMaterialOverride() : null;
        if (override != null) {
            return override;
        }
        if (design != null && design.hasTowerStructure()) {
            return design.getTowerStructure().getBraceMaterial();
        }
        return MaterialMix.single(TowerStructureDesign.DEFAULT_BRACE_MATERIAL);
    }

    public static MaterialMix displayArmChordMaterial(PoleDesign design, PowerLineFootprint line) {
        MaterialMix override = line != null ? line.getArmChordMaterialOverride() : null;
        if (override != null) {
            return override;
        }
        if (design != null && design.hasTowerStructure() && !design.getTowerStructure().getArms().isEmpty()) {
            TowerArm arm = design.getTowerStructure().getArms().getFirst();
            if (arm.getMaterial() != null) {
                return arm.getMaterial();
            }
        }
        if (design != null && design.hasTowerStructure()) {
            return design.getTowerStructure().getPrimaryMaterial();
        }
        return MaterialMix.single(TowerStructureDesign.DEFAULT_ARM_MATERIAL);
    }

    public static MaterialMix displayArmBraceMaterial(PoleDesign design, PowerLineFootprint line) {
        MaterialMix override = line != null ? line.getArmBraceMaterialOverride() : null;
        if (override != null) {
            return override;
        }
        if (design != null && design.hasTowerStructure() && !design.getTowerStructure().getArms().isEmpty()) {
            TowerArm arm = design.getTowerStructure().getArms().getFirst();
            if (arm.getBraceMaterial() != null) {
                return arm.getBraceMaterial();
            }
        }
        if (design != null && design.hasTowerStructure()) {
            return design.getTowerStructure().getBraceMaterial();
        }
        return MaterialMix.single(TowerStructureDesign.DEFAULT_BRACE_MATERIAL);
    }

    private static void applyColumnMaterial(PoleDesign design, MaterialMix poleMaterial) {
        if (poleMaterial == null) {
            return;
        }
        MaterialMix mix = poleMaterial.copy();
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.COLUMN) {
                layer.setMaterial(mix.copy());
            }
        }
    }

    private static void applyArmMaterials(TowerStructureDesign structure, MaterialMix chord, MaterialMix brace) {
        for (TowerArm arm : structure.getArms()) {
            if (chord != null) {
                arm.setMaterial(chord.copy());
            }
            if (brace != null) {
                arm.setBraceMaterial(brace.copy());
            }
        }
    }
}
