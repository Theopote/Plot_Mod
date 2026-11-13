package com.plot.plugin.road;

import com.plot.plugin.road.crossing.CrossingMaterializationFailure;
import com.plot.plugin.road.crossing.CrossingMaterializationReport;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.crossing.RoadStackCrossingReconciler;
import com.plot.plugin.road.crossing.SnapshotMaterialization;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadEdgeGenerationOrdering;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import com.plot.plugin.road.pipeline.corridor.JunctionFinalVoxelValidator;
import com.plot.plugin.road.pipeline.corridor.JunctionProtectedOverburdenValidator;
import com.plot.plugin.road.pipeline.corridor.PlacementRecordColumnIndex;
import com.plot.plugin.road.pipeline.corridor.TunnelFinalVoxelValidator;
import com.plot.plugin.road.pipeline.corridor.TunnelJunctionConnectivityPass;
import com.plot.plugin.road.pipeline.corridor.TunnelJunctionShellPass;
import com.plot.plugin.road.pipeline.profile.AdjacentJunctionProfileContinuity;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileSamplingAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.RoadCorridorTerrainPrefetch;
import com.plot.plugin.road.vertical.VerticalAlignmentGradeSmoother;
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
        private final Map<String, JunctionConstructionReport> junctionReports = new LinkedHashMap<>();
        private final Set<String> failedEdgeIds = new LinkedHashSet<>();
        private final Set<String> skippedEdgeIds = new LinkedHashSet<>();
        private final Set<String> skippedJunctionIds = new LinkedHashSet<>();
        private final Set<String> missingJunctionValidationIds = new LinkedHashSet<>();
        private final Set<String> failClosedJunctionIds = new LinkedHashSet<>();
        private final Set<String> protectedOverburdenViolationIds = new LinkedHashSet<>();
        private final List<CrossingMaterializationFailure> crossingMaterializationFailures = new ArrayList<>();
        private final List<AdjacentJunctionProfileContinuity.Violation> adjacentJunctionProfileViolations =
            new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private Map<String, Integer> nodeElevations = Map.of();

        public Map<String, RoadGenerationResult> getEdgeResults() {
            return Collections.unmodifiableMap(edgeResults);
        }

        public Map<String, RoadJunctionGenerator.JunctionBlocks> getJunctionResults() {
            return Collections.unmodifiableMap(junctionResults);
        }

        public Map<String, JunctionConstructionReport> getJunctionReports() {
            return Collections.unmodifiableMap(junctionReports);
        }

        void mergeJunctionReport(JunctionConstructionReport report) {
            if (report == null || report.nodeId() == null || report.nodeId().isBlank()) {
                return;
            }
            junctionReports.merge(report.nodeId(), report, JunctionConstructionReport::merge);
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

        public Set<String> getMissingJunctionValidationIds() {
            return Collections.unmodifiableSet(missingJunctionValidationIds);
        }

        public Set<String> getFailClosedJunctionIds() {
            return Collections.unmodifiableSet(failClosedJunctionIds);
        }

        public Set<String> getProtectedOverburdenViolationIds() {
            return Collections.unmodifiableSet(protectedOverburdenViolationIds);
        }

        public List<CrossingMaterializationFailure> getCrossingMaterializationFailures() {
            return Collections.unmodifiableList(crossingMaterializationFailures);
        }

        public List<AdjacentJunctionProfileContinuity.Violation> getAdjacentJunctionProfileViolations() {
            return Collections.unmodifiableList(adjacentJunctionProfileViolations);
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

        void recordCrossingMaterialization(CrossingMaterializationReport report) {
            if (report == null || report.failures().isEmpty()) {
                return;
            }
            crossingMaterializationFailures.addAll(report.failures());
            for (CrossingMaterializationFailure failure : report.failures()) {
                errors.add("crossing " + failure.crossingId() + ": " + failure.detail());
            }
        }

        void recordMissingJunctionValidation(String nodeId, String reason) {
            if (nodeId == null) {
                return;
            }
            missingJunctionValidationIds.add(nodeId);
            errors.add("junction " + nodeId + ": "
                + (reason != null && !reason.isBlank() ? reason : "expected validation result missing"));
        }

        void recordFailClosedJunction(String nodeId, String reason) {
            if (nodeId == null) {
                return;
            }
            failClosedJunctionIds.add(nodeId);
            errors.add("junction " + nodeId + " tunnel-protect conflict (fail-closed): "
                + (reason != null && !reason.isBlank() ? reason : "destructive clearance denied"));
        }

        void recordProtectedOverburdenViolation(String nodeId, String detail) {
            if (nodeId == null) {
                return;
            }
            protectedOverburdenViolationIds.add(nodeId);
            errors.add("junction " + nodeId + " protected overburden: "
                + (detail != null && !detail.isBlank() ? detail : "violation"));
        }

        void recordAdjacentJunctionProfileViolations(
                List<AdjacentJunctionProfileContinuity.Violation> violations) {
            if (violations == null || violations.isEmpty()) {
                return;
            }
            adjacentJunctionProfileViolations.addAll(violations);
            for (AdjacentJunctionProfileContinuity.Violation violation : violations) {
                String detail = violation.detail();
                if (violation.blocking()) {
                    errors.add(detail);
                } else {
                    warnings.add(detail);
                }
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
            return !failedEdgeIds.isEmpty()
                || !skippedEdgeIds.isEmpty()
                || !skippedJunctionIds.isEmpty()
                || !crossingMaterializationFailures.isEmpty()
                || !missingJunctionValidationIds.isEmpty()
                || !failClosedJunctionIds.isEmpty()
                || !protectedOverburdenViolationIds.isEmpty()
                || adjacentJunctionProfileViolations.stream().anyMatch(
                    AdjacentJunctionProfileContinuity.Violation::blocking);
        }

        /**
         * 供状态栏 / 建造面板展示的具体失败分类（避免一律显示「未生成道路」）。
         */
        public List<String> partialFailureDetails() {
            List<String> details = new ArrayList<>();
            if (!failedEdgeIds.isEmpty()) {
                details.add("failedEdges=" + failedEdgeIds.size() + " " + failedEdgeIds);
            }
            if (!skippedEdgeIds.isEmpty()) {
                details.add("skippedEdges=" + skippedEdgeIds.size() + " " + skippedEdgeIds);
            }
            if (!skippedJunctionIds.isEmpty()) {
                details.add("skippedJunctions=" + skippedJunctionIds.size() + " " + skippedJunctionIds);
            }
            if (!failClosedJunctionIds.isEmpty()) {
                details.add("tunnelProtectConflicts=" + failClosedJunctionIds);
            }
            if (!protectedOverburdenViolationIds.isEmpty()) {
                details.add("protectedOverburdenViolations=" + protectedOverburdenViolationIds);
            }
            if (!crossingMaterializationFailures.isEmpty()) {
                details.add("crossingMaterializationFailures=" + crossingMaterializationFailures.size());
                for (CrossingMaterializationFailure failure : crossingMaterializationFailures) {
                    details.add("  crossing " + failure.crossingId() + ": " + failure.detail());
                }
            }
            if (!missingJunctionValidationIds.isEmpty()) {
                details.add("missingJunctionValidation=" + missingJunctionValidationIds);
            }
            for (AdjacentJunctionProfileContinuity.Violation violation : adjacentJunctionProfileViolations) {
                if (violation.blocking()) {
                    details.add(violation.detail());
                }
            }
            for (String error : errors) {
                if (error != null && !error.isBlank() && details.stream().noneMatch(error::equals)) {
                    details.add(error);
                }
            }
            return List.copyOf(details);
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

    public TerrainSampler createTerrainSampler(net.minecraft.world.World world) {
        return roadGenerator.createTerrainSampler(world);
    }

    public com.plot.plugin.config.RoadSystemConfig getConfig() {
        return roadGenerator.getConfig();
    }

    public com.plot.api.world.ICoordinateService coordinateTransformer() {
        return roadGenerator.coordinateTransformer();
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
        // 先路口铺装确定权威高程场，再写隧道壳顶衬，避免壳层另起一套标高。
        while (session.hasMoreJunctions()) {
            tickPreviewSessionJunctions(session, Integer.MAX_VALUE);
        }
        applyPreviewSessionTunnelConnectivity(session);
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
        reconcileCrossingsForGeneration(network);
        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNetwork generationNetwork = materialization.network();
        NetworkGenerationResult networkResult = new NetworkGenerationResult();
        networkResult.recordCrossingMaterialization(materialization.report());
        List<RoadEdge> edges = RoadEdgeGenerationOrdering.sortForGeneration(
            generationNetwork,
            new ArrayList<>(generationNetwork.getEdges().values()));
        List<RoadNode> junctionNodes = RoadNetworkPreviewSession.collectJunctionNodes(generationNetwork);
        return new RoadNetworkPreviewSession(
            network,
            generationNetwork,
            networkResult,
            terrain,
            edges,
            junctionNodes);
    }

    public RoadNetworkPreviewSession beginPreviewSession(RoadNetwork network, World world) {
        if (network == null || world == null) {
            return RoadNetworkPreviewSession.empty(network);
        }
        return beginPreviewSession(network, prepareTerrainForNetwork(network, world));
    }

    /**
     * 预览/纵断面采样前解析走廊地形：缓存命中则用服务端预取快照，否则非阻塞启动预取并先用客户端采样。
     */
    private TerrainSampler prepareTerrainForNetwork(RoadNetwork network, World world) {
        TerrainSampler clientSampler = roadGenerator.createTerrainSampler(world);
        return RoadCorridorTerrainPrefetch.resolve(
            network,
            world,
            roadGenerator.coordinateTransformer(),
            roadGenerator.getConfig(),
            roadGenerator::estimateCanvasUnitsPerBlock,
            clientSampler);
    }

    /** 准备阶段：同步派生中心线并决议节点标高。 */
    public void preparePreviewSession(RoadNetworkPreviewSession session) {
        if (session == null || !session.isValid() || session.isPrepared()) {
            return;
        }
        RoadNetwork generationNetwork = session.generationNetwork();
        Set<String> confirmedProfiles = new LinkedHashSet<>();
        for (var road : generationNetwork.getRoads().values()) {
            if (com.plot.plugin.road.vertical.ResolvedProfileAuthority.usesEditedProfileForGeneration(generationNetwork, road)) {
                confirmedProfiles.add(road.getId());
            }
        }
        int strippedProfiles = VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(
            generationNetwork);
        if (strippedProfiles > 0) {
            LOGGER.debug("生成快照已剥离 {} 条道路的非权威设计纵断面", strippedProfiles);
        }
        int synchronizedRoads = DerivedCenterlineSynchronizer.synchronizeAll(
            generationNetwork,
            roadGenerator.getConfig().getPathSampleDistance());
        if (synchronizedRoads > 0) {
            // Rebuilding a derived polyline cache from the same horizontal design
            // must not demote a previously confirmed vertical design on this snapshot.
            for (String id : confirmedProfiles) {
                var road = generationNetwork.getRoad(id);
                road.setEditedGeometrySignature(com.plot.plugin.road.vertical.EditedProfileGeometry.signature(generationNetwork, road));
            }
            LOGGER.debug("生成前已在快照上同步 {} 条道路的设计平面线形到派生中心线", synchronizedRoads);
        }
        try (var ignored = session.stationingCache().activate()) {
            int smoothedRoads = VerticalAlignmentGradeSmoother.smoothAllExceeding(
                generationNetwork,
                roadGenerator.getConfig());
            if (smoothedRoads > 0) {
                LOGGER.info("生成前已自动平缓 {} 条道路纵断面坡度", smoothedRoads);
            }
            Map<String, Integer> nodeElevations =
                roadGenerator.resolveNetworkNodeElevations(generationNetwork, session.terrain());
            session.networkResult().setNodeElevations(nodeElevations);
            session.markPrepared();
        }
    }

    /** @return 本帧处理的边数 */
    public int tickPreviewSessionEdges(RoadNetworkPreviewSession session, int maxEdges) {
        return tickPreviewSessionEdges(session, maxEdges, Long.MAX_VALUE);
    }

    /** Yield between items once the frame budget is consumed; always finish one item. */
    public int tickPreviewSessionEdges(RoadNetworkPreviewSession session, int maxEdges, long budgetNanos) {
        long started = System.nanoTime();
        int processed = 0;
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
        try (var ignored = session.stationingCache().activate()) {
            for (RoadEdge edge : batch) {
                if (processed > 0 && System.nanoTime() - started >= budgetNanos) break;
                processed++;
                long edgeStarted = System.nanoTime();
                RoadNode start = generationNetwork.getNode(edge.getStartNodeId());
                RoadNode end = generationNetwork.getNode(edge.getEndNodeId());
                EdgeGenerationResult edgeOutcome = roadGenerator.generateEdgeOutcome(
                    generationNetwork, edge, start, end, session.terrain(), nodeElevations);
                networkResult.recordEdgeOutcome(edge.getId(), edgeOutcome);
                long edgeMs = (System.nanoTime() - edgeStarted) / 1_000_000L;
                if (edgeMs >= 500L) {
                    LOGGER.info("预览边生成较慢 edge={} durationMs={}", edge.getId(), edgeMs);
                }
            }
        }
        session.advanceEdges(processed);
        return processed;
    }

    /** @return 本帧处理的路口数 */
    public int tickPreviewSessionJunctions(RoadNetworkPreviewSession session, int maxJunctions) {
        return tickPreviewSessionJunctions(session, maxJunctions, Long.MAX_VALUE);
    }

    /** Yield between items once the frame budget is consumed; always finish one item. */
    public int tickPreviewSessionJunctions(RoadNetworkPreviewSession session, int maxJunctions, long budgetNanos) {
        long started = System.nanoTime();
        int processed = 0;
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
        try (var ignored = session.stationingCache().activate()) {
            for (RoadNode node : batch) {
                if (processed > 0 && System.nanoTime() - started >= budgetNanos) break;
                processed++;
                long junctionStarted = System.nanoTime();
                if (!shouldGenerateJunction(node, failedEdgeIds)) {
                    networkResult.recordSkippedJunction(
                        node.getId(),
                        "connected edge generation failed");
                    continue;
                }
                RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                    junctionGenerator.generateJunction(
                        node,
                        generationNetwork,
                        session.terrain(),
                        nodeElevations,
                        networkResult.getEdgeResults());
                // 立交下层可能故意不铺广场（仅清障）；只要已写出校验清单就视为已生成，
                // 避免空 solids 被记成 skippedJunction → 误报「未生成道路」。
                if (!junctionBlocks.isEmpty() || hasJunctionGenerationManifest(junctionBlocks)) {
                    networkResult.junctionResults.put(node.getId(), junctionBlocks);
                } else {
                    networkResult.recordSkippedJunction(
                        node.getId(),
                        "no plaza generated");
                }
                if (junctionBlocks.constructionContext() != null
                        && junctionBlocks.constructionContext().failClosed()) {
                    networkResult.recordFailClosedJunction(
                        node.getId(),
                        junctionBlocks.constructionContext().failClosedReason());
                }
                if (junctionBlocks.report() != null) {
                    networkResult.mergeJunctionReport(junctionBlocks.report());
                }
                long junctionMs = (System.nanoTime() - junctionStarted) / 1_000_000L;
                if (junctionMs >= 500L) {
                    LOGGER.info("预览路口生成较慢 junction={} durationMs={}", node.getId(), junctionMs);
                }
            }
        }
        session.advanceJunctions(processed);
        return processed;
    }

    /**
     * 边/路口完成后应用隧道路口壳与连通（同步路径一次跑完）。
     * 已应用过则跳过，避免汇总阶段重复重算。
     */
    public void applyPreviewSessionTunnelConnectivity(RoadNetworkPreviewSession session) {
        if (session == null || !session.isPrepared() || session.isTunnelConnectivityApplied()) {
            return;
        }
        applyTunnelJunctionConnectivity(session);
        session.markTunnelConnectivityApplied();
    }

    /**
     * 分帧壳层+连通：每帧按预算处理若干路口，避免 CONNECTIVITY 单帧卡死。
     *
     * @return true 当壳层与连通都已完成
     */
    public boolean tickPreviewSessionTunnelConnectivity(
            RoadNetworkPreviewSession session,
            long budgetNanos) {
        if (session == null || !session.isPrepared()) {
            return true;
        }
        if (session.isTunnelConnectivityApplied()) {
            return true;
        }
        long started = System.nanoTime();
        List<RoadNode> junctions = session.junctionNodes();
        int processed = 0;
        if (session.tunnelPassStage() == 0) {
            while (session.tunnelShellIndex() < junctions.size()) {
                if (processed > 0 && System.nanoTime() - started >= budgetNanos) {
                    return false;
                }
                RoadNode node = junctions.get(session.tunnelShellIndex());
                session.setTunnelShellIndex(session.tunnelShellIndex() + 1);
                processed++;
                if (node == null) {
                    continue;
                }
                long nodeStarted = System.nanoTime();
                applyTunnelJunctionShellForNodes(session, Set.of(node.getId()));
                long nodeMs = (System.nanoTime() - nodeStarted) / 1_000_000L;
                if (nodeMs >= 500L) {
                    LOGGER.info("预览隧道路口壳较慢 junction={} durationMs={}", node.getId(), nodeMs);
                }
            }
            session.setTunnelPassStage(1);
        }
        if (session.tunnelPassStage() == 1) {
            while (session.tunnelConnectivityIndex() < junctions.size()) {
                if (processed > 0 && System.nanoTime() - started >= budgetNanos) {
                    return false;
                }
                RoadNode node = junctions.get(session.tunnelConnectivityIndex());
                session.setTunnelConnectivityIndex(session.tunnelConnectivityIndex() + 1);
                processed++;
                if (node == null) {
                    continue;
                }
                long nodeStarted = System.nanoTime();
                applyTunnelJunctionConnectivityForNodes(session, Set.of(node.getId()));
                long nodeMs = (System.nanoTime() - nodeStarted) / 1_000_000L;
                if (nodeMs >= 500L) {
                    LOGGER.info("预览隧道连通较慢 junction={} durationMs={}", node.getId(), nodeMs);
                }
            }
            session.markTunnelConnectivityApplied();
        }
        return true;
    }

    /**
     * 分帧汇总：每调用推进一档（纵断面装配 → 合并 → 校验），完成后返回 true。
     */
    public boolean tickPreviewSessionAggregation(RoadNetworkPreviewSession session) {
        return tickPreviewSessionAggregation(session, 8_000_000L);
    }

    public boolean tickPreviewSessionAggregation(RoadNetworkPreviewSession session, long budgetNanos) {
        if (session == null || !session.isValid()) {
            return true;
        }
        if (session.aggregationStep() == RoadNetworkPreviewSession.AggregationStep.DONE
            && session.completedPreview() != null) {
            return true;
        }
        try (var ignored = session.stationingCache().activate()) {
            switch (session.aggregationStep()) {
                case PENDING, PROFILES -> {
                    applyPreviewSessionTunnelConnectivity(session);
                    Map<String, RoadProfileChartData> roadProfiles = RoadProfileSamplingAssembler.assembleAll(
                        session.generationNetwork(),
                        session.networkResult().getEdgeResults(),
                        roadGenerator.getConfig());
                    session.setPendingRoadProfiles(roadProfiles);
                    session.setAggregationStep(RoadNetworkPreviewSession.AggregationStep.MERGE);
                    return false;
                }
                case MERGE -> {
                    long mergeStarted = System.nanoTime();
                    RoadGenerationResult aggregate = aggregateNetworkResult(
                        session.generationNetwork(), session.networkResult());
                    session.setPendingAggregate(aggregate);
                    LOGGER.info(
                        "预览汇总合并完成 placements={} mergeEdgesMs={} mergeJunctionsMs={} clearMs={} railMs={} totalMs={}",
                        aggregate.placementRecords.size(),
                        aggregate.assembleMergeEdgesNanos / 1_000_000L,
                        aggregate.assembleMergeJunctionsNanos / 1_000_000L,
                        aggregate.assembleClearIntrudingFillNanos / 1_000_000L,
                        aggregate.assembleRailPassNanos / 1_000_000L,
                        (System.nanoTime() - mergeStarted) / 1_000_000L);
                    session.setAggregationStep(RoadNetworkPreviewSession.AggregationStep.VALIDATE);
                    return false;
                }
                case VALIDATE -> {
                    return tickPreviewSessionValidation(session, budgetNanos);
                }
                case DONE -> {
                    return true;
                }
            }
        }
        return session.aggregationStep() == RoadNetworkPreviewSession.AggregationStep.DONE;
    }

    public PreviewResult completePreviewSession(RoadNetworkPreviewSession session) {
        if (session == null || !session.isValid()) {
            return emptyPreviewResult();
        }
        while (!tickPreviewSessionAggregation(session, Long.MAX_VALUE)) {
            // drain remaining aggregation steps synchronously
        }
        PreviewResult completed = session.completedPreview();
        return completed != null ? completed : emptyPreviewResult();
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

    static boolean hasJunctionGenerationManifest(RoadJunctionGenerator.JunctionBlocks blocks) {
        if (blocks == null || blocks.validationManifest() == null) {
            return false;
        }
        String nodeId = blocks.validationManifest().nodeId();
        return nodeId != null && !nodeId.isBlank();
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
        return calculateProfileSampling(network, prepareTerrainForNetwork(network, world));
    }

    public ProfileSamplingResult calculateProfileSampling(RoadNetwork network, TerrainSampler terrain) {
        RoadNetworkPreviewSession session = beginPreviewSession(network, terrain);
        if (!session.isValid()) {
            return ProfileSamplingResult.empty();
        }
        preparePreviewSession(session);
        try (var ignored = session.stationingCache().activate()) {
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
    }

    private PreviewResult buildPreviewResult(RoadNetworkPreviewSession session) {
        while (!tickPreviewSessionAggregation(session, Long.MAX_VALUE)) {
            // sync path drains aggregation without per-frame budget
        }
        PreviewResult completed = session.completedPreview();
        return completed != null ? completed : emptyPreviewResult();
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

    private static void reconcileCrossingsForGeneration(RoadNetwork network) {
        if (network == null) {
            return;
        }
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
    }

    private void applyTunnelJunctionConnectivity(RoadNetworkPreviewSession session) {
        applyTunnelJunctionShellForNodes(session, null);
        applyTunnelJunctionConnectivityForNodes(session, null);
    }

    private void applyTunnelJunctionShellForNodes(
            RoadNetworkPreviewSession session,
            Set<String> onlyNodeIds) {
        if (session == null || !session.isPrepared()) {
            return;
        }
        // 圆角弧形侧墙/顶衬须在连通清障之前写入，避免只剩挖空而无壳。
        // 路口已生成时传入 elevationField，顶衬与圆角铺装共用同一路面基准。
        TunnelJunctionShellPass.apply(
            session.generationNetwork(),
            session.networkResult().getEdgeResults(),
            session.nodeElevations(),
            roadGenerator.getConfig(),
            roadGenerator.coordinateTransformer(),
            roadGenerator::estimateCanvasUnitsPerBlock,
            session.terrain(),
            roadGenerator::getBlockIdFromMaterial,
            roadGenerator.projectionHandler()::getBlockIdAt,
            session.networkResult().getJunctionResults(),
            onlyNodeIds);
    }

    private void applyTunnelJunctionConnectivityForNodes(
            RoadNetworkPreviewSession session,
            Set<String> onlyNodeIds) {
        if (session == null || !session.isPrepared()) {
            return;
        }
        TunnelJunctionConnectivityPass.apply(
            session.generationNetwork(),
            session.networkResult().getEdgeResults(),
            session.nodeElevations(),
            roadGenerator.getConfig(),
            roadGenerator.coordinateTransformer(),
            roadGenerator::estimateCanvasUnitsPerBlock,
            roadGenerator.projectionHandler()::getBlockIdAt,
            session.networkResult().junctionReports,
            session.terrain(),
            onlyNodeIds,
            session.networkResult().getJunctionResults());
    }

    /**
     * 分帧合并后校验：每帧处理若干路口；全部完成后写隧道终态与相邻纵断连续性。
     */
    boolean tickPreviewSessionValidation(RoadNetworkPreviewSession session, long budgetNanos) {
        if (session == null || !session.isValid()) {
            return true;
        }
        RoadNetwork profileNetwork = session.generationNetwork();
        NetworkGenerationResult networkResult = session.networkResult();
        RoadGenerationResult aggregate = session.pendingAggregate();
        if (aggregate == null) {
            aggregate = aggregateNetworkResult(profileNetwork, networkResult);
            session.setPendingAggregate(aggregate);
        }
        if (session.validationColumnIndex() == null) {
            session.setValidationColumnIndex(PlacementRecordColumnIndex.build(aggregate));
            session.setValidationJunctionIndex(0);
        }
        List<RoadNode> junctions = session.junctionNodes();
        Set<String> skipped = networkResult.getSkippedJunctionIds();
        PlacementRecordColumnIndex index = session.validationColumnIndex();
        long started = System.nanoTime();
        int processed = 0;
        while (session.validationJunctionIndex() < junctions.size()) {
            if (processed > 0 && System.nanoTime() - started >= budgetNanos) {
                return false;
            }
            RoadNode node = junctions.get(session.validationJunctionIndex());
            session.setValidationJunctionIndex(session.validationJunctionIndex() + 1);
            processed++;
            if (node == null || node.getDegree() < 3) {
                continue;
            }
            // Explicit skip already marks partial failure; do not double-count as integrity gap.
            if (skipped.contains(node.getId())) {
                continue;
            }
            validateOneJunctionPostMerge(
                node, networkResult, aggregate, index, session.terrain());
        }
        TunnelFinalVoxelValidator.applyReportToResult(
            aggregate, TunnelFinalVoxelValidator.validate(aggregate, session.terrain()));
        networkResult.recordAdjacentJunctionProfileViolations(
            AdjacentJunctionProfileContinuity.find(
                profileNetwork,
                networkResult.getNodeElevations(),
                roadGenerator.getConfig()));
        session.setCompletedPreview(new PreviewResult(
            profileNetwork,
            aggregate,
            networkResult.getEdgeResults(),
            networkResult.getNodeElevations(),
            networkResult,
            session.pendingRoadProfiles()));
        session.setAggregationStep(RoadNetworkPreviewSession.AggregationStep.DONE);
        return true;
    }

    private void applyPostMergeJunctionValidation(
            RoadNetwork profileNetwork,
            NetworkGenerationResult networkResult,
            RoadGenerationResult aggregate,
            TerrainSampler terrain) {
        if (profileNetwork == null || networkResult == null || aggregate == null) {
            return;
        }
        PlacementRecordColumnIndex index = PlacementRecordColumnIndex.build(aggregate);
        Set<String> skipped = networkResult.getSkippedJunctionIds();
        for (RoadNode node : profileNetwork.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            if (skipped.contains(node.getId())) {
                continue;
            }
            validateOneJunctionPostMerge(node, networkResult, aggregate, index, terrain);
        }
    }

    private void validateOneJunctionPostMerge(
            RoadNode node,
            NetworkGenerationResult networkResult,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            TerrainSampler terrain) {
        RoadJunctionGenerator.JunctionBlocks junctionBlocks =
            networkResult.getJunctionResults().get(node.getId());
        if (junctionBlocks == null || junctionBlocks.validationManifest() == null) {
            networkResult.recordMissingJunctionValidation(
                node.getId(),
                "expected junction has no generation/validation result");
            return;
        }
        int strippedOverburden = JunctionProtectedOverburdenValidator.stripUnauthorizedOverburdenAir(
            junctionBlocks, aggregate);
        if (strippedOverburden > 0) {
            LOGGER.info(
                "路口 {} 已剔除封闭洞顶以上非法覆土空气 {} 格",
                node.getId(),
                strippedOverburden);
        }

        JunctionFinalVoxelValidator.PostMergeReport postMerge =
            JunctionFinalVoxelValidator.validateNode(
                junctionBlocks.validationManifest(),
                aggregate,
                index,
                roadGenerator.coordinateTransformer());
        networkResult.mergeJunctionReport(JunctionConstructionReport.empty(node.getId()).withPostMerge(
            postMerge.missingSurfaceColumns(),
            postMerge.surfaceSemanticMismatches(),
            postMerge.protectedLosses(),
            postMerge.disconnectedTunnelComponents(),
            postMerge.crossLayerLeaks(),
            postMerge.surfaceYMismatches(),
            postMerge.undesignedProtrusions()));

        JunctionProtectedOverburdenValidator.Metrics overburden =
            JunctionProtectedOverburdenValidator.evaluate(junctionBlocks, aggregate, terrain);
        if (overburden.protectedOverburdenAirCount() > 0
                || overburden.unexpectedSurfaceElevationCount() > 0
                || overburden.missingAuthorizedLiningCount() > 0) {
            networkResult.recordProtectedOverburdenViolation(
                node.getId(),
                "overburdenAir=" + overburden.protectedOverburdenAirCount()
                    + " elevationMismatch=" + overburden.unexpectedSurfaceElevationCount()
                    + " missingLining=" + overburden.missingAuthorizedLiningCount());
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
