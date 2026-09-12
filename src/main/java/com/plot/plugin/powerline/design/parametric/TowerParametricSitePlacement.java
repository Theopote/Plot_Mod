package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

import java.util.ArrayList;
import java.util.List;

/** 单杆位参数化放置（实验性）：按线路相对世界包络插值塔高。默认关闭。 */
public final class TowerParametricSitePlacement {
    private TowerParametricSitePlacement() {
    }

    public record PreparationResult(PoleDesign design, List<String> warnings) {
    }

    public static PreparationResult prepareForSite(
            PoleDesign source,
            TowerLineBuildEnvelope lineEnvelope,
            int siteIndex) {
        if (source == null || lineEnvelope == null) {
            return new PreparationResult(source, List.of());
        }
        TowerBuildEnvelope siteEnvelope = lineEnvelope.siteEnvelope(siteIndex);
        if (!source.isParametricMode()) {
            return new PreparationResult(source, List.of());
        }

        PoleDesign design = source.copy();
        TowerGeneratorConfig config = design.getGeneratorConfig();
        TowerParameterProfile profile = TowerParametricEditor.findProfile(config.profileId()).orElse(null);
        if (profile == null) {
            return new PreparationResult(design, List.of());
        }

        TowerParameterSet parameters = config.parameters();
        double adaptedHeight = resolveAdaptiveHeight(profile, parameters, siteEnvelope, lineEnvelope);
        List<String> warnings = new ArrayList<>();
        if (parameters.height() > adaptedHeight + 1e-6) {
            warnings.add("parametric.height_clamped_for_site:" + (int) Math.floor(adaptedHeight));
            parameters = replaceHeight(parameters, adaptedHeight);
            design.setGeneratorConfig(config.withParameters(parameters));
        } else if (adaptedHeight > parameters.height() + 1e-6) {
            parameters = replaceHeight(parameters, adaptedHeight);
            design.setGeneratorConfig(config.withParameters(parameters));
        }

        TowerConstraintResult result = TowerParametricEditor.recompile(design, siteEnvelope);
        if (result != null) {
            for (ConstraintIssue issue : result.issues()) {
                if (TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())) {
                    warnings.add("parametric.world_height_exceeded_on_site");
                }
            }
        }
        return new PreparationResult(design, warnings);
    }

    /**
     * 在 profile 范围内按线路相对净空插值塔高（山谷更高、山顶更矮）。
     */
    public static double resolveAdaptiveHeight(
            TowerParameterProfile profile,
            TowerParameterSet template,
            TowerBuildEnvelope siteEnvelope,
            TowerLineBuildEnvelope lineEnvelope) {
        if (profile == null || template == null || siteEnvelope == null || lineEnvelope == null) {
            return template != null ? template.height() : 0.0;
        }
        double profileMin = profile.heightRange().min();
        double siteMaxAllowed = TowerParametricHeightLimits.maxAllowedHeight(profile, template, siteEnvelope);
        double lineMin = lineEnvelope.limitingAvailableHeight();
        double lineMax = lineEnvelope.maxAvailableHeight();
        if (lineMax - lineMin < 1e-6) {
            return Math.max(profileMin, Math.min(template.height(), siteMaxAllowed));
        }
        double siteAvailable = siteEnvelope.availableLocalHeight();
        double blend = (siteAvailable - lineMin) / (lineMax - lineMin);
        blend = Math.max(0.0, Math.min(1.0, blend));
        double target = profileMin + blend * (siteMaxAllowed - profileMin);
        return Math.max(profileMin, Math.min(siteMaxAllowed, target));
    }

    private static TowerParameterSet replaceHeight(TowerParameterSet parameters, double height) {
        return new TowerParameterSet(
            height,
            parameters.baseWidth(),
            parameters.armSpan(),
            parameters.depthScale(),
            parameters.waistRatio(),
            parameters.armLevelScales(),
            parameters.density());
    }
}
