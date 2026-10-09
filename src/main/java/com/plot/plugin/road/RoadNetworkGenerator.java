package com.plot.plugin.road;

import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.pipeline.EdgeGenerationOutcome;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileSamplingAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plot.core.terrain.TerrainSampler;

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
                    RoadGenerationResult geometry = outcome.geometry();
                    if (geometry != null
                            && (geometry.droppedSolidCount > 0 || !geometry.placementRecords.isEmpty())) {
                        edgeResults.put(edgeId, geometry);
                    }
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

    /** 分帧预览：每帧生成的道路边数量。 */
    public static final int PREVIEW_EDGES_PER_TICK = 8;
    /** 分帧预览：每帧生成的路口数量。 */
    public static final int PREVIEW_JUNCTIONS_PER_TICK = 4;

    private final RoadGenerator roadGenerator;
    private final RoadJunctionGenerator junctionGenerator;

    public RoadNetworkGenerator(RoadGenerator roadGenerator) {
        this.roadGenerator = roadGenerator;
        this.junctionGenerator = new RoadJunctionGenerator(roadGenerator);
    }

    public NetworkGenerationResult generateAll(RoadNetwork network, World world) {
        if (network == null || world == null) {
            return new NetworkGenerationResult();
        }
        return generateAll(network, roadGenerator.createTerrainSampler(world));
    }

    /**
     * 在路网快照上生成，避免预览/落地修改 live 派生 {@code centerlinePoints}。
     */
    public NetworkGenerationResult generateAll(RoadNetwork network, TerrainSampler terrain) {
        RoadNetworkPreviewSession session = beginPreviewSession(network, terrain);
        if (!session.isValid()) {
            return new NetworkGenerationResult();
        }
        preparePreviewSession(session);
        runPreviewSessionToCompletion(session);
        return session.networkResult();
    }

    private void runPreviewSessionToCompletion(RoadNetworkPreviewSession session) {
        while (session.hasMoreEdges()) {
            tickPreviewSessionEdges(session, Integer.MAX_VALUE);
        }
        while (session.hasMoreJunctions()) {
            tickPreviewSessionJunctions(session, Integer.MAX_VALUE);
        }
        logGenerationSummary(session.networkResult(), session.nodeElevations());
    }

    /**
     * 开启分帧预览会话（快照 + 队列）；{@link #preparePreviewSession} 完成地形采样与标高决议。
     * <p>
     * 生成在 {@link RoadCrossingMaterializer#materializeForSnapshot(RoadNetwork)} 副本上运行，
     * 仅物化 registry 中已注册的 Crossing，不修改 live 路网拓扑。
     */
    public RoadNetworkPreviewSession beginPreviewSession(RoadNetwork network, TerrainSampler terrain) {
        if (network == null || terrain == null || network.getEdges().isEmpty()) {
            return RoadNetworkPreviewSession.empty(network);
        }
        RoadNetwork generationNetwork = RoadCrossingMaterializer.materializeForSnapshot(network);
        List<RoadEdge> edges = new ArrayList<>(generationNetwork.getEdges().values());
        List<RoadNode> junctionNodes = RoadNetworkPreviewSession.collectJunctionNodes(generationNetwork);
        return new RoadNetworkPreviewSession(
            network,
            generationNetwork,
            new NetworkGenerationResult(),
            terrain,
            edges,
            junctionNodes);
    }

    public RoadNetworkPreviewSession beginPreviewSession(RoadNetwork network, World world) {
        if (network == null || world == null) {
            return RoadNetworkPreviewSession.empty(network);
        }
        return beginPreviewSession(network, roadGenerator.createTerrainSampler(world));
    }

    /** 准备阶段：同步派生中心线并决议节点标高。 */
    public void preparePreviewSession(RoadNetworkPreviewSession session) {
        if (session == null || !session.isValid() || session.isPrepared()) {
            return;
        }
        RoadNetwork generationNetwork = session.generationNetwork();
        int synchronizedRoads = DerivedCenterlineSynchronizer.synchronizeAll(
            generationNetwork,
            roadGenerator.getConfig().getPathSampleDistance());
        if (synchronizedRoads > 0) {
            LOGGER.debug("生成前已在快照上同步 {} 条道路的设计平面线形到派生中心线", synchronizedRoads);
        }
        Map<String, Integer> nodeElevations =
            roadGenerator.resolveNetworkNodeElevations(generationNetwork, session.terrain());
        session.networkResult().setNodeElevations(nodeElevations);
        session.markPrepared();
    }

    /** @return 本帧处理的边数 */
    public int tickPreviewSessionEdges(RoadNetworkPreviewSession session, int maxEdges) {
        if (session == null || !session.isPrepared() || maxEdges <= 0) {
            return 0;
        }
        List<RoadEdge> batch = session.edgeBatch(maxEdges);
        if (batch.isEmpty()) {
            return 0;
        }
        Map<String, Integer> nodeElevations = session.nodeElevations();
        RoadNetwork generationNetwork = session.generationNetwork();
        NetworkGenerationResult networkResult = session.networkResult();
        for (RoadEdge edge : batch) {
            RoadNode start = generationNetwork.getNode(edge.getStartNodeId());
            RoadNode end = generationNetwork.getNode(edge.getEndNodeId());
            EdgeGenerationResult edgeOutcome = roadGenerator.generateEdgeOutcome(
                generationNetwork, edge, start, end, session.terrain(), nodeElevations);
            networkResult.recordEdgeOutcome(edge.getId(), edgeOutcome);
        }
        session.advanceEdges(batch.size());
        return batch.size();
    }

    /** @return 本帧处理的路口数 */
    public int tickPreviewSessionJunctions(RoadNetworkPreviewSession session, int maxJunctions) {
        if (session == null || !session.isPrepared() || maxJunctions <= 0) {
            return 0;
        }
        List<RoadNode> batch = session.junctionBatch(maxJunctions);
        if (batch.isEmpty()) {
            return 0;
        }
        Set<String> failedEdgeIds = session.networkResult().getFailedEdgeIds();
        Map<String, Integer> nodeElevations = session.nodeElevations();
        RoadNetwork generationNetwork = session.generationNetwork();
        NetworkGenerationResult networkResult = session.networkResult();
        for (RoadNode node : batch) {
            if (!shouldGenerateJunction(node, failedEdgeIds)) {
                networkResult.recordSkippedJunction(
                    node.getId(),
                    "connected edge generation failed");
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(
                    node, generationNetwork, session.terrain(), nodeElevations);
            if (!junctionBlocks.isEmpty()) {
                networkResult.junctionResults.put(node.getId(), junctionBlocks);
            }
        }
        session.advanceJunctions(batch.size());
        return batch.size();
    }

    public PreviewResult completePreviewSession(RoadNetworkPreviewSession session) {
        if (session == null || !session.isValid()) {
            return emptyPreviewResult();
        }
        return buildPreviewResult(session);
    }

    private void logGenerationSummary(NetworkGenerationResult networkResult, Map<String, Integer> nodeElevations) {
        LOGGER.info(
            "路网生成完成: {} 条边成功 / {} 总计, {} 失败, {} 跳过; {} 个路口, {} 个路口跳过（统一标高节点 {} 个）",
            networkResult.successEdgeCount(),
            networkResult.totalEdgeCount(),
            networkResult.getFailedEdgeIds().size(),
            networkResult.getSkippedEdgeIds().size(),
            networkResult.getJunctionResults().size(),
            networkResult.getSkippedJunctionIds().size(),
            nodeElevations.size());
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
        if (network == null || world == null) {
            return emptyPreviewResult();
        }
        return generatePreview(network, roadGenerator.createTerrainSampler(world));
    }

    /**
     * 无 World 的预览入口（基准 / Golden / 单元测试）。
     */
    public PreviewResult generatePreview(RoadNetwork network, TerrainSampler terrain) {
        RoadNetworkPreviewSession session = beginPreviewSession(network, terrain);
        if (!session.isValid()) {
            return emptyPreviewResult();
        }
        preparePreviewSession(session);
        runPreviewSessionToCompletion(session);
        return buildPreviewResult(session);
    }

    /**
     * 轻量纵断面采样：同步派生中心线、决议节点标高并对每条边求解 profile，
     * 不执行横断面生成、土方、路口方块与 placement 聚合。
     */
    public ProfileSamplingResult calculateProfileSampling(RoadNetwork network, World world) {
        if (network == null || world == null) {
            return ProfileSamplingResult.empty();
        }
        return calculateProfileSampling(network, roadGenerator.createTerrainSampler(world));
    }

    public ProfileSamplingResult calculateProfileSampling(RoadNetwork network, TerrainSampler terrain) {
        RoadNetworkPreviewSession session = beginPreviewSession(network, terrain);
        if (!session.isValid()) {
            return ProfileSamplingResult.empty();
        }
        preparePreviewSession(session);
        Map<String, Integer> nodeElevations = session.nodeElevations();
        RoadNetwork generationNetwork = session.generationNetwork();
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : generationNetwork.getEdges().values()) {
            RoadNode start = generationNetwork.getNode(edge.getStartNodeId());
            RoadNode end = generationNetwork.getNode(edge.getEndNodeId());
            if (start == null || end == null) {
                continue;
            }
            RoadGenerationResult profile = roadGenerator.sampleEdgeProfile(
                generationNetwork, edge, start, end, terrain, nodeElevations);
            if (profile != null && profile.hasProfileData()) {
                edgeResults.put(edge.getId(), profile);
            }
        }
        if (edgeResults.isEmpty()) {
            return ProfileSamplingResult.empty();
        }
        Map<String, RoadProfileChartData> roadProfiles = RoadProfileSamplingAssembler.assembleAll(
            generationNetwork,
            edgeResults,
            roadGenerator.getConfig());
        LOGGER.info("纵断面采样完成: {} 条边, {} 条道路纵断面", edgeResults.size(), roadProfiles.size());
        return new ProfileSamplingResult(generationNetwork, edgeResults, nodeElevations, roadProfiles);
    }

    private PreviewResult buildPreviewResult(RoadNetworkPreviewSession session) {
        RoadNetwork profileNetwork = session.generationNetwork();
        NetworkGenerationResult networkResult = session.networkResult();
        Map<String, RoadGenerationResult> edgeResults = networkResult.getEdgeResults();
        Map<String, RoadProfileChartData> roadProfiles = RoadProfileSamplingAssembler.assembleAll(
            profileNetwork,
            edgeResults,
            roadGenerator.getConfig());
        RoadGenerationResult aggregate = aggregateNetworkResult(session.sourceNetwork(), networkResult);
        return new PreviewResult(
            profileNetwork,
            aggregate,
            edgeResults,
            networkResult.getNodeElevations(),
            networkResult,
            roadProfiles);
    }

    private static PreviewResult emptyPreviewResult() {
        NetworkGenerationResult empty = new NetworkGenerationResult();
        return new PreviewResult(
            null,
            new RoadGenerationResult(0),
            Map.of(),
            Map.of(),
            empty,
            Map.of());
    }

    public record PreviewResult(
            RoadNetwork profileNetwork,
            RoadGenerationResult aggregate,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            NetworkGenerationResult networkResult,
            Map<String, RoadProfileChartData> roadProfiles) {
    }

    /** 仅纵断面采样结果，不含建造预览聚合。 */
    public record ProfileSamplingResult(
            RoadNetwork profileNetwork,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> nodeElevations,
            Map<String, RoadProfileChartData> roadProfiles) {

        public static ProfileSamplingResult empty() {
            return new ProfileSamplingResult(null, Map.of(), Map.of(), Map.of());
        }

        public boolean isEmpty() {
            return edgeResults.isEmpty();
        }
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
