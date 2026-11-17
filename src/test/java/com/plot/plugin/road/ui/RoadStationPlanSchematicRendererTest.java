package com.plot.plugin.road.ui;

import imgui.ImVec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStationPlanSchematicRendererTest {

    @Test
    void nearestFractionMapsClickOntoPolyline() {
        List<ImVec2> screen = List.of(
            new ImVec2(0f, 0f),
            new ImVec2(100f, 0f));
        float mid = RoadStationPlanSchematicRenderer.nearestFractionOnPolyline(screen, 50f, 3f);
        assertEquals(0.5f, mid, 1e-3f);
        float start = RoadStationPlanSchematicRenderer.nearestFractionOnPolyline(screen, -5f, 0f);
        assertTrue(start <= 1e-3f);
        float end = RoadStationPlanSchematicRenderer.nearestFractionOnPolyline(screen, 105f, 0f);
        assertTrue(end >= 1f - 1e-3f);
    }
}
