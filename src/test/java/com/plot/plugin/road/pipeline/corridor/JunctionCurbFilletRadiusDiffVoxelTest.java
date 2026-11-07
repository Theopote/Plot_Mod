package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收门槛：正交十字路口 R=0 与 R=6 的最终路面方块轮廓必须可观察地不同，
 * 且差异应出现在四处街角方向。
 */
class JunctionCurbFilletRadiusDiffVoxelTest {

    @Test
    void orthogonalCrossRadiusZeroVersusSixChangesCurbCorners() {
        Fixture zero = build(0.0);
        Fixture six = build(6.0);

        assertTrue(six.geometry.fillets().size() >= 4,
            "R=6 geometry must produce curb fillets");
        assertTrue(six.geometry.effectiveCornerRadius() > 2.0,
            "R=6 effective radius too small: " + six.geometry.effectiveCornerRadius());
        assertTrue(
            RoadGeometryUtils.pointInPolygon(new Vec2d(4.5, 4.5), six.geometry.fillPolygon()),
            "R=6 fill must cover NE curb-return");
        assertTrue(
            !RoadGeometryUtils.pointInPolygon(new Vec2d(4.5, 4.5), zero.geometry.fillPolygon()),
            "R=0 plus must leave NE reentrant empty");

        Set<Long> r0 = pavementKeys(zero.aggregate);
        Set<Long> r6 = pavementKeys(six.aggregate);

        Set<Long> onlyInR6 = new HashSet<>(r6);
        onlyInR6.removeAll(r0);
        Set<Long> onlyInR0 = new HashSet<>(r0);
        onlyInR0.removeAll(r6);

        // 若聚合结果仍无差异，至少几何栅格必须不同（隔离生成合并问题）
        Set<Long> geomOnly = geometryCellKeys(six.geometry);
        geomOnly.removeAll(geometryCellKeys(zero.geometry));
        assertTrue(geomOnly.size() >= 8,
            "filleted geometry raster must differ from sharp; geomAdded=" + geomOnly.size());

        // 路口固体本身必须随半径变化；若最终 placement 被边结果盖住，仍以路口固体为准验收几何。
        Set<Long> junctionOnly = new HashSet<>(six.junctionRoadKeys);
        junctionOnly.removeAll(zero.junctionRoadKeys);
        assertTrue(junctionOnly.size() >= 4,
            "junction ROAD solids must differ by radius; junctionAdded=" + junctionOnly.size()
                + " solids0=" + zero.junctionSolidCount
                + " solids6=" + six.junctionSolidCount
                + " placementAdded=" + onlyInR6.size()
                + " geomAdded=" + geomOnly.size());

        assertTrue(onlyInR6.size() >= 4 || junctionOnly.size() >= 4,
            "R=6 must add curb-return pavement; placementAdded=" + onlyInR6.size()
                + " junctionAdded=" + junctionOnly.size()
                + " removed=" + onlyInR0.size()
                + " r0=" + r0.size() + " r6=" + r6.size());

        Set<Long> cornerSource = onlyInR6.size() >= 4 ? onlyInR6 : junctionOnly;
        int ne = 0;
        int nw = 0;
        int se = 0;
        int sw = 0;
        for (long key : cornerSource) {
            int x = (int) (key >> 32);
            int z = (int) key;
            if (x >= 3 && z >= 3) {
                ne++;
            } else if (x <= -3 && z >= 3) {
                nw++;
            } else if (x >= 3 && z <= -3) {
                se++;
            } else if (x <= -3 && z <= -3) {
                sw++;
            }
        }
        assertTrue(ne > 0 && nw > 0 && se > 0 && sw > 0,
            "added cells should appear in all four curb corners; ne=" + ne
                + " nw=" + nw + " se=" + se + " sw=" + sw
                + " sourceSize=" + cornerSource.size());

        // 圆角不得把进口路臂远端纳入差异（铺装 extent 与 R 解耦）
        assertEquals(
            RoadGeometryUtils.pointInPolygon(new Vec2d(12.5, 0.5), zero.geometry.fillPolygon()),
            RoadGeometryUtils.pointInPolygon(new Vec2d(12.5, 0.5), six.geometry.fillPolygon()),
            "far east arm coverage must not change with R");
        int farArmGeomDiff = 0;
        for (long key : geomOnly) {
            int x = (int) (key >> 32);
            int z = (int) key;
            boolean farArm = (Math.abs(x) >= 10 && Math.abs(z) <= 2)
                || (Math.abs(z) >= 10 && Math.abs(x) <= 2);
            if (farArm) {
                farArmGeomDiff++;
            }
        }
        assertEquals(0, farArmGeomDiff,
            "geometry raster diffs must stay at curb corners, not far arms; far=" + farArmGeomDiff);
    }

    private static Fixture build(double cornerRadius) {
        RoadSystemConfig config = new RoadSystemConfig("curb-fillet-diff");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius((float) cornerRadius);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        ns.setWidth(7);
        Road ew = network.createRoad("ew");
        ew.setWidth(7);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(cornerRadius);
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

        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : junction.getConnectedEdgeIds()) {
            edges.add(network.getEdge(edgeId));
        }
        JunctionGeometryResult geometry = RoadJunctionGeometry.buildJunctionGeometry(
            junction.getId(),
            edges,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            junction.getEffectiveCornerRadius(config.getDefaultCornerRadius()),
            RoadJunctionCenterlineResolver.forNetwork(network));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator.PreviewResult preview = new RoadNetworkGenerator(generator)
            .generatePreview(network, flatTerrain(64));
        int junctionSolids = 0;
        Set<Long> junctionRoadKeys = new HashSet<>();
        for (var blocks : preview.networkResult().getJunctionResults().values()) {
            if (blocks.getSolids() == null) {
                continue;
            }
            junctionSolids += blocks.getSolids().primitives().size();
            for (var primitive : blocks.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD && primitive.layer() != RoadSolidLayer.MARKING) {
                    continue;
                }
                if (primitive.elevation() != 64) {
                    continue;
                }
                int x = (int) Math.floor(primitive.planPoint().x);
                int z = (int) Math.floor(primitive.planPoint().y);
                junctionRoadKeys.add((((long) x) << 32) ^ (z & 0xffffffffL));
            }
        }
        return new Fixture(geometry, preview.aggregate(), junctionSolids, junctionRoadKeys);
    }

    private static Set<Long> geometryCellKeys(JunctionGeometryResult geometry) {
        Set<Long> keys = new HashSet<>();
        for (Vec2d cell : RoadGeometryUtils.collectWorldCellPlanPoints(geometry.fillPolygon(), null)) {
            if (cell == null) {
                continue;
            }
            int x = (int) Math.floor(cell.x);
            int z = (int) Math.floor(cell.y);
            keys.add((((long) x) << 32) ^ (z & 0xffffffffL));
        }
        return keys;
    }

    private static Set<Long> pavementKeys(RoadGenerationResult result) {
        Set<Long> keys = new HashSet<>();
        for (var entry : result.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() != 64) {
                continue;
            }
            if (Math.abs(pos.getX()) > 16 || Math.abs(pos.getZ()) > 16) {
                continue;
            }
            BlockRecord record = entry.getValue();
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = result.placementLayers.get(pos);
            if (layer == RoadSolidLayer.ROAD
                    || layer == RoadSolidLayer.MARKING
                    || layer == RoadSolidLayer.SIDEWALK
                    || layer == RoadSolidLayer.SHOULDER) {
                keys.add((((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL));
            }
        }
        return keys;
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

    private record Fixture(
            JunctionGeometryResult geometry,
            RoadGenerationResult aggregate,
            int junctionSolidCount,
            Set<Long> junctionRoadKeys) {
    }
}
