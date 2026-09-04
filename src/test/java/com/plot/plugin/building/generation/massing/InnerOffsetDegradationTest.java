package com.plot.plugin.building.generation.massing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InnerOffsetDegradationTest {

    @Test
    void wallMassCellFillsOuterWhenInnerOffsetFails() {
        List<Vec2d> outerPoints = List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 2),
            new Vec2d(0, 2)
        );
        Polygon outer = BuildingGeometryUtils.toPolygon(outerPoints);
        Vec2d interior = new Vec2d(6, 1);

        assertTrue(InnerOffsetDegradation.isWallMassCell(outer, null, interior));
        assertFalse(InnerOffsetDegradation.isInteriorCell(null, interior));
        assertFalse(InnerOffsetDegradation.hasInteriorSpace(null));
    }

    @Test
    void wallMassCellExcludesInteriorWhenInnerOffsetSucceeds() {
        List<Vec2d> outerPoints = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 6),
            new Vec2d(0, 6)
        );
        Polygon outer = BuildingGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> innerPoints = BuildingGeometryUtils.offsetInward(outerPoints, 1);
        Polygon inner = BuildingGeometryUtils.toPolygon(innerPoints);
        Vec2d interior = new Vec2d(5, 3);
        Vec2d wallRing = new Vec2d(0.5, 0.5);

        assertFalse(InnerOffsetDegradation.isWallMassCell(outer, inner, interior));
        assertTrue(InnerOffsetDegradation.isInteriorCell(inner, interior));
        assertTrue(InnerOffsetDegradation.isWallMassCell(outer, inner, wallRing));
    }

    @Test
    void noteInnerOffsetFailureIsIdempotent() {
        com.plot.plugin.building.generation.BuildingGenerationResult result =
            new com.plot.plugin.building.generation.BuildingGenerationResult();
        InnerOffsetDegradation.noteInnerOffsetFailure(result);
        InnerOffsetDegradation.noteInnerOffsetFailure(result);
        assertEquals(1, result.warnings.size());
        assertEquals("plugin.building.warn.inner_offset_failed", result.warnings.getFirst());
    }
}
