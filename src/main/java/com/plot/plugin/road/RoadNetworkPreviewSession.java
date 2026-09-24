package com.plot.plugin.road;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 路网预览分帧会话：在快照网络上增量生成边与路口，供 {@link RoadNetworkPreviewJob} 按帧推进。
 */
public final class RoadNetworkPreviewSession {

    private final RoadNetwork sourceNetwork;
    private final RoadNetwork generationNetwork;
    private final RoadNetworkGenerator.NetworkGenerationResult networkResult;
    private final TerrainSampler terrain;
    private final List<RoadEdge> edges;
    private final List<RoadNode> junctionNodes;
    private int edgeIndex;
    private int junctionIndex;
    private boolean prepared;

    RoadNetworkPreviewSession(
            RoadNetwork sourceNetwork,
            RoadNetwork generationNetwork,
            RoadNetworkGenerator.NetworkGenerationResult networkResult,
            TerrainSampler terrain,
            List<RoadEdge> edges,
            List<RoadNode> junctionNodes) {
        this.sourceNetwork = sourceNetwork;
        this.generationNetwork = generationNetwork;
        this.networkResult = networkResult;
        this.terrain = terrain;
        this.edges = edges;
        this.junctionNodes = junctionNodes;
    }

    static RoadNetworkPreviewSession empty(RoadNetwork sourceNetwork) {
        return new RoadNetworkPreviewSession(
            sourceNetwork,
            null,
            new RoadNetworkGenerator.NetworkGenerationResult(),
            null,
            List.of(),
            List.of());
    }

    public boolean isValid() {
        return generationNetwork != null && terrain != null && !edges.isEmpty();
    }

    public RoadNetwork sourceNetwork() {
        return sourceNetwork;
    }

    RoadNetwork generationNetwork() {
        return generationNetwork;
    }

    RoadNetworkGenerator.NetworkGenerationResult networkResult() {
        return networkResult;
    }

    TerrainSampler terrain() {
        return terrain;
    }

    Map<String, Integer> nodeElevations() {
        return networkResult.getNodeElevations();
    }

    boolean isPrepared() {
        return prepared;
    }

    void markPrepared() {
        prepared = true;
    }

    int totalEdgeCount() {
        return edges.size();
    }

    int totalJunctionCount() {
        return junctionNodes.size();
    }

    int processedEdgeCount() {
        return edgeIndex;
    }

    int processedJunctionCount() {
        return junctionIndex;
    }

    public int totalWorkUnits() {
        return edges.size() + junctionNodes.size();
    }

    public int processedWorkUnits() {
        return Math.min(totalWorkUnits(), edgeIndex + junctionIndex);
    }

    public boolean hasMoreEdges() {
        return edgeIndex < edges.size();
    }

    public boolean hasMoreJunctions() {
        return junctionIndex < junctionNodes.size();
    }

    List<RoadEdge> edgeBatch(int maxEdges) {
        if (!hasMoreEdges() || maxEdges <= 0) {
            return List.of();
        }
        int end = Math.min(edges.size(), edgeIndex + maxEdges);
        return Collections.unmodifiableList(edges.subList(edgeIndex, end));
    }

    void advanceEdges(int count) {
        edgeIndex = Math.min(edges.size(), edgeIndex + Math.max(0, count));
    }

    List<RoadNode> junctionBatch(int maxJunctions) {
        if (!hasMoreJunctions() || maxJunctions <= 0) {
            return List.of();
        }
        int end = Math.min(junctionNodes.size(), junctionIndex + maxJunctions);
        return Collections.unmodifiableList(junctionNodes.subList(junctionIndex, end));
    }

    void advanceJunctions(int count) {
        junctionIndex = Math.min(junctionNodes.size(), junctionIndex + Math.max(0, count));
    }

    static List<RoadNode> collectJunctionNodes(RoadNetwork network) {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() >= 3) {
                junctions.add(node);
            }
        }
        return junctions;
    }
}
