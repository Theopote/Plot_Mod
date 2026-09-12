package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 参数化杆塔编辑：将 {@link TowerParameterSet} 编译进 {@link PoleDesign}，保留 id/名称/工程元数据。
 * <p>
 * Constraint ERROR 时与 {@link TowerParametricDesignFactory#compileProfile} 一致：拒绝写入结构。
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

    public static void enableParametricSteampunk(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.STEAMPUNK_ID, parameters, TowerParameterSet.steampunkDefaults());
    }

    public static void enableParametricModernHvGlass(PoleDesign design, TowerParameterSet parameters) {
        enableParametric(design, TowerParameterProfiles.MODERN_HV_GLASS_ID, parameters, TowerParameterSet.modernHvGlassDefaults());
    }

    private static void enableParametric(
            PoleDesign design,
            String profileId,
            TowerParameterSet parameters,
            TowerParameterSet fallback) {
        if (design == null) {
            return;
        }
        String previousProfileId = design.getGeneratorConfig() != null
            ? design.getGeneratorConfig().profileId()
            : null;
        TowerParameterSet resolved = parameters != null ? parameters : fallback;
        design.setGeneratorConfig(new TowerGeneratorConfig(profileId, TowerGeneratorMode.PARAMETRIC, resolved));
        recompile(design, null, previousProfileId);
    }

    public static TowerConstraintResult recompile(PoleDesign design, TowerBuildEnvelope envelope) {
        return recompile(design, envelope, null);
    }

    public static TowerConstraintResult recompile(
            PoleDesign design,
            TowerBuildEnvelope envelope,
            String previousProfileId) {
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
        design.setGeneratorConfig(config.withParameters(toParameterSet(result.resolved())));
        if (result.hasErrors()) {
            return result;
        }

        List<ConductorAttachment> previousAttachments = new ArrayList<>(design.getAttachments());
        boolean sameProfile = previousProfileId == null
            || Objects.equals(previousProfileId, config.profileId());
        PoleDesign compiled = TowerStructureCompiler.compile(profile, result.resolved());
        design.setTowerStructure(compiled.getTowerStructure());
        if (previousAttachments.isEmpty() || !sameProfile) {
            design.setAttachments(compiled.getAttachments());
        } else {
            TowerArmAttachmentBinding.ensureV2Bindings(design);
            design.setAttachments(previousAttachments);
        }
        return result;
    }

    public static boolean hasBlockingErrors(PoleDesign design, TowerBuildEnvelope envelope) {
        TowerConstraintResult result = preview(design, envelopeParameters(design), envelope);
        return result != null && result.hasErrors();
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
        if (design == null || design.getGeneratorConfig() == null) {
            return;
        }
        TowerGeneratorConfig current = design.getGeneratorConfig();
        design.setGeneratorConfig(new TowerGeneratorConfig(
            current.profileId(),
            TowerGeneratorMode.MANUAL_LEGACY,
            current.parameters()));
    }

    public static boolean restoreParametric(PoleDesign design, TowerBuildEnvelope envelope) {
        if (design == null || design.getGeneratorConfig() == null) {
            return false;
        }
        TowerGeneratorConfig current = design.getGeneratorConfig();
        if (current.mode() != TowerGeneratorMode.MANUAL_LEGACY) {
            return false;
        }
        design.setGeneratorConfig(new TowerGeneratorConfig(
            current.profileId(),
            TowerGeneratorMode.PARAMETRIC,
            current.parameters()));
        recompile(design, envelope);
        return true;
    }

    private static TowerParameterSet envelopeParameters(PoleDesign design) {
        return design.getGeneratorConfig().parameters();
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
