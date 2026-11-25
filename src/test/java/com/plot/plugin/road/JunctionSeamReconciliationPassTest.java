package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.corridor.JunctionElevationField;
import com.plot.plugin.road.pipeline.corridor.JunctionFilletElevationLoft;
import com.plot.plugin.road.pipeline.corridor.ExpectedRoadSurface;
import com.plot.plugin.road.pipeline.corridor.JunctionSeamReconciliationPass;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.JunctionWorldFootprint;
import com.plot.plugin.road.pipeline.corridor.FinalPlacementAcceptanceValidator;
import com.plot.plugin.road.pipeline.corridor.RoadSurfacePlacementPass;
import com.plot.plugin.road.pipeline.corridor.UnderpassPavementSealPass;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圆角接缝：进口路缘/衬砌/装饰环不得残留在设计路面之下形成叠层突起。
 */
class JunctionSeamReconciliationPassTest {

    @Test
    void doesNotLowerPavementWhenSupportMissingFromPlacementRecords() {
        // 脚下是天然石头、无放置记录：不得因 hasNonAirSolid=false 就下移设计路面。
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
        JunctionElevationField field = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(64), columns, 64);
        ColumnKey filletColumn = footprint.filletAdditions().iterator().next();
        int targetY = field.surfaceY(filletColumn);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pavement = new BlockPos(filletColumn.worldX(), targetY, filletColumn.worldZ());
        plant(aggregate, pavement, "minecraft:stone", RoadSolidLayer.ROAD);
        // Y-1 故意不写 placementRecords，模拟天然地形承托。

        JunctionBlocks blocks = new JunctionBlocks();
        blocks.setElevationField(field);
        blocks.setValidationManifest(JunctionValidationManifest.empty("n0"));
        JunctionSeamReconciliationPass.apply(aggregate, Map.of("n0", blocks));

        assertEquals("minecraft:stone", aggregate.placementRecords.get(pavement).newBlockId,
            "design pavement must stay at target Y when only natural stone supports below");
        BlockPos below = new BlockPos(filletColumn.worldX(), targetY - 1, filletColumn.worldZ());
        assertTrue(
            !aggregate.placementRecords.containsKey(below)
                || !"minecraft:stone".equals(aggregate.placementRecords.get(below).newBlockId)
                || aggregate.placementLayers.get(below) != RoadSolidLayer.ROAD,
            "must not move ROAD one block down just because Y-1 lacks a placement record");
    }

    @Test
    void restoresPavementFromSavedMaterialAfterStrippingSoleDonor() {
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
        JunctionElevationField field = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(66), columns, 66);
        ColumnKey filletColumn = footprint.filletAdditions().iterator().next();
        int targetY = field.surfaceY(filletColumn);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos highOnly = new BlockPos(filletColumn.worldX(), targetY + 2, filletColumn.worldZ());
        plant(aggregate, highOnly, "minecraft:stone", RoadSolidLayer.ROAD);

        JunctionBlocks blocks = new JunctionBlocks();
        blocks.setElevationField(field);
        blocks.setValidationManifest(JunctionValidationManifest.empty("n0"));
        JunctionSeamReconciliationPass.apply(aggregate, Map.of("n0", blocks));

        BlockPos restored = new BlockPos(filletColumn.worldX(), targetY, filletColumn.worldZ());
        assertEquals("minecraft:air", aggregate.placementRecords.get(highOnly).newBlockId);
        assertEquals("minecraft:stone", aggregate.placementRecords.get(restored).newBlockId,
            "must restore solid pavement; never write air from stripped donor");
    }

    @Test
    void overlappingJunctionsShareUpdatedColumnIndexAfterRestore() {
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
        JunctionElevationField field = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(64), columns, 64);
        ColumnKey filletColumn = footprint.filletAdditions().iterator().next();
        int targetY = field.surfaceY(filletColumn);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos highOnly = new BlockPos(filletColumn.worldX(), targetY + 2, filletColumn.worldZ());
        plant(aggregate, highOnly, "minecraft:stone", RoadSolidLayer.ROAD);

        JunctionBlocks first = new JunctionBlocks();
        first.setElevationField(field);
        first.setValidationManifest(JunctionValidationManifest.empty("near-a"));
        JunctionBlocks second = new JunctionBlocks();
        second.setElevationField(field);
        second.setValidationManifest(JunctionValidationManifest.empty("near-b"));

        JunctionSeamReconciliationPass.apply(
            aggregate, Map.of("near-a", first, "near-b", second));

        BlockPos restored = new BlockPos(filletColumn.worldX(), targetY, filletColumn.worldZ());
        assertEquals("minecraft:stone", aggregate.placementRecords.get(restored).newBlockId);
        Map<ColumnKey, List<BlockPos>> index =
            JunctionSeamReconciliationPass.indexSolidPlacements(aggregate);
        assertTrue(index.getOrDefault(filletColumn, List.of()).contains(restored),
            "restored pavement from earlier overlapping junction must remain visible in column index");
    }

    @Test
    void indexSolidPlacementsGroupsByWorldColumn() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        plant(aggregate, new BlockPos(3, 64, 5), "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, new BlockPos(3, 65, 5), "minecraft:stone_bricks", RoadSolidLayer.TUNNEL);
        plant(aggregate, new BlockPos(4, 64, 5), "minecraft:stone", RoadSolidLayer.ROAD);
        aggregate.recordPlacementOverride(
            new BlockPos(3, 70, 5), "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(new BlockPos(3, 70, 5), RoadSolidLayer.TUNNEL);

        Map<ColumnKey, List<BlockPos>> index =
            JunctionSeamReconciliationPass.indexSolidPlacements(aggregate);
        assertEquals(2, index.size(), "two solid columns");
        assertEquals(2, index.get(new ColumnKey(3, 5)).size(),
            "air placements must not enter the solid column index");
        assertEquals(1, index.get(new ColumnKey(4, 5)).size());
    }

    @Test
    void revokeConflictingSurfaceExpectationsScalesWithColumnIndex() {
        int columns = 5_000;
        int expectationsPerColumn = 40;
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        for (int x = 0; x < columns; x++) {
            for (int dy = 0; dy < expectationsPerColumn; dy++) {
                BlockPos pos = new BlockPos(x, 60 + dy, 0);
                aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
                    pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
            }
        }
        assertEquals(columns * expectationsPerColumn, aggregate.expectedRoadSurfaces.size());

        Map<ColumnKey, List<BlockPos>> expectedByColumn =
            JunctionSeamReconciliationPass.indexExpectedSurfacesByColumn(aggregate);
        long start = System.nanoTime();
        for (int x = 0; x < columns; x++) {
            JunctionSeamReconciliationPass.revokeConflictingSurfaceExpectations(
                aggregate,
                new ColumnKey(x, 0),
                64,
                java.util.Collections.emptySet(),
                expectedByColumn);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        // Indexed O(S+K) path; full-table O(C×S) would be ~200M scans and far slower.
        assertTrue(elapsedMs < 5_000,
            "indexed revoke took " + elapsedMs + "ms on " + (columns * expectationsPerColumn)
                + " expectations across " + columns + " columns");

        ColumnKey sample = new ColumnKey(0, 0);
        assertFalse(aggregate.expectedRoadSurfaces.containsKey(new BlockPos(0, 60, 0)));
        assertTrue(aggregate.expectedRoadSurfaces.containsKey(new BlockPos(0, 64, 0)));
        assertEquals(1, expectedByColumn.getOrDefault(sample, List.of()).size());
    }

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

        JunctionBlocks blocks = new JunctionBlocks();
        blocks.setElevationField(field);
        blocks.setValidationManifest(JunctionValidationManifest.empty("n0"));

        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put("n0", blocks);
        JunctionSeamReconciliationPass.apply(aggregate, junctions);

        assertEquals("minecraft:air", aggregate.placementRecords.get(underRoad).newBlockId);
        assertEquals("minecraft:air", aggregate.placementRecords.get(underLining).newBlockId);
        assertEquals("minecraft:air", aggregate.placementRecords.get(underAccent).newBlockId);
        assertEquals("minecraft:stone", aggregate.placementRecords.get(surfaceRoad).newBlockId);
    }

    @Test
    void stripsLeftoverPavementAboveFilletAndCore() {
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
        JunctionElevationField field = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(64), columns, 64);
        ColumnKey filletColumn = footprint.filletAdditions().iterator().next();
        ColumnKey coreColumn = footprint.roadCore().iterator().next();
        int filletY = field.surfaceY(filletColumn);
        int coreY = field.surfaceY(coreColumn);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos spikeFillet = new BlockPos(filletColumn.worldX(), filletY + 2, filletColumn.worldZ());
        BlockPos spikeCore = new BlockPos(coreColumn.worldX(), coreY + 3, coreColumn.worldZ());
        plant(aggregate, spikeFillet, "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, spikeCore, "minecraft:stone", RoadSolidLayer.ROAD);

        JunctionBlocks blocks = new JunctionBlocks();
        blocks.setElevationField(field);
        blocks.setValidationManifest(JunctionValidationManifest.empty("n0"));
        JunctionSeamReconciliationPass.apply(aggregate, Map.of("n0", blocks));

        assertEquals("minecraft:air", aggregate.placementRecords.get(spikeFillet).newBlockId,
            "fillet leftover above design pavement must be stripped");
        assertEquals("minecraft:air", aggregate.placementRecords.get(spikeCore).newBlockId,
            "plaza core leftover spikes must be stripped");
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
        // 取 gore 内稳定列，避免 HashSet.findFirst 在不同 JVM 哈希种子下漂移。
        ColumnKey filletColumn = footprint.filletAdditions().stream()
            .filter(key -> {
                Vec2d plan = new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5);
                return RoadGeometryUtils.pointInPolygon(
                    plan, com.plot.plugin.road.RoadJunctionGenerator.filletGorePolygon(fillet));
            })
            .sorted((a, b) -> {
                int cx = Integer.compare(a.worldX(), b.worldX());
                return cx != 0 ? cx : Integer.compare(a.worldZ(), b.worldZ());
            })
            .findFirst()
            .orElse(null);
        assertTrue(filletColumn != null, "precondition: fillet gore column exists");

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
        int y = loft.surfaceY(plan, footprint);
        assertEquals(67, y,
            "fillet gore lofts between curb rails; leftover approach Y is stripped later, not pinned");
    }

    @Test
    void unconnectedFootprintOverlapKeepsBothTargetYsOrderIndependent() {
        OverlapFixture fixture = overlapFixture(64, 66);
        RoadGenerationResult ab = seedOverlapAggregate(fixture.filletColumn(), 64, 66);
        RoadGenerationResult ba = seedOverlapAggregate(fixture.filletColumn(), 64, 66);

        // 无网络 / 无共享边：不得 peerMax，两高度均保留。
        JunctionSeamReconciliationPass.apply(ab, fixture.junctionsAb());
        JunctionSeamReconciliationPass.apply(ba, fixture.junctionsBa());

        BlockPos lowY = new BlockPos(fixture.filletColumn().worldX(), 64, fixture.filletColumn().worldZ());
        BlockPos highY = new BlockPos(fixture.filletColumn().worldX(), 66, fixture.filletColumn().worldZ());
        assertEquals("minecraft:stone", ab.placementRecords.get(highY).newBlockId);
        assertEquals("minecraft:stone", ba.placementRecords.get(highY).newBlockId);
        // 低处若已有实体应保留；若仅靠回填，至少高处一致且顺序无关。
        assertEquals(
            ab.placementRecords.containsKey(lowY) ? ab.placementRecords.get(lowY).newBlockId : null,
            ba.placementRecords.containsKey(lowY) ? ba.placementRecords.get(lowY).newBlockId : null);
        assertEquals("minecraft:stone_bricks",
            ab.placementRecords.get(new BlockPos(fixture.filletColumn().worldX(), 70, fixture.filletColumn().worldZ()))
                .newBlockId);
        assertEquals("minecraft:smooth_stone",
            ab.placementRecords.get(new BlockPos(fixture.filletColumn().worldX(), 80, fixture.filletColumn().worldZ()))
                .newBlockId);
    }

    @Test
    void connectedSameLayerOverlapNegotiatesSharedYOrderIndependent() {
        OverlapFixture fixture = overlapFixtureAtGrade(64, 66);
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put(a.getId(), fixture.lowBlocks());
        junctions.put(b.getId(), fixture.highBlocks());
        network.createEdge(a.getId(), b.getId(),
            List.of(a.getPosition(), b.getPosition()));

        RoadGenerationResult ab = seedOverlapAggregate(fixture.filletColumn(), 64, 66);
        RoadGenerationResult ba = seedOverlapAggregate(fixture.filletColumn(), 64, 66);

        JunctionSeamReconciliationPass.apply(ab, junctions, network);
        JunctionSeamReconciliationPass.apply(
            ba, new LinkedHashMap<>(Map.of(b.getId(), fixture.highBlocks(), a.getId(), fixture.lowBlocks())), network);

        BlockPos lowY = new BlockPos(fixture.filletColumn().worldX(), 64, fixture.filletColumn().worldZ());
        BlockPos highY = new BlockPos(fixture.filletColumn().worldX(), 66, fixture.filletColumn().worldZ());
        assertEquals("minecraft:stone", ab.placementRecords.get(highY).newBlockId);
        assertEquals("minecraft:stone", ba.placementRecords.get(highY).newBlockId);
        assertTrue(isAirOrMissing(ab, lowY), "same-layer connected peers negotiate max Y");
        assertTrue(isAirOrMissing(ba, lowY));
    }

    @Test
    void seamClearDropsExpectedSoSealDoesNotRestore() {
        OverlapFixture fixture = overlapFixture(64, 64);
        ColumnKey column = fixture.filletColumn();
        BlockPos low = new BlockPos(column.worldX(), 64, column.worldZ());
        BlockPos spike = new BlockPos(column.worldX(), 66, column.worldZ());
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        plant(aggregate, spike, "minecraft:stone", RoadSolidLayer.ROAD);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            spike, "minecraft:stone", RoadSolidLayer.ROAD, "stale"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            low, "minecraft:stone", RoadSolidLayer.ROAD, "keep"));

        JunctionSeamReconciliationPass.apply(aggregate, Map.of("n0", fixture.lowBlocks()));
        assertTrue(isAirOrMissing(aggregate, spike));
        assertFalse(aggregate.expectedRoadSurfaces.containsKey(spike),
            "cleared leftover must drop ExpectedRoadSurface");

        UnderpassPavementSealPass.apply(aggregate);
        assertTrue(isAirOrMissing(aggregate, spike),
            "seal must not restore seam-cleared leftover from stale expectation");
    }

    @Test
    void connectedSameLayerFullPipelineKeepsSinglePavementY() {
        OverlapFixture fixture = overlapFixtureAtGrade(64, 66);
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()));
        Map<String, JunctionBlocks> junctions = new LinkedHashMap<>();
        junctions.put(a.getId(), fixture.lowBlocks());
        junctions.put(b.getId(), fixture.highBlocks());

        ColumnKey column = fixture.filletColumn();
        BlockPos lowY = new BlockPos(column.worldX(), 64, column.worldZ());
        BlockPos highY = new BlockPos(column.worldX(), 66, column.worldZ());
        RoadGenerationResult aggregate = seedOverlapAggregate(column, 64, 66);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            lowY, "minecraft:stone", RoadSolidLayer.ROAD, "low"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            highY, "minecraft:stone", RoadSolidLayer.ROAD, "high"));

        runSeamSealFinal(aggregate, junctions, network);

        assertEquals("minecraft:stone", aggregate.placementRecords.get(highY).newBlockId);
        assertTrue(isAirOrMissing(aggregate, lowY), "same-layer connected must not keep dual pavement");
        assertFalse(aggregate.expectedRoadSurfaces.containsKey(lowY),
            "losing design Y expectation must be revoked before seal");
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void gradeSeparatedOverlapFullPipelineKeepsBothPavementYs() {
        OverlapFixture fixture = overlapFixture(64, 66); // elevatedDeck → grade-separated claim
        ColumnKey column = fixture.filletColumn();
        BlockPos lowY = new BlockPos(column.worldX(), 64, column.worldZ());
        BlockPos highY = new BlockPos(column.worldX(), 66, column.worldZ());
        RoadGenerationResult aggregate = seedOverlapAggregate(column, 64, 66);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            lowY, "minecraft:stone", RoadSolidLayer.ROAD, "lower"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            highY, "minecraft:stone", RoadSolidLayer.ROAD, "upper"));

        runSeamSealFinal(aggregate, fixture.junctionsAb(), null);

        assertEquals("minecraft:stone", aggregate.placementRecords.get(lowY).newBlockId);
        assertEquals("minecraft:stone", aggregate.placementRecords.get(highY).newBlockId);
        assertTrue(aggregate.expectedRoadSurfaces.containsKey(lowY));
        assertTrue(aggregate.expectedRoadSurfaces.containsKey(highY));
        assertEquals("minecraft:stone_bricks",
            aggregate.placementRecords.get(new BlockPos(column.worldX(), 70, column.worldZ())).newBlockId,
            "tunnel lining between decks must survive");
        assertFalse(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void singleJunctionElevatedDeckKeepsUpperExpectationWithSolid() {
        // 单路口声明列不会写入 multilayer preservedYs；须靠 elevatedDeckY 同时保护实体与期望。
        OverlapFixture fixture = overlapFixture(64, 66);
        ColumnKey column = fixture.filletColumn();
        BlockPos lowY = new BlockPos(column.worldX(), 64, column.worldZ());
        BlockPos deckY = new BlockPos(column.worldX(), 80, column.worldZ());
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        plant(aggregate, lowY, "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, deckY, "minecraft:smooth_stone", RoadSolidLayer.ROAD);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            lowY, "minecraft:stone", RoadSolidLayer.ROAD, "lower"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            deckY, "minecraft:smooth_stone", RoadSolidLayer.ROAD, "deck"));

        JunctionSeamReconciliationPass.apply(aggregate, Map.of("a", fixture.lowBlocks()));

        assertEquals("minecraft:smooth_stone", aggregate.placementRecords.get(deckY).newBlockId);
        assertTrue(aggregate.expectedRoadSurfaces.containsKey(deckY),
            "elevated deck expectation must survive lower-junction seam revoke");
    }

    @Test
    void elevatedDeckMissingUpperStillKeepsExpectationAndBlocksFinalAcceptance() {
        OverlapFixture fixture = overlapFixture(64, 66);
        ColumnKey column = fixture.filletColumn();
        BlockPos lowY = new BlockPos(column.worldX(), 64, column.worldZ());
        BlockPos deckY = new BlockPos(column.worldX(), 80, column.worldZ());
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        plant(aggregate, lowY, "minecraft:stone", RoadSolidLayer.ROAD);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            lowY, "minecraft:stone", RoadSolidLayer.ROAD, "lower"));
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            deckY, "minecraft:smooth_stone", RoadSolidLayer.ROAD, "deck"));

        JunctionSeamReconciliationPass.apply(aggregate, Map.of("a", fixture.lowBlocks()));
        // 不跑封口：封口会按残留期望回铺；本用例验证接缝未 scrub 上层期望。
        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(aggregate.expectedRoadSurfaces.containsKey(deckY),
            "missing upper deck must not be scrubbed by lower seam");
        assertTrue(report.hasBlockingIssues(),
            "upper missing with lower complete must still block build");
    }

    private static void runSeamSealFinal(
            RoadGenerationResult aggregate,
            Map<String, JunctionBlocks> junctions,
            RoadNetwork network) {
        JunctionSeamReconciliationPass.apply(aggregate, junctions, network);
        RoadSurfacePlacementPass.clearIntrudingFill(aggregate);
        UnderpassPavementSealPass.apply(aggregate);
        FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);
    }

    private record OverlapFixture(
            ColumnKey filletColumn,
            JunctionBlocks lowBlocks,
            JunctionBlocks highBlocks) {
        Map<String, JunctionBlocks> junctionsAb() {
            return new LinkedHashMap<>(Map.of("a", lowBlocks, "b", highBlocks));
        }

        Map<String, JunctionBlocks> junctionsBa() {
            return new LinkedHashMap<>(Map.of("b", highBlocks, "a", lowBlocks));
        }
    }

    private static OverlapFixture overlapFixture(int lowY, int highY) {
        return overlapFixture(lowY, highY, true);
    }

    private static OverlapFixture overlapFixtureAtGrade(int lowY, int highY) {
        return overlapFixture(lowY, highY, false);
    }

    private static OverlapFixture overlapFixture(int lowY, int highY, boolean withElevatedDeck) {
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
        ColumnKey filletColumn = footprint.filletAdditions().iterator().next();
        JunctionElevationField lowField = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(lowY), columns, lowY);
        JunctionElevationField highField = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(highY), columns, highY);

        Integer deckY = withElevatedDeck ? 80 : null;
        JunctionBlocks low = new JunctionBlocks();
        low.setElevationField(lowField);
        low.setValidationManifest(liningAndDeckManifest("low", filletColumn, lowY, deckY));
        JunctionBlocks high = new JunctionBlocks();
        high.setElevationField(highField);
        high.setValidationManifest(liningAndDeckManifest("high", filletColumn, highY, deckY));
        return new OverlapFixture(filletColumn, low, high);
    }

    private static RoadGenerationResult seedOverlapAggregate(
            ColumnKey filletColumn, int lowY, int highY) {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        plant(aggregate, new BlockPos(filletColumn.worldX(), highY + 2, filletColumn.worldZ()),
            "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, new BlockPos(filletColumn.worldX(), lowY, filletColumn.worldZ()),
            "minecraft:stone", RoadSolidLayer.ROAD);
        plant(aggregate, new BlockPos(filletColumn.worldX(), highY, filletColumn.worldZ()),
            "minecraft:stone", RoadSolidLayer.ROAD);
        BlockPos lining = new BlockPos(filletColumn.worldX(), 70, filletColumn.worldZ());
        plant(aggregate, lining, "minecraft:stone_bricks", RoadSolidLayer.TUNNEL);
        aggregate.expectedTunnelLining.add(lining);
        aggregate.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.STRUCTURAL);
        plant(aggregate, new BlockPos(filletColumn.worldX(), 80, filletColumn.worldZ()),
            "minecraft:smooth_stone", RoadSolidLayer.ROAD);
        return aggregate;
    }

    private static JunctionValidationManifest liningAndDeckManifest(
            String nodeId, ColumnKey column, int junctionY, Integer elevatedDeckY) {
        BlockPos lining = new BlockPos(column.worldX(), 70, column.worldZ());
        return new JunctionValidationManifest(
            nodeId,
            List.of(),
            List.of(lining),
            List.of(),
            List.of(),
            junctionY,
            elevatedDeckY,
            0,
            0);
    }

    private static boolean isAirOrMissing(RoadGenerationResult aggregate, BlockPos pos) {
        var record = aggregate.placementRecords.get(pos);
        return record == null || "minecraft:air".equals(record.newBlockId);
    }

    private static void plant(
            RoadGenerationResult result, BlockPos pos, String blockId, RoadSolidLayer layer) {
        result.recordPlacementOverride(pos, "minecraft:air", blockId);
        result.placementLayers.put(pos, layer);
        result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
    }
}


