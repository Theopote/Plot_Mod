package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricDesignFactory;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;

/** Style / tower family → 参数化 Profile 默认配置。 */
public final class PowerLineStyleParametricCatalog {
    private PowerLineStyleParametricCatalog() {
    }

    public static TowerGeneratorConfig resolve(String towerFamilyId, String poleDesignId) {
        TowerGeneratorConfig fromFamily = forTowerFamilyId(towerFamilyId);
        if (fromFamily != null) {
            return fromFamily;
        }
        return forPoleDesignId(poleDesignId);
    }

    public static TowerGeneratorConfig forTowerFamilyId(String towerFamilyId) {
        if (towerFamilyId == null || towerFamilyId.isBlank()) {
            return null;
        }
        return switch (towerFamilyId) {
            case TowerFamily.STANDARD_LATTICE_3_PHASE_ID,
                 TowerFamily.GRADED_LATTICE_3_PHASE_ID -> TowerGeneratorConfig.parametricClassic(
                TowerParameterSet.classicDefaults());
            case TowerFamily.HEAVY_TRANSMISSION_ID -> TowerGeneratorConfig.parametricHeavy(
                TowerParameterSet.heavyDefaults());
            case TowerFamily.TRIPLE_ARM_3_PHASE_ID -> TowerGeneratorConfig.parametricTripleArm(
                TowerParameterSet.tripleArmDefaults());
            case TowerFamily.CUP_TOWER_ID -> TowerGeneratorConfig.parametricCup(
                TowerParameterSet.cupDefaults());
            case TowerFamily.MEGA_LATTICE_ID -> TowerGeneratorConfig.parametricMega(
                TowerParameterSet.megaDefaults());
            case TowerFamily.HEAVY_DOUBLE_CIRCUIT_ID -> TowerGeneratorConfig.parametricDrum(
                TowerParameterSet.drumDefaults());
            case TowerFamily.INDUSTRIAL_PORTAL_ID -> TowerGeneratorConfig.parametricPortal(
                TowerParameterSet.portalDefaults());
            case TowerFamily.MONSTER_PYLON_ID -> TowerGeneratorConfig.parametricUhv(
                TowerParameterSet.uhvDefaults());
            default -> null;
        };
    }

    public static TowerGeneratorConfig forPoleDesignId(String poleDesignId) {
        if (poleDesignId == null || poleDesignId.isBlank()) {
            return null;
        }
        if (PoleDesignCatalog.LATTICE_STEEL_TOWER_ID.equals(poleDesignId)
                || PoleDesignCatalog.TAPERED_LATTICE_TOWER_ID.equals(poleDesignId)) {
            return TowerGeneratorConfig.parametricSmallLattice(TowerParameterSet.smallLatticeDefaults());
        }
        return null;
    }

    public static PoleDesign compileRepresentative(TowerGeneratorConfig config) {
        if (config == null || !config.isParametric()) {
            return null;
        }
        TowerParameterProfile profile = TowerParametricEditor.findProfile(config.profileId()).orElse(null);
        if (profile == null) {
            return null;
        }
        PoleDesign design = TowerParametricDesignFactory.compileProfile(profile, config.parameters(), null);
        design.setGeneratorConfig(config.copy());
        return design;
    }

    public static boolean parametersMatch(TowerGeneratorConfig expected, TowerGeneratorConfig actual) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        if (!expected.profileId().equals(actual.profileId())) {
            return false;
        }
        TowerParameterSet a = expected.parameters();
        TowerParameterSet b = actual.parameters();
        return Double.compare(a.height(), b.height()) == 0
            && Double.compare(a.baseWidth(), b.baseWidth()) == 0
            && Double.compare(a.armSpan(), b.armSpan()) == 0
            && Double.compare(a.depthScale(), b.depthScale()) == 0
            && Double.compare(a.waistRatio(), b.waistRatio()) == 0
            && a.density() == b.density()
            && armLevelScalesMatch(a, b);
    }

    private static boolean armLevelScalesMatch(TowerParameterSet a, TowerParameterSet b) {
        java.util.List<Double> left = a.armLevelScales();
        java.util.List<Double> right = b.armLevelScales();
        if (left == null || left.isEmpty()) {
            return right == null || right.isEmpty();
        }
        if (right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (Double.compare(left.get(i), right.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }
}
