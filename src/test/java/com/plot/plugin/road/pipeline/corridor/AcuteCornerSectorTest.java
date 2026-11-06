package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcuteCornerSectorTest {

    @Test
    void ninetyDegreeFanEmitsMultipleNormals() {
        List<Vec2d> normals = new ArrayList<>();
        AcuteCornerSector.forEachNormal(
            new Vec2d(0, 1),
            new Vec2d(-1, 0),
            3.0,
            normals::add);
        assertTrue(normals.size() >= 3, "90° sector must emit intermediate normals");
        assertEquals(0.0, normals.getFirst().x, 1e-9);
        assertEquals(1.0, normals.getFirst().y, 1e-9);
        assertEquals(-1.0, normals.getLast().x, 1e-9);
        assertEquals(0.0, normals.getLast().y, 1e-9);
    }

    @Test
    void insignificantTurnDoesNotFan() {
        List<Vec2d> normals = new ArrayList<>();
        AcuteCornerSector.forEachNormal(
            new Vec2d(0, 1),
            new Vec2d(0.05, 1).normalize(),
            3.0,
            normals::add);
        assertEquals(1, normals.size());
        assertFalse(AcuteCornerSector.needsAngularFan(Math.toRadians(10)));
        assertTrue(AcuteCornerSector.needsAngularFan(Math.toRadians(30)));
    }

    @Test
    void skipRadiusMatchesTunnelConvention() {
        assertEquals(1.25, AcuteCornerSector.skipRadius(1.0), 1e-9);
        assertEquals(2.5, AcuteCornerSector.skipRadius(2.0), 1e-9);
    }
}
