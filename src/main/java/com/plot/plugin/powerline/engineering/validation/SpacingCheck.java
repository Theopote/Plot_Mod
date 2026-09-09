package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.SimpleEngineeringIssue;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;

/** 跨距过长 / 过密。 */
public final class SpacingCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, LineEngineeringReport report) {
        if (context.geometry() == null || context.limits() == null) {
            return;
        }
        for (ConductorSpanGeometry span : context.geometry().getConductorSpans()) {
            SpanAnalysis spanAnalysis = SpanValidationSupport.beginSpanAnalysis(
                context.geometry(), span, report);
            if (span.getSpanLength() > context.limits().maximumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    EngineeringSeverity.ERROR,
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    EngineeringIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                    span.getSpanLength(),
                    context.limits().maximumSpan()));
            } else if (span.getSpanLength() < context.limits().minimumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MINIMUM,
                    EngineeringSeverity.WARNING,
                    EngineeringRuleIds.SPAN_MINIMUM,
                    EngineeringIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                    span.getSpanLength(),
                    context.limits().minimumSpan()));
            }
        }
    }
}
