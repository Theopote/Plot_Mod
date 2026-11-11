package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开敞山地路堑（地表高路面 8～15 格）：圆角必须生成挖方边坡固体。
 */
class JunctionOpenCutFilletBatterTest {

    @Test
    void openMountainCutEmitsFilletBatterSubgrade() {
        RoadSystemConfig config = new RoadSystemConfig("open-cut-fillet-batter");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(8.0f);
        config.setSelectedMaterial("minecraft:stone");
        config.setRoadWidth(7);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("cut");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(8.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());
        network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), road.getId());

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            edgeResults.put(edge.getId(), new RoadGenerationResult(edge.getLength()));
        }

        int roadY = 64;
        int surfaceY = 64 + 12; // 12 格覆土开敞路堑
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(generator)
            .generateJunction(
                junction, network, surfaceAt(surfaceY), Map.of(junction.getId(), roadY), edgeResults);

        long cutOrSubgrade = blocks.getSolids().primitives().stream()
            .filter(p -> p != null && p.layer() == RoadSolidLayer.SUBGRADE)
            .count();
        long cutAir = blocks.getSolids().primitives().stream()
            .filter(p -> p != null
                && "minecraft:air".equals(p.materialId())
                && p.elevation() > roadY
                && p.elevation() <= surfaceY)
            .count();
        assertTrue(cutOrSubgrade > 0 || cutAir > 0,
            "open mountain cut fillet must emit batter cut/fill solids or cut air; subgrade="
                + cutOrSubgrade + " cutAir=" + cutAir);
    }

    private static TerrainSampler surfaceAt(int surfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y < surfaceY;
            }

            @Override
            public boolean isChunkLoaded(int x, int z) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return false;
            }
        };
    }
}
