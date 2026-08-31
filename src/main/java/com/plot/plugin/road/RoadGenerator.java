package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.pipeline.RoadEdgeBuildOrchestrator;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineHost;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.GradeSeparationPolicy;
import com.plot.plugin.road.pipeline.profile.NetworkNodeElevationResolver;
import com.plot.plugin.road.pipeline.profile.NodeTargetHeightResolver;
import com.plot.plugin.road.pipeline.profile.RoadGeneratorProfileContext;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolveCoordinator;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 道路生成器（Façade）
 *
 * <p>对外入口保持不变；中心线落地由 {@link com.plot.plugin.road.pipeline.RoadEdgeBuildOrchestrator} 与
 * {@link com.plot.plugin.road.pipeline.RoadGenerationPipeline} 分阶段编排。
 */
public class RoadGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadGenerator");
    
    private final RoadGenerationPipelineHost pipelineHost;
    private final RoadEdgeBuildOrchestrator edgeBuild;
    private final GradeSeparationPolicy gradeSeparationPolicy;
    private final NodeTargetHeightResolver nodeTargetHeightResolver;
    private final NetworkNodeElevationResolver networkNodeElevationResolver;

    public RoadGenerator(
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.pipelineHost = new RoadGenerationPipelineHost(config, coordinateTransformer, projectionHandler);
        RoadGeneratorProfileContext profileContext = new RoadGeneratorProfileContext(config, pipelineHost::estimateCanvasUnitsPerBlock);
        this.gradeSeparationPolicy = new GradeSeparationPolicy(profileContext);
        this.nodeTargetHeightResolver = new NodeTargetHeightResolver(profileContext, gradeSeparationPolicy);
        this.networkNodeElevationResolver =
            new NetworkNodeElevationResolver(profileContext, gradeSeparationPolicy, nodeTargetHeightResolver);
        RoadProfileSolveCoordinator profileSolve = new RoadProfileSolveCoordinator(
                profileContext,
            new com.plot.plugin.road.pipeline.profile.ProfileEndpointHeightResolver(
                gradeSeparationPolicy, nodeTargetHeightResolver));
        this.edgeBuild = new RoadEdgeBuildOrchestrator(profileSolve);
    }

    public RoadSystemConfig getConfig() {
        return pipelineHost.config();
    }

    public TerrainSampler createTerrainSampler(World world) {
        return MinecraftTerrainSampler.of(world, pipelineHost.coordinateTransformer());
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
        synchronizeDerivedCenterline(network, edge);
        return edgeBuild.generateEdge(
            network, edge, startNode, endNode, terrain, networkNodeElevations, pipelineHost);
    }

    private void synchronizeDerivedCenterline(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null || edge.getRoadId() == null) {
            return;
        }
        Road road = network.getRoad(edge.getRoadId());
        if (road == null) {
            return;
        }
        DerivedCenterlineSynchronizer.synchronizeRoad(
            network,
            road,
            pipelineHost.config().getPathSampleDistance());
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
        return edgeBuild.generateFromPathPoints(pathPoints, terrain, manualRoadElevation, pipelineHost);
    }

    RoadGenerationPipelineHost pipelineHost() {
        return pipelineHost;
    }

    public int computeJunctionTargetHeight(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return networkNodeElevationResolver.junctionTargetHeight(node, network, terrain);
    }

    /**
     * 路网节点统一标高（第一遍）：按各边自然高程决议，供边生成第二遍强制对齐端点。
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
        return pipelineHost.toBlockPos(canvasPos, y);
    }

    public double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
        return pipelineHost.estimateCanvasUnitsPerBlock(pathPoints, segments);
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
        return pipelineHost.resolveBlockId(material);
    }
}
