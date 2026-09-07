package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.EdgeGenerationOutcome;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路网级道路生成入口
 */
public class RoadNetworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetworkGenerator");

    /**
     * 路网生成结果：边与路口分域存储，并区分成功 / 跳过 / 失败。
     */
    public static class NetworkGenerationResult {
        private final Map<String, EdgeGenerationResult> edgeOutcomes = new LinkedHashMap<>();
        private final Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        private final Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults = new LinkedHashMap<>();
        private final Set<String> failedEdgeIds = new LinkedHashSet<>();
        private final Set<String> skippedEdgeIds = new LinkedHashSet<>();
        private final Set<String> skippedJunctionIds = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private Map<String, Integer> nodeElevations = Map.of();

        public Map<String, EdgeGenerationResult> getEdgeOutcomes() {
            return Collections.unmodifiableMap(edgeOutcomes);
        }

        public Map<String, RoadGenerationResult> getEdgeResults() {
            return Collections.unmodifiableMap(edgeResults);
        }

        public Map<String, RoadJunctionGenerator.JunctionBlocks> getJunctionResults() {
            return Collections.unmodifiableMap(junctionResults);
        }

        public Set<String> getFailedEdgeIds() {
            return Collections.unmodifiableSet(failedEdgeIds);
        }

        public Set<String> getSkippedEdgeIds() {
            return Collections.unmodifiableSet(skippedEdgeIds);
        }

        public Set<String> getSkippedJunctionIds() {
            return Collections.unmodifiableSet(skippedJunctionIds);
        }

        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public Map<String, Integer> getNodeElevations() {
            return nodeElevations;
        }

        void setNodeElevations(Map<String, Integer> nodeElevations) {
            this.nodeElevations = nodeElevations != null
                ? Collections.unmodifiableMap(nodeElevations)
                : Map.of();
        }

        void recordEdgeOutcome(String edgeId, EdgeGenerationResult outcome) {
            if (edgeId == null || outcome == null) {
                return;
            }
            edgeOutcomes.put(edgeId, outcome);
            switch (outcome.outcome()) {
                case SUCCESS -> edgeResults.put(edgeId, outcome.geometry());
                case SKIPPED -> {
                    skippedEdgeIds.add(edgeId);
                    if (!outcome.message().isBlank()) {
                        warnings.add("edge " + edgeId + ": " + outcome.message());
                    }
                }
                case FAILED -> {
                    failedEdgeIds.add(edgeId);
                    errors.add("edge " + edgeId + ": " + outcome.message());
                }
            }
        }

        void recordSkippedJunction(String nodeId, String reason) {
            if (nodeId == null) {
                return;
            }
            skippedJunctionIds.add(nodeId);
            if (reason != null && !reason.isBlank()) {
                warnings.add("junction " + nodeId + ": " + reason);
            }
        }

        public int successEdgeCount() {
            return (int) edgeOutcomes.values().stream()
                .filter(EdgeGenerationResult::isSuccess)
                .count();
        }

        public int totalEdgeCount() {
            return edgeOutcomes.size();
        }

        public boolean hasPartialFailure() {
            return !failedEdgeIds.isEmpty() || !skippedEdgeIds.isEmpty() || !skippedJunctionIds.isEmpty();
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
        int synchronizedRoads = DerivedCenterlineSynchronizer.synchronizeAll(
            network,
            roadGenerator.getConfig().getPathSampleDistance());
        if (synchronizedRoads > 0) {
            LOGGER.debug("生成前已同步 {} 条道路的设计平面线形到派生中心线", synchronizedRoads);
        }
        Map<String, Integer> nodeElevations =
            roadGenerator.resolveNetworkNodeElevations(network, terrain);
        networkResult.setNodeElevations(nodeElevations);

        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            EdgeGenerationResult edgeOutcome = roadGenerator.generateEdgeOutcome(
                network, edge, start, end, terrain, nodeElevations);
            networkResult.recordEdgeOutcome(edge.getId(), edgeOutcome);
        }

        Set<String> failedEdgeIds = networkResult.getFailedEdgeIds();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            if (!shouldGenerateJunction(node, failedEdgeIds)) {
                networkResult.recordSkippedJunction(
                    node.getId(),
                    "connected edge generation failed");
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(node, network, terrain, nodeElevations);
            if (!junctionBlocks.isEmpty()) {
                networkResult.junctionResults.put(node.getId(), junctionBlocks);
            }
        }

        LOGGER.info(
            "路网生成完成: {} 条边成功 / {} 总计, {} 失败, {} 跳过; {} 个路口, {} 个路口跳过（统一标高节点 {} 个）",
            networkResult.successEdgeCount(),
            networkResult.totalEdgeCount(),
            networkResult.getFailedEdgeIds().size(),
            networkResult.getSkippedEdgeIds().size(),
            networkResult.junctionResults.size(),
            networkResult.getSkippedJunctionIds().size(),
            nodeElevations.size());
        return networkResult;
    }

    /**
     * 连接任一边生成失败时，不单独生成路口（避免「路口在、连接道路没了」）。
     */
    public static boolean shouldGenerateJunction(RoadNode node, Set<String> failedEdgeIds) {
        if (node == null || failedEdgeIds == null || failedEdgeIds.isEmpty()) {
            return true;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            if (failedEdgeIds.contains(edgeId)) {
                return false;
            }
        }
        return true;
    }

    public RoadGenerationResult generateAggregated(RoadNetwork network, World world) {
        return generatePreview(network, world).aggregate();
    }

    public PreviewResult generatePreview(RoadNetwork network, World world) {
        NetworkGenerationResult networkResult = generateAll(network, world);
        RoadGenerationResult aggregate = aggregateNetworkResult(network, networkResult);
        return new PreviewResult(
            aggregate,
            networkResult.getEdgeResults(),
            networkResult.getNodeElevations(),
            networkResult);
    }

    public record PreviewResult(
            RoadGenerationResult aggregate,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            NetworkGenerationResult networkResult) {
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
        World world = client.world;
        return world;
    }
}
