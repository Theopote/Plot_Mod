package com.plot.plugin.road;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.manager.RoadNetworkPreviewJob;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.corridor.PlacementRecordColumnIndex;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.solid.RoadGenerationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 路网预览分帧会话：在快照网络上增量生成边与路口，供 {@link RoadNetworkPreviewJob} 按帧推进。
 */
public final class RoadNetworkPreviewSession {

    /** 汇总阶段子步进：每帧推进一档，避免边/路口完成后单帧卡死。 */
    public enum AggregationStep {
        PENDING,
        PROFILES,
        MERGE,
        VALIDATE,
        DONE
    }

    private final RoadNetwork sourceNetwork;
    private final RoadNetwork generationNetwork;
    private final com.plot.plugin.road.station.RoadStationingReadCache stationingCache;
    private final RoadNetworkGenerator.NetworkGenerationResult networkResult;
    private final TerrainSampler terrain;
    private final List<RoadEdge> edges;
    private final List<RoadNode> junctionNodes;
    private int edgeIndex;
    private int junctionIndex;
    private boolean prepared;
    private boolean tunnelConnectivityApplied;
    /** 0=壳层分帧，1=连通分帧，2=完成。 */
    private int tunnelPassStage;
    private int tunnelShellIndex;
    private int tunnelConnectivityIndex;
    /** 合并后校验的路口游标。 */
    private int validationJunctionIndex;
    private PlacementRecordColumnIndex validationColumnIndex;
    private AggregationStep aggregationStep = AggregationStep.PENDING;
    private Map<String, RoadProfileChartData> pendingRoadProfiles = Map.of();
    private RoadGenerationResult pendingAggregate;
    private RoadNetworkGenerator.PreviewResult completedPreview;

    RoadNetworkPreviewSession(
            RoadNetwork sourceNetwork,
            RoadNetwork generationNetwork,
            RoadNetworkGenerator.NetworkGenerationResult networkResult,
            TerrainSampler terrain,
            List<RoadEdge> edges,
            List<RoadNode> junctionNodes) {
        this.sourceNetwork = sourceNetwork;
        this.generationNetwork = generationNetwork;
        this.stationingCache = new com.plot.plugin.road.station.RoadStationingReadCache(generationNetwork);
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

    com.plot.plugin.road.station.RoadStationingReadCache stationingCache() {
        return stationingCache;
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

    boolean isTunnelConnectivityApplied() {
        return tunnelConnectivityApplied;
    }

    void markTunnelConnectivityApplied() {
        tunnelConnectivityApplied = true;
        tunnelPassStage = 2;
    }

    int tunnelPassStage() {
        return tunnelPassStage;
    }

    void setTunnelPassStage(int tunnelPassStage) {
        this.tunnelPassStage = Math.max(0, tunnelPassStage);
    }

    int tunnelShellIndex() {
        return tunnelShellIndex;
    }

    void setTunnelShellIndex(int tunnelShellIndex) {
        this.tunnelShellIndex = Math.max(0, tunnelShellIndex);
    }

    int tunnelConnectivityIndex() {
        return tunnelConnectivityIndex;
    }

    void setTunnelConnectivityIndex(int tunnelConnectivityIndex) {
        this.tunnelConnectivityIndex = Math.max(0, tunnelConnectivityIndex);
    }

    List<RoadNode> junctionNodes() {
        return Collections.unmodifiableList(junctionNodes);
    }

    int validationJunctionIndex() {
        return validationJunctionIndex;
    }

    void setValidationJunctionIndex(int validationJunctionIndex) {
        this.validationJunctionIndex = Math.max(0, validationJunctionIndex);
    }

    PlacementRecordColumnIndex validationColumnIndex() {
        return validationColumnIndex;
    }

    void setValidationColumnIndex(PlacementRecordColumnIndex validationColumnIndex) {
        this.validationColumnIndex = validationColumnIndex;
    }

    /** 壳层+连通各扫一遍路口队列。 */
    public int tunnelConnectivityWorkUnits() {
        return Math.max(1, junctionNodes.size() * 2);
    }

    public int processedTunnelConnectivityWorkUnits() {
        if (tunnelConnectivityApplied || tunnelPassStage >= 2) {
            return tunnelConnectivityWorkUnits();
        }
        int shellDone = Math.min(junctionNodes.size(), tunnelShellIndex);
        int connectDone = tunnelPassStage >= 1
            ? Math.min(junctionNodes.size(), tunnelConnectivityIndex)
            : 0;
        return shellDone + connectDone;
    }

    public int validationWorkUnits() {
        return Math.max(1, junctionNodes.size());
    }

    public int processedValidationWorkUnits() {
        if (aggregationStep == AggregationStep.DONE) {
            return validationWorkUnits();
        }
        if (aggregationStep != AggregationStep.VALIDATE) {
            return 0;
        }
        return Math.min(validationWorkUnits(), validationJunctionIndex);
    }

    public AggregationStep aggregationStep() {
        return aggregationStep;
    }

    void setAggregationStep(AggregationStep aggregationStep) {
        this.aggregationStep = aggregationStep == null ? AggregationStep.PENDING : aggregationStep;
    }

    Map<String, RoadProfileChartData> pendingRoadProfiles() {
        return pendingRoadProfiles;
    }

    void setPendingRoadProfiles(Map<String, RoadProfileChartData> pendingRoadProfiles) {
        this.pendingRoadProfiles = pendingRoadProfiles == null ? Map.of() : pendingRoadProfiles;
    }

    RoadGenerationResult pendingAggregate() {
        return pendingAggregate;
    }

    void setPendingAggregate(RoadGenerationResult pendingAggregate) {
        this.pendingAggregate = pendingAggregate;
    }

    public RoadNetworkGenerator.PreviewResult completedPreview() {
        return completedPreview;
    }

    void setCompletedPreview(RoadNetworkGenerator.PreviewResult completedPreview) {
        this.completedPreview = completedPreview;
    }

    public int totalWorkUnits() {
        return edges.size() + junctionNodes.size();
    }

    public int processedWorkUnits() {
        return Math.min(totalWorkUnits(), edgeIndex + junctionIndex);
    }

    /** 汇总子步进：profiles + merge + 分帧校验路口数。 */
    public int aggregationWorkUnits() {
        return 2 + validationWorkUnits();
    }

    public int processedAggregationWorkUnits() {
        return switch (aggregationStep) {
            case PENDING, PROFILES -> 0;
            case MERGE -> 1;
            case VALIDATE -> 2 + processedValidationWorkUnits();
            case DONE -> aggregationWorkUnits();
        };
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
