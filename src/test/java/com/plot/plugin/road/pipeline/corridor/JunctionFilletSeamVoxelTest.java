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
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锐角走廊裁剪 ∪ 圆角路口铺装 与最终路面体素接缝：不允许漏铺三角空洞。
 */
class JunctionFilletSeamVoxelTest {

    @Test
    void skewedUnequalWidthCrossingHasNoPavementHolesInExpectedUnion() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-seam");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(true);
        config.setIncludeSidewalk(true);
        config.setDefaultCornerRadius(4.0f);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork live = new RoadNetwork();
        Road primary = live.createRoad("primary");
        primary.setWidth(9);
        primary.setMaterial("minecraft:white_concrete");
        Road secondary = live.createRoad("secondary");
        secondary.setWidth(5);
        secondary.setMaterial("minecraft:stone");

        // 斜交：secondary 旋转约 60°
        RoadNode a0 = live.createNode(new Vec2d(-50, 0));
        RoadNode a1 = live.createNode(new Vec2d(50, 0));
        RoadNode b0 = live.createNode(new Vec2d(-25, -43));
        RoadNode b1 = live.createNode(new Vec2d(25, 43));
        live.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), primary.getId());
        live.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), secondary.getId());
        live.registerCrossing(RoadCrossing.atGrade(
            primary.getId(), 50, secondary.getId(), 50, new Vec2d(0, 0)).withCornerRadius(4.0));

        RoadNetwork network = RoadCrossingMaterializer.materializeForSnapshot(live);
        RoadNode junction = network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .filter(n -> n.getPosition() != null && n.getPosition().distance(new Vec2d(0, 0)) < 1.0)
            .findFirst()
            .orElseThrow();

        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        var halfWidth = JunctionPreviewHalfWidth.resolver(network, config);
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            halfWidth,
            junction.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS));
        JunctionGeometryResult geometry = RoadJunctionGeometry.buildJunctionGeometry(
            junction.getId(),
            edges,
            halfWidth,
            junctionRadius,
            junction.getEffectiveCornerRadius(config.getDefaultCornerRadius()),
            RoadJunctionCenterlineResolver.forNetwork(network));
        assertTrue(geometry.hasFillPolygon());

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        TerrainSampler terrain = flatTerrain(64);
        RoadGenerationResult aggregate = networkGenerator.generatePreview(network, terrain).aggregate();

        // 与生成器相同的世界格扫描，避免手工栅格与 pointInPolygon 边界差 1 格。
        List<Vec2d> expectedCells = RoadGeometryUtils.collectWorldCellPlanPoints(
            geometry.fillPolygon(), IdentityCoordinateService.INSTANCE);
        // 再内缩：只验收明显落在圆角内部的柱，边缘弧切可能因半格采样差 1。
        int missing = 0;
        int checked = 0;
        for (Vec2d cell : expectedCells) {
            if (cell == null) {
                continue;
            }
            if (!RoadGeometryUtils.pointInPolygon(cell, geometry.fillPolygon())) {
                continue;
            }
            // 要求离锐角外轮廓也有一点余量：跳过紧贴路缘弧/狭长臂边界柱
            if (distanceToPolygonBoundary(cell, geometry.fillPolygon()) < 1.25) {
                continue;
            }
            checked++;
            int x = (int) Math.floor(cell.x);
            int z = (int) Math.floor(cell.y);
            if (!hasPavementAt(aggregate, x, 64, z)) {
                missing++;
            }
        }
        assertTrue(checked > 20, "expected enough fillet interior columns, got " + checked);
        assertTrue(missing == 0,
            "fillet interior must be paved without holes; missing=" + missing + " checked=" + checked);
    }

    private static double distanceToPolygonBoundary(Vec2d point, List<Vec2d> polygon) {
        double best = Double.POSITIVE_INFINITY;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = polygon.get(i);
            Vec2d b = polygon.get((i + 1) % n);
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double lenSq = dx * dx + dy * dy;
            double t = lenSq < 1e-18 ? 0.0
                : Math.max(0.0, Math.min(1.0, ((point.x - a.x) * dx + (point.y - a.y) * dy) / lenSq));
            double px = a.x + t * dx;
            double py = a.y + t * dy;
            best = Math.min(best, Math.hypot(point.x - px, point.y - py));
        }
        return best;
    }

    private static boolean hasPavementAt(RoadGenerationResult result, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockRecord record = result.placementRecords.get(pos);
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = result.placementLayers.get(pos);
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.MARKING;
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

    /** 测试内联，避免对生产 API 额外依赖。 */
    private static final class JunctionPreviewHalfWidth {
        static java.util.function.ToDoubleFunction<RoadEdge> resolver(
                RoadNetwork network, RoadSystemConfig config) {
            return edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks());
        }
    }
}
