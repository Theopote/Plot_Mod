package com.plot.plugin.road.benchmark;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineHost;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 道路性能基准 harness：不依赖 Minecraft World，直接注入 {@link TerrainSampler}。
 */
public final class RoadBenchmarkHarness {
    private static final RoadSystemConfig CONFIG = benchmarkConfig();

    private RoadBenchmarkHarness() {
    }

    public record PreviewRun(
            RoadGenerationResult aggregate,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults,
            long junctionGenerationNanos) {
    }

    public static RoadBenchmarkResult run(String scaleId, int edgeCount) {
        long createStart = System.nanoTime();
        RoadNetwork network = RoadNetworkBenchmarkFactory.chainNetwork(edgeCount);
        long createMillis = nanosToMillis(System.nanoTime() - createStart);

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMem = runtime.totalMemory() - runtime.freeMemory();

        long snapshotStart = System.nanoTime();
        String json = network.toJson();
        RoadNetwork ignored = RoadNetwork.parseSnapshot(json);
        long snapshotMillis = nanosToMillis(System.nanoTime() - snapshotStart);

        TerrainSampler terrain = new FlatTerrainSampler(64);
        RoadGenerator generator = new RoadGenerator(CONFIG, null, BlockProjectionHandler.getInstance());

        PreviewRun preview = generatePreview(network, generator, terrain);
        long validationStart = System.nanoTime();
        RoadNetworkValidationReport validation = RoadNetworkEngineeringValidator.analyze(
            network,
            preview.edgeResults(),
            CONFIG);
        long validationMillis = nanosToMillis(System.nanoTime() - validationStart);
        validation.blocksBuild(); // force report materialization

        long previewStart = System.nanoTime();
        PreviewRun previewAgain = generatePreview(network, generator, terrain);
        long previewMillis = nanosToMillis(System.nanoTime() - previewStart);

        long afterMem = runtime.totalMemory() - runtime.freeMemory();
        int placementRecords = previewAgain.aggregate().placementRecords.size();

        return new RoadBenchmarkResult(
            scaleId,
            network.getRoads().size(),
            network.getEdges().size(),
            network.getNodes().size(),
            createMillis,
            snapshotMillis,
            json.length(),
            validationMillis,
            previewMillis,
            previewMillis,
            nanosToMillis(previewAgain.junctionGenerationNanos()),
            placementRecords,
            Math.max(0, afterMem - beforeMem));
    }

    public static PreviewRun generatePreview(
            RoadNetwork network,
            RoadGenerator generator,
            TerrainSampler terrain) {
        DerivedCenterlineSynchronizer.synchronizeAll(
            network,
            generator.getConfig().getPathSampleDistance());

        Map<String, Integer> nodeElevations =
            generator.resolveNetworkNodeElevations(network, terrain);

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        long junctionStart = System.nanoTime();
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults = new LinkedHashMap<>();
        Set<String> failedEdgeIds = new java.util.LinkedHashSet<>();

        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            EdgeGenerationResult outcome = generator.generateEdgeOutcome(
                network, edge, start, end, terrain, nodeElevations);
            if (outcome.isSuccess()) {
                edgeResults.put(edge.getId(), outcome.geometry());
            } else if (outcome.isFailed()) {
                failedEdgeIds.add(edge.getId());
            }
        }
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() < 3) {
                continue;
            }
            if (!RoadNetworkGenerator.shouldGenerateJunction(node, failedEdgeIds)) {
                continue;
            }
            RoadJunctionGenerator.JunctionBlocks junctionBlocks =
                junctionGenerator.generateJunction(node, network, terrain, nodeElevations);
            if (!junctionBlocks.isEmpty()) {
                junctionResults.put(node.getId(), junctionBlocks);
            }
        }
        long junctionNanos = System.nanoTime() - junctionStart;

        RoadGenerationPipelineHost host = new RoadGenerationPipelineHost(
            CONFIG,
            null,
            BlockProjectionHandler.getInstance());
        RoadGenerationResult aggregate = RoadGenerationResultAssembler.aggregateNetwork(
            network,
            edgeResults.values(),
            junctionResults,
            host);

        return new PreviewRun(aggregate, edgeResults, junctionResults, junctionNanos);
    }

    public static RoadSystemConfig benchmarkConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road-benchmark");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setMaxSlope(12.0f);
        return config;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
