package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

import java.util.ArrayList;
import java.util.List;

/** 参数化塔型在整条线路上的放置准备（线路级世界包络约束）。 */
public final class TowerParametricLinePlacement {
    private TowerParametricLinePlacement() {
    }

    public record PreparationResult(PoleDesign design, List<String> warnings) {
    }

    public static PreparationResult prepare(PoleDesign source, TowerLineBuildEnvelope lineEnvelope) {
        if (source == null) {
            return new PreparationResult(null, List.of());
        }
        if (!source.isParametricMode() || lineEnvelope == null) {
            return new PreparationResult(source, List.of());
        }

        PoleDesign design = source.copy();
        TowerGeneratorConfig config = design.getGeneratorConfig();
        TowerParameterProfile profile = TowerParametricEditor.findProfile(config.profileId()).orElse(null);
        if (profile == null) {
            return new PreparationResult(design, List.of());
        }

        TowerBuildEnvelope constraint = lineEnvelope.constraintEnvelope();
        TowerParameterSet parameters = config.parameters();
        double maxHeight = TowerParametricHeightLimits.maxAllowedHeight(profile, parameters, constraint);
        List<String> warnings = new ArrayList<>();
        if (parameters.height() > maxHeight + 1e-6) {
            warnings.add("parametric.height_clamped_for_line:" + (int) Math.floor(maxHeight));
            parameters = new TowerParameterSet(
                maxHeight,
                parameters.baseWidth(),
                parameters.armSpan(),
                parameters.depthScale(),
                parameters.waistRatio(),
                parameters.armLevelScales(),
                parameters.density());
            design.setGeneratorConfig(config.withParameters(parameters));
        }

        TowerConstraintResult result = TowerParametricEditor.recompile(design, constraint);
        if (result != null) {
            for (ConstraintIssue issue : result.issues()) {
                if (TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())) {
                    warnings.add("parametric.world_height_exceeded_on_line");
                }
            }
        }
        return new PreparationResult(design, warnings);
    }
}
