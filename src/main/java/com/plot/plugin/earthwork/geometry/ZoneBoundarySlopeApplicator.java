package com.plot.plugin.earthwork.geometry;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.grading.SlopeDaylightSolver;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 在 {@link DesignTerrainComposer} 合成后，按分区边界策略修正目标高程。
 */
public final class ZoneBoundarySlopeApplicator {

  private ZoneBoundarySlopeApplicator() {
  }

  public static void apply(
      DesignTerrainGrid grid,
      List<GradingZone> zones,
      Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
    if (grid == null || zones == null || zones.isEmpty()) {
      return;
    }
    List<ZoneContext> contexts = buildContexts(zones, zoneEvaluators);
    if (contexts.isEmpty()) {
      return;
    }
    List<ZonePolygon> zonePolygons = contexts.stream()
        .map(context -> new ZonePolygon(context.zoneId(), context.priority(), context.polygon()))
        .toList();

    for (DesignTerrainCell cell : grid.cells().values()) {
      if (!cell.participatesInEarthwork()) {
        continue;
      }
      applyInteriorMatchExisting(cell, contexts, zonePolygons);
      applyExteriorSlopes(grid, cell, contexts, zonePolygons);
    }
    grid.finalizeStats();
  }

  private static void applyInteriorMatchExisting(
      DesignTerrainCell cell,
      List<ZoneContext> contexts,
      List<ZonePolygon> zonePolygons) {
    for (ZoneContext context : contexts) {
      if (!context.polygon().contains(cell.center())) {
        continue;
      }
      BoundaryProximity proximity = nearestBoundary(context.outerPoints(), cell.center());
      if (proximity.distance() > context.settings().getMaximumReachBlocks()) {
        continue;
      }
      EdgeTreatment treatment = context.settings().resolveTreatment(proximity.edgeIndex());
      if (treatment != EdgeTreatment.MATCH_EXISTING) {
        continue;
      }
      cell.setTargetY(cell.existingGroundY());
      cell.setZoneId(context.zoneId());
      return;
    }
  }

  private static void applyExteriorSlopes(
      DesignTerrainGrid grid,
      DesignTerrainCell cell,
      List<ZoneContext> contexts,
      List<ZonePolygon> zonePolygons) {
    Integer bestTarget = null;
    String bestZoneId = null;
    int bestPriority = Integer.MIN_VALUE;

    for (ZoneContext context : contexts) {
      if (context.polygon().contains(cell.center())) {
        continue;
      }
      if (isInsideAnotherZone(cell.center(), context.zoneId(), zonePolygons)) {
        continue;
      }
      BoundaryProximity proximity = nearestBoundary(context.outerPoints(), cell.center());
      if (proximity.distance() > context.settings().getMaximumReachBlocks()) {
        continue;
      }
      EdgeTreatment treatment = context.settings().resolveTreatment(proximity.edgeIndex());
      if (treatment != EdgeTreatment.CUT_FILL_SLOPE) {
        continue;
      }
      DesignSurfaceResolver.ZoneTargetEvaluator evaluator = context.evaluator();
      if (evaluator == null) {
        continue;
      }
      int toeY = evaluator.evaluateAt(cell);
      int slopeTarget = computeExteriorSlopeTarget(
          grid,
          cell,
          toeY,
          proximity,
          context.settings());
      if (slopeTarget == cell.targetY()) {
        continue;
      }
      if (context.priority() >= bestPriority) {
        bestTarget = slopeTarget;
        bestZoneId = context.zoneId();
        bestPriority = context.priority();
      }
    }

    if (bestTarget != null) {
      cell.setTargetY(bestTarget);
      cell.setZoneId(bestZoneId);
    }
  }

  static int computeExteriorSlopeTarget(
      DesignTerrainGrid grid,
      DesignTerrainCell cell,
      int toeY,
      BoundaryProximity proximity,
      ZoneEdgeSettings settings) {
    if (proximity.distance() <= 0.0) {
      return toeY;
    }
    SlopeDaylightSolver.SlopeMode mode = SlopeDaylightSolver.modeFor(
        cell.existingGroundY(), toeY);
    double pitchRatio = mode == SlopeDaylightSolver.SlopeMode.CUT
        ? settings.getCutSlopePitchRatio()
        : settings.getFillSlopePitchRatio();
    return SlopeDaylightSolver.resolveExteriorTargetY(
        toeY,
        cell.existingGroundY(),
        proximity.distance(),
        mode,
        pitchRatio,
        settings.getBenchWidthBlocks(),
        buildGroundProfile(grid, cell, proximity.closestPoint()),
        settings.getMaximumReachBlocks());
  }

  /** 无格网上下文时的退化入口（沿射线假设现状高程不变）。 */
  static int computeExteriorSlopeTarget(
      int existingGroundY,
      int toeY,
      double distanceToBoundary,
      ZoneEdgeSettings settings) {
    if (distanceToBoundary <= 0.0) {
      return toeY;
    }
    SlopeDaylightSolver.SlopeMode mode = SlopeDaylightSolver.modeFor(existingGroundY, toeY);
    double pitchRatio = mode == SlopeDaylightSolver.SlopeMode.CUT
        ? settings.getCutSlopePitchRatio()
        : settings.getFillSlopePitchRatio();
    return SlopeDaylightSolver.resolveExteriorTargetY(
        toeY,
        existingGroundY,
        distanceToBoundary,
        mode,
        pitchRatio,
        settings.getBenchWidthBlocks(),
        offset -> existingGroundY,
        settings.getMaximumReachBlocks());
  }

  private static java.util.function.IntUnaryOperator buildGroundProfile(
      DesignTerrainGrid grid,
      DesignTerrainCell cell,
      Vec2d closestBoundaryPoint) {
    if (grid == null || cell == null || closestBoundaryPoint == null) {
      return offset -> cell != null ? cell.existingGroundY() : 64;
    }
    Vec2d delta = cell.center().subtract(closestBoundaryPoint);
    double length = delta.length();
    if (length < 1e-9) {
      return offset -> cell.existingGroundY();
    }
    Vec2d unit = delta.multiply(1.0 / length);
    int fallback = cell.existingGroundY();
    return offset -> sampleGroundAt(grid, closestBoundaryPoint, unit, offset, fallback);
  }

  private static int sampleGroundAt(
      DesignTerrainGrid grid,
      Vec2d origin,
      Vec2d unitDir,
      int offset,
      int fallback) {
    Vec2d point = origin.add(unitDir.multiply(offset));
    int worldX = (int) Math.floor(point.x);
    int worldZ = (int) Math.floor(point.y);
    DesignTerrainCell sample = grid.get(worldX, worldZ);
    return sample != null ? sample.existingGroundY() : fallback;
  }

  private static boolean isInsideAnotherZone(Vec2d point, String ownerZoneId, List<ZonePolygon> zones) {
    for (ZonePolygon zone : zones) {
      if (zone.zoneId().equals(ownerZoneId)) {
        continue;
      }
      if (zone.polygon().contains(point)) {
        return true;
      }
    }
    return false;
  }

  private static List<ZoneContext> buildContexts(
      List<GradingZone> zones,
      Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
    List<ZoneContext> contexts = new ArrayList<>();
    for (GradingZone zone : zones) {
      if (zone == null || !zone.isEnabled() || !zone.isSupportedInComposer()) {
        continue;
      }
      ZoneEdgeSettings settings = zone.getEdgeSettings();
      if (!settings.hasActiveTreatment()) {
        continue;
      }
      List<Vec2d> outerPoints = zone.getOuterPoints();
      if (outerPoints.size() < 3) {
        continue;
      }
      DesignSurfaceResolver.ZoneTargetEvaluator evaluator = zoneEvaluators != null
          ? zoneEvaluators.get(zone.getId())
          : null;
      if (evaluator == null) {
        continue;
      }
      contexts.add(new ZoneContext(
          zone.getId(),
          zone.getPriority(),
          outerPoints,
          EarthworkGeometryUtils.toPolygon(outerPoints),
          settings,
          evaluator));
    }
    contexts.sort(Comparator.comparingInt(ZoneContext::priority).reversed());
    return contexts;
  }

  static BoundaryProximity nearestBoundary(List<Vec2d> polygon, Vec2d point) {
    if (polygon == null || polygon.size() < 2 || point == null) {
      return new BoundaryProximity(0.0, 0, point);
    }
    double minDistance = Double.MAX_VALUE;
    int nearestEdge = 0;
    Vec2d nearestPoint = point;
    int count = polygon.size();
    for (int edgeIndex = 0; edgeIndex < count; edgeIndex++) {
      Vec2d start = polygon.get(edgeIndex);
      Vec2d end = polygon.get((edgeIndex + 1) % count);
      if (start == null || end == null) {
        continue;
      }
      Vec2d projected = GeometryUtils.projectPointOnLine(point, start, end);
      projected = clampToSegment(projected, start, end);
      double distance = point.distance(projected);
      if (distance < minDistance) {
        minDistance = distance;
        nearestEdge = edgeIndex;
        nearestPoint = projected;
      }
    }
    return new BoundaryProximity(minDistance, nearestEdge, nearestPoint);
  }

  private static Vec2d clampToSegment(Vec2d point, Vec2d start, Vec2d end) {
    if (start.distance(end) <= 1e-9) {
      return start;
    }
    Vec2d segment = end.subtract(start);
    double t = point.subtract(start).dot(segment) / segment.dot(segment);
    t = Math.max(0.0, Math.min(1.0, t));
    return start.add(segment.multiply(t));
  }

  /**
   * 单分区 legacy 生成路径：在平面设计高程上应用边界策略。
   */
  public static int resolveLegacyTargetY(
      Vec2d canvasCenter,
      int existingGroundY,
      int designTargetY,
      List<Vec2d> outerPoints,
      ZoneEdgeSettings settings) {
    if (settings == null || !settings.hasActiveTreatment() || outerPoints == null || outerPoints.size() < 3) {
      return designTargetY;
    }
    boolean inside = EarthworkGeometryUtils.containsCanvasPoint(outerPoints, canvasCenter);
    BoundaryProximity proximity = nearestBoundary(outerPoints, canvasCenter);
    EdgeTreatment treatment = settings.resolveTreatment(proximity.edgeIndex());
    if (inside) {
      if (treatment == EdgeTreatment.MATCH_EXISTING
          && proximity.distance() <= settings.getMaximumReachBlocks()) {
        return existingGroundY;
      }
      return designTargetY;
    }
    if (treatment != EdgeTreatment.CUT_FILL_SLOPE
        || proximity.distance() > settings.getMaximumReachBlocks()) {
      return existingGroundY;
    }
    return computeExteriorSlopeTarget(
        existingGroundY,
        designTargetY,
        proximity.distance(),
        settings);
  }

  record BoundaryProximity(double distance, int edgeIndex, Vec2d closestPoint) {
  }

  private record ZonePolygon(String zoneId, int priority, Polygon polygon) {
  }

  private record ZoneContext(
      String zoneId,
      int priority,
      List<Vec2d> outerPoints,
      Polygon polygon,
      ZoneEdgeSettings settings,
      DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
  }
}
