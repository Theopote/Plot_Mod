package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import com.plot.plugin.road.pipeline.corridor.JunctionCrossSectionFill;
import com.plot.plugin.road.pipeline.corridor.JunctionExpectedProtectedCollector;
import com.plot.plugin.road.pipeline.corridor.JunctionSurfaceExpectation;
import com.plot.plugin.road.pipeline.corridor.JunctionSurfaceMask;
import com.plot.plugin.road.pipeline.corridor.JunctionTerrainClearance;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.TunnelJunctionSeedResolver;
import com.plot.plugin.road.pipeline.corridor.TunnelPortalSeed;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadGenerationResult;
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
        private JunctionValidationManifest validationManifest = JunctionValidationManifest.empty("");

        public RoadSolidModel getSolids() {
            return solids;
        }

        public JunctionConstructionReport report() {
            return report;
        }

        public JunctionValidationManifest validationManifest() {
            return validationManifest;
        }

        void setReport(JunctionConstructionReport report) {
            this.report = report != null ? report : JunctionConstructionReport.empty("");
        }

        void setValidationManifest(JunctionValidationManifest validationManifest) {
            this.validationManifest = validationManifest != null
                ? validationManifest
                : JunctionValidationManifest.empty("");
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
        return generateJunction(node, network, terrain, networkNodeElevations, null);
    }

    /**
     * @param edgeResults connected edge build profiles for per-cell elevation interpolation
     */
    public JunctionBlocks generateJunction(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations,
            Map<String, RoadGenerationResult> edgeResults) {
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
            blocks, node, network, connectedEdges, junctionY, terrain, edgeResults,
            networkNodeElevations);
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
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
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
            generateSimpleEnvelope(
                blocks, node, network, center, edges, junctionY, terrain, edgeResults, networkNodeElevations);
            return List.of();
        }

        fillPolygon(
            blocks, node, network, edges, polygon, center, junctionY, terrain, edgeResults, networkNodeElevations);
        return polygon;
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        // 边缘情况保护：如果边列表为空，至少生成节点位置的方块
        if (edges.isEmpty()) {
            LOGGER.warn("路口节点边列表为空，生成最小覆盖（中心点）");
            fillJunctionCells(
                blocks, List.of(center), null, node, network, edges, junctionY, terrain, edgeResults,
                networkNodeElevations);
            return;
        }

        int maxWidth = edges.stream()
            .mapToInt(edge -> RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                .totalHardSurfaceWidthBlocks())
            .max()
            .orElse(5);
        int radius = maxWidth + 2;

        List<Vec2d> circle = approximateCirclePolygon(center, radius);
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            circle, generator.coordinateTransformer());
        if (cells.isEmpty()) {
            LOGGER.warn("路口世界格扫描为空，回退到中心点");
            fillJunctionCells(
                blocks, List.of(center), circle, node, network, edges, junctionY, terrain, edgeResults,
                networkNodeElevations);
            return;
        }
        fillJunctionCells(
            blocks, cells, circle, node, network, edges, junctionY, terrain, edgeResults, networkNodeElevations);
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
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            polygon,
            generator.coordinateTransformer());
        if (cells.isEmpty() && center != null) {
            cells = List.of(center);
        }
        fillJunctionCells(
            blocks, cells, polygon, node, network, edges, junctionY, terrain, edgeResults, networkNodeElevations);
    }

    private void fillJunctionCells(
            JunctionBlocks blocks,
            List<Vec2d> cells,
            List<Vec2d> clearancePolygon,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
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
        Integer elevatedDeckY = resolveElevatedDeckY(node, junctionY, generator.getConfig());
        Integer maxClearY = elevatedDeckY != null ? elevatedDeckY - 1 : null;
        Map<ColumnKey, Integer> leftoverTops = JunctionTerrainClearance.leftoverTopByColumn(edgeResults);
        JunctionSurfaceMask surfaceMask = new JunctionSurfaceMask();
        List<JunctionSurfaceExpectation> surfaceExpectations = new ArrayList<>();
        int unresolvedClearance = 0;

        for (Vec2d point : cells) {
            ColumnKey columnKey = new ColumnKey(columnResolver.worldX(point), columnResolver.worldZ(point));
            surfaceMask.expectColumn(columnKey);
            int cellY = junctionY;
            boolean stamped = JunctionCrossSectionFill.fillCell(
                blocks.getSolids(),
                point,
                cellY,
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
                fillMaterialId,
                maxClearY);
            if (stamped) {
                surfaceMask.markStamped(columnKey);
                RoadSolidLayer surfaceLayer = JunctionCrossSectionFill.resolveSurfaceLayer(
                    point, node.getId(), edges, network, generator.getConfig(), unitsPerBlock);
                BlockPos surfacePos = JunctionCrossSectionFill.surfaceBlockPos(point, cellY, materials);
                surfaceExpectations.add(new JunctionSurfaceExpectation(surfacePos, surfaceLayer));
            } else if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                unresolvedClearance++;
            }
            if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                BlockPos column = generator.toBlockPos(point, 0);
                int leftoverTop = leftoverTops.getOrDefault(columnKey, cellY);
                JunctionTerrainClearance.clearOverheadInPolygon(
                    blocks.getSolids(),
                    clearancePolygon,
                    cellY,
                    tunnelThreshold,
                    terrain,
                    column.getX(),
                    column.getZ(),
                    point,
                    elevatedDeckY,
                    leftoverTop);
            }
        }

        blocks.setReport(new JunctionConstructionReport(
            node.getId(),
            surfaceMask.expectedColumnCount(),
            surfaceMask.missingColumnCount(),
            0,
            0,
            unresolvedClearance,
            false,
            0,
            0,
            0,
            0,
            0));

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
            bridgeThreshold,
            maxClearY);

        List<Vec2d> footprint = clearancePolygon != null && clearancePolygon.size() >= 3
            ? List.copyOf(clearancePolygon)
            : List.of();
        Map<String, Integer> elevations = networkNodeElevations != null
            ? networkNodeElevations
            : Map.of(node.getId(), junctionY);
        Map<String, RoadGenerationResult> safeEdgeResults =
            edgeResults != null ? edgeResults : Map.of();
        List<TunnelJunctionSeedResolver.TunnelApproach> tunnelApproaches =
            TunnelJunctionSeedResolver.collectTunnelApproaches(
                network,
                node,
                safeEdgeResults,
                elevations,
                generator.getConfig(),
                (path, segments) -> unitsPerBlock);
        List<TunnelPortalSeed> portalSeeds = TunnelJunctionSeedResolver.collectPortalSeeds(
            tunnelApproaches, footprint, generator.coordinateTransformer());
        TunnelJunctionSeedResolver.UnderpassYBand underpassBand =
            TunnelJunctionSeedResolver.resolveUnderpassYBand(tunnelApproaches);
        // Batters can extend past the plaza polygon; their excavation must also
        // preserve the real sloping upper deck in those world columns.
        var constructionColumns = new java.util.HashSet<ColumnKey>();
        if (elevatedDeckY != null) {
            for (var primitive : blocks.getSolids().primitives()) {
                if (!"minecraft:air".equals(primitive.materialId())) continue;
                var column = generator.toBlockPos(primitive.planPoint(), 0);
                constructionColumns.add(new ColumnKey(column.getX(), column.getZ()));
            }
        }
        List<BlockPos> expectedProtected = JunctionExpectedProtectedCollector.collect(
            network,
            node,
            safeEdgeResults,
            elevations,
            generator.getConfig(),
            generator.coordinateTransformer(),
            (n, net) -> generator.resolveElevatedRoadId(n, net, terrain), constructionColumns);

        blocks.setValidationManifest(new JunctionValidationManifest(
            node.getId(),
            List.copyOf(surfaceExpectations),
            expectedProtected,
            portalSeeds,
            footprint,
            junctionY,
            elevatedDeckY,
            underpassBand.minY(),
            underpassBand.maxY()));
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

    public static Integer resolveElevatedDeckY(RoadNode node, int junctionY, RoadSystemConfig config) {
        if (node == null) {
            return null;
        }
        double clearance;
        if (node.isStackGradeSeparated()) {
            clearance = node.getStackCrossingClearance() != null
                ? node.getStackCrossingClearance()
                : (config != null ? config.getDefaultCrossingClearance() : 4.0);
        } else if (node.isGradeSeparated()) {
            clearance = node.getCrossingClearance() != null
                ? node.getCrossingClearance()
                : (config != null ? config.getDefaultCrossingClearance() : 4.0);
        } else {
            return null;
        }
        return junctionY + (int) Math.ceil(Math.max(1.0, clearance));
    }

    private double estimateUnitsPerBlock(RoadNetwork network, List<RoadEdge> edges) {
        if (generator == null || edges == null || edges.isEmpty()) {
            return 1.0;
        }
        for (RoadEdge edge : edges) {
            List<Vec2d> centerline = RoadJunctionCenterlineResolver.forNetwork(network).apply(edge);
            if (centerline != null && centerline.size() >= 2) {
                double units = generator.estimateCanvasUnitsPerBlock(centerline, List.of());
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
