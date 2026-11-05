package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上层宽 9 / 土径，下层宽 5 / 白混凝土，90° 立交：交叉区 MissingPavement=0 且 WrongMaterial=0。
 */
class UnderpassCrossPavementE2ETest {

    private static final String LOWER_MATERIAL = "minecraft:white_concrete";
    private static final String UPPER_MATERIAL = "minecraft:dirt_path";

    @Test
    void ninetyDegreeCrossHasZeroMissingOrWrongLowerPavement() {
        RoadSystemConfig config = new RoadSystemConfig("underpass-cross-e2e");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCrossingClearance(6.0);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial("minecraft:dirt"); // 故意不同于下层

        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(-48, 0), new Vec2d(48, 0)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, -48), new Vec2d(0, 48)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);
        assertFalse(network.getCrossings().isEmpty());

        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        String elevatedId = crossing.roadBId();
        String underpassId = crossing.otherRoadId(elevatedId);
        Road underpass = network.getRoad(underpassId);
        Road overpass = network.getRoad(elevatedId);
        underpass.setWidth(5);
        underpass.setMaterial(LOWER_MATERIAL);
        overpass.setWidth(9);
        overpass.setMaterial(UPPER_MATERIAL);
        assertTrue(network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, elevatedId, 6.0));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        RoadNetworkGenerator.PreviewResult preview =
            networkGenerator.generatePreview(network, new FlatTerrainSampler(64));
        RoadGenerationResult aggregate = preview.aggregate();
        assertNotNull(aggregate);

        int missingPavement = 0;
        int wrongMaterial = 0;
        int outsideExtraWhite = 0;
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isCarriageway()) {
                continue;
            }
            if (!LOWER_MATERIAL.equals(expected.materialId())) {
                continue;
            }
            BlockPos pos = expected.position();
            // 交叉邻域：下层应在桥下完整成材
            if (Math.abs(pos.getX()) > 8 || Math.abs(pos.getZ()) > 8) {
                continue;
            }
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                missingPavement++;
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.MARKING) {
                continue;
            }
            if (!LOWER_MATERIAL.equals(actual.newBlockId)) {
                wrongMaterial++;
            }
        }

        // 下层足迹外不应被额外铺成白混凝土（排除上层泥土径）
        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX()) > 12 || Math.abs(pos.getZ()) > 12) {
                continue;
            }
            if (!LOWER_MATERIAL.equals(entry.getValue().newBlockId)) {
                continue;
            }
            ExpectedRoadSurface expected = aggregate.expectedRoadSurfaces.get(pos);
            if (expected == null || !LOWER_MATERIAL.equals(expected.materialId())) {
                // 允许路口期望写入后与走廊期望同柱；仅惩罚完全无期望的白混凝土
                if (expected == null) {
                    outsideExtraWhite++;
                }
            }
        }

        assertEquals(0, missingPavement,
            "MissingPavementCount must be 0 in underpass cross neighborhood");
        assertEquals(0, wrongMaterial,
            "WrongMaterialCount must be 0 for underpass carriageway");
        assertEquals(0, outsideExtraWhite,
            "must not pave white concrete outside lower design footprint");
    }
}
