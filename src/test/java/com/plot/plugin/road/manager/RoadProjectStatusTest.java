package com.plot.plugin.road.manager;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.plugin.road.ui.RoadStatusUi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProjectStatusTest {

    @Test
    void storesSeverityAndMessage() {
        RoadProjectStatus status = new RoadProjectStatus();
        status.error("Preview failed");

        assertEquals(RoadStatus.Severity.ERROR, status.getStatus().severity());
        assertEquals("Preview failed", status.getStatus().message());
        assertEquals("Preview failed", status.get());
    }

    @Test
    void convenienceMethodsSetExpectedSeverity() {
        RoadProjectStatus status = new RoadProjectStatus();

        status.success("Done");
        assertEquals(RoadStatus.Severity.SUCCESS, status.getStatus().severity());

        status.warning("Empty network");
        assertEquals(RoadStatus.Severity.WARNING, status.getStatus().severity());

        status.progress("Building…");
        assertEquals(RoadStatus.Severity.PROGRESS, status.getStatus().severity());
    }

    @Test
    void toolbarMapsSeverityToDistinctColors() {
        assertEquals(PluginUiColors.STATUS_OK, RoadStatusUi.colorFor(RoadStatus.Severity.SUCCESS));
        assertEquals(PluginUiColors.ERROR, RoadStatusUi.colorFor(RoadStatus.Severity.ERROR));
        assertEquals(PluginUiColors.WARNING, RoadStatusUi.colorFor(RoadStatus.Severity.WARNING));
        assertEquals(PluginUiColors.STATUS_INFO, RoadStatusUi.colorFor(RoadStatus.Severity.PROGRESS));
    }

    @Test
    void prefixesIncludeIconGlyphs() {
        assertTrue(RoadStatusUi.formatLabel(RoadStatus.of(RoadStatus.Severity.ERROR, "Failed")).startsWith("\u2717"));
        assertTrue(RoadStatusUi.formatLabel(RoadStatus.of(RoadStatus.Severity.SUCCESS, "OK")).startsWith("\u2713"));
    }
}
