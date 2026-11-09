package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 非 1:1 投影下生成必须按 unitsPerBlock 缩放路口半宽/圆角，
 * 否则街角倒圆相对走廊缩在交点旁，Minecraft 中看不见。
 */
class JunctionFilletCanvasScaleGenerationTest {

    @Test
    void projectedGenerationStampsOuterCurbReturnsMatchingCorridorScale() {
        // 1 canvas unit = 0.5 block → canvasUnitsPerWorldBlock = 2
        var coords = SnapshotCoordinateService.uniformScale(0.5);
        RoadSystemConfig config = new RoadSystemConfig("fillet-proj-gen");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(6.0f);
        config.setRoadWidth(6);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        ns.setWidth(7);
        Road ew = network.createRoad("ew");
        ew.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(6.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), ns.getId());
        network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), ns.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), ew.getId());
        network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), ew.getId());

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
            new RoadGenerator(config, coords, BlockProjectionHandler.getInstance()))
            .generatePreview(network, flat);

        assertFalse(preview.networkResult().getJunctionResults().isEmpty(),
            "junction plaza must generate under projected coordinates");

        // halfWidth≈3.5 blocks * scale2 ⇒ canvas half≈7；NE 街角补铺约在 canvas (8..14)
        int outerCanvas = 0;
        for (var blocks : preview.networkResult().getJunctionResults().values()) {
            if (blocks.getSolids() == null) {
                continue;
            }
            for (var primitive : blocks.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD || primitive.elevation() != 64) {
                    continue;
                }
                double x = primitive.planPoint().x;
                double z = primitive.planPoint().y;
                if (x >= 7 && z >= 7 && x <= 16 && z <= 16) {
                    outerCanvas++;
                }
            }
        }
        assertTrue(outerCanvas >= 4,
            "scaled generation must stamp NE curb-return in canvas space; outer=" + outerCanvas);

        // flush 后世界坐标：canvas*0.5 ⇒ 约 (4..8)
        int worldOuter = 0;
        for (var entry : preview.aggregate().placementRecords.entrySet()) {
            if (entry.getKey().getY() != 64) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = preview.aggregate().placementLayers.get(entry.getKey());
            if (layer != RoadSolidLayer.ROAD && layer != RoadSolidLayer.MARKING) {
                continue;
            }
            int x = entry.getKey().getX();
            int z = entry.getKey().getZ();
            if (x >= 3 && z >= 3 && x <= 9 && z <= 9) {
                worldOuter++;
            }
        }
        assertTrue(worldOuter >= 3,
            "flushed world must show NE curb-return blocks; worldOuter=" + worldOuter);
    }
}
