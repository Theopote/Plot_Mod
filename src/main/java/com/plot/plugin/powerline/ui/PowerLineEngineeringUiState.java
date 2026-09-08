package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.optimization.OptimizationResult;

/** 工程分析与会话状态扩展。 */
public final class PowerLineEngineeringUiState {
    private LineEngineeringReport lastEngineeringReport;
    private LineEngineeringReport lastTerrainReport;
    private String lastAnalyzedFootprintId;
    private OptimizationResult pendingOptimization;
    private boolean optimizationConfirmPending;
    private boolean overlayEnabled = false;

    public LineEngineeringReport getLastEngineeringReport() {
        return lastEngineeringReport;
    }

    public void setLastEngineeringReport(LineEngineeringReport lastEngineeringReport) {
        this.lastEngineeringReport = lastEngineeringReport;
    }

    public LineEngineeringReport getLastTerrainReport() {
        return lastTerrainReport;
    }

    public void setLastTerrainReport(LineEngineeringReport lastTerrainReport) {
        this.lastTerrainReport = lastTerrainReport;
    }

    public String getLastAnalyzedFootprintId() {
        return lastAnalyzedFootprintId;
    }

    public void setLastAnalyzedFootprintId(String lastAnalyzedFootprintId) {
        this.lastAnalyzedFootprintId = lastAnalyzedFootprintId;
    }

    public boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
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
