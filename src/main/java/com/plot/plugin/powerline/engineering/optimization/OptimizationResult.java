package com.plot.plugin.powerline.engineering.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 工程优化提案结果。 */
public class OptimizationResult {
    private final List<OptimizationAction> actions = new ArrayList<>();
    private int estimatedErrorsResolved;
    private int remainingErrors;
    private boolean requiresRegeneration = true;

    public List<OptimizationAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public void addAction(OptimizationAction action) {
        if (action != null) {
            actions.add(action);
        }
    }

    public int getEstimatedErrorsResolved() {
        return estimatedErrorsResolved;
    }

    public void setEstimatedErrorsResolved(int estimatedErrorsResolved) {
        this.estimatedErrorsResolved = Math.max(0, estimatedErrorsResolved);
    }

    public int getRemainingErrors() {
        return remainingErrors;
    }

    public void setRemainingErrors(int remainingErrors) {
        this.remainingErrors = Math.max(0, remainingErrors);
    }

    public boolean isRequiresRegeneration() {
        return requiresRegeneration;
    }

    public void setRequiresRegeneration(boolean requiresRegeneration) {
        this.requiresRegeneration = requiresRegeneration;
    }
}
