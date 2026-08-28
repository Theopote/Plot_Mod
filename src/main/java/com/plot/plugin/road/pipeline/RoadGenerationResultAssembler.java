package com.plot.plugin.road.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;

import java.util.Map;

/**
 * Merges edge/junction build output and applies pipeline metrics onto {@link RoadGenerationResult}.
 */
public final class RoadGenerationResultAssembler {
    private RoadGenerationResultAssembler() {
    }

    public static RoadGenerationResult aggregateNetwork(
            RoadNetwork network,
            Iterable<RoadGenerationResult> edgeResults,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults,
            RoadGenerationPipelineHost host) {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        if (edgeResults != null) {
            for (RoadGenerationResult edgeResult : edgeResults) {
                mergeResult(aggregate, edgeResult);
            }
        }
        if (network == null || junctionResults == null || host == null) {
            return aggregate;
        }
        for (Map.Entry<String, RoadJunctionGenerator.JunctionBlocks> entry : junctionResults.entrySet()) {
            mergeJunctionForNode(aggregate, network.getNode(entry.getKey()), entry.getValue(), network, host);
        }
        return aggregate;
    }

    public static void mergeJunctionForNode(
            RoadGenerationResult target,
            RoadNode node,
            RoadJunctionGenerator.JunctionBlocks junction,
            RoadNetwork network,
            RoadGenerationPipelineHost host) {
        if (target == null || junction == null || host == null) {
            return;
        }
        RoadSystemConfig config = host.config();
        mergeJunction(
            target,
            junction,
            host.coordinateTransformer(),
            host.projectionHandler(),
            host.resolveBlockId(resolveJunctionMaterial(node, network, config, false)),
            host.resolveBlockId(resolveJunctionMaterial(node, network, config, true)),
            host.resolveBlockId(resolveJunctionMarkingMaterial(node, network, config)));
    }

    public static void mergeResult(RoadGenerationResult target, RoadGenerationResult source) {
        if (target != null) {
            target.mergeFrom(source);
        }
    }

    public static void mergeJunction(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        if (target == null || junction == null) {
            return;
        }
        RoadVoxelRasterizer.flushJunctionSolids(
            target,
            junction.getSolids(),
            coordinateTransformer,
            projectionHandler,
            roadBlockId,
            sidewalkBlockId,
            markingBlockId);
    }

    public static void mergeJunctionBlocks(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            RoadGenerationPipelineHost host,
            String roadMaterial,
            String sidewalkMaterial) {
        if (host == null) {
            return;
        }
        mergeJunction(
            target,
            junction,
            host.coordinateTransformer(),
            host.projectionHandler(),
            host.resolveBlockId(roadMaterial),
            host.resolveBlockId(sidewalkMaterial),
            null);
    }

    /**
     * @deprecated 使用 {@link #mergeJunctionBlocks(RoadGenerationResult, RoadJunctionGenerator.JunctionBlocks, RoadGenerationPipelineHost, String, String)}
     */
    @Deprecated(since = "1.x", forRemoval = true)
    public static void mergeJunctionBlocksWithConfigDefaults(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            RoadGenerationPipelineHost host) {
        if (host == null) {
            return;
        }
        String fallback = host.config().getSelectedMaterial().getPrimaryMaterial();
        mergeJunctionBlocks(target, junction, host, fallback, fallback);
    }

    public static String resolveJunctionMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            boolean sidewalk) {
        String fallback = sidewalk
            ? config.getSelectedSidewalkMaterial()
            : config.getSelectedMaterial().getPrimaryMaterial();
        if (node == null || network == null) {
            return fallback;
        }

        String selectedMaterial = null;
        int widestRoad = -1;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (sidewalk && !RoadModelUtils.getEffectiveIncludeSidewalk(network, edge, config)) {
                continue;
            }
            int width = RoadModelUtils.getEffectiveWidth(network, edge, config);
            if (width >= widestRoad) {
                widestRoad = width;
                selectedMaterial = sidewalk
                    ? RoadModelUtils.getEffectiveSidewalkMaterial(network, edge, config)
                    : RoadModelUtils.getEffectiveMaterial(network, edge, config);
            }
        }
        return selectedMaterial != null ? selectedMaterial : fallback;
    }

    public static String resolveJunctionMarkingMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (node == null || network == null) {
            return ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        }
        String selectedMaterial = null;
        int widestRoad = -1;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            int width = RoadModelUtils.getEffectiveWidth(network, edge, config);
            if (width >= widestRoad) {
                widestRoad = width;
                selectedMaterial = RoadModelUtils.resolveCrossSection(network, edge, config).markingMaterial;
            }
        }
        return selectedMaterial != null ? selectedMaterial : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    }

    public static void applyBuildMetrics(RoadGenerationResult result, RoadEdgeBuildMetrics metrics) {
        result.cutVolume = metrics.cutVolume;
        result.fillVolume = metrics.fillVolume;
        result.bridgeCount = metrics.bridgeCount;
        result.tunnelCount = metrics.tunnelCount;
    }

    public static void applyConstructionStats(RoadGenerationResult result, ConstructionDetection detection) {
        result.constructionTypes.addAll(detection.constructionTypes());
        for (int i = 0; i < detection.constructionTypes().size(); i++) {
            double distance = detection.segmentDistances().get(i);
            switch (detection.constructionTypes().get(i)) {
                case BRIDGE -> result.bridgeLength += distance;
                case TUNNEL -> result.tunnelLength += distance;
                case ROAD, CUT, FILL -> result.normalRoadLength += distance;
            }
        }
    }
}
