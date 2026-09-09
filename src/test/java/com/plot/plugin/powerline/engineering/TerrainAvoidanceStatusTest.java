package com.plot.plugin.powerline.engineering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TerrainAvoidanceStatusTest {

    @Test
    void maxAttemptsWithRemainingIssuesReportsPartialNotFullSuccess() {
        String message = TerrainAvoidance.resolveStatusMessage(3, 1);
        assertNotNull(message);
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.partially_fixed", 3, 1),
            message);
        assertNotEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.auto_fixed"),
            message);
    }

    @Test
    void noRemainingIssuesAfterFixesReportsFixed() {
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.auto_fixed"),
            TerrainAvoidance.resolveStatusMessage(2, 0));
    }

    @Test
    void remainingIssuesWithNoFixesReportsManual() {
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.manual_needed"),
            TerrainAvoidance.resolveStatusMessage(0, 2));
    }

    @Test
    void noFixesAndNoIssuesYieldsNoStatus() {
        assertNull(TerrainAvoidance.resolveStatusMessage(0, 0));
    }
}
