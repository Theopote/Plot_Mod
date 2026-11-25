package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.lamp.RoadLampPlacementPass;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionBlocks;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.marking.RoadRailConnectivityValidator;
import com.plot.plugin.road.pipeline.marking.RoadRailPlacementPass;
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
            Map<String, JunctionBlocks> junctionResults,
            RoadGenerationPipelineHost host) {
        return aggregateNetwork(network, edgeResults, junctionResults, host, true);
    }

    /**
     * @param runFinalAcceptance false 时跳过合并内终态验收（预览 MERGE 用，由 VALIDATE 阶段权威执行）。
     */
    public static RoadGenerationResult aggregateNetwork(
            RoadNetwork network,
            Iterable<RoadGenerationResult> edgeResults,
            Map<String, JunctionBlocks> junctionResults,
            RoadGenerationPipelineHost host,
            boolean runFinalAcceptance) {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        long t0 = System.nanoTime();
        if (edgeResults != null) {
            for (RoadGenerationResult edgeResult : edgeResults) {
                mergeResult(aggregate, edgeResult);
            }
        }
        long t1 = System.nanoTime();
        if (network != null && junctionResults != null && host != null) {
            for (Map.Entry<String, JunctionBlocks> entry : junctionResults.entrySet()) {
                mergeJunctionForNode(aggregate, network.getNode(entry.getKey()), entry.getValue(), network, host);
            }
        }
        long t2 = System.nanoTime();
        // 合并前一次规划重叠列设计 Y；接缝只消费，不再内嵌裁决。
        var columnPlan = com.plot.plugin.road.pipeline.corridor.ColumnDesignElevationPlanner.plan(
            junctionResults, network);
        com.plot.plugin.road.pipeline.corridor.JunctionSeamReconciliationPass.apply(
            aggregate, junctionResults, columnPlan);
        long tSeam = System.nanoTime();
        com.plot.plugin.road.pipeline.corridor.RoadSurfacePlacementPass.clearIntrudingFill(aggregate);
        long tClear = System.nanoTime();
        com.plot.plugin.road.pipeline.corridor.UnderpassPavementSealPass.apply(aggregate);
        long tSeal = System.nanoTime();
        RoadRailPlacementPass.apply(aggregate);
        RoadRailConnectivityValidator.validate(aggregate);
        if (!aggregate.roadLampCandidates.isEmpty() && host != null) {
            var junctionColumns = new java.util.HashSet<ColumnKey>();
            if (junctionResults != null) {
                for (var junction : junctionResults.values()) {
                    var context = junction.constructionContext();
                    junctionColumns.addAll(context.footprint().clearance());
                    junctionColumns.addAll(context.protectedCavityColumns());
                    junctionColumns.addAll(context.approachCavityColumns());
                }
            }
            RoadLampPlacementPass.apply(aggregate, junctionColumns, host.projectionHandler()::getSetBlockArgumentAt);
        }
        // 合并阶段只做候选 scrub；这里再全表收一次，保证用途表不变量。
        aggregate.scrubStaleClearancePurposes();
        long tRail = System.nanoTime();
        long acceptanceNanos = 0L;
        if (runFinalAcceptance) {
            // 边级完整性指标在 mergeFrom 后已滞后；用设计期望对照最终方块覆盖严重计数。
            long tAcc0 = System.nanoTime();
            com.plot.plugin.road.pipeline.corridor.FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);
            acceptanceNanos = System.nanoTime() - tAcc0;
        }
        aggregate.assembleMergeEdgesNanos = t1 - t0;
        aggregate.assembleMergeJunctionsNanos = t2 - t1;
        aggregate.assembleSeamNanos = tSeam - t2;
        aggregate.assembleClearIntrudingFillNanos = tClear - tSeam;
        aggregate.assembleUnderpassSealNanos = tSeal - tClear;
        aggregate.assembleRailPassNanos = tRail - tSeal;
        aggregate.assembleAcceptanceNanos = acceptanceNanos;
        return aggregate;
    }

    public static void mergeJunctionForNode(
            RoadGenerationResult target,
            RoadNode node,
            JunctionBlocks junction,
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
            JunctionBlocks junction,
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
            markingBlockId,
            junction.validationManifest() != null
                ? new java.util.HashSet<>(junction.validationManifest().expectedProtectedBlocks()) : java.util.Set.of());
    }

    public static void mergeJunctionBlocks(
            RoadGenerationResult target,
            JunctionBlocks junction,
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

    public static String resolveJunctionMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            boolean sidewalk) {
        if (sidewalk) {
            return com.plot.plugin.road.pipeline.corridor.JunctionMaterialPolicy
                .resolveSidewalkMaterial(node, network, config);
        }
        return com.plot.plugin.road.pipeline.corridor.JunctionMaterialPolicy
            .resolveCarriagewayMaterial(node, network, config);
    }

    public static String resolveJunctionMarkingMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        return com.plot.plugin.road.pipeline.corridor.JunctionMaterialPolicy
            .resolveMarkingMaterial(node, network, config);
    }

    /**
     * @param resolvedElevatedRoadId 生成前已解析的上层道路（含 AUTO 判定结果）；
     *     为 null 时回退到 {@link RoadNode#getElevatedRoadId()}。
     */
    public static boolean isElevatedJunctionEdge(
            RoadNode node,
            RoadEdge edge,
            String resolvedElevatedRoadId) {
        if (node == null || edge == null) {
            return false;
        }
        if (node.isStackGradeSeparated() && node.isStackUpperEdge(edge.getId())) {
            return true;
        }
        String elevatedRoadId = resolvedElevatedRoadId != null && !resolvedElevatedRoadId.isBlank()
            ? resolvedElevatedRoadId
            : node.getElevatedRoadId();
        return elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId());
    }

    public static void applyBuildMetrics(RoadGenerationResult result, RoadEdgeBuildMetrics metrics) {
        result.cutVolume = metrics.cutVolume;
        result.fillVolume = metrics.fillVolume;
        result.bridgeCount = metrics.bridgeCount;
        result.tunnelCount = metrics.tunnelCount;
        result.portalFoundCount = metrics.portalFoundCount;
        result.portalNotFoundCount = metrics.portalNotFoundCount;
    }

    public static void applyConstructionStats(RoadGenerationResult result, ConstructionDetection detection) {
        result.constructionTypes.addAll(detection.constructionTypes());
        for (int i = 0; i < detection.constructionTypes().size(); i++) {
            double distance = detection.segmentDistances().get(i);
            switch (detection.constructionTypes().get(i)) {
                case BRIDGE, BRIDGE_ABUTMENT -> result.bridgeLength += distance;
                case TUNNEL, TUNNEL_PORTAL -> result.tunnelLength += distance;
                case ROAD, CUT, FILL -> result.normalRoadLength += distance;
            }
        }
        result.profileConstructionRuns.clear();
        if (detection.runs() == null) {
            return;
        }
        for (var run : detection.runs()) {
            if (run.type() == com.plot.plugin.road.RoadConstructionType.BRIDGE
                    || run.type() == com.plot.plugin.road.RoadConstructionType.TUNNEL) {
                result.profileConstructionRuns.add(new com.plot.plugin.road.profile.ConstructionRunChartMarker(
                    run.startStation(), run.endStation(), run.type()));
            }
        }
    }
}
