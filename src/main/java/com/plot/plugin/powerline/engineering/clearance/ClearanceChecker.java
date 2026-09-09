package com.plot.plugin.powerline.engineering.clearance;

import com.plot.plugin.powerline.engineering.PowerLineIssue;
import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.road.terrain.TerrainSampler;

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
            double clearance = WireClearanceMath.computeSampleClearance(sample, terrain);
            if (clearance < minClearance) {
                minClearance = clearance;
                critical = sample;
                criticalTerrainY = WireClearanceMath.obstructionTopY(sample, terrain);
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
            EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM,
            severity,
            EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM,
            PowerLineIssueLocation.at(
                analysis.getCriticalLocation(),
                0.0),
            analysis.getMinimumClearance(),
            requiredClearance);
    }
}
