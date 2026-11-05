package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.*;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;

import static org.junit.jupiter.api.Assertions.*;

class RoadSurfacePlacementPassTest {
    @Test
    void pavementWinsOverTunnelAliasingAndRetainsOriginalUndoState() {
        var plan = plan();
        for (String obstructingMaterial : List.of("minecraft:air", "minecraft:stone_bricks")) {
            var result = new RoadGenerationResult(0);
            var surface = new BlockPos(3, 69, -3);
            result.recordPlacementByLayerPriority(surface, "minecraft:grass_block", obstructingMaterial,
                RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
            RoadSurfacePlacementPass.apply(result, plan, 5);
            assertEquals("minecraft:white_concrete", result.placementRecords.get(surface).newBlockId);
            assertEquals("minecraft:grass_block", result.placementRecords.get(surface).previousBlockId);
            assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(surface));
        }
    }

    @Test
    void roadMarkingsSurviveAndMissingSurfaceMaskIsStamped() {
        var result = new RoadGenerationResult(0);
        RoadSurfacePlacementPass.apply(
            result, plan(), 5, GradeSeparationBuildContext.NONE, pos -> "minecraft:grass_block");
        var surface = new BlockPos(3, 69, -3);
        assertEquals("minecraft:white_concrete", result.placementRecords.get(surface).newBlockId);
        assertEquals("minecraft:grass_block", result.placementRecords.get(surface).previousBlockId);
        assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(surface));

        result.recordPlacementByLayerPriority(surface, "minecraft:stone", "minecraft:yellow_concrete",
            RoadSolidLayer.MARKING, RoadSolidMaterialPriority.DEFAULT);
        RoadSurfacePlacementPass.apply(result, plan(), 5);
        assertEquals("minecraft:yellow_concrete", result.placementRecords.get(surface).newBlockId);
    }

    @Test
    void mergedApproachsFillCannotOccupyReservedRoadHeadroom() {
        for (boolean roadFirst : new boolean[]{true, false}) {
            var road = new RoadGenerationResult(0);
            var surface = new BlockPos(3, 69, -3);
            road.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
                RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
            RoadSurfacePlacementPass.apply(road, plan(), 5);
            var other = new RoadGenerationResult(0);
            other.recordPlacementByLayerPriority(surface.up(2), "minecraft:air", "minecraft:coarse_dirt",
                RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
            // Outside reserved headroom (surface+1..surface+5)
            other.recordPlacementByLayerPriority(surface.up(6), "minecraft:air", "minecraft:stone",
                RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
            var merged = new RoadGenerationResult(0);
            merged.mergeFrom(roadFirst ? road : other);
            merged.mergeFrom(roadFirst ? other : road);
            RoadSurfacePlacementPass.clearIntrudingFill(merged);
            assertEquals("minecraft:air", merged.placementRecords.get(surface.up(2)).newBlockId);
            assertEquals("minecraft:stone", merged.placementRecords.get(surface.up(6)).newBlockId);
        }
    }

    @Test
    void clearIntrudingFillPreservesElevatedDeckUnderside() {
        var underpass = new RoadGenerationResult(0);
        var surface = new BlockPos(0, 64, 0);
        underpass.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        // Headroom reserved up to underside of deck (68)
        underpass.openRoadClearanceTops.put(surface, 68);

        var elevated = new RoadGenerationResult(0);
        elevated.recordPlacementByLayerPriority(surface.up(4), "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
        elevated.recordPlacementByLayerPriority(surface.up(5), "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);

        var merged = new RoadGenerationResult(0);
        merged.mergeFrom(underpass);
        merged.mergeFrom(elevated);
        RoadSurfacePlacementPass.clearIntrudingFill(merged);

        assertEquals("minecraft:stone_bricks",
            merged.placementRecords.get(surface.up(4)).newBlockId,
            "elevated BRIDGE underside must survive underpass headroom clear");
        assertEquals("minecraft:white_concrete",
            merged.placementRecords.get(surface.up(5)).newBlockId,
            "elevated ROAD deck must remain");
    }

    @Test
    void clearIntrudingFillPreservesElevatedSubgradeUnderside() {
        var underpass = new RoadGenerationResult(0);
        var surface = new BlockPos(0, 64, 0);
        underpass.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        underpass.openRoadClearanceTops.put(surface, 68);

        var elevated = new RoadGenerationResult(0);
        elevated.recordPlacementByLayerPriority(surface.up(4), "minecraft:air", "minecraft:dirt",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
        elevated.recordPlacementByLayerPriority(surface.up(5), "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);

        var merged = new RoadGenerationResult(0);
        merged.mergeFrom(underpass);
        merged.mergeFrom(elevated);
        RoadSurfacePlacementPass.clearIntrudingFill(merged);

        assertEquals("minecraft:dirt",
            merged.placementRecords.get(surface.up(4)).newBlockId,
            "elevated SUBGRADE underside must survive underpass headroom clear");
    }

    @Test
    void underpassPavementSealFillsMissingCrossUnderElevatedDeck() {
        var result = new RoadGenerationResult(0);
        BlockPos paved = new BlockPos(0, 64, 0);
        BlockPos hole = new BlockPos(1, 64, 0);
        result.recordPlacementByLayerPriority(
            paved, "minecraft:grass_block", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.openRoadClearanceTops.put(paved, 68);
        result.openRoadClearanceTops.put(hole, 68);
        // 上层路面投影覆盖空洞柱；空洞本身无下层路面记录 → 原地形会透出
        result.recordPlacementByLayerPriority(
            hole.up(6), "minecraft:air", "minecraft:dirt_path",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(
            paved.up(6), "minecraft:air", "minecraft:dirt_path",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);

        UnderpassPavementSealPass.apply(result);

        assertEquals("minecraft:white_concrete",
            result.placementRecords.get(hole).newBlockId,
            "missing underpass pavement under elevated deck must be sealed");
        assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(hole));
    }

    @Test
    void pavementYIndexFindsLowestDeckAboveSurface() {
        var result = new RoadGenerationResult(0);
        result.recordPlacementByLayerPriority(new BlockPos(1, 64, 2), "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(new BlockPos(1, 70, 2), "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(new BlockPos(1, 72, 2), "minecraft:air", "minecraft:yellow_concrete",
            RoadSolidLayer.MARKING, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(new BlockPos(1, 68, 2), "minecraft:air", "minecraft:dirt",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
        Map<ColumnKey, NavigableSet<Integer>> index = RoadSurfacePlacementPass.buildPavementYIndex(result);
        assertEquals(70, RoadSurfacePlacementPass.lowestProtectedPavementAbove(index, 1, 64, 2));
        assertNull(RoadSurfacePlacementPass.lowestProtectedPavementAbove(index, 1, 72, 2));
        assertNull(RoadSurfacePlacementPass.lowestProtectedPavementAbove(index, 9, 64, 9));
    }

    @Test
    void clearIntrudingFillScalesOnLargeSyntheticNetwork() {
        int columns = 5_000;
        int fillersPerColumn = 20;
        var result = new RoadGenerationResult(0);
        for (int i = 0; i < columns; i++) {
            BlockPos surface = new BlockPos(i, 64, 0);
            result.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
                RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
            result.openRoadClearanceTops.put(surface, 69);
            for (int dy = 1; dy <= fillersPerColumn; dy++) {
                BlockPos filler = new BlockPos(i, 64 + dy, 0);
                result.recordPlacementByLayerPriority(filler, "minecraft:air", "minecraft:coarse_dirt",
                    RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
            }
            // Elevated deck above headroom so underside protection path is exercised
            result.recordPlacementByLayerPriority(new BlockPos(i, 72, 0), "minecraft:air", "minecraft:white_concrete",
                RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
            result.recordPlacementByLayerPriority(new BlockPos(i, 71, 0), "minecraft:air", "minecraft:stone",
                RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
        }
        assertTrue(result.placementLayers.size() >= 100_000,
            "synthetic fixture must be large enough to catch O(S*P) regressions");

        long start = System.nanoTime();
        RoadSurfacePlacementPass.clearIntrudingFill(result);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        // Indexed path should finish well under a second even on constrained CI; 5s is a hard ceiling.
        assertTrue(elapsedMs < 5_000,
            "clearIntrudingFill took " + elapsedMs + "ms on ~" + result.placementLayers.size() + " placements");

        Map<String, Integer> cleared = new HashMap<>();
        for (int i = 0; i < columns; i++) {
            assertEquals("minecraft:air",
                result.placementRecords.get(new BlockPos(i, 66, 0)).newBlockId);
            assertEquals("minecraft:stone",
                result.placementRecords.get(new BlockPos(i, 71, 0)).newBlockId,
                "elevated underside must survive at column " + i);
            cleared.merge("ok", 1, Integer::sum);
        }
        assertEquals(columns, cleared.get("ok"));
    }

    private static RoadCorridorPlan plan() {
        var plan = new RoadCorridorPlan();
        var key = new ColumnKey(3, -3);
        plan.surfaceMask().put(key, new SurfaceCell(new Vec2d(3, -3), 69,
            RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(new Vec2d(3, -3), 69, 70,
            RoadCorridorConstructionMode.OPEN, com.plot.plugin.road.RoadConstructionType.FILL));
        return plan;
    }
}
