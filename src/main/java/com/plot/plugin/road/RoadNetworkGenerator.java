package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路网级道路生成入口
 */
public class RoadNetworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetworkGenerator");

    /**
     * 路网生成结果：边与路口分域存储
     */
    public static class NetworkGenerationResult {
        private final Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        private final Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults = new LinkedHashMap<>();
        private Map<String, Integer> nodeElevations = Map.of();

        public Map<String, RoadGenerationResult> getEdgeResults() {
            return Collections.unmodifiableMap(edgeResults);
        }

        public Map<String, RoadJunctionGenerator.JunctionBlocks> getJunctionResults() {
            return Collections.unmodifiableMap(junctionResults);
        }

        public Map<String, Integer> getNodeElevations() {
            return nodeElevations;
        }

        void setNodeElevations(Map<String, Integer> nodeElevations) {
            this.nodeElevations = nodeElevations != null
                ? Collections.unmodifiableMap(nodeElevations)
                : Map.of();
        }

        public boolean isEmpty() {
            return edgeResults.isEmpty() && junctionResults.isEmpty();
        }
    }

    private final RoadGenerator roadGenerator;
    private final RoadJunctionGenerator junctionGenerator;

    public RoadNetworkGenerator(RoadGenerator roadGenerator) {
        this.roadGenerator = roadGenerator;
        this.junctionGenerator = new RoadJunctionGenerator(roadGenerator);
    }

    public NetworkGenerationResult generateAll(RoadNetwork network, World world) {
        NetworkGenerationResult networkResult = new NetworkGenerationResult();
        if (network == null || world == null) {
            return networkResult;
        }

        TerrainSampler terrain = roadGenerator.createTerrainSampler(world);
        // 第一遍：决议全网节点统一标高，消除路口台阶
        Map<String, Integer> nodeElevations =
            roadGenerator.resolveNetworkNodeElevations(network, terrain);
        networkResult.setNodeElevations(nodeElevations);

        // 第二遍：各边端点强制对齐到统一标高
        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            RoadGenerationResult edgeResult =
                roadGenerator.generateEdge(network, edge, start, end, terrain, nodeElevations);
            networkResult.edgeResults.put(edge.getId(), edgeResult);
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(node, network, terrain, nodeElevations);
            if (!junctionBlocks.isEmpty()) {
                networkResult.junctionResults.put(node.getId(), junctionBlocks);
            }
        }

        LOGGER.info("路网生成完成: {} 条边, {} 个路口（统一标高节点 {} 个）",
            networkResult.edgeResults.size(),
            networkResult.junctionResults.size(),
            nodeElevations.size());
        return networkResult;
    }

    public RoadGenerationResult generateAggregated(RoadNetwork network, World world) {
        return generatePreview(network, world).aggregate();
    }

    public PreviewResult generatePreview(RoadNetwork network, World world) {
        NetworkGenerationResult networkResult = generateAll(network, world);
        RoadGenerationResult aggregate = aggregateNetworkResult(network, networkResult);
        return new PreviewResult(aggregate, networkResult.getEdgeResults(), networkResult.getNodeElevations());
    }

    public record PreviewResult(
            RoadGenerationResult aggregate,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations) {
    }

    private RoadGenerationResult aggregateNetworkResult(
            RoadNetwork network,
            NetworkGenerationResult networkResult) {
        return RoadGenerationResultAssembler.aggregateNetwork(
            network,
            networkResult.getEdgeResults().values(),
            networkResult.getJunctionResults(),
            roadGenerator.pipelineHost());
    }

    /**
     * 获取客户端世界实例（缓存引用避免TOCTOU问题）
     */
    public static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        // 缓存world引用，避免在检查后使用前变为null
        World world = client.world;
        return world;
    }
}
