package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
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
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 斜交平交：Minecraft 生成中增大圆角半径必须在街角外扩更多路面方块。
 */
class JunctionSkewFilletVoxelGrowthTest {

    @Test
    void skewCrossLargerCornerRadiusAddsMoreCurbReturnVoxels() {
        Set<Long> r4 = pavementKeys(build(4.0));
        Set<Long> r10 = pavementKeys(build(10.0));

        Set<Long> onlyInR10 = new HashSet<>(r10);
        onlyInR10.removeAll(r4);
        assertTrue(onlyInR10.size() >= 6,
            "R=10 must add curb-return voxels beyond R=4; added=" + onlyInR10.size()
                + " r4=" + r4.size() + " r10=" + r10.size());

        int outerGore = 0;
        for (long key : onlyInR10) {
            int x = (int) (key >> 32);
            int z = (int) key;
            // 斜交街角外扩：远离两轴中心线的象限块
            if (Math.abs(x) >= 3 && Math.abs(z) >= 2) {
                outerGore++;
            }
        }
        assertTrue(outerGore >= 3,
            "added voxels should sit in outer curb-return pockets; outer=" + outerGore);
    }

    private static RoadNetworkGenerator.PreviewResult build(double cornerRadius) {
        RoadSystemConfig config = new RoadSystemConfig("skew-fillet-voxel");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius((float) cornerRadius);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        a.setWidth(7);
        Road b = network.createRoad("b");
        b.setWidth(7);

        double theta = Math.toRadians(35);
        Vec2d dirB = new Vec2d(Math.cos(theta), Math.sin(theta));
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(cornerRadius);
        RoadNode aPos = network.createNode(new Vec2d(40, 0));
        RoadNode aNeg = network.createNode(new Vec2d(-40, 0));
        RoadNode bPos = network.createNode(dirB.multiply(40));
        RoadNode bNeg = network.createNode(dirB.multiply(-40));
        network.createEdge(junction.getId(), aPos.getId(),
            List.of(junction.getPosition(), aPos.getPosition()), a.getId());
        network.createEdge(junction.getId(), aNeg.getId(),
            List.of(junction.getPosition(), aNeg.getPosition()), a.getId());
        network.createEdge(junction.getId(), bPos.getId(),
            List.of(junction.getPosition(), bPos.getPosition()), b.getId());
        network.createEdge(junction.getId(), bNeg.getId(),
            List.of(junction.getPosition(), bNeg.getPosition()), b.getId());

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
        return new RoadNetworkGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generatePreview(network, flat);
    }

    private static Set<Long> pavementKeys(RoadNetworkGenerator.PreviewResult preview) {
        Set<Long> keys = new HashSet<>();
        for (var blocks : preview.networkResult().getJunctionResults().values()) {
            if (blocks.getSolids() == null) {
                continue;
            }
            for (var primitive : blocks.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD || primitive.elevation() != 64) {
                    continue;
                }
                if (Math.abs(primitive.planPoint().x) > 18 || Math.abs(primitive.planPoint().y) > 18) {
                    continue;
                }
                int x = (int) Math.floor(primitive.planPoint().x);
                int z = (int) Math.floor(primitive.planPoint().y);
                keys.add((((long) x) << 32) ^ (z & 0xffffffffL));
            }
        }
        return keys;
    }
}
