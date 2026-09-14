package com.plot.plugin.powerline.engineering.optimization;

import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssue;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LineOptimizationEngineTest {

    @Test
    void insertPoleUsesMidSpanStationingNotHalfSpanLength() {
        PowerPoleSite start = new PowerPoleSite("start", new Vec2d(0, 0));
        start.setStationing(220.0);
        PowerPoleSite end = new PowerPoleSite("end", new Vec2d(180, 0));
        end.setStationing(400.0);

        SpanAnalysis span = new SpanAnalysis();
        span.setId("span-1");
        span.setStartPoleSiteId(start.getId());
        span.setEndPoleSiteId(end.getId());
        span.setHorizontalLength(180.0);
        span.addIssue(new com.plot.plugin.powerline.engineering.SimplePowerLineIssue(
            EngineeringRuleIds.SPAN_MAXIMUM,
            com.plot.plugin.powerline.engineering.PowerLineIssueSeverity.ERROR,
            "span too long",
            null,
            180.0,
            120.0));

        PowerLineValidationReport report = new PowerLineValidationReport();
        report.addSpan(span);

        LineOptimizationEngine.PowerLineGeometrySites sites =
            new LineOptimizationEngine.PowerLineGeometrySites(List.of(start, end));

        OptimizationResult result = LineOptimizationEngine.propose(
            report,
            sites,
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(400, 0))),
            6.0,
            2.0,
            null);

        assertFalse(result.getActions().isEmpty());
        assertEquals(OptimizationActionType.INSERT_POLE, result.getActions().getFirst().getType());
        assertEquals(310.0, result.getActions().getFirst().getStationing(), 0.001);
    }
}
