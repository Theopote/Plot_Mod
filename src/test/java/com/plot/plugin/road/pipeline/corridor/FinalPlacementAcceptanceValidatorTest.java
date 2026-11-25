package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalPlacementAcceptanceValidatorTest {

    @Test
    void mergeRepairClearsStaleEdgeMissingCount() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.missingRoadSurfaceCount = 4;
        BlockPos hole = new BlockPos(2, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            hole, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(hole, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(hole, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertFalse(report.hasBlockingIssues());
    }

    @Test
    void mergeDamageReportsFinalMissingSurface() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.missingRoadSurfaceCount = 0;
        BlockPos hole = new BlockPos(3, 64, 1);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            hole, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(hole, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(hole, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(report.missingRoadSurfaceCount() >= 1);
        assertTrue(aggregate.missingRoadSurfaceCount >= 1);
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void wrongElevationCountsAsBlockingSurfaceDefectOnRawValidate() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos expected = new BlockPos(0, 64, 0);
        BlockPos elsewhere = new BlockPos(0, 66, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            expected, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(elsewhere, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(elsewhere, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(1, report.wrongElevationCount());
        assertEquals(0, report.wrongMaterialOrLayerCount());
        assertEquals(1, report.wrongElevationOrMaterialCount());
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void applyToAggregateKeepsUnauthorizedWrongYExpectationAndBlocks() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos expected = new BlockPos(0, 64, 0);
        BlockPos elsewhere = new BlockPos(0, 66, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            expected, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(elsewhere, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(elsewhere, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(expected),
            "unauthorized wrong-Y must not scrub design expectation");
        assertEquals(1, report.wrongElevationCount());
        assertTrue(report.hasBlockingIssues());
        assertTrue(aggregate.missingRoadSurfaceCount >= 1);
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void applyToAggregateDoesNotScrubUnauthorizedTunnelClearanceAirExpectation() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(1, 64, 1);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(pos, RoadSolidLayer.TUNNEL);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(pos),
            "tunnel air alone must not scrub pavement expectation in final acceptance");
        assertTrue(report.missingRoadSurfaceCount() >= 1 || report.wrongElevationCount() >= 1);
        assertTrue(report.hasBlockingIssues());
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void dualLayerColumnLowerMissingUpperPresentBlocks() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos lower = new BlockPos(10, 64, 0);
        BlockPos upper = new BlockPos(10, 80, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            lower, "minecraft:stone", RoadSolidLayer.ROAD, "lower"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            upper, "minecraft:stone", RoadSolidLayer.ROAD, "upper"));
        aggregate.recordPlacementOverride(upper, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(upper, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(lower));
        assertEquals(1, report.wrongElevationCount());
        assertTrue(report.hasBlockingIssues());
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void authorizedSeamRevokeClearsOldYExpectationBeforeFinalAcceptance() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        ColumnKey column = new ColumnKey(5, 5);
        BlockPos stale = new BlockPos(5, 64, 5);
        BlockPos design = new BlockPos(5, 66, 5);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            stale, "minecraft:stone", RoadSolidLayer.ROAD, "stale"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            design, "minecraft:stone", RoadSolidLayer.ROAD, "design"));
        aggregate.recordPlacementOverride(design, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(design, RoadSolidLayer.ROAD);

        JunctionSeamReconciliationPass.revokeConflictingSurfaceExpectations(
            aggregate, column, 66, Set.of());

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertFalse(aggregate.expectedRoadSurfaces.containsKey(stale));
        assertTrue(aggregate.expectedRoadSurfaces.containsKey(design));
        assertFalse(report.hasBlockingIssues());
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void nonRoadLayerOnExpectedCarriagewayCountsAsWrong() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(4, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:dirt");
        aggregate.placementLayers.put(pos, RoadSolidLayer.SUBGRADE);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(1, report.wrongMaterialOrLayerCount());
        assertEquals(1, report.wrongElevationOrMaterialCount());
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void sidewalkWrongElevationBlocksAsStructuralDefect() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos expected = new BlockPos(1, 64, 2);
        BlockPos elsewhere = new BlockPos(1, 66, 2);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            expected, "minecraft:oak_planks", RoadSolidLayer.SIDEWALK, "edge"));
        aggregate.recordPlacementOverride(elsewhere, "minecraft:air", "minecraft:oak_planks");
        aggregate.placementLayers.put(elsewhere, RoadSolidLayer.SIDEWALK);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(1, report.wrongElevationCount());
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void roadSubstitutingForSidewalkCountsAsWrong() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(2, 64, 2);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:oak_planks", RoadSolidLayer.SIDEWALK, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(pos, RoadSolidLayer.ROAD);

        assertTrue(FinalPlacementAcceptanceValidator.validate(aggregate).hasBlockingIssues());
    }

    @Test
    void legalMarkingOverlayDoesNotCountAsWrong() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(5, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:white_concrete");
        aggregate.placementLayers.put(pos, RoadSolidLayer.MARKING);

        assertFalse(FinalPlacementAcceptanceValidator.validate(aggregate).hasBlockingIssues());
    }

    @Test
    void legalGuardrailOverlayOnSidewalkDoesNotCountAsWrong() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(6, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:oak_planks", RoadSolidLayer.SIDEWALK, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:cobblestone_wall");
        aggregate.placementLayers.put(pos, RoadSolidLayer.GUARDRAIL);

        assertFalse(FinalPlacementAcceptanceValidator.validate(aggregate).hasBlockingIssues(),
            "guardrail on sidewalk expectation must not block final placement");
    }

    @Test
    void legalGuardrailOverlayOnBikeLaneDoesNotCountAsWrong() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(7, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:gray_concrete", RoadSolidLayer.BIKE_LANE, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:iron_bars");
        aggregate.placementLayers.put(pos, RoadSolidLayer.GUARDRAIL);

        assertFalse(FinalPlacementAcceptanceValidator.validate(aggregate).hasBlockingIssues());
    }

    @Test
    void illegalGuardrailInsideCarriagewayBlocks() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(8, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:iron_bars");
        aggregate.placementLayers.put(pos, RoadSolidLayer.GUARDRAIL);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(1, report.wrongMaterialOrLayerCount());
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void floatingTunnelLightIsAdvisoryAndDoesNotBlockBuild() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.floatingTunnelLightCount = 2;

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertEquals(2, report.floatingTunnelLightCount());
        assertTrue(report.hasFloatingLightAdvisory());
        assertFalse(report.hasBlockingIssues(), "floating lights must not block build");
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void materialMismatchIsSeparateFromWrongElevation() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(9, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:black_concrete", RoadSolidLayer.ROAD, "a"));
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(pos, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(0, report.wrongElevationCount());
        assertEquals(1, report.wrongMaterialOrLayerCount());
        assertTrue(report.hasBlockingIssues());
    }

    @Test
    void authorizedLanternInCavityDoesNotBlockAfterTunnelThenFinalChain() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(40, 66, 0);
        BlockPos lining = cavity.up(); // ordinary lantern needs hang/sit mount, not side contact
        aggregate.recordExpectedTunnelCavity(cavity);
        aggregate.recordPlacementByLayerPriority(
            lining, "minecraft:stone", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        aggregate.recordPlacementByLayerPriority(
            cavity, "minecraft:air", "minecraft:lantern",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.DECORATIVE);

        runTunnelThenFinal(aggregate, null);

        assertEquals(0, aggregate.unclearedTunnelCavityCount);
        assertEquals(0, aggregate.floatingTunnelLightCount);
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void authorizedLiningOverlapCavityDoesNotBlockAfterTunnelThenFinalChain() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(41, 66, 0);
        aggregate.recordExpectedTunnelCavity(pos);
        aggregate.recordExpectedTunnelLining(pos);
        aggregate.recordPlacementByLayerPriority(
            pos, "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);

        runTunnelThenFinal(aggregate, null);

        assertEquals(0, aggregate.unclearedTunnelCavityCount);
        assertEquals(0, aggregate.missingTunnelLiningCount);
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void terrainObstructionWithoutPlacementBlocksAfterTunnelThenFinalChain() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(42, 66, 0);
        aggregate.recordExpectedTunnelCavity(cavity);
        TerrainSampler rock = solidTerrain();

        runTunnelThenFinal(aggregate, rock);

        assertEquals(1, aggregate.unclearedTunnelCavityCount);
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void liningSupersededByPlazaSurfaceIsNotMissingLiningHole() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos lining = new BlockPos(43, 67, 0);
        aggregate.recordExpectedTunnelLining(lining);
        aggregate.recordPlacementOverride(lining, "minecraft:stone_bricks", "minecraft:oak_planks");
        aggregate.placementLayers.put(lining, RoadSolidLayer.ROAD);
        aggregate.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.DEFAULT);

        runTunnelThenFinal(aggregate, null);

        // 与覆土校验同口径：非空气实体占据 ≠ lining 空洞（广场面层顶替属设计行为）。
        assertEquals(0, aggregate.missingTunnelLiningCount);
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void connectivityOpenedLiningAirDoesNotBlockAfterTunnelThenFinalChain() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos opened = new BlockPos(44, 67, 0);
        aggregate.recordExpectedTunnelLining(opened);
        aggregate.recordExpectedTunnelCavity(opened);
        aggregate.recordPlacementByLayerPriority(
            opened, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        runTunnelThenFinal(aggregate, null);

        assertEquals(0, aggregate.missingTunnelLiningCount);
        assertEquals(0, aggregate.unclearedTunnelCavityCount);
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void applyToAggregateDoesNotClobberTunnelCountsFromSpecialist() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.unclearedTunnelCavityCount = 7;
        aggregate.missingTunnelLiningCount = 3;
        aggregate.floatingTunnelLightCount = 2;

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertEquals(7, aggregate.unclearedTunnelCavityCount);
        assertEquals(3, aggregate.missingTunnelLiningCount);
        assertEquals(2, aggregate.floatingTunnelLightCount);
        assertEquals(7, report.unclearedTunnelCavityCount());
        assertEquals(3, report.missingTunnelLiningCount());
        assertEquals(2, report.floatingTunnelLightCount());
        assertTrue(report.hasBlockingIssues(), "uncleared cavity / missing lining still block");
        assertTrue(report.hasFloatingLightAdvisory());
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    private static void runTunnelThenFinal(RoadGenerationResult aggregate, TerrainSampler terrain) {
        TunnelFinalVoxelValidator.applyReportToResult(
            aggregate, TunnelFinalVoxelValidator.validate(aggregate, terrain));
        FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);
    }

    private static TerrainSampler solidTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return true;
            }
        };
    }
}
