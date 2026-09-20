package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorMode;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerStabilityParameters;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;

/**
 * 塔型族角色 PoleDesign → 参数化种子配置。
 * <p>
 * 用于在 Style 级参数化调参时保留各角色的结构差异（Smart Towers 分档、转角耐张绝缘子等）。
 */
public final class TowerFamilyRoleParametricCatalog {
    private TowerFamilyRoleParametricCatalog() {
    }

    public static boolean isFamilyRoleDesign(PoleDesign design) {
        return design != null
            && design.hasTowerStructure()
            && !design.isParametricMode()
            && profileIdForDesignId(design.getId()) != null;
    }

    public static TowerGeneratorConfig seedForRoleDesign(PoleDesign design) {
        if (!isFamilyRoleDesign(design)) {
            return null;
        }
        String profileId = profileIdForDesignId(design.getId());
        TowerParameterProfile profile = TowerParametricEditor.findProfile(profileId).orElse(null);
        if (profile == null) {
            return null;
        }
        TowerParameterSet parameters = inferParameters(design.getTowerStructure());
        return new TowerGeneratorConfig(profileId, TowerGeneratorMode.PARAMETRIC, parameters);
    }

    public static TowerParameterSet mergeTunedParameters(
            TowerParameterSet roleBaseline,
            TowerParameterSet familyDefault,
            TowerParameterSet tuned) {
        if (roleBaseline == null || familyDefault == null || tuned == null) {
            return tuned != null ? tuned : roleBaseline;
        }
        TowerParameterSet merged = new TowerParameterSet(
            scale(roleBaseline.height(), familyDefault.height(), tuned.height()),
            scale(roleBaseline.baseWidth(), familyDefault.baseWidth(), tuned.baseWidth()),
            scale(roleBaseline.armSpan(), familyDefault.armSpan(), tuned.armSpan()),
            tuned.depthScale(),
            tuned.waistRatio(),
            tuned.armLevelScales(),
            tuned.density());
        return TowerStabilityParameters.enforceStableBaseWidth(merged, roleBaseline);
    }

    static String profileIdForDesignId(String designId) {
        if (designId == null || designId.isBlank()) {
            return null;
        }
        return switch (designId) {
            case TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID -> TowerParameterProfiles.SMALL_LATTICE_ID;
            case TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID -> TowerParameterProfiles.TRIPLE_ARM_ID;
            case TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID,
                 TowerFamilyDesignPresets.LATTICE_SUSPENSION_MEDIUM_ID,
                 TowerFamilyDesignPresets.LATTICE_ANGLE_ID,
                 TowerFamilyDesignPresets.LATTICE_DEAD_END_ID,
                 TowerFamilyDesignPresets.LATTICE_TERMINAL_ID -> TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID;
            case TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID,
                 TowerFamilyDesignPresets.HV_TRANSMISSION_ANGLE_ID,
                 TowerFamilyDesignPresets.HV_TRANSMISSION_DEAD_END_ID,
                 TowerFamilyDesignPresets.HV_TRANSMISSION_TERMINAL_ID -> TowerParameterProfiles.HEAVY_ID;
            case TowerFamilyDesignPresets.TRIPLE_ARM_SUSPENSION_ID,
                 TowerFamilyDesignPresets.TRIPLE_ARM_ANGLE_ID,
                 TowerFamilyDesignPresets.TRIPLE_ARM_DEAD_END_ID,
                 TowerFamilyDesignPresets.TRIPLE_ARM_TERMINAL_ID -> TowerParameterProfiles.TRIPLE_ARM_ID;
            case TowerFamilyDesignPresets.CUP_TOWER_SUSPENSION_ID,
                 TowerFamilyDesignPresets.CUP_TOWER_ANGLE_ID,
                 TowerFamilyDesignPresets.CUP_TOWER_DEAD_END_ID,
                 TowerFamilyDesignPresets.CUP_TOWER_TERMINAL_ID -> TowerParameterProfiles.CUP_ID;
            case TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID,
                 TowerFamilyDesignPresets.MEGA_LATTICE_ANGLE_ID,
                 TowerFamilyDesignPresets.MEGA_LATTICE_DEAD_END_ID,
                 TowerFamilyDesignPresets.MEGA_LATTICE_TERMINAL_ID -> TowerParameterProfiles.MEGA_ID;
            case TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID,
                 TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_ANGLE_ID,
                 TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_DEAD_END_ID,
                 TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_TERMINAL_ID -> TowerParameterProfiles.DRUM_ID;
            case TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID,
                 TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_ANGLE_ID,
                 TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_DEAD_END_ID,
                 TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_TERMINAL_ID -> TowerParameterProfiles.PORTAL_ID;
            case TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID,
                 TowerFamilyDesignPresets.MONSTER_PYLON_ANGLE_ID,
                 TowerFamilyDesignPresets.MONSTER_PYLON_DEAD_END_ID,
                 TowerFamilyDesignPresets.MONSTER_PYLON_TERMINAL_ID -> TowerParameterProfiles.UHV_ID;
            default -> null;
        };
    }

    static TowerParameterSet inferParameters(TowerStructureDesign structure) {
        if (structure == null) {
            return TowerParameterSet.classicDefaults();
        }
        double height = Math.max(1.0, structure.maxHeight());
        double baseWidth = Math.max(1.0, structure.maxHalfWidth() * 2.0);
        double armSpan = structure.getArms().stream()
            .mapToDouble(TowerArm::getLateralReach)
            .max()
            .orElse(baseWidth) * 2.0;
        armSpan = Math.max(baseWidth, armSpan);
        return new TowerParameterSet(
            height,
            baseWidth,
            armSpan,
            1.0,
            TowerParameterSet.DEFAULT_WAIST_RATIO,
            null,
            StructureDensity.MEDIUM);
    }

    private static double scale(double roleValue, double familyDefault, double tuned) {
        if (familyDefault <= 1e-6) {
            return roleValue;
        }
        return roleValue * (tuned / familyDefault);
    }
}
