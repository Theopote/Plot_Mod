package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
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
     * @param edgeResults connected edge results for leftover clearance tops / protected upper deck;
     *     plaza surface itself stays on consensus {@code junctionY} (endpoint snap + corridor trim
     *     keep approach ROAD meeting that plane — see {@code JunctionApproachElevationTest})
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

        // 自动上跨判定结果钉到节点，材质策略/走廊过滤与路口填充共用同一上层道路 ID。
        pinResolvedElevatedRoadId(node, network, terrain);

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

        // 立交过滤上层后可能只剩两臂：区域几何会拒绝虚构四臂广场并回退简单包络，
        // 仍需走清障/保护清单，不能整段跳过路口生成。
        if (degree >= 5) {
            LOGGER.debug("复杂路口（{}条道路汇聚），使用多边形填充（必要时回退凸包）", degree);
        }

        List<Vec2d> polygon = generatePolygonJunction(
            blocks, node, network, connectedEdges, junctionY, terrain, edgeResults,
            networkNodeElevations);
        markingGenerator.generateStopLines(blocks, node, network, connectedEdges, junctionY);
        markingGenerator.generateMarkings(blocks, node, network, connectedEdges, polygon, junctionY);
        Integer elevatedDeckY = resolveElevatedDeckY(node, junctionY, network, generator.getConfig());
        if (elevatedDeckY != null) {
            List<RoadEdge> elevatedEdges = collectElevatedJunctionEdges(node, network, terrain);
            markingGenerator.generateElevatedRailwayContinuation(
                blocks, node, network, elevatedEdges, elevatedDeckY);
        }
        return blocks;
    }

    private List<RoadEdge> collectElevatedJunctionEdges(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        List<RoadEdge> elevated = new ArrayList<>();
        if (node == null || network == null) {
            return elevated;
        }
        if (node.isStackGradeSeparated()) {
            for (String edgeId : node.getConnectedEdgeIds()) {
                if (!node.isStackUpperEdge(edgeId)) {
                    continue;
                }
                RoadEdge edge = network.getEdge(edgeId);
                if (edge != null) {
                    elevated.add(edge);
                }
            }
            return elevated;
        }
        String elevatedRoadId = node.isGradeSeparated()
            ? generator.resolveElevatedRoadId(node, network, terrain)
            : null;
        if (elevatedRoadId == null) {
            return elevated;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && elevatedRoadId.equals(edge.getRoadId())) {
                elevated.add(edge);
            }
        }
        return elevated;
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
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS)
        );
        double cornerRadius = resolveCornerRadius(node, network, generator.getConfig());
        JunctionGeometryResult geometry = RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            edges,
            edge -> hardSurfaceHalfExtent(network, edge),
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network),
            network,
            generator.getConfig());
        List<Vec2d> polygon = geometry.fillPolygon();

        if (polygon.size() < 3) {
            // 立交下层两臂：保护区与铺装区分离——扩大圆仅用于净空/保护，不铺圆形广场路面
            if (node.isGradeSeparated() || node.isStackGradeSeparated()) {
                generateUnderpassClearanceWithoutPlaza(
                    blocks, node, network, center, edges, junctionY, terrain, edgeResults,
                    networkNodeElevations);
                return List.of();
            }
            generateSimpleEnvelope(
                blocks, node, network, center, edges, junctionY, terrain, edgeResults, networkNodeElevations);
            return List.of();
        }

        // 清障外形用完整倒圆角足迹；表面铺装责任区优先为重叠核∪圆角补铺，
        // 进口路臂交给各道路走廊保留原材质。
        fillPolygon(
            blocks, node, network, edges, polygon, geometry.sharpPolygon(), geometry,
            center, junctionY, terrain, edgeResults, networkNodeElevations);
        return polygon;
    }

    private void pinResolvedElevatedRoadId(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (node == null || !node.isGradeSeparated()) {
            return;
        }
        if (node.getElevatedRoadId() != null && !node.getElevatedRoadId().isBlank()) {
            return;
        }
        String resolved = generator.resolveElevatedRoadId(node, network, terrain);
        if (resolved != null && !resolved.isBlank()) {
            node.setElevatedRoadId(resolved);
        }
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
                blocks, List.of(center), List.of(center), null, node, network, edges, junctionY, terrain,
                edgeResults, networkNodeElevations, true);
            return;
        }

        List<Vec2d> circle = protectionCircle(center, edges, network);
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            circle, generator.coordinateTransformer());
        if (cells.isEmpty()) {
            LOGGER.warn("路口世界格扫描为空，回退到中心点");
            fillJunctionCells(
                blocks, List.of(center), List.of(center), circle, node, network, edges, junctionY, terrain,
                edgeResults, networkNodeElevations, true);
            return;
        }
        fillJunctionCells(
            blocks, cells, cells, circle, node, network, edges, junctionY, terrain, edgeResults,
            networkNodeElevations, true);
    }

    /**
     * 立交下层连续道路：ProtectionFootprint 用扩大圆做净空与上层保护；
     * SurfaceFootprint 为空——路面留给走廊自身，禁止圆形广场误铺。
     */
    private void generateUnderpassClearanceWithoutPlaza(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        if (center == null) {
            return;
        }
        List<Vec2d> protection = protectionCircle(center, edges, network);
        if (protection.size() < 3) {
            protection = approximateCirclePolygon(center, 6);
        }
        List<Vec2d> clearanceCells = RoadGeometryUtils.collectWorldCellPlanPoints(
            protection, generator.coordinateTransformer());
        if (clearanceCells.isEmpty()) {
            clearanceCells = List.of(center);
        }
        fillJunctionCells(
            blocks, List.of(), clearanceCells, protection, node, network, edges, junctionY, terrain,
            edgeResults, networkNodeElevations, /*stampSurface*/ false);
    }

    private List<Vec2d> protectionCircle(Vec2d center, List<RoadEdge> edges, RoadNetwork network) {
        int maxWidth = 5;
        if (edges != null) {
            for (RoadEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                maxWidth = Math.max(
                    maxWidth,
                    RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                        .totalHardSurfaceWidthBlocks());
            }
        }
        return approximateCirclePolygon(center, maxWidth + 2);
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
            List<Vec2d> filletedPolygon,
            List<Vec2d> sharpPolygon,
            JunctionGeometryResult geometry,
            Vec2d center,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        // 清障外形：完整倒圆角足迹。表面铺装：区域式时仅重叠核∪圆角补铺。
        List<Vec2d> clearance = filletedPolygon != null && filletedPolygon.size() >= 3
            ? filletedPolygon
            : sharpPolygon;
        if (clearance == null || clearance.size() < 3) {
            clearance = filletedPolygon;
        }
        List<Vec2d> stampFootprint = junctionSurfaceFootprint(geometry, clearance, sharpPolygon);
        List<Vec2d> surfaceCells = new ArrayList<>(RoadGeometryUtils.collectWorldCellPlanPoints(
            stampFootprint,
            generator.coordinateTransformer()));
        // 圆角补铺可能是多个小多边形，合并栅格
        if (geometry != null && geometry.hasRegionZones()) {
            for (List<Vec2d> addition : geometry.filletAdditions()) {
                if (addition == null || addition.size() < 3) {
                    continue;
                }
                surfaceCells.addAll(RoadGeometryUtils.collectWorldCellPlanPoints(
                    addition, generator.coordinateTransformer()));
            }
            surfaceCells = dedupePlanCells(surfaceCells);
        }
        // 清障按完整倒圆角外形栅格；表面按核区∪补铺——二者不得绑死
        List<Vec2d> clearanceCells = clearance != null && clearance.size() >= 3
            ? new ArrayList<>(RoadGeometryUtils.collectWorldCellPlanPoints(
                clearance, generator.coordinateTransformer()))
            : List.of();
        if (surfaceCells.isEmpty() && center != null) {
            surfaceCells = List.of(center);
        }
        if (clearanceCells.isEmpty()) {
            clearanceCells = surfaceCells;
        }
        fillJunctionCells(
            blocks, surfaceCells, clearanceCells, clearance, node, network, edges, junctionY, terrain,
            edgeResults, networkNodeElevations, true);
    }

    /**
     * 区域式：路口表面责任区 = 重叠核（圆角补铺另栅格合并）。
     * 旧路径：整块 fill/sharp 足迹。
     */
    private static List<Vec2d> junctionSurfaceFootprint(
            JunctionGeometryResult geometry,
            List<Vec2d> clearance,
            List<Vec2d> sharpPolygon) {
        if (geometry != null && geometry.hasRegionZones()) {
            return geometry.overlapRegion();
        }
        if (clearance != null && clearance.size() >= 3) {
            return clearance;
        }
        return sharpPolygon != null ? sharpPolygon : List.of();
    }

    private static List<Vec2d> dedupePlanCells(List<Vec2d> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<Long, Vec2d> unique = new java.util.LinkedHashMap<>();
        for (Vec2d cell : cells) {
            if (cell == null) {
                continue;
            }
            int x = (int) Math.floor(cell.x);
            int z = (int) Math.floor(cell.y);
            unique.putIfAbsent((((long) x) << 32) ^ (z & 0xffffffffL), cell);
        }
        return new ArrayList<>(unique.values());
    }

    private void fillJunctionCells(
            JunctionBlocks blocks,
            List<Vec2d> surfaceCells,
            List<Vec2d> clearanceCells,
            List<Vec2d> clearancePolygon,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations,
            boolean stampSurface) {
        if (node == null || network == null || edges == null) {
            return;
        }
        List<Vec2d> stamp = surfaceCells != null ? surfaceCells : List.of();
        List<Vec2d> clearCells = clearanceCells != null && !clearanceCells.isEmpty()
            ? clearanceCells
            : stamp;
        if (stamp.isEmpty() && clearCells.isEmpty()) {
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
        Integer elevatedDeckY = resolveElevatedDeckY(node, junctionY, network, generator.getConfig());
        Map<String, Integer> elevations = networkNodeElevations != null
            ? networkNodeElevations
            : Map.of(node.getId(), junctionY);
        Map<String, RoadGenerationResult> safeEdgeResults =
            edgeResults != null ? edgeResults : Map.of();
        // 必须在广场清障/放坡之前收集隧道进口，否则会按露天逻辑掏穿洞顶。
        List<TunnelJunctionSeedResolver.TunnelApproach> tunnelApproaches =
            TunnelJunctionSeedResolver.collectTunnelApproaches(
                network,
                node,
                safeEdgeResults,
                elevations,
                generator.getConfig(),
                (path, segments) -> unitsPerBlock);
        Integer tunnelCavityCap = TunnelJunctionSeedResolver.resolveTunnelCavityClearCap(tunnelApproaches);
        // 净空上限：立交桥底与隧道洞腔顶取更严者。
        Integer maxClearY = elevatedDeckY != null
            ? elevatedDeckY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH - 1
            : null;
        if (tunnelCavityCap != null) {
            maxClearY = maxClearY == null ? tunnelCavityCap : Math.min(maxClearY, tunnelCavityCap);
        }
        Map<ColumnKey, Integer> leftoverTops = JunctionTerrainClearance.leftoverTopByColumn(edgeResults);
        JunctionSurfaceMask surfaceMask = new JunctionSurfaceMask();
        List<JunctionSurfaceExpectation> surfaceExpectations = new ArrayList<>();
        int unresolvedClearance = 0;

        if (stampSurface) {
            for (Vec2d point : stamp) {
                if (point == null) {
                    continue;
                }
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
                    fillMaterialId,
                    maxClearY);
                if (stamped) {
                    surfaceMask.markStamped(columnKey);
                    RoadSolidLayer surfaceLayer = JunctionCrossSectionFill.resolveSurfaceLayer(
                        point, node.getId(), edges, network, generator.getConfig(), unitsPerBlock);
                    BlockPos surfacePos = JunctionCrossSectionFill.surfaceBlockPos(point, junctionY, materials);
                    surfaceExpectations.add(new JunctionSurfaceExpectation(surfacePos, surfaceLayer));
                } else if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                    unresolvedClearance++;
                }
            }
        }

        if (clearancePolygon != null && clearancePolygon.size() >= 3) {
            // 清障列 = 外形栅格 ∪ 实际铺装列，避免核区/补铺落点因栅格缝漏清树木
            List<Vec2d> overheadCells = new ArrayList<>(clearCells);
            if (stampSurface && !stamp.isEmpty()) {
                overheadCells.addAll(stamp);
                overheadCells = dedupePlanCells(overheadCells);
            }
            for (Vec2d point : overheadCells) {
                if (point == null) {
                    continue;
                }
                ColumnKey columnKey = new ColumnKey(columnResolver.worldX(point), columnResolver.worldZ(point));
                BlockPos column = generator.toBlockPos(point, 0);
                int leftoverTop = leftoverTops.getOrDefault(columnKey, junctionY);
                // 列集已由外形∪铺装栅格确定；勿再 point-in-polygon，否则圆角补铺列会漏清树木
                JunctionTerrainClearance.clearOverheadColumn(
                    blocks.getSolids(),
                    junctionY,
                    tunnelThreshold,
                    terrain,
                    column.getX(),
                    column.getZ(),
                    point,
                    elevatedDeckY,
                    leftoverTop,
                    tunnelCavityCap);
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

        // 隧道进口禁止开敞切坡：与走廊 RoadEarthworkPolicy 一致，避免洞内十字边坡掏山。
        if (stampSurface && tunnelApproaches.isEmpty()) {
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
        }

        List<Vec2d> footprint = clearancePolygon != null && clearancePolygon.size() >= 3
            ? List.copyOf(clearancePolygon)
            : List.of();
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

    public static Integer resolveElevatedDeckY(
            RoadNode node, int junctionY, RoadNetwork network, RoadSystemConfig config) {
        if (node == null) {
            return null;
        }
        double clearance;
        if (node.isStackGradeSeparated()) {
            String roadId = null;
            if (network != null) {
                for (String edgeId : node.getConnectedEdgeIds()) {
                    RoadEdge edge = network.getEdge(edgeId);
                    if (edge != null && edge.getRoadId() != null) {
                        roadId = edge.getRoadId();
                        break;
                    }
                }
            }
            clearance = com.plot.plugin.road.crossing.GradeSeparationClearance.resolve(
                node.getStackCrossingClearance(),
                network != null && roadId != null ? network.getRoad(roadId) : null,
                config);
        } else if (node.isGradeSeparated()) {
            clearance = com.plot.plugin.road.crossing.GradeSeparationClearance.forNode(
                node.getCrossingClearance(),
                node,
                network,
                node.getElevatedRoadId(),
                config);
        } else {
            return null;
        }
        return junctionY + (int) Math.ceil(Math.max(1.0, clearance));
    }

    public static Integer resolveElevatedDeckY(RoadNode node, int junctionY, RoadSystemConfig config) {
        return resolveElevatedDeckY(node, junctionY, null, config);
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

    /**
     * 与画布预览一致：平交圆角以 Crossing 为权威，否则用节点/网络默认。
     */
    public static double resolveCornerRadius(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        double networkDefault = config != null ? config.getDefaultCornerRadius() : RoadNode.DEFAULT_CORNER_RADIUS;
        if (node != null && (node.isGradeSeparated() || node.isStackGradeSeparated())) {
            return 0.0;
        }
        RoadCrossing crossing = findAtGradeCrossingNear(network, node);
        if (crossing != null) {
            return crossing.effectiveCornerRadius(networkDefault);
        }
        return node != null ? node.getEffectiveCornerRadius(networkDefault) : networkDefault;
    }

    private static RoadCrossing findAtGradeCrossingNear(RoadNetwork network, RoadNode node) {
        if (network == null || node == null || node.getPosition() == null) {
            return null;
        }
        RoadCrossing best = null;
        double bestDistance = 0.75;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing == null
                    || crossing.type() != CrossingType.AT_GRADE
                    || crossing.position() == null) {
                continue;
            }
            double distance = crossing.position().distance(node.getPosition());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = crossing;
            }
        }
        return best;
    }
}
