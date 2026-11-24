package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.corridor.ColumnDesignElevationPlanner;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.corridor.JunctionElevationField;
import com.plot.plugin.road.pipeline.corridor.JunctionFilletElevationLoft;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.JunctionWorldFootprint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnDesignElevationPlannerTest {

    @Test
    void connectedSameLayerNegotiatesMaxBeforeSeam() {
        Fixture fixture = fixture(64, 66, false);
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()));

        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put(a.getId(), fixture.low());
        junctions.put(b.getId(), fixture.high());

        ColumnDesignElevationPlanner.ColumnElevationPlan plan =
            ColumnDesignElevationPlanner.plan(junctions, network);

        assertEquals(66, plan.designY(a.getId(), fixture.column(), 64));
        assertEquals(66, plan.designY(b.getId(), fixture.column(), 66));
        assertTrue(plan.preservedYs(fixture.column()).contains(66));
        assertEquals(1, plan.preservedYs(fixture.column()).size());
    }

    @Test
    void gradeSeparatedKeepsBothDesignYs() {
        Fixture fixture = fixture(64, 66, true);
        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put("low", fixture.low());
        junctions.put("high", fixture.high());

        ColumnDesignElevationPlanner.ColumnElevationPlan plan =
            ColumnDesignElevationPlanner.plan(junctions, null);

        assertEquals(64, plan.designY("low", fixture.column(), -1));
        assertEquals(66, plan.designY("high", fixture.column(), -1));
        assertTrue(plan.preservedYs(fixture.column()).contains(64));
        assertTrue(plan.preservedYs(fixture.column()).contains(66));
    }

    @Test
    void unconnectedOverlapKeepsBothWithoutNetwork() {
        Fixture fixture = fixture(64, 66, false);
        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put("a", fixture.low());
        junctions.put("b", fixture.high());

        ColumnDesignElevationPlanner.ColumnElevationPlan plan =
            ColumnDesignElevationPlanner.plan(junctions, null);

        assertEquals(64, plan.designY("a", fixture.column(), -1));
        assertEquals(66, plan.designY("b", fixture.column(), -1));
    }

    private record Fixture(
            ColumnKey column,
            JunctionBlocks low,
            JunctionBlocks high) {
    }

    private static Fixture fixture(int lowY, int highY, boolean elevatedDeck) {
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(3, 8),
            new Vec2d(8, 3),
            8.0,
            Math.PI / 2.0,
            FilletArcSweep.resolveShort(new Vec2d(8, 8), new Vec2d(3, 8), new Vec2d(8, 3)));
        JunctionGeometryResult geometry = new JunctionGeometryResult(
            List.of(new Vec2d(-6, -6), new Vec2d(20, -6), new Vec2d(20, 20), new Vec2d(-6, 20)),
            List.of(new Vec2d(-4, -4), new Vec2d(16, -4), new Vec2d(16, 16), new Vec2d(-4, 16)),
            List.of(),
            8.0,
            8.0,
            List.of(fillet),
            List.of(new Vec2d(-2, -2), new Vec2d(10, -2), new Vec2d(10, 10), new Vec2d(-2, 10)),
            List.of());
        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };
        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        ColumnKey column = footprint.filletAdditions().iterator().next();
        JunctionElevationField lowField = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(lowY), columns, lowY);
        JunctionElevationField highField = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(highY), columns, highY);
        Integer deckY = elevatedDeck ? 80 : null;

        JunctionBlocks low = new JunctionBlocks();
        low.setElevationField(lowField);
        low.setValidationManifest(manifest("low", column, lowY, deckY));
        JunctionBlocks high = new JunctionBlocks();
        high.setElevationField(highField);
        high.setValidationManifest(manifest("high", column, highY, deckY));
        return new Fixture(column, low, high);
    }

    private static JunctionValidationManifest manifest(
            String nodeId, ColumnKey column, int junctionY, Integer elevatedDeckY) {
        return new JunctionValidationManifest(
            nodeId,
            List.of(),
            List.of(new net.minecraft.util.math.BlockPos(column.worldX(), 70, column.worldZ())),
            List.of(),
            List.of(),
            junctionY,
            elevatedDeckY,
            0,
            0);
    }
}
