package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.PowerLineSagPolicy;
import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;

import java.util.List;

/** 单跨下垂过深（超过玩家配置的最大下垂深度）。 */
public final class SagCheck implements LineValidationCheck {
    private static final double SAG_TOLERANCE = 0.5;

    @Override
    public void apply(LineValidationContext context, PowerLineValidationReport report) {
        if (context.geometry() == null || context.footprint() == null) {
            return;
        }
        double maxSagDepth = PowerLineSagPolicy.resolveMaxSagDepth(context.footprint());
        if (maxSagDepth <= 0.0) {
            return;
        }
        for (ConductorSpanGeometry span : context.geometry().getConductorSpans()) {
            double observed = observedSagDepth(span);
            if (observed <= maxSagDepth + SAG_TOLERANCE) {
                continue;
            }
            SpanAnalysis spanAnalysis = findOrCreateSpanAnalysis(context.geometry(), span, report);
            spanAnalysis.addIssue(new SimplePowerLineIssue(
                EngineeringRuleIds.SAG_MAXIMUM,
                PowerLineIssueSeverity.WARNING,
                EngineeringRuleIds.SAG_MAXIMUM,
                PowerLineIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                observed,
                maxSagDepth));
        }
    }

    private static double observedSagDepth(ConductorSpanGeometry span) {
        List<ConductorSample> samples = span.getSamples();
        if (samples.size() < 2) {
            return 0.0;
        }
        double startY = samples.getFirst().worldY();
        double endY = samples.getLast().worldY();
        double totalLength = 0.0;
        for (int i = 1; i < samples.size(); i++) {
            totalLength += samples.get(i - 1).planPoint().distance(samples.get(i).planPoint());
        }
        double maxSag = 0.0;
        double walked = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            double t = totalLength > 1e-6 ? walked / totalLength : 0.0;
            double chordY = startY + (endY - startY) * t;
            maxSag = Math.max(maxSag, chordY - samples.get(i).worldY());
            if (i + 1 < samples.size()) {
                walked += samples.get(i).planPoint().distance(samples.get(i + 1).planPoint());
            }
        }
        return maxSag;
    }

    private static SpanAnalysis findOrCreateSpanAnalysis(
            com.plot.plugin.powerline.geometry.PowerLineGeometryModel geometry,
            ConductorSpanGeometry span,
            PowerLineValidationReport report) {
        for (SpanAnalysis existing : report.getSpans()) {
            if (span.getSpanId() != null && span.getSpanId().equals(existing.getId())) {
                return existing;
            }
        }
        return SpanValidationSupport.beginSpanAnalysis(geometry, span, report);
    }
}
