package com.plot.plugin.powerline.engineering.clearance;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.SimpleEngineeringIssue;
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
            int terrainY = terrain.sampleSurfaceY(sample.planPoint());
            double clearance = sample.worldY() - terrainY;
            if (clearance < minClearance) {
                minClearance = clearance;
                critical = sample;
                criticalTerrainY = terrainY;
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

    public static EngineeringIssue toIssue(
            ClearanceAnalysis analysis,
            double requiredClearance,
            EngineeringSeverity severity) {
        if (analysis == null || analysis.getMinimumClearance() >= requiredClearance) {
            return null;
        }
        return new SimpleEngineeringIssue(
            EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM,
            severity,
            EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM,
            EngineeringIssueLocation.at(
                analysis.getCriticalLocation(),
                0.0),
            analysis.getMinimumClearance(),
            requiredClearance);
    }
}
