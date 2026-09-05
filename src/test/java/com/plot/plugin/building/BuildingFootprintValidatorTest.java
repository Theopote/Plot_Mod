package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFootprintValidatorTest {

    @Test
    void acceptsSimpleRectangle() {
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ));
        assertTrue(result.valid());
        assertEquals(4, result.cleanedPoints().size());
    }

    @Test
    void stripsClosingDuplicateVertex() {
        List<Vec2d> closed = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8),
            new Vec2d(0, 0)
        );
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(closed);
        assertTrue(result.valid());
        assertEquals(4, result.cleanedPoints().size());
    }

    @Test
    void rejectsTooFewVertices() {
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0)
        ));
        assertFalse(result.valid());
        assertEquals(BuildingFootprintValidator.RejectReason.TOO_FEW_VERTICES, result.reason());
    }

    @Test
    void rejectsZeroArea() {
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(5, 0)
        ));
        assertFalse(result.valid());
        assertEquals(BuildingFootprintValidator.RejectReason.DEGENERATE_AREA, result.reason());
    }

    @Test
    void rejectsSelfIntersection() {
        // bow-tie：可能被标为 self_intersection 或因正负面积抵消被标为 degenerate_area
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4),
            new Vec2d(4, 0)
        ));
        assertFalse(result.valid());
        assertTrue(
            result.reason() == BuildingFootprintValidator.RejectReason.SELF_INTERSECTION
                || result.reason() == BuildingFootprintValidator.RejectReason.DEGENERATE_AREA,
            "bow-tie should be rejected, got " + result.reason());
    }

    @Test
    void rejectsDuplicateInteriorVertices() {
        List<Vec2d> points = new ArrayList<>();
        points.add(new Vec2d(0, 0));
        points.add(new Vec2d(10, 0));
        points.add(new Vec2d(10, 0)); // duplicate
        points.add(new Vec2d(10, 8));
        points.add(new Vec2d(0, 8));
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(points);
        assertFalse(result.valid());
        assertEquals(BuildingFootprintValidator.RejectReason.DUPLICATE_VERTICES, result.reason());
    }
}
