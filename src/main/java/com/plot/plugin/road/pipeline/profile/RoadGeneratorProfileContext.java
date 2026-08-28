package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Default {@link ProfileEdgeContext} for {@link com.plot.plugin.road.RoadGenerator}.
 */
public final class RoadGeneratorProfileContext implements ProfileEdgeContext {
  private final RoadSystemConfig config;
  private final CanvasUnitsPerBlockEstimator canvasUnitsPerBlock;
  private final NodeGroundHeightResolver nodeGroundHeight;
  private final ProfileSolveSupport profileSupport;

  public RoadGeneratorProfileContext(
      RoadSystemConfig config,
      CanvasUnitsPerBlockEstimator canvasUnitsPerBlock) {
    this.config = config;
    this.canvasUnitsPerBlock = canvasUnitsPerBlock;
    this.nodeGroundHeight = new NodeGroundHeightResolver(config);
    this.profileSupport = ProfileSolveSupport.fromConfig(
        config, segments -> canvasUnitsPerBlock.estimate(null, segments));
  }

  @Override
  public List<PathSegment> samplePath(List<Vec2d> pathPoints) {
    return RoadGeometrySampler.sample(
        pathPoints,
        config.getPathSampleDistance(),
        canvasUnitsPerBlock::estimate);
  }

  @Override
  public ProfileSolveResult solveEdgeProfile(
      List<PathSegment> segments,
      TerrainSampler terrain,
      RoadNetwork network,
      RoadEdge edge,
      RoadNode startNode,
      RoadNode endNode,
      Integer manualStartHeight,
      Integer manualEndHeight) {
    double halfWidth = RoadDimensionUtils.halfExtentFromCenter(
        RoadModelUtils.getEffectiveWidth(network, edge, config));
    return RoadProfileSolver.solveForEdge(
        segments,
        terrain,
        network,
        edge,
        config,
        halfWidth,
        manualStartHeight,
        manualEndHeight,
        profileSupport);
  }

  public ProfileSolveResult solveStandalone(List<PathSegment> segments, TerrainSampler terrain) {
    double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
    return RoadProfileSolver.solveStandalone(segments, terrain, halfWidth, profileSupport);
  }

  public ProfileSolveResult solveWithManualElevation(
      List<PathSegment> segments,
      TerrainSampler terrain,
      int manualRoadElevation) {
    double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
    return RoadProfileSolver.solveWithManualElevation(
        segments, terrain, halfWidth, manualRoadElevation, profileSupport);
  }

  @Override
  public int groundHeightAtNode(TerrainSampler terrain, RoadNode node, RoadNetwork network) {
    return nodeGroundHeight.groundHeightAtNode(terrain, node, network);
  }

  @Override
  public double defaultCrossingClearance() {
    return config.getDefaultCrossingClearance();
  }

  @FunctionalInterface
  public interface CanvasUnitsPerBlockEstimator {
    double estimate(List<Vec2d> pathPoints, List<PathSegment> segments);
  }
}
