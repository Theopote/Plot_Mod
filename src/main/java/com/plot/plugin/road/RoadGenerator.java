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
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnap;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.GradeSeparationPolicy;
import com.plot.plugin.road.pipeline.profile.NetworkNodeElevationResolver;
import com.plot.plugin.road.pipeline.profile.NodeTargetHeightResolver;
import com.plot.plugin.road.pipeline.profile.ProfileEndpointHeightResolver;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.RoadGeneratorProfileContext;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
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

    private final RoadGeneratorProfileContext profileContext;
    private final GradeSeparationPolicy gradeSeparationPolicy;
    private final NodeTargetHeightResolver nodeTargetHeightResolver;
    private final NetworkNodeElevationResolver networkNodeElevationResolver;
    private final ProfileEndpointHeightResolver profileEndpointHeightResolver;

    public RoadGenerator(
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
        this.projectionHandler = java.util.Objects.requireNonNull(projectionHandler, "projectionHandler");
        this.profileContext = new RoadGeneratorProfileContext(config, this::estimateCanvasUnitsPerBlock);
        this.gradeSeparationPolicy = new GradeSeparationPolicy(profileContext);
        this.nodeTargetHeightResolver = new NodeTargetHeightResolver(profileContext, gradeSeparationPolicy);
        this.networkNodeElevationResolver =
            new NetworkNodeElevationResolver(profileContext, gradeSeparationPolicy, nodeTargetHeightResolver);
        this.profileEndpointHeightResolver =
            new ProfileEndpointHeightResolver(gradeSeparationPolicy, nodeTargetHeightResolver);
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
        return profileContext.solveWithManualElevation(segments, terrain, manualRoadElevation);
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
        return networkNodeElevationResolver.junctionTargetHeight(node, network, terrain);
    }

    /**
     * 路网节点统一标高（第一遍）：按各边自然高程决议，供边生成第二遍强制对齐端点。
     * 立体交叉节点存的是下层（非跨越方）标高；跨越方高度仍由 {@link GradeSeparationPolicy} 处理。
     */
    public Map<String, Integer> resolveNetworkNodeElevations(RoadNetwork network, TerrainSampler terrain) {
        return networkNodeElevationResolver.resolve(
            network,
            terrain,
            (nodeId, samples, spread, height) -> LOGGER.info(
                "路口/节点 {} 自然高程散布较大 {}（spread={}），统一到 Y={}",
                nodeId, samples, spread, height));
    }

    int getTargetHeightAtNode(RoadEdge edge, RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return nodeTargetHeightResolver.targetHeightAtNode(edge, node, network, terrain);
    }

    /**
     * 确定立体交叉的跨越方道路 ID。手动指定时直接返回；否则按自然高度自动判断（不写入节点）。
     */
    public String resolveElevatedRoadId(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return gradeSeparationPolicy.resolveElevatedRoadId(
            node,
            network,
            terrain,
            nodeTargetHeightResolver.naturalRoadHeightAtNode());
    }

    int getTargetHeightAtNodeIgnoringGradeSeparation(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        return nodeTargetHeightResolver.targetHeightIgnoringGradeSeparation(edge, node, network, terrain);
    }

    public BlockPos toBlockPos(Vec2d canvasPos, int y) {
        return RoadVoxelRasterizer.toBlockPos(canvasPos, y, coordinateTransformer);
    }

    @Override
    public RoadSystemConfig config() {
        return config;
    }

    private List<PathSegment> samplePath(List<Vec2d> pathPoints) {
        return profileContext.samplePath(pathPoints);
    }

    private ProfileSolveResult calculateSegmentHeights(List<PathSegment> segments, TerrainSampler terrain) {
        return profileContext.solveStandalone(segments, terrain);
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
        Integer manualStartHeight = profileEndpointHeightResolver.resolve(
            startNode, network, edge, terrain, applyGradeSeparation, networkNodeElevations);
        Integer manualEndHeight = profileEndpointHeightResolver.resolve(
            endNode, network, edge, terrain, applyGradeSeparation, networkNodeElevations);
        return profileContext.solveEdgeProfile(
            segments,
            terrain,
            network,
            edge,
            startNode,
            endNode,
            manualStartHeight,
            manualEndHeight);
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

    private static int blendEndpointElevation(Vec2d center, EndpointElevationSnap snap, int currentY) {
        double distance = center.distance(snap.position());
        if (distance >= snap.blendRadius()) {
            return currentY;
        }
        double blend = 1.0 - distance / snap.blendRadius();
        return (int) Math.round(currentY * (1.0 - blend) + snap.elevation() * blend);
    }
}
