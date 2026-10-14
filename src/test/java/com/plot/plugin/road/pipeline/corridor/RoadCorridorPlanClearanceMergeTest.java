package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorPlanClearanceMergeTest {

    @Test
    void structureInteriorOutranksOpenRoadEvenWithLowerClearTop() {
        ClearanceCell open = new ClearanceCell(
            new Vec2d(10, 0), 64, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD);
        ClearanceCell tunnel = new ClearanceCell(
            new Vec2d(10, 0), 64, 72,
            RoadCorridorConstructionMode.TUNNEL,
            RoadConstructionType.TUNNEL);

        ClearanceCell merged = RoadCorridorPlan.mergeClearance(open, tunnel);
        assertEquals(RoadConstructionType.TUNNEL, merged.constructionType());
        assertEquals(RoadCorridorConstructionMode.TUNNEL, merged.mode());
        assertEquals(80, merged.clearTopY(), "envelope height must expand to the larger clearTop");
    }

    @Test
    void openRoadCannotOverwriteExistingBridgeClearance() {
        ClearanceCell bridge = new ClearanceCell(
            new Vec2d(15, 0), 64, 65,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE);
        ClearanceCell open = new ClearanceCell(
            new Vec2d(15, 0), 64, 90,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.CUT);

        ClearanceCell merged = RoadCorridorPlan.mergeClearance(bridge, open);
        assertEquals(RoadConstructionType.BRIDGE, merged.constructionType());
        assertEquals(90, merged.clearTopY());
    }

    @Test
    void transitionOutranksNormalButYieldsToStructureInterior() {
        ClearanceCell abutment = new ClearanceCell(
            new Vec2d(8, 0), 64, 70,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE_ABUTMENT);
        ClearanceCell road = new ClearanceCell(
            new Vec2d(8, 0), 64, 75,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD);
        ClearanceCell deck = new ClearanceCell(
            new Vec2d(8, 0), 64, 66,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE);

        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT,
            RoadCorridorPlan.mergeClearance(road, abutment).constructionType());
        assertEquals(RoadConstructionType.BRIDGE,
            RoadCorridorPlan.mergeClearance(abutment, deck).constructionType());
    }

    @Test
    void putClearanceStripsSlopeWhenStructureWins() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(12, 0);
        plan.slopeCellOrCreate(key, new Vec2d(12, 0)).mergeFill(55, "minecraft:dirt");
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(12, 0), 64, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        assertTrue(plan.slopeCell(key) != null && plan.slopeCell(key).hasFill());

        plan.putClearance(key, new ClearanceCell(
            new Vec2d(12, 0), 64, 70,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        assertNull(plan.slopeCell(key));
        assertEquals(RoadConstructionType.BRIDGE, plan.clearanceCell(key).constructionType());
    }

    @Test
    void sameRankStillPrefersHigherClearTop() {
        ClearanceCell low = new ClearanceCell(
            new Vec2d(0, 0), 64, 66,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD);
        ClearanceCell high = new ClearanceCell(
            new Vec2d(0, 0), 64, 70,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL);
        assertEquals(70, RoadCorridorPlan.mergeClearance(low, high).clearTopY());
        assertEquals(RoadConstructionType.FILL,
            RoadCorridorPlan.mergeClearance(low, high).constructionType());
    }
}
