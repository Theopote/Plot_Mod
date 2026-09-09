package com.plot.plugin.powerline.engineering.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;

/** 跨距类检查的 span 分析条目复用。 */
final class SpanValidationSupport {
    private SpanValidationSupport() {
    }

    static SpanAnalysis beginSpanAnalysis(
            PowerLineGeometryModel geometry,
            ConductorSpanGeometry span,
            LineEngineeringReport report) {
        SpanAnalysis spanAnalysis = new SpanAnalysis();
        spanAnalysis.setId(span.getSpanId());
        spanAnalysis.setStartPoleSiteId(span.getStartPoleSiteId());
        spanAnalysis.setEndPoleSiteId(span.getEndPoleSiteId());
        spanAnalysis.setHorizontalLength(span.getSpanLength());
        if (span.getStartPoleIndex() >= 0
                && span.getEndPoleIndex() < geometry.getPlacements().size()) {
            var start = geometry.getPlacements().get(span.getStartPoleIndex());
            var end = geometry.getPlacements().get(span.getEndPoleIndex());
            spanAnalysis.setElevationDifference(Math.abs(start.legacyWireHangY() - end.legacyWireHangY()));
        }
        report.addSpan(spanAnalysis);
        return spanAnalysis;
    }

    static Vec2d midpoint(ConductorSpanGeometry span) {
        if (span.getSamples().isEmpty()) {
            return new Vec2d(0, 0);
        }
        int mid = span.getSamples().size() / 2;
        return span.getSamples().get(mid).planPoint().copy();
    }
}
