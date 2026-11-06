package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadPlacementRecorder;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorSolidEmitterEarthworkTest {

    @Test
    void longitudinalStepCladsUpperRiserWithSubgrade() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey lower = new ColumnKey(0, 0);
        ColumnKey upper = new ColumnKey(1, 0);
        plan.surfaceMask().put(lower, new SurfaceCell(
            new Vec2d(0, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.surfaceMask().put(upper, new SurfaceCell(
            new Vec2d(1, 0), 65, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(lower, new ClearanceCell(
            new Vec2d(0, 0), 64, 70, RoadCorridorConstructionMode.OPEN, RoadConstructionType.CUT));
        plan.putClearance(upper, new ClearanceCell(
            new Vec2d(1, 0), 65, 70, RoadCorridorConstructionMode.OPEN, RoadConstructionType.FILL));

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:cobblestone");

        assertTrue(solids.primitives().stream().anyMatch(p ->
                Math.round(p.planPoint().x) == 1
                    && Math.round(p.planPoint().y) == 0
                    && p.elevation() == 64
                    && p.layer() == RoadSolidLayer.SUBGRADE
                    && "minecraft:cobblestone".equals(p.materialId())),
            "upper step column must clad Y=surfaceY-1 with subgrade, not leave natural dirt");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                Math.round(p.planPoint().x) == 1
                    && p.elevation() == 65
                    && p.layer() == RoadSolidLayer.ROAD),
            "upper pavement must remain at Y=65");
    }

    @Test
    void doesNotEmitOpenFillUnderBridgeInteriorClearance() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey deck = new ColumnKey(15, 0);
        ColumnKey lateral = new ColumnKey(15, 3);
        plan.surfaceMask().put(deck, new SurfaceCell(
            new Vec2d(15, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(deck, new ClearanceCell(
            new Vec2d(15, 0), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        plan.putClearance(lateral, new ClearanceCell(
            new Vec2d(15, 3), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        SlopeColumnCell residual = plan.slopeCellOrCreate(lateral, new Vec2d(15, 3));
        residual.mergeFill(56, "minecraft:gravel");
        // Re-assert structure clearance so stale slope on the same column is stripped.
        plan.putClearance(lateral, new ClearanceCell(
            new Vec2d(15, 3), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        assertEquals(null, plan.slopeCell(lateral));

        // Residual slope without a second strip: emitter must still refuse open fill.
        SlopeColumnCell stale = plan.slopeCellOrCreate(lateral, new Vec2d(15, 3));
        stale.mergeFill(56, "minecraft:gravel");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        int gravelShoulder = 0;
        for (var primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.SHOULDER
                    || primitive.layer() == RoadSolidLayer.SUBGRADE) {
                gravelShoulder++;
            }
        }
        assertEquals(0, gravelShoulder,
            "bridge interior must not emit open shoulder/subgrade fill");
    }

    @Test
    void portalTransitionCapsFillAndCutBatterAboveTunnelRoof() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey portalColumn = new ColumnKey(20, 4);
        int designY = 64;
        plan.putClearance(portalColumn, new ClearanceCell(
            new Vec2d(20, 4), designY, 70,
            RoadCorridorConstructionMode.PORTAL,
            RoadConstructionType.TUNNEL_PORTAL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(portalColumn, new Vec2d(20, 4));
        slope.mergeFill(78, "minecraft:gravel");
        slope.mergeCut(76, "minecraft:stone");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(CorridorTestFixtures.planHost(config), designY);
        for (var primitive : solids.primitives()) {
            if (primitive.planPoint().x == 20 && primitive.planPoint().y == 4
                    && primitive.elevation() > roofTop) {
                org.junit.jupiter.api.Assertions.fail(
                    "portal batter must not emit blocks above tunnel roof at y=" + primitive.elevation());
            }
        }
    }

    @Test
    void cutBatterFaceUsesSubgradeNotShoulderAboveRoadSurface() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey slopeColumn = new ColumnKey(12, 3);
        SlopeColumnCell slope = plan.slopeCellOrCreate(slopeColumn, new Vec2d(12, 3));
        slope.mergeCut(65, "minecraft:stone");
        plan.surfaceMask().put(new ColumnKey(12, 2), new SurfaceCell(
            new Vec2d(12, 2), 64, RoadSolidLayer.SHOULDER, "minecraft:coarse_dirt"));

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        for (var primitive : solids.primitives()) {
            if (primitive.layer() != RoadSolidLayer.SHOULDER) {
                continue;
            }
            if (primitive.elevation() > 64 && primitive.planPoint().x == 12 && primitive.planPoint().y == 3) {
                org.junit.jupiter.api.Assertions.fail(
                    "cut batter must not emit shoulder-layer blocks above road surface on slope columns");
            }
        }
    }

    @Test
    void elevatedGradeSeparationEdgeDoesNotEmitFillBatter() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 2);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 2), 70, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 2), 70, 74,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 2));
        slope.mergeFill(69, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        GradeSeparationBuildContext elevated = new GradeSeparationBuildContext(
            true,
            java.util.List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(
                java.util.List.of(
                    new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10)),
                64,
                66)));
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:dirt",
            elevated);

        long dirtFill = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirtFill, "elevated grade-sep edge must not emit open fill batter");
    }

    @Test
    void lakeColumnDoesNotEmitOrdinaryFillBatter() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 0);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirt, "ordinary road must not embank into lake columns");
    }

    @Test
    void ordinaryFillTypeDoesNotBypassWaterBan() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 0);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirt, "ordinary FILL classification must not embank over water");
    }

    @Test
    void authorizedCausewayMayEmbankOverWater() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 0);
        plan.markAllowSlopeWaterFill(key);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        var metrics = new com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            metrics,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        org.junit.jupiter.api.Assertions.assertTrue(dirt > 0, "authorized causeway may embank over water");
        org.junit.jupiter.api.Assertions.assertTrue(metrics.fillVolume > 0,
            "slope fill path must count fillVolume");
    }

    @Test
    void roadbedWaterAuthDoesNotAllowSlopeFillOverWater() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(11, 0);
        plan.markAllowRoadbedWaterFill(key);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(11, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(11, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(11, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirt,
            "roadbed causeway auth must not authorize ordinary slope fill over water");
    }

    @Test
    void retainingFinishDoesNotEmbankUnauthorizedWaterColumn() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey tip = new ColumnKey(20, 0);
        SlopeColumnCell slope = plan.slopeCellOrCreate(tip, new Vec2d(20, 0));
        slope.mergeFill(63, "minecraft:dirt");
        slope.markRetainingFinish();

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:dirt");

        long wallOrFill = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL
                || p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, wallOrFill, "width-cap retaining must not refill banned water columns");
    }

    @Test
    void widthCapTipEmitsVerticalRetainingFinish() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey tip = new ColumnKey(20, 0);
        SlopeColumnCell slope = plan.slopeCellOrCreate(tip, new Vec2d(20, 0));
        slope.mergeCut(70, "minecraft:stone");
        slope.markRetainingFinish();

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:stone");

        long wall = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL)
            .filter(p -> Math.round(p.planPoint().x) == 20 && Math.round(p.planPoint().y) == 0)
            .count();
        org.junit.jupiter.api.Assertions.assertTrue(wall > 0,
            "capped cut tip must emit vertical retaining finish up to ground");
    }

    @Test
    void retainingFinishSurvivesClearanceAirInFinalPlacementRecords() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey tip = new ColumnKey(20, 0);
        Vec2d point = new Vec2d(20, 0);
        plan.putClearance(tip, new ClearanceCell(
            point, 70, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(tip, point);
        slope.mergeCut(70, "minecraft:stone");
        slope.markRetainingFinish();

        RoadSolidModel solids = new RoadSolidModel();
        var host = CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig());
        TerrainSampler terrain = mountainTerrain();
        RoadCorridorClearancePass.apply(solids, plan, terrain, host);
        RoadCorridorSolidEmitter.emit(solids, plan, terrain, host, null, "minecraft:stone");

        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        Map<BlockPos, RoadSolidLayer> layers = new HashMap<>();
        Map<BlockPos, Integer> materialPriorities = new HashMap<>();
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            BlockPos pos = new BlockPos(
                (int) Math.round(primitive.planPoint().x),
                primitive.elevation(),
                (int) Math.round(primitive.planPoint().y));
            RoadPlacementRecorder.recordByLayerPriority(
                records,
                layers,
                materialPriorities,
                pos,
                "minecraft:stone",
                primitive.materialId(),
                primitive.layer(),
                primitive.materialPriority());
        }

        for (int y = 70; y < 80; y++) {
            BlockPos pos = new BlockPos(20, y, 0);
            BlockRecord record = records.get(pos);
            org.junit.jupiter.api.Assertions.assertNotNull(record, "retaining cell y=" + y);
            org.junit.jupiter.api.Assertions.assertEquals(
                "minecraft:stone", record.newBlockId, "retaining must beat clearance AIR at y=" + y);
            org.junit.jupiter.api.Assertions.assertEquals(
                RoadSolidLayer.RETAINING_WALL, layers.get(pos));
        }
    }

    @Test
    void causewayRoadbedOrdinarySlopeAndTunnelClearanceStaySeparated() {
        // 岸边重叠：堤道路基授权柱、普通边坡填方柱、隧道净空柱 + 合法截断挡墙。
        ColumnKey causewayRoadbed = new ColumnKey(50, 0);
        ColumnKey ordinarySlope = new ColumnKey(51, 0);
        ColumnKey retainingTip = new ColumnKey(52, 0);
        ColumnKey tunnelClearance = new ColumnKey(53, 0);

        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.markAllowRoadbedWaterFill(causewayRoadbed);
        plan.surfaceMask().put(causewayRoadbed, new SurfaceCell(
            new Vec2d(50, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(causewayRoadbed, new ClearanceCell(
            new Vec2d(50, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));

        // 普通道路边坡落在相邻柱：不得继承堤道路基授权。
        plan.putClearance(ordinarySlope, new ClearanceCell(
            new Vec2d(51, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell ordinary = plan.slopeCellOrCreate(ordinarySlope, new Vec2d(51, 0));
        ordinary.mergeFill(63, "minecraft:dirt");

        SlopeColumnCell tip = plan.slopeCellOrCreate(retainingTip, new Vec2d(52, 0));
        tip.mergeCut(70, "minecraft:stone");
        tip.markRetainingFinish();
        plan.putClearance(retainingTip, new ClearanceCell(
            new Vec2d(52, 0), 70, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));

        RoadSolidModel solids = new RoadSolidModel();
        var host = CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig());
        TerrainSampler lake = lakeTerrain(60, 62);
        TerrainSampler mountain = mountainTerrain();

        RoadCorridorSolidEmitter.emit(solids, plan, lake, host, null, "minecraft:dirt");

        long ordinarySlopeDirt = solids.primitives().stream()
            .filter(p -> Math.round(p.planPoint().x) == 51)
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, ordinarySlopeDirt,
            "ordinary slope must not inherit neighboring causeway roadbed water auth");

        RoadSolidModel cutSolids = new RoadSolidModel();
        RoadCorridorClearancePass.apply(cutSolids, plan, mountain, host);
        RoadCorridorSolidEmitter.emit(cutSolids, plan, mountain, host, null, "minecraft:stone");
        // 隧道净空与挡墙冲突：跨结果合并时隧道 AIR 必须压倒挡墙。
        var slopeEdge = new com.plot.plugin.road.solid.RoadGenerationResult(0);
        var tunnelEdge = new com.plot.plugin.road.solid.RoadGenerationResult(0);
        BlockPos retainingPos = new BlockPos(52, 75, 0);
        BlockPos tunnelPos = new BlockPos(53, 66, 0);
        slopeEdge.recordPlacementByLayerPriority(
            retainingPos, "minecraft:stone", "minecraft:cobblestone",
            RoadSolidLayer.RETAINING_WALL, RoadSolidMaterialPriority.DEFAULT);
        slopeEdge.recordPlacementByLayerPriority(
            retainingPos, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR,
            ClearancePurpose.SLOPE_EXCAVATION);
        tunnelEdge.recordPlacementByLayerPriority(
            tunnelPos, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);
        tunnelEdge.recordPlacementByLayerPriority(
            tunnelPos, "minecraft:stone", "minecraft:cobblestone",
            RoadSolidLayer.RETAINING_WALL, RoadSolidMaterialPriority.DEFAULT);

        var merged = new com.plot.plugin.road.solid.RoadGenerationResult(0);
        merged.mergeFrom(slopeEdge);
        merged.mergeFrom(tunnelEdge);

        assertEquals("minecraft:cobblestone",
            merged.placementRecords.get(retainingPos).newBlockId,
            "legal retaining must survive own slope excavation air");
        assertEquals("minecraft:air",
            merged.placementRecords.get(tunnelPos).newBlockId,
            "tunnel interior must not keep foreign retaining wall");
        assertEquals(RoadSolidLayer.TUNNEL, merged.placementLayers.get(tunnelPos));
    }

    @Test
    void cutVolumeNotDoubleCountedWhenClearanceAlreadyCoversSlope() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(12, 4);
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(12, 4), 65, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(12, 4));
        slope.mergeCut(65, "minecraft:stone");

        var metrics = new com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics();
        RoadCorridorSolidEmitter.emit(
            new RoadSolidModel(),
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            metrics,
            "minecraft:stone");

        assertEquals(0, metrics.cutVolume,
            "emitter must not re-count cut when SlopeEnvelopeClearance already owns the column");
    }

    @Test
    void fillOnlySlopeColumnWithoutSurfaceDoesNotThrowOnDeepCoverCheck() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey fillColumn = new ColumnKey(8, 2);
        plan.putClearance(fillColumn, new ClearanceCell(
            new Vec2d(8, 2), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(fillColumn, new Vec2d(8, 2));
        slope.mergeFill(64, "minecraft:gravel");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        assertEquals(64, RoadCorridorSolidEmitter.resolveDesignY(null, slope, 50));
    }

    private static TerrainSampler mountainTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d p) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 80;
            }
        };
    }

    private static TerrainSampler valleyTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return p.x >= 8 && p.x <= 22 ? 50 : 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
    }

    /** Lake bed groundY, exposed water surface above bed. */
    private static TerrainSampler lakeTerrain(int bedY, int waterY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return bedY;
            }

            @Override
            public java.util.OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return java.util.OptionalInt.of(waterY);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= bedY;
            }
        };
    }
}
