package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.ResolvedTowerParameters;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;

/**
 * Explicit parametric edit state for the tower designer session.
 * <p>
 * {@code requestedParameters} drives sliders; {@code resolvedParameters} is the latest solver output;
 * {@code lastValidParameters} / {@code lastValidStructure} are the last successful compile used for
 * preview and footprint sync when constraints block structure updates.
 */
final class ParametricTowerEditState {
    private TowerParameterSet requestedParameters;
    private TowerParameterSet resolvedParameters;
    private TowerParameterSet lastValidParameters;
    private TowerStructureDesign lastValidStructure;

    void reset() {
        requestedParameters = null;
        resolvedParameters = null;
        lastValidParameters = null;
        lastValidStructure = null;
    }

    void captureFromDesign(PoleDesign draft) {
        if (draft == null || !draft.hasTowerStructure() || draft.getGeneratorConfig() == null) {
            reset();
            return;
        }
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        requestedParameters = parameters;
        resolvedParameters = parameters;
        lastValidParameters = parameters;
        lastValidStructure = draft.getTowerStructure().copy();
    }

    TowerParameterSet parametersForEdit(PoleDesign draft) {
        if (requestedParameters != null) {
            return requestedParameters;
        }
        if (draft != null && draft.getGeneratorConfig() != null) {
            return draft.getGeneratorConfig().parameters();
        }
        return TowerParameterSet.classicDefaults();
    }

    void recordRequested(PoleDesign draft, TowerParameterSet requested) {
        requestedParameters = requested;
        if (draft != null && draft.getGeneratorConfig() != null) {
            draft.setGeneratorConfig(draft.getGeneratorConfig().withParameters(requested));
        }
    }

    void applyRecompileResult(PoleDesign draft, TowerConstraintResult result) {
        if (result == null) {
            return;
        }
        resolvedParameters = toParameterSet(result.resolved());
        if (result.hasErrors()) {
            if (lastValidStructure != null && draft != null) {
                draft.setTowerStructure(lastValidStructure.copy());
            }
            return;
        }
        lastValidParameters = resolvedParameters;
        requestedParameters = resolvedParameters;
        if (draft != null && draft.getGeneratorConfig() != null) {
            draft.setGeneratorConfig(draft.getGeneratorConfig().withParameters(resolvedParameters));
        }
        if (draft != null && draft.getTowerStructure() != null) {
            lastValidStructure = draft.getTowerStructure().copy();
        }
    }

    TowerParameterSet footprintParameters(TowerConstraintResult constraintResult) {
        if (constraintResult != null && constraintResult.hasErrors() && lastValidParameters != null) {
            return lastValidParameters;
        }
        if (resolvedParameters != null) {
            return resolvedParameters;
        }
        return requestedParameters;
    }

    TowerParameterSet requestedParameters() {
        return requestedParameters;
    }

    TowerParameterSet resolvedParameters() {
        return resolvedParameters;
    }

    TowerParameterSet lastValidParameters() {
        return lastValidParameters;
    }

    TowerStructureDesign lastValidStructure() {
        return lastValidStructure;
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
