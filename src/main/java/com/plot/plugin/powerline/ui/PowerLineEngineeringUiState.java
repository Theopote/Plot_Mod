package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.optimization.OptimizationResult;

/** 工程分析与会话状态扩展。 */
public final class PowerLineEngineeringUiState {
    private LineEngineeringReport lastEngineeringReport;
    private OptimizationResult pendingOptimization;
    private boolean optimizationConfirmPending;

    public LineEngineeringReport getLastEngineeringReport() {
        return lastEngineeringReport;
    }

    public void setLastEngineeringReport(LineEngineeringReport lastEngineeringReport) {
        this.lastEngineeringReport = lastEngineeringReport;
    }

    public OptimizationResult getPendingOptimization() {
        return pendingOptimization;
    }

    public void setPendingOptimization(OptimizationResult pendingOptimization) {
        this.pendingOptimization = pendingOptimization;
    }

    public boolean isOptimizationConfirmPending() {
        return optimizationConfirmPending;
    }

    public void setOptimizationConfirmPending(boolean optimizationConfirmPending) {
        this.optimizationConfirmPending = optimizationConfirmPending;
    }

    public void clearOptimization() {
        pendingOptimization = null;
        optimizationConfirmPending = false;
    }
}
