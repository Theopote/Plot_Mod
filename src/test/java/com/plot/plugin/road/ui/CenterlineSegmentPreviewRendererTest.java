package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import imgui.ImVec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenterlineSegmentPreviewRendererTest {

    @Test
    void pointAtFractionHitsEndpointsAndMidpoint() {
        List<Vec2d> points = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
        Vec2d start = CenterlineSegmentPreviewRenderer.pointAtFraction(points, 0f);
        Vec2d mid = CenterlineSegmentPreviewRenderer.pointAtFraction(points, 0.5f);
        Vec2d end = CenterlineSegmentPreviewRenderer.pointAtFraction(points, 1f);
        assertNotNull(start);
        assertNotNull(mid);
        assertNotNull(end);
        assertEquals(0.0, start.x, 1e-6);
        assertEquals(50.0, mid.x, 1e-6);
        assertEquals(100.0, end.x, 1e-6);
    }

    @Test
    void nearestFractionOnStraightScreenPolyline() {
        List<ImVec2> screen = List.of(new ImVec2(0, 0), new ImVec2(100, 0));
        float mid = CenterlineSegmentPreviewRenderer.nearestFractionOnPolyline(screen, 50f, 3f);
        assertTrue(mid > 0.45f && mid < 0.55f, "mid=" + mid);
        float start = CenterlineSegmentPreviewRenderer.nearestFractionOnPolyline(screen, -5f, 0f);
        assertTrue(start < 0.05f, "start=" + start);
    }

    @Test
    void hitInteriorPiPrefersNearestVertexWithinRadius() {
        List<ImVec2> screen = List.of(
            new ImVec2(0, 0),
            new ImVec2(40, 0),
            new ImVec2(80, 20),
            new ImVec2(120, 0));
        assertEquals(1, CenterlineSegmentPreviewRenderer.hitInteriorPi(screen, 42f, 2f));
        assertEquals(2, CenterlineSegmentPreviewRenderer.hitInteriorPi(screen, 81f, 18f));
        assertEquals(0, CenterlineSegmentPreviewRenderer.hitInteriorPi(screen, 0f, 0f));
        assertEquals(0, CenterlineSegmentPreviewRenderer.hitInteriorPi(screen, 200f, 200f));
    }
}
