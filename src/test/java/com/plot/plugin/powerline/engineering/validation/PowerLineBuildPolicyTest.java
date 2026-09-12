package com.plot.plugin.powerline.engineering.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineBuildPolicyTest {

    @Test
    void blocksBuildWhenEngineeringErrorsPresent() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setLineChecksEnabled(true);
        PowerLineValidationReport report = new PowerLineValidationReport();
        report.addIssue(new SimplePowerLineIssue(
            "span.maximum",
            PowerLineIssueSeverity.ERROR,
            "span too long",
            null,
            0,
            0));

        assertTrue(PowerLineBuildPolicy.hasBlockingIssues(line, report, null));
    }

    @Test
    void warningsDoNotBlockBuild() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setLineChecksEnabled(true);
        PowerLineValidationReport report = new PowerLineValidationReport();
        report.addIssue(new SimplePowerLineIssue(
            "sag.maximum",
            PowerLineIssueSeverity.WARNING,
            "sag high",
            null,
            0,
            0));

        assertFalse(PowerLineBuildPolicy.hasBlockingIssues(line, report, null));
    }

    @Test
    void disabledChecksDoNotBlockBuild() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setLineChecksEnabled(false);
        PowerLineValidationReport report = new PowerLineValidationReport();
        report.addIssue(new SimplePowerLineIssue(
            "span.maximum",
            PowerLineIssueSeverity.ERROR,
            "span too long",
            null,
            0,
            0));

        assertFalse(PowerLineBuildPolicy.hasBlockingIssues(line, report, null));
    }
}
