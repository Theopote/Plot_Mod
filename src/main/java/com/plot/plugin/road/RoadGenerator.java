package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.pipeline.RoadGenerationBuildRequest;
import com.plot.plugin.road.pipeline.RoadGenerationPipeline;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnap;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路生成器（Façade）
 *
 * <p>对外入口保持不变；中心线落地流程由 {@link com.plot.plugin.road.pipeline.RoadGenerationPipeline}
 * 分阶段编排，逻辑可渐进迁移至各 stage 类。
 *
 * <p>负责将2D路径转换为3D道路方块，包括：
 * - 路径采样和分段
 * - 地形高度检测
 * - 坡度限制和调整
 * - 桥/隧道检测和生成
 * - 挖填方计算
 */
public class RoadGenerator implements RoadGenerationPipelineContext.Host {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadGenerator");
    
    private final RoadSystemConfig config;
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;

    /** 路网边生成时用于路口端部标高平滑，仅在 {@link #buildFromCenterline} 内短暂赋值。 */
    private EndpointElevationSnap endpointStartSnap;
    private EndpointElevationSnap endpointEndSnap;
    
    public RoadSystemConfig getConfig() {
        return config;
    }

    public TerrainSampler createTerrainSampler(World world) {
        return MinecraftTerrainSampler.of(world, coordinateTransformer);
    }

    public RoadGenerator(
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
        this.projectionHandler = java.util.Objects.requireNonNull(projectionHandler, "projectionHandler");
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
            ProfileSolveResult heightCalculation = calculateSegmentHeightsForEdge(
                segments, terrain, network, edge, startNode, endNode, true, networkNodeElevations);
            RoadGenerationResult result = buildFromCenterline(
                pathPoints, terrain, crossSection, heightCalculation.heightInfos(), edge.getLength(),
                resolveEndpointSnap(startNode, endNode, networkNodeElevations, crossSection, pathPoints),
                edge.getId());
            result.edgeId = edge.getId();
            result.copyProfileFrom(RoadProfileSolver.toProfileSnapshot(heightCalculation));
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
        ProfileSolveResult heightCalculation = manualRoadElevation != null
            ? calculateSegmentHeightsWithManualElevation(segments, terrain, manualRoadElevation)
            : calculateSegmentHeights(segments, terrain);
        double pathLength = segments.stream().mapToDouble(s -> s.distance).sum();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        RoadGenerationResult result = buildFromCenterline(
            pathPoints, terrain, crossSection, heightCalculation.heightInfos(), pathLength);
        result.copyProfileFrom(RoadProfileSolver.toProfileSnapshot(heightCalculation));
        return result;
    }

    private ProfileSolveResult calculateSegmentHeightsWithManualElevation(
            List<PathSegment> segments,
            TerrainSampler terrain,
            int manualRoadElevation) {
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        return RoadProfileSolver.solveWithManualElevation(
            segments, terrain, halfWidth, manualRoadElevation, profileSupport());
    }

    private RoadGenerationResult buildFromCenterline(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            List<SegmentHeightInfo> heightInfos,
            double pathLength) {
        return buildFromCenterline(pathPoints, terrain, crossSection, heightInfos, pathLength, null);
    }

    private RoadGenerationResult buildFromCenterline(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            List<SegmentHeightInfo> heightInfos,
            double pathLength,
            EndpointElevationSnaps endpointSnaps) {
        return buildFromCenterline(pathPoints, terrain, crossSection, heightInfos, pathLength, endpointSnaps, "standalone");
    }

    private RoadGenerationResult buildFromCenterline(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            List<SegmentHeightInfo> heightInfos,
            double pathLength,
            EndpointElevationSnaps endpointSnaps,
            String carriagewaySeedKey) {
        return new RoadGenerationPipeline().execute(
            new RoadGenerationBuildRequest(
                pathPoints,
                terrain,
                crossSection,
                heightInfos,
                pathLength,
                endpointSnaps,
                carriagewaySeedKey),
            this);
    }

    @Override
    public void setEndpointSnaps(EndpointElevationSnaps endpointSnaps) {
        endpointStartSnap = endpointSnaps != null ? endpointSnaps.start() : null;
        endpointEndSnap = endpointSnaps != null ? endpointSnaps.end() : null;
    }

    @Override
    public void clearEndpointSnaps() {
        endpointStartSnap = null;
        endpointEndSnap = null;
    }

    @Override
    public BlockPos canvasToBlockPos(Vec2d canvasPos) {
        return RoadGeometryUtils.canvasToBlockXZ(canvasPos, coordinateTransformer);
    }

    @Override
    public String resolveBlockId(String material) {
        return getBlockIdFromMaterial(material);
    }

    @Override
    public int snapEndpointElevation(Vec2d center, int targetY) {
        int snapped = targetY;
        if (endpointStartSnap != null) {
            snapped = blendEndpointElevation(center, endpointStartSnap, snapped);
        }
        if (endpointEndSnap != null) {
            snapped = blendEndpointElevation(center, endpointEndSnap, snapped);
        }
        return snapped;
    }

    @Override
    public int bridgeThreshold() {
        return config.getBridgeThreshold();
    }

    @Override
    public void flushEdgeSolids(RoadGenerationResult result, RoadSolidModel solids) {
        RoadVoxelRasterizer.flushEdgeSolids(result, solids, coordinateTransformer, projectionHandler);
    }

    private EndpointElevationSnaps resolveEndpointSnap(
            RoadNode startNode,
            RoadNode endNode,
            Map<String, Integer> networkNodeElevations,
            ResolvedCrossSection crossSection,
            List<Vec2d> pathPoints) {
        if (networkNodeElevations == null || networkNodeElevations.isEmpty()) {
            return null;
        }
        List<PathSegment> segments = samplePath(pathPoints);
        double unitsPerBlock = estimateCanvasUnitsPerBlock(pathPoints, segments);
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(crossSection.carriagewayWidth) * unitsPerBlock;
        double blendRadius = Math.max(halfWidth + 2.0, RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);

        EndpointElevationSnap start = null;
        EndpointElevationSnap end = null;
        if (startNode != null) {
            Integer elevation = networkNodeElevations.get(startNode.getId());
            if (elevation != null) {
                start = new EndpointElevationSnap(startNode.getPosition(), elevation, blendRadius);
            }
        }
        if (endNode != null) {
            Integer elevation = networkNodeElevations.get(endNode.getId());
            if (elevation != null) {
                end = new EndpointElevationSnap(endNode.getPosition(), elevation, blendRadius);
            }
        }
        if (start == null && end == null) {
            return null;
        }
        return new EndpointElevationSnaps(start, end);
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
            projectionHandler,
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

    /**
     * @deprecated 使用带材质参数的 {@link #mergeJunctionBlocks(RoadGenerationResult, RoadJunctionGenerator.JunctionBlocks, String, String)}
     *             计划在 v2.0 版本移除
     */
    @Deprecated(since = "1.x", forRemoval = true)
    public void mergeJunctionBlocks(RoadGenerationResult target, RoadJunctionGenerator.JunctionBlocks junction) {
        mergeJunctionBlocks(
            target,
            junction,
            getBlockIdFromMaterial(config.getSelectedMaterial().getPrimaryMaterial()),
            getBlockIdFromMaterial(config.getSelectedMaterial().getPrimaryMaterial())
        );
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

    int getTargetHeightAtNode(RoadEdge edge, RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        if (edge == null || node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }

        // 立体交叉优先：上跨边 = 下层标高(手动或自然) + 净空；下穿边用手动/自然下层标高
        Integer elevatedTarget = resolveElevatedCrossingHeight(edge, node, network, terrain);
        if (elevatedTarget != null) {
            return elevatedTarget;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
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

    /**
     * 端点强制标高：
     * <ul>
     *   <li>立体交叉上跨边：下层标高 + 净空（下层标高优先用节点手动标高）</li>
     *   <li>立体交叉下穿边 / 普通节点：手动标高（若有）</li>
     * </ul>
     * 手动标高不再整网覆盖掉立体交叉，而是作为下层基准。
     */
    private Integer resolveForcedHeightAtNode(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation) {
        if (node == null) {
            return null;
        }
        if (applyGradeSeparation) {
            Integer elevated = resolveElevatedCrossingHeight(edge, node, network, terrain);
            if (elevated != null) {
                return elevated;
            }
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        return null;
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
        // 手动标高即下层（下穿）基准面
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
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

    @Override
    public RoadSystemConfig config() {
        return config;
    }

    private List<PathSegment> samplePath(List<Vec2d> pathPoints) {
        return RoadGeometrySampler.sample(
            pathPoints,
            config.getPathSampleDistance(),
            this::estimateCanvasUnitsPerBlock);
    }

    private ProfileSolveSupport profileSupport() {
        return ProfileSolveSupport.fromConfig(config, segments -> estimateCanvasUnitsPerBlock(null, segments));
    }

    private ProfileSolveResult calculateSegmentHeights(List<PathSegment> segments, TerrainSampler terrain) {
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        return RoadProfileSolver.solveStandalone(segments, terrain, halfWidth, profileSupport());
    }

    private ProfileSolveResult calculateSegmentHeightsForEdge(
            List<PathSegment> segments, TerrainSampler terrain, RoadNetwork network, RoadEdge edge,
            RoadNode startNode, RoadNode endNode) {
        return calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, startNode, endNode, true, null);
    }

    private ProfileSolveResult calculateSegmentHeightsForEdge(
            List<PathSegment> segments, TerrainSampler terrain, RoadNetwork network, RoadEdge edge,
            RoadNode startNode, RoadNode endNode, boolean applyGradeSeparation) {
        return calculateSegmentHeightsForEdge(
            segments, terrain, network, edge, startNode, endNode, applyGradeSeparation, null);
    }

    private ProfileSolveResult calculateSegmentHeightsForEdge(
            List<PathSegment> segments,
            TerrainSampler terrain,
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            boolean applyGradeSeparation,
            Map<String, Integer> networkNodeElevations) {
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
        return RoadProfileSolver.solveForEdge(
            segments,
            terrain,
            network,
            edge,
            config,
            halfWidth,
            manualStartHeight,
            manualEndHeight,
            profileSupport());
    }

    private static Integer lookupNetworkNodeElevation(
            Map<String, Integer> networkNodeElevations, RoadNode node) {
        if (networkNodeElevations == null || node == null) {
            return null;
        }
        return networkNodeElevations.get(node.getId());
    }

    @Override
    public double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
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

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y) {
        return rasterizeSpan(left, right, y, null);
    }

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y, ICoordinateService transformer) {
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

    private static int blendEndpointElevation(Vec2d center, EndpointElevationSnap snap, int currentY) {
        double distance = center.distance(snap.position());
        if (distance >= snap.blendRadius()) {
            return currentY;
        }
        double blend = 1.0 - distance / snap.blendRadius();
        return (int) Math.round(currentY * (1.0 - blend) + snap.elevation() * blend);
    }
}
