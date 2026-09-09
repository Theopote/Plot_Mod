package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.TerrainAvoidance;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.road.terrain.TerrainSampler;

/** 导线与地形碰撞 / 净空不足。 */
public final class TerrainCollisionCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, LineEngineeringReport report) {
        if (context.geometry() == null || context.terrain() == null || context.limits() == null) {
            return;
        }
        double clearanceLimit = resolveClearanceLimit(context);
        for (ConductorSpanGeometry span : context.geometry().getConductorSpans()) {
            if (span.getSamples().isEmpty()) {
                continue;
            }
            SpanAnalysis spanAnalysis = findOrCreateSpanAnalysis(context.geometry(), span, report);
            ClearanceAnalysis clearance = ClearanceChecker.analyzeSpan(span, context.terrain());
            spanAnalysis.setMinimumGroundClearance(clearance.getMinimumClearance());
            EngineeringIssue issue = ClearanceChecker.toIssue(
                clearance,
                clearanceLimit,
                com.plot.plugin.powerline.engineering.EngineeringSeverity.ERROR);
            if (issue != null) {
                spanAnalysis.addIssue(issue);
            }
        }
    }

    /** 供 {@link TerrainAvoidance} 复用的地形专用报告。 */
    public static LineEngineeringReport analyzeTerrainOnly(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain) {
        LineEngineeringReport report = new LineEngineeringReport();
        report.setProfileId(TerrainAvoidance.PROFILE_ID);
        report.setProfileName("Terrain Avoidance");
        if (geometry == null || terrain == null) {
            return report;
        }
        LineValidationContext context = new LineValidationContext(
            geometry,
            terrain,
            null,
            null,
            new ValidationLimits(
                Double.MAX_VALUE,
                0.0,
                TerrainAvoidance.SAFETY_MARGIN_BLOCKS,
                ValidationLimits.fromFootprint(null, null).wireOverlapThreshold(),
                Double.MAX_VALUE));
        new TerrainCollisionCheck().apply(context, report);
        return report;
    }

    private static double resolveClearanceLimit(LineValidationContext context) {
        if (context.footprint() != null && context.footprint().isTerrainAvoidanceEnabled()) {
            return TerrainAvoidance.SAFETY_MARGIN_BLOCKS;
        }
        return context.limits().minimumGroundClearance();
    }

    private static SpanAnalysis findOrCreateSpanAnalysis(
            PowerLineGeometryModel geometry,
            ConductorSpanGeometry span,
            LineEngineeringReport report) {
        for (SpanAnalysis existing : report.getSpans()) {
            if (span.getSpanId() != null && span.getSpanId().equals(existing.getId())) {
                return existing;
            }
        }
        return SpanValidationSupport.beginSpanAnalysis(geometry, span, report);
    }
}
