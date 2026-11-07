package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionRegionBuilder;
import com.plot.plugin.road.JunctionRegionGeometry;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 区域式路口材质所有权：重叠核/圆角补铺用最宽道路材质；
 * 距街角较远的进口路臂保留各自道路材质；R 变化不蚕食路臂。
 */
class JunctionRegionMaterialOwnershipVoxelTest {

    private static final String WIDE_MATERIAL = "minecraft:white_concrete";
    private static final String NARROW_MATERIAL = "minecraft:black_concrete";

    @Test
    void unequalWidthSkewedCrossKeepsApproachMaterialsOutsideCore() {
        double theta = Math.toRadians(60.0);
        JunctionRegionGeometry region = JunctionRegionBuilder.build(
            new Vec2d(0, 0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 4.5),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(Math.cos(theta), Math.sin(theta)), 2.5),
            4.0);
        assertTrue(region.hasPavement());
        assertTrue(region.overlapRegion().size() >= 3);
        assertTrue(region.filletAdditions().size() >= 1);

        Fixture r0 = build(0.0);
        Fixture r4 = build(4.0);
        Fixture r12 = build(12.0);

        // 宽路远端（东向臂外）：始终宽路材质，不随 R 变成窄路或消失
        assertApproachMaterial(r0, 14, 0, WIDE_MATERIAL, "R0 wide arm");
        assertApproachMaterial(r4, 14, 0, WIDE_MATERIAL, "R4 wide arm");
        assertApproachMaterial(r12, 14, 0, WIDE_MATERIAL, "R12 wide arm");

        // 窄路远端（约 60° 方向）：保持窄路材质
        int nx = (int) Math.round(14 * Math.cos(theta));
        int nz = (int) Math.round(14 * Math.sin(theta));
        assertApproachMaterial(r0, nx, nz, NARROW_MATERIAL, "R0 narrow arm");
        assertApproachMaterial(r4, nx, nz, NARROW_MATERIAL, "R4 narrow arm");
        assertApproachMaterial(r12, nx, nz, NARROW_MATERIAL, "R12 narrow arm");

        // 重叠核附近：允许最宽道路材质主导
        assertTrue(countMaterialNear(r4, 0, 0, 3, WIDE_MATERIAL) > 0,
            "junction core should include widest-road material");

        // R 变化不应改宽路远端（|z| 小）表面键；斜向街角邻域允许随圆角变化
        Set<Long> farArmDiff = symmetricDiff(farWideArmKeys(r0), farWideArmKeys(r12));
        assertEquals(0, farArmDiff.size(),
            "far wide-arm pavement keys must not change with R; diff=" + farArmDiff.size());
    }

    private static void assertApproachMaterial(
            Fixture fixture, int x, int z, String expected, String label) {
        String actual = surfaceMaterialAt(fixture.result, x, 64, z);
        assertTrue(actual != null && !actual.isBlank(), label + " missing surface at " + x + "," + z);
        assertEquals(expected, actual, label + " material");
    }

    private static int countMaterialNear(Fixture fixture, int cx, int cz, int radius, String material) {
        int count = 0;
        for (var entry : fixture.result.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() != 64) {
                continue;
            }
            if (Math.abs(pos.getX() - cx) > radius || Math.abs(pos.getZ() - cz) > radius) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = fixture.result.placementLayers.get(pos);
            if (record == null || layer != RoadSolidLayer.ROAD) {
                continue;
            }
            if (material.equals(record.newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static Set<Long> farWideArmKeys(Fixture fixture) {
        Set<Long> keys = new HashSet<>();
        for (var entry : fixture.result.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() != 64) {
                continue;
            }
            // 仅宽路正东远端，避开斜交街角圆角影响区
            if (pos.getX() < 12 || Math.abs(pos.getZ()) > 2) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = fixture.result.placementLayers.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (layer != RoadSolidLayer.ROAD && layer != RoadSolidLayer.MARKING) {
                continue;
            }
            keys.add((((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL));
        }
        return keys;
    }

    private static Set<Long> symmetricDiff(Set<Long> a, Set<Long> b) {
        Set<Long> diff = new HashSet<>(a);
        diff.removeAll(b);
        Set<Long> other = new HashSet<>(b);
        other.removeAll(a);
        diff.addAll(other);
        return diff;
    }

    private static String surfaceMaterialAt(RoadGenerationResult result, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockRecord record = result.placementRecords.get(pos);
        if (record == null) {
            return null;
        }
        RoadSolidLayer layer = result.placementLayers.get(pos);
        if (layer != RoadSolidLayer.ROAD && layer != RoadSolidLayer.MARKING) {
            return null;
        }
        return record.newBlockId;
    }

    private static Fixture build(double cornerRadius) {
        RoadSystemConfig config = new RoadSystemConfig("region-material");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius((float) cornerRadius);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial(WIDE_MATERIAL);

        RoadNetwork network = new RoadNetwork();
        Road wide = network.createRoad("wide");
        wide.setWidth(9);
        wide.setMaterial(WIDE_MATERIAL);
        Road narrow = network.createRoad("narrow");
        narrow.setWidth(5);
        narrow.setMaterial(NARROW_MATERIAL);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(cornerRadius);
        // 60° 斜交：宽路东西向，窄路沿 (cos60, sin60)
        double c = Math.cos(Math.toRadians(60));
        double s = Math.sin(Math.toRadians(60));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        RoadNode n = network.createNode(new Vec2d(40 * c, 40 * s));
        RoadNode so = network.createNode(new Vec2d(-40 * c, -40 * s));
        network.createEdge(junction.getId(), e.getId(),
            java.util.List.of(junction.getPosition(), e.getPosition()), wide.getId());
        network.createEdge(junction.getId(), w.getId(),
            java.util.List.of(junction.getPosition(), w.getPosition()), wide.getId());
        network.createEdge(junction.getId(), n.getId(),
            java.util.List.of(junction.getPosition(), n.getPosition()), narrow.getId());
        network.createEdge(junction.getId(), so.getId(),
            java.util.List.of(junction.getPosition(), so.getPosition()), narrow.getId());

        // 几何自检：远端点不应落在重叠核内
        JunctionRegionGeometry geo = JunctionRegionBuilder.build(
            new Vec2d(0, 0),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 4.5),
            new JunctionRegionBuilder.StraightRoad(new Vec2d(c, s), 2.5),
            cornerRadius);
        assertTrue(!RoadGeometryUtils.pointInPolygon(new Vec2d(14.5, 0.5), geo.overlapRegion()),
            "far wide arm must be outside overlap core");

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadGenerationResult aggregate = new RoadNetworkGenerator(generator)
            .generatePreview(network, flatTerrain(64))
            .aggregate();
        return new Fixture(aggregate);
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int blockY, int z) {
                return blockY <= y;
            }
        };
    }

    private record Fixture(RoadGenerationResult result) {
    }
}
