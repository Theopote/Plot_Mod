package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.client.MinecraftClient;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路生成器
 * 
 * 负责将2D路径转换为3D道路方块，包括：
 * - 路径采样和分段
 * - 地形高度检测
 * - 坡度限制和调整
 * - 桥/隧道检测和生成
 * - 挖填方计算
 */
public class RoadGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadGenerator");
    
    private final RoadSystemConfig config;
    private final CoordinateTransformer coordinateTransformer;
    
    public RoadSystemConfig getConfig() {
        return config;
    }

    public TerrainSampler createTerrainSampler(World world) {
        return MinecraftTerrainSampler.of(world, coordinateTransformer);
    }

    public RoadGenerator(RoadSystemConfig config, CoordinateTransformer coordinateTransformer) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
    }
    
    private static final class EdgeBuildMetrics {
        int cutVolume;
        int fillVolume;
        int bridgeCount;
        int tunnelCount;
    }
    
    /**
     * 生成道路
     * 
     * @param path 路径图形（PolylineShape、FreeDrawPath或BezierCurveShape）
     * @return 道路生成结果
     */
    public RoadGenerationResult generateRoad(Shape path) {
        if (path == null) {
            LOGGER.warn("路径为空，无法生成道路");
            return new RoadGenerationResult(0);
        }
        
        try {
            // 1. 从路径中提取点列表
            List<Vec2d> pathPoints = extractPathPoints(path);
            if (pathPoints == null || pathPoints.size() < 2) {
                LOGGER.warn("路径点数不足，无法生成道路");
                return new RoadGenerationResult(0);
            }
            
            LOGGER.info("开始生成道路，路径点数: {}", pathPoints.size());
            
            // 2. 采样路径点（细分以确保足够的采样密度）
            List<PathSegment> segments = samplePath(pathPoints);
            LOGGER.debug("路径分段数: {}", segments.size());
            
            // 3. 获取Minecraft世界（客户端），使用缓存引用避免TOCTOU问题
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                LOGGER.error("无法获取Minecraft客户端");
                return new RoadGenerationResult(0);
            }

            // 缓存world引用，避免在检查后使用前变为null
            World world = client.world;
            if (world == null) {
                LOGGER.error("无法获取世界实例");
                return new RoadGenerationResult(0);
            }

            TerrainSampler terrain = MinecraftTerrainSampler.of(world, coordinateTransformer);
            
            // 4. 计算每个分段的目标高度（考虑坡度限制）
            SegmentHeightCalculation heightCalculation = calculateSegmentHeights(segments, terrain);
            double pathLength = segments.stream().mapToDouble(s -> s.distance).sum();
            ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);

            // 5–6. 检测桥/隧道并生成 solids
            RoadGenerationResult result = buildFromCenterline(
                pathPoints, terrain, crossSection, heightCalculation.heightInfos(), pathLength);
            result.copyProfileFrom(toProfileResult(heightCalculation));
            
            LOGGER.info("道路生成完成: 挖{} 填{} 桥{}座 隧道{}段", 
                result.cutVolume, result.fillVolume, result.bridgeCount, result.tunnelCount);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("生成道路时发生错误: {}", e.getMessage(), e);
            return new RoadGenerationResult(0);
        }
    }
    
    private List<Vec2d> extractPathPoints(Shape path) {
        return RoadGeometryUtils.extractShapePoints(path);
    }
    
    /**
     * 基于路网边生成道路（不依赖 Shape）
     */
    public RoadGenerationResult generateEdge(
            RoadNetwork network, RoadEdge edge, RoadNode startNode, RoadNode endNode, World world) {
        if (edge == null || world == null) {
            LOGGER.warn("道路边或世界为空，无法生成");
            return new RoadGenerationResult(0);
        }
        return generateEdge(
            network, edge, startNode, endNode, MinecraftTerrainSampler.of(world, coordinateTransformer), null);
    }

    /**
     * 基于路网边生成道路；{@code networkNodeElevations} 为路网统一节点标高（两遍求解第二遍使用）。
     */
    public RoadGenerationResult generateEdge(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations) {
        if (edge == null || terrain == null) {
            LOGGER.warn("道路边或地形为空，无法生成");
            return new RoadGenerationResult(0);
        }

        List<Vec2d> pathPoints = edge.getCenterlinePoints();
        if (pathPoints.size() < 2) {
            LOGGER.warn("道路中心线点数不足");
            return new RoadGenerationResult(0);
        }

        try {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, edge, config);
            List<PathSegment> segments = samplePath(pathPoints);
            SegmentHeightCalculation heightCalculation = calculateSegmentHeightsForEdge(
                segments, terrain, network, edge, startNode, endNode, true, networkNodeElevations);
            RoadGenerationResult result = buildFromCenterline(
                pathPoints, terrain, crossSection, heightCalculation.heightInfos(), edge.getLength());
            result.edgeId = edge.getId();
            result.copyProfileFrom(toProfileResult(heightCalculation));
            return result;
        } catch (Exception e) {
            LOGGER.error("生成道路边失败: {}", e.getMessage(), e);
            return new RoadGenerationResult(0);
        }
    }

    /**
     * 基于中心线与横断面生成道路（测试用，不依赖 Minecraft World）。
     */
    RoadGenerationResult generateFromPathPoints(List<Vec2d> pathPoints, TerrainSampler terrain) {
        return generateFromPathPoints(pathPoints, terrain, null);
    }

    RoadGenerationResult generateFromPathPoints(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            Integer manualRoadElevation) {
        if (pathPoints == null || pathPoints.size() < 2 || terrain == null) {
            return new RoadGenerationResult(0);
        }
        List<PathSegment> segments = samplePath(pathPoints);
        SegmentHeightCalculation heightCalculation = manualRoadElevation != null
            ? calculateSegmentHeightsWithManualElevation(segments, terrain, manualRoadElevation)
            : calculateSegmentHeights(segments, terrain);
        double pathLength = segments.stream().mapToDouble(s -> s.distance).sum();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        RoadGenerationResult result = buildFromCenterline(
            pathPoints, terrain, crossSection, heightCalculation.heightInfos(), pathLength);
        result.copyProfileFrom(toProfileResult(heightCalculation));
        return result;
    }

    private SegmentHeightCalculation calculateSegmentHeightsWithManualElevation(
            List<PathSegment> segments,
            TerrainSampler terrain,
            int manualRoadElevation) {
        if (segments.isEmpty()) {
            return emptyHeightCalculation();
        }
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        HeightSampleData sampleData = collectHeightSamples(segments, terrain, halfWidth);
        return buildSegmentHeights(
            segments,
            sampleData,
            List.of(),
            manualRoadElevation,
            manualRoadElevation,
            segmentIndex -> config.getMaxSlope());
    }

    private RoadGenerationResult buildFromCenterline(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            List<SegmentHeightInfo> heightInfos,
            double pathLength) {
        List<PathSegment> segments = samplePath(pathPoints);
        ConstructionDetection detection = detectConstruction(segments, heightInfos, terrain);
        List<BridgeSegment> bridges = detection.bridges();
        List<TunnelSegment> tunnels = detection.tunnels();

        // 横向 1 格 = 1 世界方块：补偿画布坐标经相机映射后的缩放
        double unitsPerBlock = estimateCanvasUnitsPerBlock(pathPoints, segments);

        RoadSolidModel solids = new RoadSolidModel();
        EdgeBuildMetrics metrics = new EdgeBuildMetrics();
        generateCarriagewayBlocks(
            solids, metrics, segments, heightInfos, bridges, tunnels, terrain,
            crossSection.carriagewayWidth,
            getBlockIdFromMaterial(crossSection.carriagewayMaterial),
            unitsPerBlock);

        int shoulderWidth = crossSection.includeShoulder ? crossSection.shoulderWidth : 0;
        if (crossSection.includeShoulder && shoulderWidth > 0) {
            double shoulderCenterOffset = crossSection.shoulderCenterOffset() * unitsPerBlock;
            generateShoulderBlocks(solids, segments, heightInfos, shoulderCenterOffset,
                shoulderWidth, getBlockIdFromMaterial(crossSection.shoulderMaterial), unitsPerBlock);
            generateSlopeBatterBlocks(solids, metrics, segments, heightInfos, shoulderCenterOffset,
                shoulderWidth, pathPoints, terrain, crossSection, unitsPerBlock);
        }

        if (crossSection.includeBikeLane && crossSection.bikeLaneWidth > 0) {
            generateBikeLaneBlocks(solids, segments, heightInfos,
                crossSection.bikeLaneCenterOffset() * unitsPerBlock,
                crossSection.bikeLaneWidth,
                getBlockIdFromMaterial(crossSection.bikeLaneMaterial),
                unitsPerBlock);
        }

        if (crossSection.includeSidewalk && crossSection.sidewalkWidth > 0) {
            generateSidewalkBlocks(solids, segments, heightInfos,
                crossSection.sidewalkCenterOffset() * unitsPerBlock,
                crossSection.sidewalkWidth,
                getBlockIdFromMaterial(crossSection.sidewalkMaterial),
                unitsPerBlock);
        }

        if (crossSection.includeDrain) {
            double drainageOffset = crossSection.outerDrainageOffset() * unitsPerBlock;
            generateDrainageChannels(solids, segments, heightInfos, drainageOffset,
                getBlockIdFromMaterial("material.plot.gravel"), unitsPerBlock);
        }

        if (crossSection.includeMedian && crossSection.medianWidth > 0) {
            double halfMedian = RoadDimensionUtils.halfExtentFromCenter(crossSection.medianWidth) * unitsPerBlock;
            List<Vec2d> leftMedian = OffsetHandler.offsetPolyline(pathPoints, -halfMedian);
            List<Vec2d> rightMedian = OffsetHandler.offsetPolyline(pathPoints, halfMedian);
            generateMedianBlocks(
                solids,
                segments,
                heightInfos,
                leftMedian,
                rightMedian,
                getBlockIdFromMaterial(crossSection.medianMaterial));
        }

        if (crossSection.laneDividers || crossSection.centerLineStyle != CenterLineStyle.NONE) {
            generateLaneMarkings(solids, segments, heightInfos, pathPoints, crossSection, unitsPerBlock);
        }

        Integer spacing = crossSection.streetlightSpacing;
        if (spacing != null && spacing > 0) {
            generateStreetlights(solids, pathPoints, terrain, crossSection, unitsPerBlock);
        }

        gradeRoadEnvelope(solids, metrics, segments, heightInfos, crossSection, terrain, unitsPerBlock);

        RoadGenerationResult result = new RoadGenerationResult(pathLength);
        result.cutVolume = metrics.cutVolume;
        result.fillVolume = metrics.fillVolume;
        result.bridgeCount = metrics.bridgeCount;
        result.tunnelCount = metrics.tunnelCount;
        applyConstructionStats(result, detection);
        RoadVoxelRasterizer.flushEdgeSolids(result, solids, coordinateTransformer);
        return result;
    }

    public void mergeResult(RoadGenerationResult target, RoadGenerationResult source) {
        if (target != null) {
            target.mergeFrom(source);
        }
    }

    public void mergeJunction(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        if (target == null || junction == null) {
            return;
        }
        RoadVoxelRasterizer.flushJunctionSolids(
            target,
            junction.getSolids(),
            coordinateTransformer,
            roadBlockId,
            sidewalkBlockId,
            markingBlockId);
    }

    public void mergeJunctionBlocks(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            String roadBlockId,
            String sidewalkBlockId) {
        mergeJunction(target, junction, roadBlockId, sidewalkBlockId, null);
    }

    public void mergeJunctionMarkings(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            String markingBlockId) {
        if (target == null || junction == null || markingBlockId == null) {
            return;
        }
        RoadVoxelRasterizer.flushJunctionSolids(
            target,
            junction.getSolids(),
            coordinateTransformer,
            null,
            null,
            markingBlockId);
    }

    /**
     * @deprecated 使用带材质参数的 {@link #mergeJunctionBlocks(RoadGenerationResult, RoadJunctionGenerator.JunctionBlocks, String, String)}
     *             计划在 v2.0 版本移除
     */
    @Deprecated(since = "1.x", forRemoval = true)
    public void mergeJunctionBlocks(RoadGenerationResult target, RoadJunctionGenerator.JunctionBlocks junction) {
        mergeJunctionBlocks(
            target,
            junction,
            getBlockIdFromMaterial(config.getSelectedMaterial()),
            getBlockIdFromMaterial(config.getSelectedMaterial())
        );
    }

    /**
     * 计算路口节点处的目标路面高度（汇聚各相连边在节点处的高度均值）
     */
    public int computeJunctionTargetHeight(RoadNode node, RoadNetwork network, World world) {
        if (node == null || network == null || world == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        return computeJunctionTargetHeight(node, network, MinecraftTerrainSampler.of(world, coordinateTransformer));
    }

    public int computeJunctionTargetHeight(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        if (node == null || network == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        Map<String, Integer> resolved = resolveNetworkNodeElevations(network, terrain);
        Integer height = resolved.get(node.getId());
        return height != null ? height : getGroundHeightAtNode(terrain, node, network);
    }

    /**
     * 路网节点统一标高（第一遍）：按各边自然高程决议，供边生成第二遍强制对齐端点。
     * 立体交叉节点存的是下层（非跨越方）标高；跨越方高度仍由 {@link #resolveElevatedCrossingHeight} 处理。
     */
    public Map<String, Integer> resolveNetworkNodeElevations(RoadNetwork network, TerrainSampler terrain) {
        Map<String, Integer> resolved = new LinkedHashMap<>();
        if (network == null || terrain == null) {
            return resolved;
        }

        Map<String, List<Integer>> naturalHeightsByNode = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode startNode = network.getNode(edge.getStartNodeId());
            RoadNode endNode = network.getNode(edge.getEndNodeId());
            if (startNode == null || endNode == null) {
                continue;
            }
            List<PathSegment> segments = samplePath(edge.getCenterlinePoints());
            if (segments.isEmpty()) {
                continue;
            }
            List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
                segments, terrain, network, edge, startNode, endNode, false, null).heightInfos();
            if (heightInfos.isEmpty()) {
                continue;
            }
            collectNaturalHeightSample(
                naturalHeightsByNode, startNode, edge, heightInfos.getFirst().targetStart, network, terrain);
            collectNaturalHeightSample(
                naturalHeightsByNode, endNode, edge, heightInfos.getLast().targetEnd, network, terrain);
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node.getManualElevation() != null) {
                resolved.put(node.getId(), node.getManualElevation().intValue());
                continue;
            }
            List<Integer> samples = naturalHeightsByNode.getOrDefault(node.getId(), List.of());
            if (samples.isEmpty()) {
                resolved.put(node.getId(), getGroundHeightAtNode(terrain, node, network));
                continue;
            }
            RoadSlopeUtils.JunctionHeightResolution resolution =
                RoadSlopeUtils.resolveJunctionHeight(samples);
            if (resolution.isSignificantMismatch()) {
                LOGGER.info(
                    "路口/节点 {} 自然高程散布较大 {}（spread={}），统一到 Y={}",
                    node.getId(), samples, resolution.spread(), resolution.height());
            }
            resolved.put(node.getId(), resolution.height());
        }
        return resolved;
    }

    private void collectNaturalHeightSample(
            Map<String, List<Integer>> naturalHeightsByNode,
            RoadNode node,
            RoadEdge edge,
            int naturalHeight,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (node == null || edge == null) {
            return;
        }
        // 立体交叉：下层共识不含跨越方自然高程
        if (node.isGradeSeparated()) {
            String elevatedRoadId = resolveElevatedRoadId(node, network, terrain);
            if (elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
                return;
            }
        }
        naturalHeightsByNode
            .computeIfAbsent(node.getId(), id -> new ArrayList<>())
            .add(naturalHeight);
    }

    /**
     * 获取单条边在指定节点处的目标路面高度
     */
    public int getTargetHeightAtNode(RoadEdge edge, RoadNode node, RoadNetwork network, World world) {
        if (edge == null || node == null || world == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        return getTargetHeightAtNode(edge, node, network, MinecraftTerrainSampler.of(world, coordinateTransformer));
    }

    int getTargetHeightAtNode(RoadEdge edge, RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        if (edge == null || node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }

        Integer elevatedTarget = resolveElevatedCrossingHeight(edge, node, network, terrain);
        if (elevatedTarget != null) {
            return elevatedTarget;
        }

        RoadNode startNode = network != null ? network.getNode(edge.getStartNodeId()) : null;
        List<PathSegment> segments = samplePath(edge.getCenterlinePoints());
        if (segments.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }

        List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, startNode, node).heightInfos();
        if (heightInfos.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }

        if (edge.getStartNodeId().equals(node.getId())) {
            return heightInfos.getFirst().targetStart;
        }
        if (edge.getEndNodeId().equals(node.getId())) {
            return heightInfos.getLast().targetEnd;
        }
        return getGroundHeightAtNode(terrain, node, network);
    }

    /**
     * 确定立体交叉的跨越方道路 ID。手动指定时直接返回；否则按自然高度自动判断（不写入节点）。
     */
    public String resolveElevatedRoadId(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        if (node == null || network == null || terrain == null || !node.isGradeSeparated()) {
            return null;
        }
        if (node.getElevatedRoadId() != null && !node.getElevatedRoadId().isBlank()) {
            return node.getElevatedRoadId();
        }

        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return null;
        }

        String highestRoadId = null;
        int highestHeight = Integer.MIN_VALUE;
        for (String roadId : roadIds) {
            int naturalHeight = computeRoadNaturalHeightAtNode(node, network, terrain, roadId);
            if (naturalHeight > highestHeight) {
                highestHeight = naturalHeight;
                highestRoadId = roadId;
            }
        }
        return highestRoadId;
    }

    private int computeRoadNaturalHeightAtNode(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            String roadId) {
        List<Integer> heights = new ArrayList<>();
        for (RoadEdge edge : network.getEdgesAtNode(node.getId())) {
            if (!roadId.equals(edge.getRoadId())) {
                continue;
            }
            heights.add(getTargetHeightAtNodeIgnoringGradeSeparation(edge, node, network, terrain));
        }
        if (heights.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
    }

    int getTargetHeightAtNodeIgnoringGradeSeparation(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (edge == null || node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }

        RoadNode edgeStart = network != null ? network.getNode(edge.getStartNodeId()) : null;
        RoadNode edgeEnd = network != null ? network.getNode(edge.getEndNodeId()) : null;
        List<PathSegment> segments = samplePath(edge.getCenterlinePoints());
        if (segments.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }

        List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, edgeStart, edgeEnd, false).heightInfos();
        if (heightInfos.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }

        if (edge.getStartNodeId().equals(node.getId())) {
            return heightInfos.getFirst().targetStart;
        }
        if (edge.getEndNodeId().equals(node.getId())) {
            return heightInfos.getLast().targetEnd;
        }
        return getGroundHeightAtNode(terrain, node, network);
    }

    private Integer resolveForcedHeightAtNode(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation) {
        if (node == null) {
            return null;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        if (!applyGradeSeparation) {
            return null;
        }
        return resolveElevatedCrossingHeight(edge, node, network, terrain);
    }

    private Integer resolveElevatedCrossingHeight(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (node == null || edge == null || network == null || !node.isGradeSeparated()) {
            return null;
        }
        String elevatedRoadId = resolveElevatedRoadId(node, network, terrain);
        if (elevatedRoadId == null || !elevatedRoadId.equals(edge.getRoadId())) {
            return null;
        }
        int baseHeight = computeCrossingBaseHeight(node, network, terrain, elevatedRoadId);
        return baseHeight + (int) Math.round(resolveCrossingClearance(node));
    }

    private int computeCrossingBaseHeight(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            String elevatedRoadId) {
        List<Integer> heights = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            if (elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            heights.add(getTargetHeightAtNodeIgnoringGradeSeparation(
                connectedEdge, node, network, terrain));
        }
        if (heights.isEmpty()) {
            return getGroundHeightAtNode(terrain, node, network);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
    }

    private double resolveCrossingClearance(RoadNode node) {
        if (node.getCrossingClearance() != null) {
            return node.getCrossingClearance();
        }
        return config.getDefaultCrossingClearance();
    }

    public BlockPos toBlockPos(Vec2d canvasPos, int y) {
        return RoadVoxelRasterizer.toBlockPos(canvasPos, y, coordinateTransformer);
    }

    /**
     * 路径分段
     */
    private static class PathSegment {
        final Vec2d start;
        final Vec2d end;
        final double distance;
        
        PathSegment(Vec2d start, Vec2d end) {
            this.start = start;
            this.end = end;
            this.distance = start.distance(end);
        }
    }
    
    private record SegmentHeightCalculation(
            List<SegmentHeightInfo> heightInfos,
            List<Double> profileDistances,
            List<Integer> profileGroundHeights,
            List<Integer> profileGuideLine,
            List<Integer> profileTargetHeights) {
    }

    /**
     * 分段高度信息
     */
    private static class SegmentHeightInfo {
        final PathSegment segment;
        final int groundStart;      // 起始地面高度
        final int groundEnd;        // 结束地面高度
        final int targetStart;      // 目标起始高度（考虑坡度限制）
        final int targetEnd;        // 目标结束高度（考虑坡度限制）
        final double slope;         // 实际坡度（百分比）
        
        SegmentHeightInfo(PathSegment segment, int groundStart, int groundEnd, 
                         int targetStart, int targetEnd, double slope) {
            this.segment = segment;
            this.groundStart = groundStart;
            this.groundEnd = groundEnd;
            this.targetStart = targetStart;
            this.targetEnd = targetEnd;
            this.slope = slope;
        }
    }
    
    /**
     * 桥段
     */
    private record BridgeSegment(PathSegment segment, int bridgeHeight) {
    }

    /**
     * 隧道段
     */
    private record TunnelSegment(PathSegment segment, int tunnelDepth) {
    }

    private record ConstructionDetection(
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels,
            List<RoadConstructionType> constructionTypes,
            List<Double> segmentDistances) {
    }
    
    /**
     * 采样路径点，创建分段
     */
    private List<PathSegment> samplePath(List<Vec2d> pathPoints) {
        // 采样密度：从配置读取（可配置的采样精度），验证范围防止除零或无限循环
        double minSampleDistance = Math.max(0.1, Math.min(10.0, config.getPathSampleDistance()));

        // 预先计算总段数，避免ArrayList频繁扩容
        List<PathSegment> segments = getPathSegments(pathPoints, minSampleDistance);

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d start = pathPoints.get(i);
            Vec2d end = pathPoints.get(i + 1);
            double distance = start.distance(end);

            if (distance < minSampleDistance) {
                // 距离太短，直接添加
                segments.add(new PathSegment(start, end));
            } else {
                // 细分采样
                int samples = (int) Math.ceil(distance / minSampleDistance);
                Vec2d prev = start;
                for (int j = 1; j <= samples; j++) {
                    double t = (double) j / samples;
                    Vec2d current = start.lerp(end, t);
                    segments.add(new PathSegment(prev, current));
                    prev = current;
                }
            }
        }

        return segments;
    }

    private static @NotNull List<PathSegment> getPathSegments(List<Vec2d> pathPoints, double minSampleDistance) {
        int estimatedSegments = 0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d start = pathPoints.get(i);
            Vec2d end = pathPoints.get(i + 1);
            double distance = start.distance(end);
            if (distance < minSampleDistance) {
                estimatedSegments += 1;
            } else {
                estimatedSegments += (int) Math.ceil(distance / minSampleDistance);
            }
        }

        return new ArrayList<>(estimatedSegments);
    }

    /**
     * 计算分段高度（考虑坡度限制）
     */
    private SegmentHeightCalculation calculateSegmentHeights(List<PathSegment> segments, TerrainSampler terrain) {
        if (segments.isEmpty()) {
            return emptyHeightCalculation();
        }

        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        HeightSampleData sampleData = collectHeightSamples(segments, terrain, halfWidth);
        return buildSegmentHeights(
            segments,
            sampleData,
            List.of(),
            null,
            null,
            segmentIndex -> config.getMaxSlope());
    }

    private SegmentHeightCalculation calculateSegmentHeightsForEdge(
            List<PathSegment> segments, TerrainSampler terrain, RoadNetwork network, RoadEdge edge,
            RoadNode startNode, RoadNode endNode) {
        return calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, startNode, endNode, true, null);
    }

    private SegmentHeightCalculation calculateSegmentHeightsForEdge(
            List<PathSegment> segments, TerrainSampler terrain, RoadNetwork network, RoadEdge edge,
            RoadNode startNode, RoadNode endNode, boolean applyGradeSeparation) {
        return calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, startNode, endNode, applyGradeSeparation, null);
    }

    private SegmentHeightCalculation calculateSegmentHeightsForEdge(
            List<PathSegment> segments,
            TerrainSampler terrain,
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            boolean applyGradeSeparation,
            Map<String, Integer> networkNodeElevations) {
        if (segments.isEmpty()) {
            return emptyHeightCalculation();
        }

        // 优先级：手动标高 / 立体交叉跨越高度 > 路网统一节点标高
        Integer manualStartHeight = resolveForcedHeightAtNode(
            startNode, network, edge, terrain, applyGradeSeparation);
        Integer manualEndHeight = resolveForcedHeightAtNode(
            endNode, network, edge, terrain, applyGradeSeparation);
        if (manualStartHeight == null) {
            manualStartHeight = lookupNetworkNodeElevation(networkNodeElevations, startNode);
        }
        if (manualEndHeight == null) {
            manualEndHeight = lookupNetworkNodeElevation(networkNodeElevations, endNode);
        }

        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.getEffectiveWidth(network, edge, config));
        HeightSampleData sampleData = collectHeightSamples(segments, terrain, halfWidth);

        List<Double> distances = new ArrayList<>();
        List<Float> maxSlopes = new ArrayList<>();
        double accumulatedDistance = 0.0;
        for (PathSegment segment : segments) {
            distances.add(segment.distance);
            maxSlopes.add(RoadModelUtils.getEffectiveMaxSlope(network, edge, config, accumulatedDistance));
            accumulatedDistance += segment.distance;
        }

        return buildSegmentHeights(
            segments,
            sampleData,
            maxSlopes,
            manualStartHeight,
            manualEndHeight,
            segmentIndex -> RoadModelUtils.getEffectiveMaxSlope(
                network, edge, config, profileDistanceAtSegmentStart(sampleData, segmentIndex)));
    }

    private static Integer lookupNetworkNodeElevation(
            Map<String, Integer> networkNodeElevations, RoadNode node) {
        if (networkNodeElevations == null || node == null) {
            return null;
        }
        return networkNodeElevations.get(node.getId());
    }

    private static SegmentHeightCalculation emptyHeightCalculation() {
        return new SegmentHeightCalculation(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static RoadGenerationResult toProfileResult(SegmentHeightCalculation calculation) {
        RoadGenerationResult profile = new RoadGenerationResult(0);
        profile.profileDistances = new ArrayList<>(calculation.profileDistances());
        profile.profileGroundHeights = new ArrayList<>(calculation.profileGroundHeights());
        profile.profileGuideLine = new ArrayList<>(calculation.profileGuideLine());
        profile.profileTargetHeights = new ArrayList<>(calculation.profileTargetHeights());
        return profile;
    }

    private record HeightSampleData(
            List<Integer> groundSamples,
            List<Double> cumulativeDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds) {
    }

    private HeightSampleData collectHeightSamples(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth) {
        List<Integer> groundSamples = new ArrayList<>();
        List<Double> cumulativeDistances = new ArrayList<>();
        List<Integer> groundStarts = new ArrayList<>();
        List<Integer> groundEnds = new ArrayList<>();
        double accumulatedDistance = 0.0;

        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            int groundStart = terrain.sampleCrossSectionGroundY(segment.start, tangent, halfWidth);
            int groundEnd = terrain.sampleCrossSectionGroundY(segment.end, tangent, halfWidth);
            groundStarts.add(groundStart);
            groundEnds.add(groundEnd);
            groundSamples.add(groundStart);
            cumulativeDistances.add(accumulatedDistance);
            accumulatedDistance += segment.distance;
        }

        if (!groundEnds.isEmpty()) {
            groundSamples.add(groundEnds.getLast());
            cumulativeDistances.add(accumulatedDistance);
        }

        return new HeightSampleData(groundSamples, cumulativeDistances, groundStarts, groundEnds);
    }

    private SegmentHeightCalculation buildSegmentHeights(
            List<PathSegment> segments,
            HeightSampleData sampleData,
            List<Float> maxSlopes,
            Integer manualStartHeight,
            Integer manualEndHeight,
            java.util.function.IntFunction<Float> maxSlopeResolver) {
        List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(
            sampleData.groundSamples(),
            sampleData.cumulativeDistances(),
            config.getFillFactor(),
            manualStartHeight,
            manualEndHeight);

        List<Integer> guideStarts = new ArrayList<>();
        List<Integer> guideEnds = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            guideStarts.add(guideLine.get(i));
            guideEnds.add(guideLine.get(i + 1));
        }

        List<Double> distances = new ArrayList<>();
        List<Float> effectiveMaxSlopes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            distances.add(segments.get(i).distance);
            if (maxSlopes != null && maxSlopes.size() == segments.size()) {
                effectiveMaxSlopes.add(maxSlopes.get(i));
            } else {
                effectiveMaxSlopes.add(maxSlopeResolver.apply(i));
            }
        }

        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            distances,
            guideStarts,
            guideEnds,
            effectiveMaxSlopes,
            manualStartHeight,
            manualEndHeight,
            config.getMaxContinuousSlopeLength(),
            config.getRelaxedSlopeLength(),
            config.getRelaxedSlopePercent()
        );

        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        int currentHeight = manualStartHeight != null
            ? manualStartHeight
            : guideStarts.getFirst();

        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int targetStart = currentHeight;
            int targetEnd = targetEnds.get(i);
            double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
                targetStart, targetEnd, segment.distance);
            heightInfos.add(new SegmentHeightInfo(
                segment,
                sampleData.groundStarts().get(i),
                sampleData.groundEnds().get(i),
                targetStart,
                targetEnd,
                actualSlope));
            currentHeight = targetEnd;
        }

        return new SegmentHeightCalculation(
            heightInfos,
            new ArrayList<>(sampleData.cumulativeDistances()),
            new ArrayList<>(sampleData.groundSamples()),
            new ArrayList<>(guideLine),
            buildProfileTargetHeights(heightInfos, manualStartHeight));
    }

    private static double profileDistanceAtSegmentStart(HeightSampleData sampleData, int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= sampleData.cumulativeDistances().size()) {
            return 0.0;
        }
        return sampleData.cumulativeDistances().get(segmentIndex);
    }

    private static List<Integer> buildProfileTargetHeights(
            List<SegmentHeightInfo> heightInfos,
            Integer manualStartHeight) {
        if (heightInfos.isEmpty()) {
            return List.of();
        }
        List<Integer> profileTargetHeights = new ArrayList<>(heightInfos.size() + 1);
        profileTargetHeights.add(manualStartHeight != null
            ? manualStartHeight
            : heightInfos.getFirst().targetStart);
        for (SegmentHeightInfo info : heightInfos) {
            profileTargetHeights.add(info.targetEnd);
        }
        return profileTargetHeights;
    }

    /**
     * 基于成本比较检测桥/隧道需求，并返回每段施工类型。
     */
    private ConstructionDetection detectConstruction(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain) {
        List<Double> segmentDistances = new ArrayList<>();
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> targetHeights = new ArrayList<>();

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            segmentDistances.add(info.segment.distance);
            groundHeights.add(info.groundStart);
            targetHeights.add(info.targetStart);
        }

        RoadConstructionEvaluator.RoadConstructionCostConfig costConfig =
            RoadConstructionEvaluator.RoadConstructionCostConfig.from(config);
        List<RoadConstructionType> constructionTypes = RoadConstructionEvaluator.evaluatePath(
            segmentDistances,
            groundHeights,
            targetHeights,
            costConfig,
            config.getMinimumConstructionRunLength());

        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();

        for (int i = 0; i < constructionTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            RoadConstructionType type = constructionTypes.get(i);
            if (type == RoadConstructionType.BRIDGE) {
                int heightDifference = info.targetStart - info.groundStart;
                bridges.add(new BridgeSegment(info.segment, heightDifference));
            } else if (type == RoadConstructionType.TUNNEL) {
                BlockPos pos = canvasToBlockPos(info.segment.start);
                if (terrain.isSolidBlock(pos.getX(), pos.getY(), pos.getZ())) {
                    int heightDifference = info.groundStart - info.targetStart;
                    tunnels.add(new TunnelSegment(info.segment, heightDifference));
                }
            }
        }

        return new ConstructionDetection(bridges, tunnels, constructionTypes, segmentDistances);
    }

    private static void applyConstructionStats(
            RoadGenerationResult result,
            ConstructionDetection detection) {
        result.constructionTypes.addAll(detection.constructionTypes());
        for (int i = 0; i < detection.constructionTypes().size(); i++) {
            double distance = detection.segmentDistances().get(i);
            switch (detection.constructionTypes().get(i)) {
                case BRIDGE -> result.bridgeLength += distance;
                case TUNNEL -> result.tunnelLength += distance;
                case ROAD, CUT, FILL -> result.normalRoadLength += distance;
            }
        }
    }
    
    private double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
        Vec2d origin;
        Vec2d tangent;
        if (pathPoints != null && pathPoints.size() >= 2) {
            origin = pathPoints.getFirst();
            tangent = pathPoints.get(1).subtract(pathPoints.getFirst());
        } else if (segments != null && !segments.isEmpty()) {
            PathSegment first = segments.getFirst();
            origin = first.start;
            tangent = first.end.subtract(first.start);
        } else {
            return 1.0;
        }
        if (tangent.lengthSquared() < 1e-12) {
            tangent = new Vec2d(1, 0);
        }
        Vec2d unit = tangent.normalize();
        Vec2d normal = new Vec2d(-unit.y, unit.x);
        return RoadGeometryUtils.canvasUnitsPerWorldBlock(coordinateTransformer, origin, normal);
    }

    private void generateCarriagewayBlocks(
            RoadSolidModel solids,
            EdgeBuildMetrics metrics,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels,
            TerrainSampler terrain,
            int carriagewayWidth,
            String blockId,
            double unitsPerBlock) {
        metrics.bridgeCount = bridges.size();
        metrics.tunnelCount = tunnels.size();

        double halfExtent = RoadDimensionUtils.halfExtentFromCenter(carriagewayWidth) * unitsPerBlock;
        List<Vec2d> pathPoints = new ArrayList<>();
        for (PathSegment segment : segments) {
            if (pathPoints.isEmpty()) {
                pathPoints.add(segment.start);
            }
            pathPoints.add(segment.end);
        }
        List<Vec2d> leftBoundary = OffsetHandler.offsetPolyline(pathPoints, halfExtent);
        List<Vec2d> rightBoundary = OffsetHandler.offsetPolyline(pathPoints, -halfExtent);

        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> solids.addLateralStrip(
            center, leftNormal, carriagewayWidth, targetY, RoadSolidLayer.ROAD, blockId, unitsPerBlock));

        generateBridgeStructures(solids, bridges, segments, heightInfos, leftBoundary, rightBoundary, terrain);
    }

    private void gradeRoadEnvelope(
            RoadSolidModel solids,
            EdgeBuildMetrics metrics,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock) {
        int sideBandWidth = crossSection.outerBandBlockCount();
        int envelopeWidth = crossSection.carriagewayWidth + sideBandWidth * 2;
        if (envelopeWidth <= 0) {
            return;
        }
        int tunnelThreshold = config.getTunnelThreshold();
        int bridgeThreshold = config.getBridgeThreshold();
        String fillMaterialId = getBlockIdFromMaterial(
            crossSection.fillSlopeMaterial != null && !crossSection.fillSlopeMaterial.isBlank()
                ? crossSection.fillSlopeMaterial
                : config.getFillSlopeMaterial());
        RoadTerrainClearanceUtils.BlockColumnResolver columnResolver = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return canvasToBlockPos(planPoint).getX();
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return canvasToBlockPos(planPoint).getZ();
            }
        };
        RoadRoadbedGradingUtils.GradingVolumes[] total = {RoadRoadbedGradingUtils.GradingVolumes.ZERO};
        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) ->
            total[0] = total[0].add(RoadRoadbedGradingUtils.gradeCrossSectionEnvelope(
                solids,
                center,
                leftNormal,
                envelopeWidth,
                targetY,
                tunnelThreshold,
                bridgeThreshold,
                fillMaterialId,
                terrain,
                columnResolver,
                unitsPerBlock)));
        metrics.cutVolume = total[0].cutVolume();
        metrics.fillVolume = total[0].fillVolume();
    }

    private void generateShoulderBlocks(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int shoulderWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, shoulderWidth, targetY, RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, shoulderWidth, targetY, RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
        });
    }

    private void generateBikeLaneBlocks(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int bikeLaneWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, bikeLaneWidth, targetY, RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, bikeLaneWidth, targetY, RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
        });
    }

    private void generateSlopeBatterBlocks(
            RoadSolidModel solids,
            EdgeBuildMetrics metrics,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double shoulderCenterOffset,
            int shoulderWidth,
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        if (!crossSection.includeSlopeBatter) {
            return;
        }
        String fillBlockId = getBlockIdFromMaterial(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
            ? null
            : getBlockIdFromMaterial(crossSection.cutSlopeMaterial);
        float fillRatio = crossSection.fillSlopeRatio;
        float cutRatio = crossSection.cutSlopeRatio;
        int maxHorizontalRun = 32;
        double outerOffset = RoadDimensionUtils.halfExtentFromCenter(shoulderWidth) * unitsPerBlock;

        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(shoulderCenterOffset));
            Vec2d right = center.subtract(leftNormal.multiply(shoulderCenterOffset));
            placeSlopeBatterAtPoint(solids, left, targetY, outerOffset, pathPoints, terrain,
                fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun, 1, unitsPerBlock);
            placeSlopeBatterAtPoint(solids, right, targetY, outerOffset, pathPoints, terrain,
                fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun, -1, unitsPerBlock);
        });
    }

    private void placeSlopeBatterAtPoint(
            RoadSolidModel solids,
            Vec2d shoulderCenter,
            int targetY,
            double outerOffset,
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            float fillRatio,
            float cutRatio,
            String fillBlockId,
            String cutBlockId,
            int maxHorizontalRun,
            int sideSign,
            double unitsPerBlock) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int pathIndex = Math.max(0, Math.min(pathPoints.size() - 2,
            RoadGeometryUtils.findNearestSegmentIndex(pathPoints, shoulderCenter)));
        Vec2d direction = pathPoints.get(pathIndex + 1).subtract(pathPoints.get(pathIndex)).normalize();
        Vec2d normal = new Vec2d(-direction.y, direction.x).multiply(sideSign);
        Vec2d outerEdge = shoulderCenter.add(normal.multiply(outerOffset));

        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return;
        }

        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;

        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            targetY,
            profileDirection,
            horizontalOffset -> terrain.sampleSurfaceY(
                outerEdge.add(normal.multiply(horizontalOffset * scale))),
            slopeRatio,
            maxHorizontalRun
        );

        for (int step = 1; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d sample = outerEdge.add(normal.multiply(horizontalOffset * scale));
            int groundY = terrain.sampleSurfaceY(sample);

            if (isFill) {
                for (int y = groundY + 1; y <= slopeHeight; y++) {
                    solids.add(sample, y, RoadSolidLayer.SHOULDER, fillBlockId);
                }
            } else {
                for (int y = slopeHeight + 1; y <= groundY; y++) {
                    solids.add(sample, y, RoadSolidLayer.SHOULDER, "minecraft:air");
                }
                if (cutBlockId != null) {
                    solids.add(sample, slopeHeight, RoadSolidLayer.SHOULDER, cutBlockId);
                }
            }
        }
    }

    private void generateDrainageChannels(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double drainageOffset,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> {
            int drainY = targetY - 1;
            Vec2d left = center.add(leftNormal.multiply(drainageOffset));
            Vec2d right = center.subtract(leftNormal.multiply(drainageOffset));
            solids.addLateralStrip(left, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
            solids.addLateralStrip(right, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
        });
    }

    private void generateBridgeStructures(
            RoadSolidModel solids,
            List<BridgeSegment> bridges,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            TerrainSampler terrain) {
        if (bridges.isEmpty()) {
            return;
        }
        String pillarBlockId = getBlockIdFromMaterial("material.plot.stone");
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();

        for (BridgeSegment bridge : bridges) {
            SegmentHeightInfo info = findHeightInfo(segments, heightInfos, bridge.segment());
            if (info == null) {
                continue;
            }
            int samples = Math.max(2, (int) Math.ceil(bridge.segment().distance));
            for (int j = 0; j <= samples; j++) {
                if (j % 4 != 0 && j != samples) {
                    continue;
                }
                double t = (double) j / samples;
                double normalized = findNormalizedDistance(segments, bridge.segment(), t, totalLength);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                Vec2d center = bridge.segment().start.lerp(bridge.segment().end, t);
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                placeBridgePillars(solids, center, targetY, terrain, pillarBlockId);
                placeBridgePillars(solids, left, targetY, terrain, pillarBlockId);
                placeBridgePillars(solids, right, targetY, terrain, pillarBlockId);
            }
        }
    }

    private void placeBridgePillars(
            RoadSolidModel solids,
            Vec2d canvasPos,
            int deckY,
            TerrainSampler terrain,
            String blockId) {
        int groundY = terrain.sampleSurfaceY(canvasPos);
        if (deckY - groundY <= config.getBridgeThreshold()) {
            return;
        }
        for (int y = groundY + 1; y < deckY; y++) {
            solids.add(canvasPos, y, RoadSolidLayer.BRIDGE, blockId);
        }
    }

    /**
     * 查找指定段的高度信息（使用引用相等避免浮点数比较）
     */
    private static SegmentHeightInfo findHeightInfo(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            PathSegment target) {
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            // 使用引用相等代替浮点数距离比较，更可靠
            if (segments.get(i) == target) {
                return heightInfos.get(i);
            }
        }
        // 如果引用不相等，降级到浮点数比较（兼容性保护）
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            if (sameSegment(segments.get(i), target)) {
                return heightInfos.get(i);
            }
        }
        return null;
    }

    private static double findNormalizedDistance(
            List<PathSegment> segments,
            PathSegment target,
            double segmentT,
            double totalLength) {
        if (totalLength <= 1e-9) {
            return 0.0;
        }
        double accumulated = 0.0;
        for (PathSegment segment : segments) {
            // 优先使用引用相等
            if (segment == target) {
                return (accumulated + segmentT * segment.distance) / totalLength;
            }
            accumulated += segment.distance;
        }
        // 降级到浮点数比较
        accumulated = 0.0;
        for (PathSegment segment : segments) {
            if (sameSegment(segment, target)) {
                return (accumulated + segmentT * segment.distance) / totalLength;
            }
            accumulated += segment.distance;
        }
        return 0.0;
    }

    /**
     * 判断两个段是否相同（使用浮点数距离比较，容差1mm）
     * 注意：这是降级方案，优先使用引用相等（==）
     */
    private static boolean sameSegment(PathSegment a, PathSegment b) {
        return a.start.distance(b.start) < 1e-3 && a.end.distance(b.end) < 1e-3;
    }

    private void generateSidewalkBlocks(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int sidewalkWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, sidewalkWidth, targetY, RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, sidewalkWidth, targetY, RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
        });
    }

    private void generateMedianBlocks(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            String blockId) {
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                solids.addSpan(left, right, targetY, RoadSolidLayer.MEDIAN, blockId);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void generateLaneMarkings(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> pathPoints,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        String blockId = getBlockIdFromMaterial(crossSection.markingMaterial);
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart;

        List<RoadMarkingPasses.Pass> passes = RoadMarkingPasses.fromCrossSection(crossSection);

        for (RoadMarkingPasses.Pass pass : passes) {
            List<Vec2d> markingLine = OffsetHandler.offsetPolyline(pathPoints, pass.offset() * unitsPerBlock);
            accumulatedSegmentStart = 0.0;
            for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
                SegmentHeightInfo info = heightInfos.get(i);
                PathSegment segment = segments.get(i);
                int samples = Math.max(2, (int) Math.ceil(segment.distance));
                for (int j = 0; j <= samples; j++) {
                    if (!pass.solid() && j % 2 != 0) {
                        continue;
                    }
                    double t = (double) j / samples;
                    int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                    double normalized = totalLength > 1e-9
                        ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                        : 0.0;
                    Vec2d point = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(markingLine, normalized);
                    solids.add(point, targetY, RoadSolidLayer.MARKING, blockId);
                }
                accumulatedSegmentStart += segment.distance;
            }
        }
    }

    private void generateStreetlights(
            RoadSolidModel solids,
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        Integer spacingValue = crossSection.streetlightSpacing;
        int spacing = spacingValue != null ? spacingValue : 0;
        if (spacing <= 0) {
            return;
        }
        double skipDistance = crossSection.carriagewayWidth * unitsPerBlock;
        double offset = (RoadDimensionUtils.maxLateralOffset(crossSection.carriagewayWidth)
            + crossSection.outerBandBlockCount()
            + 0.5) * unitsPerBlock;

        List<Vec2d> samples = RoadGeometryUtils.sampleAlongPath(pathPoints, spacing, skipDistance);
        boolean placeLeft = true;

        for (Vec2d sample : samples) {
            int index = Math.max(0, Math.min(pathPoints.size() - 2,
                RoadGeometryUtils.findNearestSegmentIndex(pathPoints, sample)));
            Vec2d direction = pathPoints.get(index + 1).subtract(pathPoints.get(index)).normalize();
            Vec2d normal = new Vec2d(-direction.y, direction.x);
            double side = placeLeft ? offset : -offset;
            Vec2d lightPos = sample.add(normal.multiply(side));
            int groundY = terrain.sampleSurfaceY(lightPos);
            solids.add(lightPos, groundY + 1, RoadSolidLayer.STREETLIGHT, "minecraft:lantern");
            placeLeft = !placeLeft;
        }
    }

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y) {
        return rasterizeSpan(left, right, y, null);
    }

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y, CoordinateTransformer transformer) {
        return RoadVoxelRasterizer.rasterizeSpan(left, right, y, transformer);
    }

    public static void recordPlacementIfAbsent(
            RoadGenerationResult result,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        if (result != null) {
            result.recordPlacementIfAbsent(pos, previousBlockId, newBlockId);
        }
    }

    public String getBlockIdFromMaterial(String material) {
        return RoadMaterialUtils.resolveBlockId(material);
    }
    
    /**
     * 将画布坐标转换为BlockPos（XZ平面）
     */
    private BlockPos canvasToBlockPos(Vec2d canvasPos) {
        return RoadGeometryUtils.canvasToBlockXZ(canvasPos, coordinateTransformer);
    }

    private int getGroundHeightAtNode(TerrainSampler terrain, RoadNode node, RoadNetwork network) {
        if (node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        return terrain.sampleCrossSectionGroundY(
            node.getPosition(),
            resolveNodeTangent(node, network),
            resolveNodeHalfWidth(node, network)
        );
    }

    private Vec2d resolveNodeTangent(RoadNode node, RoadNetwork network) {
        if (node == null || network == null) {
            return null;
        }

        RoadEdge widestEdge = null;
        double widest = -1.0;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            double width = RoadModelUtils.getEffectiveWidth(network, edge, config);
            if (width > widest) {
                widest = width;
                widestEdge = edge;
            }
        }
        if (widestEdge == null) {
            return null;
        }

        List<Vec2d> points = widestEdge.getCenterlinePoints();
        if (points.size() < 2) {
            return null;
        }
        if (widestEdge.getStartNodeId().equals(node.getId())) {
            return points.get(1).subtract(points.get(0));
        }
        if (widestEdge.getEndNodeId().equals(node.getId())) {
            return points.get(points.size() - 2).subtract(points.getLast());
        }
        return null;
    }

    private double resolveNodeHalfWidth(RoadNode node, RoadNetwork network) {
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        if (node == null || network == null) {
            return halfWidth;
        }

        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                halfWidth = Math.max(halfWidth, RoadDimensionUtils.halfExtentFromCenter(
                    RoadModelUtils.getEffectiveWidth(network, edge, config)));
            }
        }
        return halfWidth;
    }

    @FunctionalInterface
    private interface PathSampleConsumer {
        void accept(Vec2d center, Vec2d leftNormal, int targetY);
    }

    private static void forEachPathSample(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            PathSampleConsumer consumer) {
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = leftNormalForSegment(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                consumer.accept(center, leftNormal, targetY);
            }
        }
    }

    private static Vec2d leftNormalForSegment(PathSegment segment) {
        Vec2d tangent = segment.end.subtract(segment.start);
        if (tangent.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = tangent.normalize();
        return new Vec2d(-unit.y, unit.x);
    }
}
