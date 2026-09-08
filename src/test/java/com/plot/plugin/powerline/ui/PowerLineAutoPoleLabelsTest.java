package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineAutoPoleLabelsTest {

    @Test
    void mapsTerrainReasonKey() {
        PoleLayoutConstraint constraint = new PoleLayoutConstraint(
            42.5,
            "plugin.powerline.route.auto_pole.reason.terrain");
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.route.auto_pole.reason.terrain"),
            PowerLineAutoPoleLabels.friendlyReason(constraint));
    }

    @Test
    void mapsLegacyTerrainReason() {
        PoleLayoutConstraint constraint = new PoleLayoutConstraint(42.5, "terrain avoidance");
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.route.auto_pole.reason.terrain"),
            PowerLineAutoPoleLabels.friendlyReason(constraint));
    }

    @Test
    void mapsEngineeringInsertPoleKeyToSpanReason() {
        PoleLayoutConstraint constraint = new PoleLayoutConstraint(
            78.0,
            "plugin.powerline.engineering.reason.insert_pole");
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.route.auto_pole.reason.span"),
            PowerLineAutoPoleLabels.friendlyReason(constraint));
    }
}
