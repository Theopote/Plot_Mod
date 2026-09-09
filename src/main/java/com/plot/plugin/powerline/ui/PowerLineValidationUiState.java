package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.optimization.OptimizationResult;

/** 线路检查与会话状态（画布叠加、智能修正确认）。 */
public final class PowerLineValidationUiState {
    private LineEngineeringReport lastEngineeringReport;
    private LineEngineeringReport lastTerrainReport;
    private PowerLineAnalysisKey engineeringReportKey;
    private PowerLineAnalysisKey terrainReportKey;
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

    public PowerLineAnalysisKey getEngineeringReportKey() {
        return engineeringReportKey;
    }

    public void setEngineeringReportKey(PowerLineAnalysisKey engineeringReportKey) {
        this.engineeringReportKey = engineeringReportKey;
    }

    public PowerLineAnalysisKey getTerrainReportKey() {
        return terrainReportKey;
    }

    public void setTerrainReportKey(PowerLineAnalysisKey terrainReportKey) {
        this.terrainReportKey = terrainReportKey;
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

    public void clearAnalysisReports() {
        lastEngineeringReport = null;
        lastTerrainReport = null;
        engineeringReportKey = null;
        terrainReportKey = null;
    }

    public void clearOptimization() {
        pendingOptimization = null;
        optimizationConfirmPending = false;
    }
}
