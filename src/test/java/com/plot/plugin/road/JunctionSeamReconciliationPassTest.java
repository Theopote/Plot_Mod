package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.corridor.JunctionElevationField;
import com.plot.plugin.road.pipeline.corridor.JunctionFilletElevationLoft;
import com.plot.plugin.road.pipeline.corridor.JunctionSeamReconciliationPass;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.JunctionWorldFootprint;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圆角接缝：进口路缘/衬砌/装饰环不得残留在设计路面之下形成叠层突起。
 */
class JunctionSeamReconciliationPassTest {

    @Test
    void stripsStackedEdgeAndLiningUnderFilletPavement() {
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
        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.inactive(67);
        JunctionElevationField field = JunctionElevationField.build(footprint, loft, columns, 67);

        ColumnKey filletColumn = null;
        for (ColumnKey key : footprint.filletAdditions()) {
            if (key != null) {
                filletColumn = key;
                break;
            }
        }
        assertTrue(filletColumn != null, "precondition: footprint has fillet column");
        int targetY = field.surfaceY(filletColumn);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos underRoad = new BlockPos(filletColumn.worldX(), targetY - 1, filletColumn.worldZ());
        BlockPos underLining = new BlockPos(filletColumn.worldX(), targetY - 2, filletColumn.worldZ());
        BlockPos underAccent = new BlockPos(filletColumn.worldX(), targetY - 3, filletColumn.worldZ());
        BlockPos surfaceRoad = new BlockPos(filletColumn.worldX(), targetY, filletColumn.worldZ());
        plant(aggregate, underRoad, "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, underLining, "minecraft:stone_bricks", RoadSolidLayer.TUNNEL);
        plant(aggregate, underAccent, "minecraft:chiseled_stone_bricks", RoadSolidLayer.TUNNEL);
        plant(aggregate, surfaceRoad, "minecraft:stone", RoadSolidLayer.ROAD);
        aggregate.placementMaterialPriorities.put(underAccent, RoadSolidMaterialPriority.ACCENT_RING);

        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator.JunctionBlocks();
        blocks.setElevationField(field);
        blocks.setValidationManifest(JunctionValidationManifest.empty("n0"));

        Map<String, RoadJunctionGenerator.JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put("n0", blocks);
        JunctionSeamReconciliationPass.apply(aggregate, junctions);

        assertEquals("minecraft:air", aggregate.placementRecords.get(underRoad).newBlockId);
        assertEquals("minecraft:air", aggregate.placementRecords.get(underLining).newBlockId);
        assertEquals("minecraft:air", aggregate.placementRecords.get(underAccent).newBlockId);
        assertEquals("minecraft:stone", aggregate.placementRecords.get(surfaceRoad).newBlockId);
    }

    @Test
    void filletExactApproachPinPreventsRaisingAboveLeftoverRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        road.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadEdge ns = network.createEdge(
            junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());

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
        ColumnKey filletColumn = footprint.filletAdditions().stream().findFirst().orElse(null);
        assertTrue(filletColumn != null, "precondition: fillet column exists");

        Map<String, RoadGenerationResult> edgeResults = new HashMap<>();
        RoadGenerationResult nsResult = new RoadGenerationResult(0);
        plant(
            nsResult,
            new BlockPos(filletColumn.worldX(), 64, filletColumn.worldZ()),
            "minecraft:stone",
            RoadSolidLayer.ROAD);
        edgeResults.put(ns.getId(), nsResult);

        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(fillet), edgeResults, List.of(ns), network, columns, 67);
        Vec2d plan = new Vec2d(filletColumn.worldX() + 0.5, filletColumn.worldZ() + 0.5);
        assertEquals(JunctionWorldFootprint.ElevationRole.FILLET, footprint.elevationRole(filletColumn));
        assertEquals(64, loft.surfaceY(plan, footprint),
            "exact leftover approach column must pin fillet Y, not loft above it");
    }

    private static void plant(
            RoadGenerationResult result, BlockPos pos, String blockId, RoadSolidLayer layer) {
        result.recordPlacementOverride(pos, "minecraft:air", blockId);
        result.placementLayers.put(pos, layer);
        result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
    }
}
