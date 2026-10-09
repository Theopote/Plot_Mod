package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Mutable state shared across pipeline stages for one edge build.
 */
public final class RoadGenerationPipelineContext {
    private final RoadGenerationBuildRequest request;
    private List<PathSegment> segments;
    private ConstructionDetection detection;
    private double unitsPerBlock;
    private RoadSolidModel solids;
    private RoadEdgeBuildMetrics metrics;
    private RoadGenerationResult result;

    public RoadGenerationPipelineContext(RoadGenerationBuildRequest request) {
        this.request = request;
    }

    public RoadGenerationBuildRequest request() {
        return request;
    }

    public List<Vec2d> pathPoints() {
        return request.pathPoints();
    }

    public TerrainSampler terrain() {
        return request.terrain();
    }

    public List<SegmentHeightInfo> heightInfos() {
        return request.heightInfos();
    }

    public double pathLength() {
        return request.pathLength();
    }

    public BuildHeightProfile buildProfile() {
        return request.buildProfile();
    }

    public EndpointElevationSnaps endpointSnaps() {
        return request.endpointSnaps();
    }

    public String carriagewaySeedKey() {
        return request.carriagewaySeedKey();
    }

    public List<PathSegment> segments() {
        return segments;
    }

    public void setSegments(List<PathSegment> segments) {
        this.segments = segments;
    }

    public ConstructionDetection detection() {
        return detection;
    }

    public void setDetection(ConstructionDetection detection) {
        this.detection = detection;
    }

    public double unitsPerBlock() {
        return unitsPerBlock;
    }

    public void setUnitsPerBlock(double unitsPerBlock) {
        this.unitsPerBlock = unitsPerBlock;
    }

    public RoadSolidModel solids() {
        return solids;
    }

    public RoadEdgeBuildMetrics metrics() {
        return metrics;
    }

    public void initBuildState() {
        this.solids = new RoadSolidModel();
        this.metrics = new RoadEdgeBuildMetrics();
        this.result = new RoadGenerationResult(pathLength());
    }

    public RoadGenerationResult result() {
        return result;
    }

    public RoadGenerationResult createResult() {
        if (result == null) {
            result = new RoadGenerationResult(pathLength());
        }
        return result;
    }

    public interface Host {
        RoadSystemConfig config();

        double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments);

        BlockPos canvasToBlockPos(Vec2d canvasPos);

        default ICoordinateService coordinateTransformer() {
            return null;
        }

        void setEndpointSnaps(EndpointElevationSnaps endpointSnaps);

        void clearEndpointSnaps();

        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);

        int bridgeThreshold();

        void flushEdgeSolids(RoadGenerationResult result, RoadSolidModel solids);
    }
}
