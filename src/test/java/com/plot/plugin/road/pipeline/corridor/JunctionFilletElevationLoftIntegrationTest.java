package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFilletElevationLoftIntegrationTest {

    @Test
    void filletGoreLoftsBetweenTwoCurbElevations() {
        // 正交街角：锐点(2,2)，切点(2,6)/(6,2)，圆心(6,6)
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);

        // 北向路缘 Y=64，东向路缘 Y=72
        RoadGenerationResult fake = new RoadGenerationResult(0);
        for (int z = 2; z <= 14; z++) {
            putRoad(fake, 2, z, 64);
        }
        for (int x = 2; x <= 14; x++) {
            putRoad(fake, x, 2, 72);
        }
        Map<String, RoadGenerationResult> edgeResults = Map.of("e", fake);

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

        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(fillet), edgeResults, List.of(), null, columns, 64);

        Vec2d nearNorthCurb = new Vec2d(2.4, 3.5);
        Vec2d nearEastCurb = new Vec2d(3.5, 2.4);
        Vec2d midGore = new Vec2d(2.8, 2.8);

        assertTrue(loft.isFilletGore(nearNorthCurb), "sample must sit in gore");
        assertTrue(loft.isFilletGore(nearEastCurb), "sample must sit in gore");
        assertTrue(loft.isFilletGore(midGore), "mid sample must sit in gore");

        int yN = loft.surfaceY(nearNorthCurb);
        int yE = loft.surfaceY(nearEastCurb);
        int yM = loft.surfaceY(midGore);
        assertTrue(yN <= yM && yM <= yE,
            "loft Y should rise from north curb toward east curb; yN=" + yN + " yM=" + yM + " yE=" + yE);
        assertTrue(yE - yN >= 4, "elevation span across fillet should be visible; span=" + (yE - yN));
        assertEquals(64, loft.surfaceY(new Vec2d(0, 0)), "core/outside gore stays junctionY");
    }

    @Test
    void sameHeightApproachesKeepFlatFilletInPreview() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-loft-flat");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(false);
        config.setDefaultCornerRadius(6.0f);
        config.setRoadWidth(7);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        ns.setWidth(7);
        Road ew = network.createRoad("ew");
        ew.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(6.0);
        for (Vec2d p : List.of(new Vec2d(0, 40), new Vec2d(0, -40), new Vec2d(40, 0), new Vec2d(-40, 0))) {
            RoadNode leaf = network.createNode(p);
            leaf.setManualElevation(64.0);
            Road road = Math.abs(p.x) > Math.abs(p.y) ? ew : ns;
            network.createEdge(junction.getId(), leaf.getId(),
                List.of(junction.getPosition(), leaf.getPosition()), road.getId());
        }

        TerrainSampler flat = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 64;
            }
        };

        RoadNetworkGenerator.PreviewResult preview = new RoadNetworkGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generatePreview(network, flat);

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int filletCells = 0;
        for (var blocks : preview.networkResult().getJunctionResults().values()) {
            for (var primitive : blocks.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD) {
                    continue;
                }
                double x = Math.abs(primitive.planPoint().x);
                double z = Math.abs(primitive.planPoint().y);
                if (x >= 3.0 && z >= 3.0 && x <= 10.0 && z <= 10.0) {
                    filletCells++;
                    minY = Math.min(minY, primitive.elevation());
                    maxY = Math.max(maxY, primitive.elevation());
                }
            }
        }
        assertTrue(filletCells >= 4, "expected curb-return ROAD cells; found=" + filletCells);
        assertEquals(minY, maxY, "same-height approaches keep fillet flat; " + minY + ".." + maxY);
    }

    private static void putRoad(RoadGenerationResult result, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
    }
}
