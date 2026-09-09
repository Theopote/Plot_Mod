package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;

/** 跨距过长 / 过密。 */
public final class SpacingCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, PowerLineValidationReport report) {
        if (context.geometry() == null || context.limits() == null) {
            return;
        }
        for (ConductorSpanGeometry span : context.geometry().getConductorSpans()) {
            SpanAnalysis spanAnalysis = SpanValidationSupport.beginSpanAnalysis(
                context.geometry(), span, report);
            if (span.getSpanLength() > context.limits().maximumSpan()) {
                spanAnalysis.addIssue(new SimplePowerLineIssue(
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    PowerLineIssueSeverity.ERROR,
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    PowerLineIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                    span.getSpanLength(),
                    context.limits().maximumSpan()));
            } else if (span.getSpanLength() < ValidationLimits.ABSOLUTE_MIN_VISUAL_SPAN) {
                spanAnalysis.addIssue(new SimplePowerLineIssue(
                    EngineeringRuleIds.SPAN_MINIMUM,
                    PowerLineIssueSeverity.ERROR,
                    EngineeringRuleIds.SPAN_MINIMUM,
                    PowerLineIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                    span.getSpanLength(),
                    ValidationLimits.ABSOLUTE_MIN_VISUAL_SPAN));
            } else if (span.getSpanLength() < context.limits().minimumSpan()) {
                spanAnalysis.addIssue(new SimplePowerLineIssue(
                    EngineeringRuleIds.SPAN_MINIMUM,
                    PowerLineIssueSeverity.WARNING,
                    EngineeringRuleIds.SPAN_MINIMUM,
                    PowerLineIssueLocation.at(SpanValidationSupport.midpoint(span), 0.0),
                    span.getSpanLength(),
                    context.limits().minimumSpan()));
            }
        }
    }
}
