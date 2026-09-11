package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

import java.util.Optional;

/**
 * 参数化杆塔编辑：将 {@link TowerParameterSet} 编译进 {@link PoleDesign}，保留 id/名称/工程元数据。
 */
public final class TowerParametricEditor {
    private TowerParametricEditor() {
    }

    public static boolean supportsProfile(String profileId) {
        return TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID.equals(profileId);
    }

    public static Optional<TowerParameterProfile> findProfile(String profileId) {
        if (TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID.equals(profileId)) {
            return Optional.of(TowerParameterProfiles.classicDoubleArm());
        }
        return Optional.empty();
    }

    public static void enableParametricClassic(PoleDesign design, TowerParameterSet parameters) {
        if (design == null) {
            return;
        }
        TowerParameterSet resolvedParameters = parameters != null
            ? parameters
            : TowerParameterSet.classicDefaults();
        design.setGeneratorConfig(TowerGeneratorConfig.parametricClassic(resolvedParameters));
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
        PoleDesign compiled = TowerStructureCompiler.compile(profile, result.resolved());
        design.setTowerStructure(compiled.getTowerStructure());
        design.setAttachments(compiled.getAttachments());
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
            resolved.density());
    }
}
