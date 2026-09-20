package com.plot.plugin.powerline.geometry.clearance;

import com.plot.plugin.powerline.geometry.PowerLineIssue;
import com.plot.plugin.powerline.geometry.PowerLineIssueLocation;
import com.plot.plugin.powerline.geometry.ClearanceRuleIds;
import com.plot.plugin.powerline.geometry.PowerLineIssueSeverity;
import com.plot.plugin.powerline.geometry.SimplePowerLineIssue;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.core.terrain.TerrainSampler;

/** 基于 conductor sample 的净空检查（只读）。 */
public final class ClearanceChecker {
    private ClearanceChecker() {
    }

    public static ClearanceAnalysis analyzeSpan(
            ConductorSpanGeometry span,
            TerrainSampler terrain) {
        ClearanceAnalysis analysis = new ClearanceAnalysis();
        if (span == null || terrain == null) {
            return analysis;
        }
        analysis.setSpanId(span.getSpanId());
        analysis.setAttachmentId(span.getAttachmentId());

        double minClearance = Double.MAX_VALUE;
        ConductorSample critical = null;
        int criticalTerrainY = 0;

        for (ConductorSample sample : span.getSamples()) {
            WireClearanceMath.SampleScanResult scan = WireClearanceMath.scanSample(sample, terrain);
            if (scan.clearance() < minClearance) {
                minClearance = scan.clearance();
                critical = sample;
                criticalTerrainY = scan.obstructionTopY();
            }
        }

        if (critical != null) {
            analysis.setMinimumClearance(minClearance);
            analysis.setCriticalLocation(critical.planPoint());
            analysis.setConductorY(critical.worldY());
            analysis.setTerrainY(criticalTerrainY);
            span.setMinimumGroundClearance(minClearance);
        }
        return analysis;
    }

    public static PowerLineIssue toIssue(
            ClearanceAnalysis analysis,
            double requiredClearance,
            PowerLineIssueSeverity severity) {
        if (analysis == null || analysis.getMinimumClearance() >= requiredClearance) {
            return null;
        }
        return new SimplePowerLineIssue(
            ClearanceRuleIds.CLEARANCE_GROUND_MINIMUM,
            severity,
            ClearanceRuleIds.CLEARANCE_GROUND_MINIMUM,
            PowerLineIssueLocation.at(
                analysis.getCriticalLocation(),
                0.0),
            analysis.getMinimumClearance(),
            requiredClearance);
    }
}
