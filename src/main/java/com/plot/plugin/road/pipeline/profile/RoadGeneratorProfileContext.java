package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalAlignmentResolver;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Default {@link ProfileEdgeContext} for {@link com.plot.plugin.road.RoadGenerator}.
 */
public final class RoadGeneratorProfileContext implements ProfileEdgeContext {
  private final RoadSystemConfig config;
  private final CanvasUnitsPerBlockEstimator canvasUnitsPerBlock;
  private final NodeGroundHeightResolver nodeGroundHeight;
  private final ProfileSolveSupport profileSupport;
  private RoadGradeSeparationProfileEvaluator gradeSeparationEvaluator;

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
    Road road = network.getRoadForEdge(edge);
    ResolvedProfileAuthority.Resolution authority = ResolvedProfileAuthority.resolve(network, road);
    String roadId = road != null ? road.getId() : "?";
    // Illegal EDITED claim carried on Resolution — fail closed (do not terrain-solve).
    if (authority.blocksGeneration()) {
      throw new IllegalStateException(
          "edited vertical alignment is not valid for generation on road " + roadId
              + " (" + authority.editedValidity() + ")");
    }
    boolean requiresDesignAuthority =
        authority.kind() == ResolvedProfileAuthority.Kind.EDITED
            || authority.kind() == ResolvedProfileAuthority.Kind.FLAT;
    if (requiresDesignAuthority && !RoadStationing.domainMap(network, road).isReliable()) {
      throw new IllegalStateException(
          "design vertical alignment requires a reliable station domain map for road " + roadId);
    }
    if (VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
      double maxGrade = road.getEffectiveMaxSlope(config);
      if (authority.kind() == ResolvedProfileAuthority.Kind.FLAT) {
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, maxGrade);
      }
      RoadVerticalAlignment alignment = RoadVerticalAlignmentResolver.resolve(network, road, maxGrade);
      RoadVerticalMode solverMode = verticalAlignmentSolverMode(network, road, authority.kind());
      ProfileSolveResult solved = VerticalAlignmentProfileSolver.solveForEdge(
          alignment,
          network,
          road,
          edge,
          segments,
          terrain,
          halfWidth,
          manualStartHeight,
          manualEndHeight,
          profileSupport,
          config,
          solverMode,
          road.getEffectiveTerrainFollowPreset(config));
      if (solved.heightInfos().isEmpty()) {
        throw new IllegalStateException(
            "design vertical alignment solve produced no heights for edge " + edge.getId());
      }
      return solved;
    }
    if (requiresDesignAuthority) {
      throw new IllegalStateException(
          "flat/edited profile authority could not enter design solver for road " + roadId);
    }
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
    // 无具体下穿道路上下文时，退回配置默认隧道净高（与立交解析一致）
    return com.plot.plugin.road.crossing.GradeSeparationClearance.fromUnderpassRoad(null, config);
  }

  @Override
  public RoadSystemConfig roadConfig() {
    return config;
  }

  public void bindGradeSeparationEvaluator(RoadGradeSeparationProfileEvaluator evaluator) {
    this.gradeSeparationEvaluator = evaluator;
  }

  @Override
  public com.plot.plugin.road.RoadGradeSeparationEvaluation evaluateGradeSeparation(
      RoadNode node,
      RoadNetwork network,
      TerrainSampler terrain) {
    if (gradeSeparationEvaluator == null) {
      return null;
    }
    return gradeSeparationEvaluator.evaluate(node, network, terrain);
  }

  public double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
    return canvasUnitsPerBlock.estimate(pathPoints, segments);
  }

  @Override
  public double estimateCanvasUnitsPerBlock(List<PathSegment> segments) {
    return canvasUnitsPerBlock.estimate(null, segments);
  }

  /**
   * VA 编译器仍用 {@link RoadVerticalMode#MANUAL_PROFILE} 表示「按设计线直接求解」；
   * 产品层纵向方式可为 FIT_TERRAIN + EDITED。分流依据为 {@link ResolvedProfileAuthority.Kind}。
   */
  private static RoadVerticalMode verticalAlignmentSolverMode(
      RoadNetwork network,
      Road road,
      ResolvedProfileAuthority.Kind kind) {
    if (kind == ResolvedProfileAuthority.Kind.FLAT) {
      return RoadVerticalMode.FLAT;
    }
    if (kind == ResolvedProfileAuthority.Kind.EDITED) {
      return RoadVerticalMode.MANUAL_PROFILE;
    }
    return road.getVerticalMode();
  }

  @FunctionalInterface
  public interface CanvasUnitsPerBlockEstimator {
    double estimate(List<Vec2d> pathPoints, List<PathSegment> segments);
  }
}
