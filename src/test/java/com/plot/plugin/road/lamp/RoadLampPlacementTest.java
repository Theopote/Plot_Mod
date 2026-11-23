package com.plot.plugin.road.lamp;

import com.plot.plugin.road.golden.RoadGoldenHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoadLampPlacementTest {
    private static final RoadLampStyle STYLE = RoadLampPresets.ALL.getFirst().style();
    private static final RoadLampPlacement PLACEMENT =
        new RoadLampPlacement(STYLE, RoadLampPlacement.Sides.BOTH, 8, 0, 2, true, false);

    @Test
    void supportsStraightCurvedAndSlopedPavement() {
        for (var scenario : List.of(RoadGoldenScenarioFactory.r01StraightFlat(),
                RoadGoldenScenarioFactory.r02Curved(), RoadGoldenScenarioFactory.r06Sloped())) {
            var result = run(scenario).aggregate();
            var bases = result.streetlightBlocks.stream()
                .filter(pos -> result.placementLayers.get(pos.down()) == RoadSolidLayer.SIDEWALK)
                .toList();
            assertFalse(bases.isEmpty(), scenario.toString());
            assertEquals(result.streetlightCount, bases.size());
            for (var base : bases) {
                for (var voxel : RoadLampCompiler.position(RoadLampCompiler.compile(STYLE), base, 1, 0)) {
                    assertEquals(voxel.block(), result.placementRecords.get(voxel.position()).newBlockId);
                }
            }
        }
    }

    @Test
    void avoidsIntersectionFootprintsAndTunnelInterior() {
        for (var scenario : List.of(RoadGoldenScenarioFactory.r03TJunction(),
                RoadGoldenScenarioFactory.r04CrossJunction(), RoadGoldenScenarioFactory.r11GradeSeparated())) {
            var preview = run(scenario);
            for (var junction : preview.junctionResults().values()) {
                var reserved = junction.constructionContext().footprint().clearance();
                assertTrue(preview.aggregate().streetlightBlocks.stream()
                    .noneMatch(pos -> reserved.contains(new ColumnKey(pos.getX(), pos.getZ()))));
            }
        }
        var tunnel = run(RoadGoldenScenarioFactory.r09Tunnel()).aggregate();
        assertFalse(tunnel.expectedTunnelLining.isEmpty(), tunnel.constructionTypes.toString());
        var tunnelColumns = tunnel.expectedTunnelLining.stream().map(pos -> pos.getX()).collect(java.util.stream.Collectors.toSet());
        assertTrue(tunnel.streetlightBlocks.stream().noneMatch(pos -> tunnelColumns.contains(pos.getX())),
            () -> tunnel.constructionTypes + " lamps=" + tunnel.streetlightBlocks + " tunnel=" + tunnelColumns);
    }

    @Test
    void followsVariableWidthAndSkipsMissingSidewalkAndShoulder() {
        var variable = RoadGoldenScenarioFactory.r01StraightFlat();
        var road = variable.network().getRoads().values().iterator().next();
        road.setWidth(6);
        var wider = road.getCrossSection().copy();
        wider.getCarriageway().setWidth(14);
        wider.getSidewalk().setEnabled(true);
        wider.getSidewalk().setWidth(3);
        road.setVariableCrossSections(new com.plot.plugin.road.model.section.RoadVariableCrossSections(List.of(
            com.plot.plugin.road.model.section.StationCrossSection.at(12, wider))));
        var result = run(variable).aggregate();
        var bases = result.streetlightBlocks.stream()
            .filter(pos -> result.placementLayers.get(pos.down()) == RoadSolidLayer.SIDEWALK).toList();
        assertEquals(6, bases.size());
        int narrow = bases.stream().filter(pos -> pos.getX() < 12).mapToInt(pos -> Math.abs(pos.getZ())).max().orElseThrow();
        int wide = bases.stream().filter(pos -> pos.getX() >= 12).mapToInt(pos -> Math.abs(pos.getZ())).max().orElseThrow();
        assertTrue(wide > narrow);
        var section = wider.resolve(variable.config());
        assertNull(RoadLampPlanner.supportLayer(section, 1, section.outerBandBlockCount() + 1));

        var noPavement = RoadGoldenScenarioFactory.r01StraightFlat();
        var bare = noPavement.network().getRoads().values().iterator().next();
        bare.setIncludeSidewalk(false);
        bare.getCrossSection().getShoulder().setEnabled(false);
        bare.getCrossSection().getStreetFurniture().setRoadLampPlacement(PLACEMENT);
        assertEquals(0, RoadGoldenHarness.run(noPavement).preview().aggregate().streetlightCount);
    }

    @Test
    void staggeredCantileversPointInwardAtHalfSpacingOffsets() {
        var scenario = RoadGoldenScenarioFactory.r01StraightFlat();
        var road = scenario.network().getRoads().values().iterator().next();
        var cantilever = RoadLampPresets.ALL.get(1).style();
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(3);
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(cantilever, RoadLampPlacement.Sides.STAGGERED, 8, 0, 2, true, false));
        var result = RoadGoldenHarness.run(scenario).preview().aggregate();
        var bases = result.streetlightBlocks.stream().filter(pos ->
            result.placementLayers.get(pos.down()) == RoadSolidLayer.SIDEWALK).toList();
        assertEquals(List.of(4, 8, 12, 16, 20, 24, 28), bases.stream().map(BlockPos::getX).sorted().toList());
        for (var base : bases) {
            int inward = -Integer.signum(base.getZ());
            for (var voxel : RoadLampCompiler.position(RoadLampCompiler.compile(cantilever), base, 0, inward)) {
                assertEquals(voxel.block(), result.placementRecords.get(voxel.position()).newBlockId);
            }
        }
    }

    @Test
    void curvedCantileversFaceBackTowardTheCarriageway() {
        var scenario = RoadGoldenScenarioFactory.r02Curved();
        var road = scenario.network().getRoads().values().iterator().next();
        var cantilever = RoadLampPresets.ALL.get(1).style();
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(3);
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(cantilever, RoadLampPlacement.Sides.BOTH, 8, 0, 2, true, false));
        var result = RoadGoldenHarness.run(scenario).preview().aggregate();
        var bases = result.streetlightBlocks.stream().filter(pos ->
            result.placementLayers.get(pos.down()) == RoadSolidLayer.SIDEWALK).toList();
        assertFalse(bases.isEmpty());
        int obliqueBases = 0;
        for (var base : bases) {
            double radialX = base.getX() - 15;
            double radialZ = base.getZ();
            if (Math.abs(radialX) < 2 || Math.abs(radialZ) < 2) continue;
            obliqueBases++;
            double radiusError = Math.hypot(radialX, radialZ) - 12.0;
            var tips = result.streetlightBlocks.stream().filter(pos -> pos.getY() == base.getY() + cantilever.height()
                && Math.abs(pos.getX() - base.getX()) + Math.abs(pos.getZ() - base.getZ()) == 3).toList();
            assertTrue(tips.stream().anyMatch(tip -> radiusError
                * (radialX * (tip.getX() - base.getX()) + radialZ * (tip.getZ() - base.getZ())) < 0), base.toString());
        }
        assertTrue(obliqueBases >= 2);
    }

    @Test
    void stationConfigDoesNotOverrideTheRoadLevelLighting() {
        var scenario = RoadGoldenScenarioFactory.r01StraightFlat();
        var road = scenario.network().getRoads().values().iterator().next();
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(3);
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(PLACEMENT);
        var station = road.getCrossSection().copy();
        station.getStreetFurniture().setRoadLampPlacement(new RoadLampPlacement(
            RoadLampPresets.ALL.get(1).style(), RoadLampPlacement.Sides.RIGHT, 16, 0, 2, false, false));
        road.setVariableCrossSections(new com.plot.plugin.road.model.section.RoadVariableCrossSections(List.of(
            com.plot.plugin.road.model.section.StationCrossSection.at(12, station))));
        var result = RoadGoldenHarness.run(scenario).preview().aggregate();
        assertEquals(6, result.streetlightCount);
        assertEquals(6 * RoadLampCompiler.compile(STYLE).size(), result.streetlightBlocks.size());
    }

    @Test
    void bridgePlacementIsOptInAndKeepsTheDeckAndItsGuards() {
        var defaultBridge = RoadGoldenScenarioFactory.r08Bridge();
        var disabled = run(defaultBridge).aggregate();
        assertTrue(disabled.constructionTypes.stream().anyMatch(type -> type.isBridgeFamily()),
            disabled.constructionTypes.toString());

        var allowedBridge = RoadGoldenScenarioFactory.r08Bridge();
        var road = allowedBridge.network().getRoads().values().iterator().next();
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(4);
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(STYLE, RoadLampPlacement.Sides.BOTH, 8, 1, 2, true, true));
        var withLamps = RoadGoldenHarness.run(allowedBridge).preview().aggregate();
        assertTrue(withLamps.streetlightCount > disabled.streetlightCount,
            () -> "default=" + disabled.streetlightBlocks + " enabled=" + withLamps.streetlightBlocks);
        for (var pos : withLamps.bridgeBlocks) {
            assertNotEquals(RoadSolidLayer.STREETLIGHT, withLamps.placementLayers.get(pos));
        }
    }

    @Test
    void locatesTopOfRaisedMedian() {
        var scenario = RoadGoldenScenarioFactory.r01StraightFlat();
        var road = scenario.network().getRoads().values().iterator().next();
        road.getCrossSection().setMedian(new com.plot.plugin.road.model.section.Median(true, 2, 3, "minecraft:stone_bricks"));
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(STYLE, RoadLampPlacement.Sides.MEDIAN, 8, 0, 2, true, false));
        var result = RoadGoldenHarness.run(scenario).preview().aggregate();
        var bases = result.streetlightBlocks.stream()
            .filter(pos -> result.placementLayers.get(pos.down()) == RoadSolidLayer.MEDIAN).toList();
        assertEquals(3, bases.size());
        assertTrue(bases.stream().allMatch(pos -> pos.getY() == bases.getFirst().getY() && pos.getZ() == 0));
    }

    @Test
    void aLateConflictRejectsWholeLampAndPreservesUndoBaseline() {
        var support = new BlockPos(2, 64, 2);
        var voxels = RoadLampCompiler.position(RoadLampCompiler.compile(STYLE), support.up(), 1, 0);
        var result = new RoadGenerationResult(10);
        result.recordPlacementOverride(support, "minecraft:dirt", "minecraft:stone");
        result.placementLayers.put(support, RoadSolidLayer.SIDEWALK);
        var blocked = voxels.getLast().position();
        result.recordPlacementOverride(blocked, "minecraft:stone", "minecraft:air");
        result.placementClearancePurposes.put(blocked, ClearancePurpose.TUNNEL_INTERIOR);
        var candidate = new RoadLampPlanner.Candidate(support, RoadSolidLayer.SIDEWALK, voxels);
        result.roadLampCandidates.add(candidate);
        RoadLampPlacementPass.apply(result, Set.of(), pos -> "minecraft:air");
        assertEquals(2, result.placementRecords.size());
        assertEquals(0, result.streetlightCount);

        result.placementClearancePurposes.put(blocked, ClearancePurpose.SLOPE_EXCAVATION);
        result.roadLampCandidates.add(candidate);
        RoadLampPlacementPass.apply(result, Set.of(), pos -> "minecraft:air");
        assertEquals(0, result.streetlightCount);

        result.placementClearancePurposes.put(blocked, ClearancePurpose.ROAD_HEADROOM);
        result.roadLampCandidates.add(candidate);
        RoadLampPlacementPass.apply(result, Set.of(), pos -> "minecraft:air");
        assertEquals(1, result.streetlightCount);
        assertEquals("minecraft:stone", result.placementRecords.get(blocked).previousBlockId);
        assertEquals(voxels.stream().map(RoadLampCompiler.Voxel::position).toList(), result.streetlightBlocks);
    }

    @Test
    void intersectingCandidatesFromTwoEdgesOnlyCommitOneCompleteLamp() {
        var shape = RoadLampCompiler.compile(RoadLampPresets.ALL.get(1).style());
        var leftBase = new BlockPos(0, 64, 0);
        var rightBase = new BlockPos(6, 64, 0);
        var left = edgeWithLamp(leftBase, RoadLampCompiler.position(shape, leftBase.up(), 1, 0));
        var right = edgeWithLamp(rightBase, RoadLampCompiler.position(shape, rightBase.up(), -1, 0));
        var merged = new RoadGenerationResult(0);
        merged.mergeFrom(left);
        merged.mergeFrom(right);
        RoadLampPlacementPass.apply(merged, Set.of(), pos -> "minecraft:air");
        assertEquals(1, merged.streetlightCount);
        assertEquals(shape.size(), merged.streetlightBlocks.size());
        assertFalse(merged.placementRecords.containsKey(rightBase.up()));
    }

    private static RoadGenerationResult edgeWithLamp(BlockPos base, List<RoadLampCompiler.Voxel> voxels) {
        var edge = new RoadGenerationResult(10);
        edge.recordPlacementOverride(base, "minecraft:air", "minecraft:stone");
        edge.placementLayers.put(base, RoadSolidLayer.SIDEWALK);
        edge.roadLampCandidates.add(new RoadLampPlanner.Candidate(base, RoadSolidLayer.SIDEWALK, voxels));
        return edge;
    }

    @Test
    void configurationsSurviveProjectRoundTripAndHistorySnapshots() throws Exception {
        var network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        var road = network.getRoads().values().iterator().next();
        assertNull(RoadNetwork.fromJson(network.toJson()).getRoad(road.getId())
            .getCrossSection().getStreetFurniture().getRoadLampPlacement());
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(PLACEMENT);
        for (var restored : List.of(RoadNetwork.fromJson(network.toJson()), network.snapshot())) {
            assertEquals(PLACEMENT, restored.getRoad(road.getId())
                .getCrossSection().getStreetFurniture().getRoadLampPlacement());
        }
    }

    private static com.plot.plugin.road.benchmark.RoadBenchmarkHarness.PreviewRun run(RoadGoldenScenario scenario) {
        scenario.network().getRoads().values().forEach(road -> {
            road.setIncludeSidewalk(true);
            road.setSidewalkWidth(3);
            road.getCrossSection().getStreetFurniture().setRoadLampPlacement(PLACEMENT);
        });
        return RoadGoldenHarness.run(scenario).preview();
    }
}
