package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;

import java.util.function.UnaryOperator;

/** Testable parametric edit pipeline used by {@link TowerDesignerSession}. */
public final class TowerDesignerParameterController {
    private TowerDesignerParameterController() {
    }

    public record ApplyResult(TowerConstraintResult constraintResult) {
    }

    public static ApplyResult applyParametricChange(
            PoleDesign draft,
            TowerBuildEnvelope envelope,
            UnaryOperator<TowerParameterSet> change) {
        if (draft == null || !draft.isParametricMode() || draft.getGeneratorConfig() == null) {
            return new ApplyResult(null);
        }
        TowerGeneratorConfig config = draft.getGeneratorConfig();
        TowerParameterSet requested = change.apply(config.parameters());
        draft.setGeneratorConfig(config.withParameters(requested));
        TowerConstraintResult result = TowerParametricEditor.recompile(draft, envelope);
        return new ApplyResult(result);
    }

    public static boolean canBuild(TowerConstraintResult result) {
        return result == null || !result.hasErrors();
    }
}
