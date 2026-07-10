package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路网级道路生成入口
 */
public class RoadNetworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetworkGenerator");

    private final RoadGenerator roadGenerator;
    private final RoadJunctionGenerator junctionGenerator;

    public RoadNetworkGenerator(RoadGenerator roadGenerator) {
        this.roadGenerator = roadGenerator;
        this.junctionGenerator = new RoadJunctionGenerator(roadGenerator);
    }

    public Map<String, RoadGenerator.RoadGenerationResult> generateAll(RoadNetwork network, World world) {
        Map<String, RoadGenerator.RoadGenerationResult> results = new LinkedHashMap<>();
        if (network == null || world == null) {
            return results;
        }

        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            RoadGenerator.RoadGenerationResult edgeResult =
                roadGenerator.generateEdge(edge, start, end, world);
            results.put(edge.getId(), edgeResult);
        }

        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(node, network, world);
            RoadGenerator.RoadGenerationResult first = results.values().stream().findFirst().orElse(null);
            if (first != null) {
                for (BlockPos pos : junctionBlocks.roadBlocks) {
                    first.roadBlocks.add(pos);
                }
                for (BlockPos pos : junctionBlocks.sidewalkBlocks) {
                    first.sidewalkBlocks.add(pos);
                }
            }
        }

        LOGGER.info("路网生成完成: {} 条边", results.size());
        return results;
    }

    public RoadGenerator.RoadGenerationResult generateAggregated(RoadNetwork network, World world) {
        Map<String, RoadGenerator.RoadGenerationResult> edgeResults = generateAll(network, world);
        RoadGenerator.RoadGenerationResult aggregate = new RoadGenerator.RoadGenerationResult(0);
        for (RoadGenerator.RoadGenerationResult result : edgeResults.values()) {
            roadGenerator.mergeResult(aggregate, result);
        }
        return aggregate;
    }

    public static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
