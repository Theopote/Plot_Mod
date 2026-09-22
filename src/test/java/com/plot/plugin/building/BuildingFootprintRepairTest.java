package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFootprintRepairTest {

    @Test
    void removesConsecutiveDuplicatesAndClosingPoint() {
        List<Vec2d> raw = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8),
            new Vec2d(0, 0)
        );
        BuildingFootprintRepair.RepairResult repair = BuildingFootprintRepair.repair(raw);
        assertTrue(repair.repaired());
        assertEquals(4, repair.points().size());
        assertTrue(BuildingFootprintValidator.validate(raw).valid());
    }

    @Test
    void removesCollinearMiddleVertex() {
        List<Vec2d> raw = List.of(
            new Vec2d(0, 0),
            new Vec2d(5, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        );
        BuildingFootprintRepair.RepairResult repair = BuildingFootprintRepair.repair(raw);
        assertTrue(repair.repaired());
        assertEquals(4, repair.points().size());
        assertTrue(BuildingFootprintValidator.validate(raw).valid());
    }

    @Test
    void collapsesMicroSerrationFromShortEdges() {
        List<Vec2d> raw = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10.0005, 0.0002),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        );
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(raw);
        assertTrue(result.valid());
        assertTrue(result.repaired());
        assertEquals(4, result.cleanedPoints().size());
    }

    @Test
    void normalizesClockwiseRingToCounterClockwise() {
        List<Vec2d> cw = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 8),
            new Vec2d(10, 8),
            new Vec2d(10, 0)
        );
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(cw);
        assertTrue(result.valid());
        assertTrue(result.repaired());
        assertTrue(PolygonUtils.isCounterClockwise(result.cleanedPoints()));
    }

    @Test
    void doesNotRepairSelfIntersection() {
        List<Vec2d> bowTie = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4),
            new Vec2d(4, 0)
        );
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(bowTie);
        assertFalse(result.valid());
        assertTrue(
            result.reason() == BuildingFootprintValidator.RejectReason.SELF_INTERSECTION
                || result.reason() == BuildingFootprintValidator.RejectReason.DEGENERATE_AREA);
    }

    @Test
    void doesNotRepairTooFewVertices() {
        BuildingFootprintValidator.Result result = BuildingFootprintValidator.validate(List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0)
        ));
        assertFalse(result.valid());
        assertEquals(BuildingFootprintValidator.RejectReason.TOO_FEW_VERTICES, result.reason());
    }

    @Test
    void removeShortEdgesPeelsSpikeUntilStable() {
        List<Vec2d> points = new ArrayList<>(List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0),
            new Vec2d(1.0002, 0.0001),
            new Vec2d(2, 0),
            new Vec2d(2, 2),
            new Vec2d(0, 2)
        ));
        List<Vec2d> repaired = BuildingFootprintRepair.removeShortEdges(
            points, BuildingFootprintRepair.MIN_EDGE_LENGTH);
        assertTrue(repaired.size() < points.size());
        assertTrue(repaired.size() >= 3);
    }
}
