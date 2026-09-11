package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 参数化杆塔编辑：将 {@link TowerParameterSet} 编译进 {@link PoleDesign}，保留 id/名称/工程元数据。
 */
public final class TowerParametricEditor {
    private TowerParametricEditor() {
    }

    public static boolean supportsProfile(String profileId) {
        return TowerParameterProfiles.find(profileId).isPresent();
    }

    public static Optional<TowerParameterProfile> findProfile(String profileId) {
        return TowerParameterProfiles.find(profileId);
    }

    public static void enableParametricClassic(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID, parameters, TowerParameterSet.classicDefaults());
    }

    public static void enableParametricSmallLattice(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.SMALL_LATTICE_ID, parameters, TowerParameterSet.smallLatticeDefaults());
    }

    public static void enableParametricTripleArm(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.TRIPLE_ARM_ID, parameters, TowerParameterSet.tripleArmDefaults());
    }

    public static void enableParametricCup(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.CUP_ID, parameters, TowerParameterSet.cupDefaults());
    }

    public static void enableParametricHeavy(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.HEAVY_ID, parameters, TowerParameterSet.heavyDefaults());
    }

    public static void enableParametricMega(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.MEGA_ID, parameters, TowerParameterSet.megaDefaults());
    }

    public static void enableParametricPortal(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.PORTAL_ID, parameters, TowerParameterSet.portalDefaults());
    }

    public static void enableParametricDrum(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.DRUM_ID, parameters, TowerParameterSet.drumDefaults());
    }

    public static void enableParametricUhv(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.UHV_ID, parameters, TowerParameterSet.uhvDefaults());
    }

    private static void enableParametric(
            PoleDesign design,
            String profileId,
            TowerParameterSet parameters,
            TowerParameterSet fallback) {
        if (design == null) {
            return;
        }
        TowerParameterSet resolved = parameters != null ? parameters : fallback;
        design.setGeneratorConfig(new TowerGeneratorConfig(profileId, TowerGeneratorMode.PARAMETRIC, resolved));
        recompile(design, null);
    }

    public static TowerConstraintResult recompile(PoleDesign design, TowerBuildEnvelope envelope) {
        if (design == null || design.getGeneratorConfig() == null || !design.getGeneratorConfig().isParametric()) {
            return null;
        }
        TowerGeneratorConfig config = design.getGeneratorConfig();
        TowerParameterProfile profile = findProfile(config.profileId())
            .orElseThrow(() -> new IllegalStateException("Unsupported profile: " + config.profileId()));
        TowerConstraintResult result = TowerParametricDesignFactory.resolveProfile(
            profile,
            config.parameters(),
            envelope);
        List<ConductorAttachment> previousAttachments = new ArrayList<>(design.getAttachments());
        PoleDesign compiled = TowerStructureCompiler.compile(profile, result.resolved());
        design.setTowerStructure(compiled.getTowerStructure());
        if (previousAttachments.isEmpty()) {
            design.setAttachments(compiled.getAttachments());
        } else {
            TowerArmAttachmentBinding.ensureV2Bindings(design);
            design.setAttachments(previousAttachments);
        }
        design.setGeneratorConfig(config.withParameters(toParameterSet(result.resolved())));
        return result;
    }

    public static TowerConstraintResult preview(
            PoleDesign design,
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        if (design == null || design.getGeneratorConfig() == null) {
            return null;
        }
        TowerParameterProfile profile = findProfile(design.getGeneratorConfig().profileId()).orElse(null);
        if (profile == null) {
            return null;
        }
        return TowerParametricDesignFactory.resolveProfile(profile, parameters, envelope);
    }

    public static void convertToManual(PoleDesign design) {
        if (design == null) {
            return;
        }
        design.setGeneratorConfig(null);
    }

    private static TowerParameterSet toParameterSet(ResolvedTowerParameters resolved) {
        return new TowerParameterSet(
            resolved.height(),
            resolved.baseWidth(),
            resolved.armSpan(),
            resolved.depthScale(),
            resolved.waistRatio(),
            resolved.armLevelScales(),
            resolved.density());
    }
}
