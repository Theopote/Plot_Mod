package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private final Map<String, RoadGenerator.RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        private final Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults = new LinkedHashMap<>();

        public Map<String, RoadGenerator.RoadGenerationResult> getEdgeResults() {
            return Collections.unmodifiableMap(edgeResults);
        }

        public Map<String, RoadJunctionGenerator.JunctionBlocks> getJunctionResults() {
            return Collections.unmodifiableMap(junctionResults);
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

        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            RoadGenerator.RoadGenerationResult edgeResult =
                roadGenerator.generateEdge(edge, start, end, world);
            networkResult.edgeResults.put(edge.getId(), edgeResult);
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(node, network, world);
            if (!junctionBlocks.roadBlocks.isEmpty() || !junctionBlocks.sidewalkBlocks.isEmpty()) {
                networkResult.junctionResults.put(node.getId(), junctionBlocks);
            }
        }

        LOGGER.info("路网生成完成: {} 条边, {} 个路口",
            networkResult.edgeResults.size(), networkResult.junctionResults.size());
        return networkResult;
    }

    public RoadGenerator.RoadGenerationResult generateAggregated(RoadNetwork network, World world) {
        NetworkGenerationResult networkResult = generateAll(network, world);
        RoadGenerator.RoadGenerationResult aggregate = new RoadGenerator.RoadGenerationResult(0);
        for (RoadGenerator.RoadGenerationResult edgeResult : networkResult.getEdgeResults().values()) {
            roadGenerator.mergeResult(aggregate, edgeResult);
        }
        for (RoadJunctionGenerator.JunctionBlocks junctionBlocks : networkResult.getJunctionResults().values()) {
            roadGenerator.mergeJunctionBlocks(aggregate, junctionBlocks);
        }
        return aggregate;
    }

    public static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
