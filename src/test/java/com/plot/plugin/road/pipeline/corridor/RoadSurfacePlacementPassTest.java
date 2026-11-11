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
    void raisedCurbIsNotAnOverpassAndMustNotReplacePavementBelow() {
        var result = new RoadGenerationResult(0);
        var road = new BlockPos(0, 64, 0);
        var curb = road.up();
        result.recordPlacementByLayerPriority(road, "minecraft:air", "minecraft:black_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(curb, "minecraft:air", "minecraft:stone_slab",
            RoadSolidLayer.SIDEWALK, RoadSolidMaterialPriority.DEFAULT);
        RoadSurfacePlacementPass.clearIntrudingFill(result);
        RoadSurfacePlacementPass.sealElevatedDeckSupport(result, 0, 65, 0);
        assertEquals("minecraft:black_concrete", result.placementRecords.get(road).newBlockId);
        assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(road));
        assertNull(RoadSurfacePlacementPass.lowestProtectedPavementAbove(
            RoadSurfacePlacementPass.buildPavementYIndex(result), 0, 64, 0));
    }

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
    void sealElevatedDeckSupportKeepsTunnelLiningAndInheritsNearbyBridge() {
        var result = new RoadGenerationResult(0);
        BlockPos pavement = new BlockPos(0, 70, 0);
        BlockPos support = pavement.down();
        BlockPos neighborSupport = support.east();
        result.recordPlacementByLayerPriority(pavement, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(neighborSupport, "minecraft:air", "minecraft:andesite",
            RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
        // Missing support under pavement — should inherit andesite from neighbor.
        RoadSurfacePlacementPass.sealElevatedDeckSupport(result, 0, 70, 0);
        assertEquals("minecraft:andesite", result.placementRecords.get(support).newBlockId);

        // Tunnel lining under another deck column must not be overwritten.
        BlockPos tunnelPavement = new BlockPos(5, 70, 0);
        BlockPos tunnelSupport = tunnelPavement.down();
        result.recordPlacementByLayerPriority(tunnelPavement, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(tunnelSupport, "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        RoadSurfacePlacementPass.sealElevatedDeckSupport(result, 5, 70, 0);
        assertEquals("minecraft:stone_bricks", result.placementRecords.get(tunnelSupport).newBlockId);
        assertEquals(RoadSolidLayer.TUNNEL, result.placementLayers.get(tunnelSupport));
    }

    @Test
    void clearIntrudingFillReplacesTerrainSubgradeUnderElevatedDeck() {
        var underpass = new RoadGenerationResult(0);
        var surface = new BlockPos(0, 64, 0);
        underpass.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        underpass.openRoadClearanceTops.put(surface, 68);

        var elevated = new RoadGenerationResult(0);
        // 十字形残留：泥土 SUBGRADE 贴在上层路面底部
        elevated.recordPlacementByLayerPriority(surface.up(4), "minecraft:air", "minecraft:dirt",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
        elevated.recordPlacementByLayerPriority(surface.up(5), "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);

        var merged = new RoadGenerationResult(0);
        merged.mergeFrom(underpass);
        merged.mergeFrom(elevated);
        RoadSurfacePlacementPass.clearIntrudingFill(merged);

        assertEquals("minecraft:stone",
            merged.placementRecords.get(surface.up(4)).newBlockId,
            "dirt/grass SUBGRADE under elevated deck must become bridge deck underlay (not tunnel lining bricks)");
        assertEquals(RoadSolidLayer.BRIDGE, merged.placementLayers.get(surface.up(4)));
        assertEquals(JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_BLOCK,
            merged.placementRecords.get(surface.up(4)).newBlockId);
    }

    @Test
    void clearIntrudingFillRemovesCrossTerrainOnElevatedOverhang() {
        // 上层比下层宽：悬挑柱没有 openRoadClearanceTops，仍须清掉贴底泥土
        var result = new RoadGenerationResult(0);
        BlockPos lower = new BlockPos(0, 64, 0);
        BlockPos overhang = new BlockPos(3, 64, 0);
        result.recordPlacementByLayerPriority(lower, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(lower.up(5), "minecraft:air", "minecraft:dirt_path",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(lower.up(4), "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
        // 悬挑：只有上层路面 + 泥土贴底，无下层路面记录
        result.recordPlacementByLayerPriority(overhang.up(5), "minecraft:air", "minecraft:dirt_path",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(overhang.up(4), "minecraft:grass_block", "minecraft:grass_block",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
        result.recordPlacementByLayerPriority(overhang.up(3), "minecraft:dirt", "minecraft:dirt",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
        // 人为制造“上下层同柱”以便悬挑柱进入 stacked pavement 路径
        result.recordPlacementByLayerPriority(overhang, "minecraft:air", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);

        RoadSurfacePlacementPass.clearIntrudingFill(result);

        assertEquals("minecraft:stone_bricks",
            result.placementRecords.get(overhang.up(4)).newBlockId,
            "must inherit nearby elevated BRIDGE underlay material");
        assertEquals("minecraft:air",
            result.placementRecords.get(overhang.up(3)).newBlockId,
            "terrain residue below elevated underside must be cleared");
    }

    @Test
    void underpassPavementSealRestoresFromExpectedSurfacesWithoutClearanceHint() {
        var result = new RoadGenerationResult(0);
        BlockPos paved = new BlockPos(0, 64, 0);
        BlockPos hole = new BlockPos(1, 64, 0);
        BlockPos farHole = new BlockPos(3, 65, 0); // 连续缺口 + 坡度不同 Y
        result.recordPlacementByLayerPriority(
            paved, "minecraft:grass_block", "minecraft:stone_bricks",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        // 故意不写 openRoadClearanceTops：封口必须只依赖设计期望
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            paved, "minecraft:stone_bricks", RoadSolidLayer.ROAD, "underpass"));
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            hole, "minecraft:stone_bricks", RoadSolidLayer.ROAD, "underpass"));
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            farHole, "minecraft:stone_bricks", RoadSolidLayer.ROAD, "underpass"));
        // 错材质残留
        result.recordPlacementByLayerPriority(
            hole, "minecraft:dirt", "minecraft:dirt",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);

        UnderpassPavementSealPass.apply(result);

        assertEquals("minecraft:stone_bricks", result.placementRecords.get(hole).newBlockId);
        assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(hole));
        assertEquals("minecraft:stone_bricks", result.placementRecords.get(farHole).newBlockId,
            "continuous hole without clearance hint must restore at design Y/material");
        assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(farHole));
        assertFalse(result.placementRecords.containsKey(new BlockPos(2, 64, 0)),
            "must not invent pavement outside expected footprint");
    }

    @Test
    void surfacePlacementRecordsExpectedRoadSurfaces() {
        var result = new RoadGenerationResult(0);
        result.edgeId = "edge-a";
        RoadSurfacePlacementPass.apply(result, plan(), 5);
        var surface = new BlockPos(3, 69, -3);
        ExpectedRoadSurface expected = result.expectedRoadSurfaces.get(surface);
        assertNotNull(expected);
        assertEquals("minecraft:white_concrete", expected.materialId());
        assertEquals(RoadSolidLayer.ROAD, expected.layer());
        assertEquals("edge-a", expected.roadId());
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
