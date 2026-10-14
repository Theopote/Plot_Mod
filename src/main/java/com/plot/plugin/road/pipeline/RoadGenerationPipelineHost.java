package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSession;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.corridor.RoadCorridorPlan;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Default {@link RoadGenerationPipelineContext.Host} backed by coordinate transform and projection services.
 */
public final class RoadGenerationPipelineHost implements RoadGenerationPipelineContext.Host {
    private final RoadSystemConfig config;
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final EndpointElevationSession endpointElevationSession = new EndpointElevationSession();
    private int maxDedupKeysOverride = -1;
    private RoadCorridorPlan lastCorridorPlanForTesting;
    private BiFunction<RoadNode, RoadNetwork, String> elevatedRoadIdResolver = (node, network) ->
        node != null ? node.getElevatedRoadId() : null;

    public RoadGenerationPipelineHost(
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
        this.projectionHandler = Objects.requireNonNull(projectionHandler, "projectionHandler");
    }

    public RoadSystemConfig config() {
        return config;
    }

    public void setElevatedRoadIdResolver(BiFunction<RoadNode, RoadNetwork, String> elevatedRoadIdResolver) {
        this.elevatedRoadIdResolver = elevatedRoadIdResolver != null
            ? elevatedRoadIdResolver
            : (node, network) -> node != null ? node.getElevatedRoadId() : null;
    }

    public String resolveElevatedRoadId(RoadNode node, RoadNetwork network) {
        return elevatedRoadIdResolver.apply(node, network);
    }

    /** 测试专用：强制更小去重上限以验证分块 flush 结果一致性。 */
    public void setMaxDedupKeysForTesting(int maxDedupKeys) {
        this.maxDedupKeysOverride = maxDedupKeys;
    }

    /** 测试专用：读取最近一次 corridor plan。 */
    public RoadCorridorPlan lastCorridorPlanForTesting() {
        return lastCorridorPlanForTesting;
    }

    void captureCorridorPlanForTesting(RoadCorridorPlan plan) {
        this.lastCorridorPlanForTesting = plan;
    }

    @Override
    public int maxDedupKeys() {
        return maxDedupKeysOverride > 0 ? maxDedupKeysOverride : RoadSolidModel.defaultMaxDedupKeys();
    }

    @Override
    public ICoordinateService coordinateTransformer() {
        return coordinateTransformer;
    }

    public IBlockProjectionService projectionHandler() {
        return projectionHandler;
    }

    @Override
    public void setEndpointSnaps(EndpointElevationSnaps endpointSnaps) {
        endpointElevationSession.setSnaps(endpointSnaps);
    }

    @Override
    public void clearEndpointSnaps() {
        endpointElevationSession.clear();
    }

    @Override
    public int snapEndpointElevation(Vec2d center, int targetY) {
        return endpointElevationSession.snap(center, targetY);
    }

    @Override
    public BlockPos canvasToBlockPos(Vec2d canvasPos) {
        return RoadGeometryUtils.canvasToBlockXZ(canvasPos, coordinateTransformer);
    }

    public BlockPos toBlockPos(Vec2d canvasPos, int y) {
        return RoadVoxelRasterizer.toBlockPos(canvasPos, y, coordinateTransformer);
    }

    @Override
    public String resolveBlockId(String material) {
        return RoadMaterialUtils.resolveBlockId(material);
    }

    @Override
    public int bridgeThreshold() {
        return RoadConstructionHeuristics.bridgeThreshold(config);
    }

    @Override
    public void flushEdgeSolids(RoadGenerationResult result, RoadSolidModel solids) {
        RoadVoxelRasterizer.flushEdgeSolids(result, solids, coordinateTransformer, projectionHandler);
    }

    @Override
    public double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
        Vec2d origin;
        Vec2d tangent;
        if (pathPoints != null && pathPoints.size() >= 2) {
            origin = pathPoints.getFirst();
            tangent = pathPoints.get(1).subtract(pathPoints.getFirst());
        } else if (segments != null && !segments.isEmpty()) {
            PathSegment first = segments.getFirst();
            origin = first.start;
            tangent = first.end.subtract(first.start);
        } else {
            return 1.0;
        }
        if (tangent.lengthSquared() < 1e-12) {
            tangent = new Vec2d(1, 0);
        }
        Vec2d unit = tangent.normalize();
        Vec2d normal = new Vec2d(-unit.y, unit.x);
        return WorldProjectionMath.canvasUnitsPerWorldBlock(coordinateTransformer, origin, normal);
    }
}
