package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import com.plot.plugin.road.pipeline.corridor.JunctionCrossSectionFill;
import com.plot.plugin.road.pipeline.corridor.JunctionSurfaceMask;
import com.plot.plugin.road.pipeline.corridor.JunctionTerrainClearance;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路口路面生成
 */
public class RoadJunctionGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadJunctionGenerator");

    /**
     * 路口实体蓝图（平面坐标 + 标高 + 层），落地前不含 BlockPos。
     */
    public static class JunctionBlocks {
        private final RoadSolidModel solids = new RoadSolidModel();
        private JunctionConstructionReport report = JunctionConstructionReport.empty("");

        public RoadSolidModel getSolids() {
            return solids;
        }

        public JunctionConstructionReport report() {
            return report;
        }

        void setReport(JunctionConstructionReport report) {
            this.report = report != null ? report : JunctionConstructionReport.empty("");
        }

        public boolean isEmpty() {
            return solids.isEmpty();
        }
    }

    private final RoadGenerator generator;
    private final RoadJunctionMarkingGenerator markingGenerator;

    public RoadJunctionGenerator(RoadGenerator generator) {
        this.generator = generator;
        this.markingGenerator = new RoadJunctionMarkingGenerator(generator);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, World world) {
        if (node == null || network == null || generator == null || world == null) {
            return new JunctionBlocks();
        }
        return generateJunction(node, network, generator.createTerrainSampler(world), null);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return generateJunction(node, network, terrain, null);
    }

    /**
     * @param networkNodeElevations 路网统一节点标高；非 null 时路口面直接使用该高度
     */
    public JunctionBlocks generateJunction(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations) {
        JunctionBlocks blocks = new JunctionBlocks();
        if (generator != null) {
            blocks.getSolids().setCoordinateService(generator.coordinateTransformer());
        }
        if (node == null || network == null || generator == null || terrain == null) {
            return blocks;
        }

        int degree = node.getDegree();
        if (degree <= 2) {
            return blocks;
        }

        int junctionY;
        if (networkNodeElevations != null && networkNodeElevations.containsKey(node.getId())) {
            junctionY = networkNodeElevations.get(node.getId());
        } else {
            junctionY = generator.computeJunctionTargetHeight(node, network, terrain);
        }
        List<RoadEdge> connectedEdges = collectUnderpassJunctionEdges(node, network, terrain);
        if (connectedEdges.isEmpty()) {
            return blocks;
        }

        if (degree >= 5) {
            LOGGER.debug("复杂路口（{}条道路汇聚），使用多边形填充（必要时回退凸包）", degree);
        }

        List<Vec2d> polygon = generatePolygonJunction(
            blocks, node, network, connectedEdges, junctionY, terrain);
        markingGenerator.generateStopLines(blocks, node, network, connectedEdges, junctionY);
        markingGenerator.generateMarkings(blocks, node, network, connectedEdges, polygon, junctionY);
        return blocks;
    }

    private List<Vec2d> generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain) {
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> hardSurfaceHalfExtent(network, edge),
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );
        double cornerRadius = node.getEffectiveCornerRadius(generator.getConfig().getDefaultCornerRadius());
        List<Vec2d> polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            edge -> hardSurfaceHalfExtent(network, edge),
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));

        if (polygon.size() < 3) {
            generateSimpleEnvelope(blocks, node, network, center, edges, junctionY, terrain);
            return List.of();
        }

        fillPolygon(blocks, node, network, edges, polygon, center, junctionY, terrain);
        return polygon;
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain) {
        // 边缘情况保护：如果边列表为空，至少生成节点位置的方块
        if (edges.isEmpty()) {
            LOGGER.warn("路口节点边列表为空，生成最小覆盖（中心点）");
            fillJunctionCells(
                blocks, List.of(center), null, node, network, edges, junctionY, terrain);
            return;
        }

        int maxWidth = edges.stream()
            .mapToInt(edge -> RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                .totalHardSurfaceWidthBlocks())
            .max()
            .orElse(5);
        int radius = maxWidth + 2;

        List<Vec2d> envelopePoints = RoadJunctionGeometry.collectSimpleEnvelopePoints(center, radius);
        // 再次保护：如果包络点为空，至少生成中心点
        if (envelopePoints.isEmpty()) {
            LOGGER.warn("路口简单包络点为空，回退到中心点");
            fillJunctionCells(
                blocks, List.of(center), null, node, network, edges, junctionY, terrain);
            return;
        }

        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            approximateCirclePolygon(center, radius),
            generator.coordinateTransformer());
        List<Vec2d> circle = approximateCirclePolygon(center, radius);
        if (cells.isEmpty()) {
            fillJunctionCells(blocks, envelopePoints, circle, node, network, edges, junctionY, terrain);
            return;
        }
        fillJunctionCells(blocks, cells, circle, node, network, edges, junctionY, terrain);
    }

    private static List<Vec2d> approximateCirclePolygon(Vec2d center, int radius) {
        if (center == null || radius <= 0) {
            return List.of();
        }
        int segments = Math.max(12, radius * 4);
        List<Vec2d> polygon = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            polygon.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)));
        }
        return polygon;
    }

    private void fillPolygon(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            List<Vec2d> polygon,
            Vec2d center,
            int junctionY,
            TerrainSampler terrain) {
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            polygon,
            generator.coordinateTransformer());
        if (cells.isEmpty() && center != null) {
            cells = List.of(center);
        }
        fillJunctionCells(blocks, cells, polygon, node, network, edges, junctionY, terrain);
    }

    private void fillJunctionCells(
            JunctionBlocks blocks,
            List<Vec2d> cells,
            List<Vec2d> clearancePolygon,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain) {
        if (cells == null || cells.isEmpty() || node == null || network == null || edges == null) {
            return;
        }
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(generator.getConfig());
        int bridgeThreshold = RoadConstructionHeuristics.bridgeThreshold(generator.getConfig());
        String fillMaterialId = generator.getBlockIdFromMaterial(generator.getConfig().getFillSlopeMaterial());
        JunctionCrossSectionFill.MaterialResolver materials = new JunctionCrossSectionFill.MaterialResolver() {
            @Override
            public String resolve(String materialId) {
                return generator.getBlockIdFromMaterial(materialId);
            }

            @Override
            public BlockPos canvasToBlockPos(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0);
            }
        };
        RoadTerrainClearanceUtils.BlockColumnResolver columnResolver =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return generator.toBlockPos(planPoint, 0).getX();
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return generator.toBlockPos(planPoint, 0).getZ();
                }
            };
        double unitsPerBlock = estimateUnitsPerBlock(network, edges);
        JunctionSurfaceMask surfaceMask = new JunctionSurfaceMask();
        int unresolvedClearance = 0;

        for (Vec2d point : cells) {
            ColumnKey columnKey = new ColumnKey(columnResolver.worldX(point), columnResolver.worldZ(point));
            surfaceMask.expectColumn(columnKey);
            boolean stamped = JunctionCrossSectionFill.fillCell(
                blocks.getSolids(),
                point,
                junctionY,
                node.getId(),
                edges,
                network,
                generator.getConfig(),
                materials,
                columnResolver,
                unitsPerBlock,
                terrain,
                tunnelThreshold,
                bridgeThreshold,
                fillMaterialId);
            if (stamped) {
                surfaceMask.markStamped(columnKey);
                if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                    BlockPos column = generator.toBlockPos(point, 0);
                    JunctionTerrainClearance.clearOverheadInPolygon(
                        blocks.getSolids(),
                        clearancePolygon,
                        junctionY,
                        tunnelThreshold,
                        terrain,
                        column.getX(),
                        column.getZ(),
                        point);
                }
            } else if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                unresolvedClearance++;
            }
        }

        blocks.setReport(new JunctionConstructionReport(
            node.getId(),
            surfaceMask.expectedColumnCount(),
            surfaceMask.missingColumnCount(),
            0,
            0,
            unresolvedClearance,
            false));

        JunctionCrossSectionFill.stampApproachSlopeBatters(
            blocks.getSolids(),
            node.getPosition(),
            junctionY,
            node.getId(),
            edges,
            network,
            generator.getConfig(),
            materials,
            columnResolver,
            unitsPerBlock,
            terrain,
            bridgeThreshold);
    }

    private List<RoadEdge> collectUnderpassJunctionEdges(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        List<RoadEdge> connectedEdges = new ArrayList<>();
        String elevatedRoadId = node.isGradeSeparated()
            ? generator.resolveElevatedRoadId(node, network, terrain)
            : null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            // Self-intersect overpass arms keep their own corridor; only fill underpass.
            if (node.isStackGradeSeparated() && node.isStackUpperEdge(edgeId)) {
                continue;
            }
            if (elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            connectedEdges.add(edge);
        }
        return connectedEdges;
    }

    private double estimateUnitsPerBlock(RoadNetwork network, List<RoadEdge> edges) {
        if (generator == null || edges == null || edges.isEmpty()) {
            return 1.0;
        }
        for (RoadEdge edge : edges) {
            List<Vec2d> centerline = RoadJunctionCenterlineResolver.forNetwork(network).apply(edge);
            if (centerline != null && centerline.size() >= 2) {
                double units = generator.estimateCanvasUnitsPerBlock(centerline, List.<PathSegment>of());
                if (Double.isFinite(units) && units > 1e-9) {
                    return units;
                }
            }
        }
        return 1.0;
    }

    private double hardSurfaceHalfExtent(RoadNetwork network, RoadEdge edge) {
        return RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                .totalHardSurfaceWidthBlocks());
    }
}
