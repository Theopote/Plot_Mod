package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadStatus;
import com.plot.utils.PlotI18n;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPanelScopedStatusTest {
    @Test
    void lampAndFloatingEditorFeedbackHiddenFromToolbar() {
        assertTrue(RoadPanelScopedStatus.shouldHideFromToolbar(
            RoadStatus.of(RoadStatus.Severity.WARNING, PlotI18n.tr("plugin.road.lamp.custom_duplicate"))));
        assertTrue(RoadPanelScopedStatus.shouldHideFromToolbar(
            RoadStatus.of(RoadStatus.Severity.INFO, PlotI18n.tr("plugin.road.lamp.custom_saved"))));
        assertTrue(RoadPanelScopedStatus.shouldHideFromToolbar(
            RoadStatus.of(RoadStatus.Severity.WARNING,
                PlotI18n.tr("plugin.road.floating_editor.invalid_draft"))));
    }

    @Test
    void globalPlacementFeedbackStaysOnToolbar() {
        assertFalse(RoadPanelScopedStatus.shouldHideFromToolbar(
            RoadStatus.of(RoadStatus.Severity.SUCCESS, "道路生成完成")));
        assertFalse(RoadPanelScopedStatus.shouldHideFromToolbar(
            RoadStatus.of(RoadStatus.Severity.WARNING,
                PlotI18n.tr("plugin.road.floating_editor.road_missing"))));
    }
}
