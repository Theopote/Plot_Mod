package com.plot.plugin.road.solid;

import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.ExpectedRoadSurface;
import com.plot.plugin.road.pipeline.corridor.FinalPlacementAcceptanceValidator;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合并净空胜出后必须同步作废路面/衬砌期望，避免终态验收假阳性。
 */
class RoadGenerationResultClearanceExpectationTest {

    @Test
    void tunnelClearanceMergeOpensLiningAndRegistersCavity() {
        RoadGenerationResult base = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(5, 64, 2);
        base.recordPlacementOverride(pos, "minecraft:stone", "minecraft:stone_bricks");
        base.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        base.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
        base.recordExpectedTunnelLining(pos);

        RoadGenerationResult clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone_bricks",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        base.mergeFrom(clearance);

        assertFalse(base.expectedTunnelLining.contains(pos),
            "clearance must drop superseded lining expectation");
        assertTrue(base.expectedTunnelCavity.contains(pos),
            "tunnel-interior clearance must register cavity");
    }

    @Test
    void layerPriorityTunnelAirRevokesStalePavementExpectation() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(6, 64, 2);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));

        result.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        assertFalse(result.expectedRoadSurfaces.containsKey(pos),
            "TUNNEL air outranking ROAD must revoke pavement expectation");
        assertTrue(result.expectedTunnelCavity.contains(pos));
    }

    @Test
    void slopeExcavationAirDoesNotRevokeMissingPavementExpectation() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(7, 64, 2);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));

        RoadGenerationResult slopeAir = new RoadGenerationResult(0);
        slopeAir.recordPlacementByLayerPriority(
            pos,
            "minecraft:dirt",
            "minecraft:air",
            RoadSolidLayer.SUBGRADE,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.SLOPE_EXCAVATION);

        aggregate.mergeFrom(slopeAir);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(pos),
            "slope excavation air must not hide a true missing pavement expectation");
        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void unauthorizedTunnelAirWithoutPurposeDoesNotRevokePavementExpectation() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(8, 64, 2);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));

        result.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            null);

        assertTrue(result.expectedRoadSurfaces.containsKey(pos),
            "TUNNEL air without authorized ClearancePurpose must not revoke design expectation");
    }

    @Test
    void gradeSeparationAirMayRevokePavementExpectation() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(9, 64, 2);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));

        RoadGenerationResult clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.GRADE_SEPARATION);

        aggregate.mergeFrom(clearance);

        assertFalse(aggregate.expectedRoadSurfaces.containsKey(pos));
        assertTrue(aggregate.expectedTunnelCavity.contains(pos));
    }

    /**
     * B01 顺序 A：先期望后授权净空 → 期望应被撤销。
     * 不改合并优先级；锁定当前授权撤销语义。
     */
    @Test
    void authorizedClearanceAfterExpectationRevokesSurfaceExpectation() {
        BlockPos pos = new BlockPos(10, 64, 2);
        RoadGenerationResult expectationOnly = expectationOnly(pos);
        RoadGenerationResult clearance = authorizedTunnelClearance(pos);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.mergeFrom(expectationOnly);
        aggregate.mergeFrom(clearance);

        assertFalse(aggregate.expectedRoadSurfaces.containsKey(pos),
            "order A: authorized clearance after expectation must revoke");
        assertTrue(aggregate.expectedTunnelCavity.contains(pos));
    }

    /**
     * B01 顺序 B：先授权净空后仅期望（无实体路面）→ 期望可能保留。
     * 记录当前 mergeFrom 不对称；若封口/终态出现非预期差异再改优先级。
     */
    @Test
    void expectationAfterAuthorizedClearanceIsRetainedUnderCurrentMerge() {
        BlockPos pos = new BlockPos(11, 64, 2);
        RoadGenerationResult clearance = authorizedTunnelClearance(pos);
        RoadGenerationResult expectationOnly = expectationOnly(pos);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.mergeFrom(clearance);
        aggregate.mergeFrom(expectationOnly);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(pos),
            "order B: expectation-only after authorized air is retained today");
        assertEquals("minecraft:air", aggregate.placementRecords.get(pos).newBlockId);
        // 期望残留 + 空气落点：终态应看到缺铺/错高，避免静默通过。
        assertTrue(FinalPlacementAcceptanceValidator.applyToAggregate(aggregate).hasBlockingIssues());
    }

    private static RoadGenerationResult expectationOnly(BlockPos pos) {
        RoadGenerationResult edge = new RoadGenerationResult(0);
        edge.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        return edge;
    }

    private static RoadGenerationResult authorizedTunnelClearance(BlockPos pos) {
        RoadGenerationResult clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);
        return clearance;
    }
}
