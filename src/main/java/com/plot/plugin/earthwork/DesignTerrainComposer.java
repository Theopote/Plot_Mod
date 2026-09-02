package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将多分区设计面合成为 Site 级 {@link DesignTerrainGrid}。
 */
public final class DesignTerrainComposer {

    private DesignTerrainComposer() {
    }

    public record ComposeResult(
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
    }

    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        return compose(site, terrain, transformer, BuildingFootprintLookup.NONE);
    }

    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup) {
        return compose(site, terrain, transformer, buildingLookup, RoadSurfaceLookup.NONE);
    }

    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        if (site == null || terrain == null || terrain.isEmpty()) {
            return new ComposeResult(new DesignTerrainGrid(), Map.of());
        }

        DesignTerrainGrid grid = new DesignTerrainGrid();
        initializeCells(grid, terrain);

        applyExclusionZones(grid, site.getExclusionZones());
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators =
            DesignSurfaceResolver.resolveZoneEvaluators(site, terrain, buildingLookup, roadLookup, transformer);
        List<Breakline> effectiveBreaklines = mergeEffectiveBreaklines(site);
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey =
            applyZoneCoverage(grid, site, zoneEvaluators, effectiveBreaklines);
        TerrainBoundaryBlender.apply(grid, site, coverageByCellKey, effectiveBreaklines);
        ZoneBoundarySlopeApplicator.apply(
            grid,
            new ArrayList<>(site.getGradingZones().values()),
            zoneEvaluators);
        grid.finalizeStats();
        return new ComposeResult(grid, zoneEvaluators);
    }

    private static List<Breakline> mergeEffectiveBreaklines(EarthworkSite site) {
        List<Breakline> breaklines = new ArrayList<>(site.getBreaklines());
        breaklines.addAll(RetainingEdgeBreaklineAdapter.toNoBlendBreaklines(site.getRetainingEdges()));
        breaklines.addAll(ZoneBoundaryRetainingEdgeAdapter.toNoBlendBreaklines(site.getGradingZones().values()));
        return breaklines;
    }

    private static void initializeCells(DesignTerrainGrid grid, TerrainSnapshot terrain) {
        for (TerrainSnapshot.Column column : terrain.columns()) {
            DesignTerrainCell cell = new DesignTerrainCell(
                column.worldX(),
                column.worldZ(),
                column.center(),
                column.groundY());
            grid.put(column.worldX(), column.worldZ(), cell);
        }
    }

    private static void applyExclusionZones(DesignTerrainGrid grid, List<ExclusionZone> exclusionZones) {
        if (exclusionZones == null || exclusionZones.isEmpty()) {
            return;
        }
        for (ExclusionZone exclusion : exclusionZones) {
            if (exclusion == null) {
                continue;
            }
            List<Vec2d> polygon = exclusion.getOuterPoints();
            if (polygon.size() < 3) {
                continue;
            }
            boolean preserveExisting = ExclusionZone.MODE_PRESERVE_EXISTING.equals(exclusion.getMode());
            boolean noTouch = ExclusionZone.MODE_NO_TOUCH.equals(exclusion.getMode());
            for (DesignTerrainCell cell : grid.cells().values()) {
                if (cell.excluded()) {
                    continue;
                }
                if (!EarthworkGeometryUtils.containsCanvasPoint(polygon, cell.center())) {
                    continue;
                }
                cell.setExcluded(true);
                cell.setZoneId(exclusion.getId());
                if (preserveExisting) {
                    cell.setTargetY(cell.existingGroundY());
                }
                if (noTouch) {
                    cell.setNoTouch(true);
                }
            }
        }
    }

    private static Map<Long, TerrainBoundaryBlender.ZoneCoverage> applyZoneCoverage(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            List<Breakline> breaklines) {
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey = new HashMap<>();
        List<ZoneCandidate> candidates = buildZoneCandidates(site);
        CompositionPolicy policy = site.getCompositionPolicy();
        boolean highestPriorityWins = CompositionPolicy.OVERLAP_HIGHEST_PRIORITY_WINS
            .equals(policy.getOverlapResolution());
        boolean applyBreaklinePrecedence = !breaklines.isEmpty()
            && CompositionPolicy.PRECEDENCE_ABSOLUTE.equals(policy.getBreaklinePrecedence());
        double breaklineInfluence = resolveBreaklineInfluenceDistance(policy);

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell.excluded()) {
                continue;
            }
            List<ZoneCandidate> covering = collectCoveringCandidates(candidates, cell);
            if (covering.isEmpty()) {
                continue;
            }
            if (applyBreaklinePrecedence) {
                covering = filterByBreaklineSide(cell.center(), covering, breaklines, breaklineInfluence);
                if (covering.isEmpty()) {
                    continue;
                }
            }
            ZoneCandidate winner = highestPriorityWins
                ? selectWinnerByPriority(covering)
                : covering.getFirst();
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator = zoneEvaluators.get(winner.zoneId());
            if (evaluator == null) {
                continue;
            }
            int winnerTarget = evaluator.evaluateAt(cell);
            Integer runnerUpTarget = resolveRunnerUpTarget(covering, winner, zoneEvaluators, cell);
            cell.setTargetY(winnerTarget);
            cell.setZoneId(winner.zoneId());
            coverageByCellKey.put(
                DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()),
                new TerrainBoundaryBlender.ZoneCoverage(winnerTarget, runnerUpTarget));
        }
        return coverageByCellKey;
    }

    private static List<ZoneCandidate> collectCoveringCandidates(
            List<ZoneCandidate> candidates,
            DesignTerrainCell cell) {
        List<ZoneCandidate> covering = new ArrayList<>();
        for (ZoneCandidate candidate : candidates) {
            if (candidate.polygon.contains(cell.center())) {
                covering.add(candidate);
            }
        }
        return covering;
    }

    private static List<ZoneCandidate> filterByBreaklineSide(
            Vec2d point,
            List<ZoneCandidate> covering,
            List<Breakline> breaklines,
            double influenceDistance) {
        String mandatedZoneId = BreaklineClassifier.resolveMandatedZoneId(point, breaklines, influenceDistance);
        if (mandatedZoneId == null || mandatedZoneId.isBlank()) {
            return covering;
        }
        List<ZoneCandidate> filtered = new ArrayList<>();
        for (ZoneCandidate candidate : covering) {
            if (mandatedZoneId.equals(candidate.zoneId())) {
                filtered.add(candidate);
            }
        }
        return filtered.isEmpty() ? covering : filtered;
    }

    private static Integer resolveRunnerUpTarget(
            List<ZoneCandidate> covering,
            ZoneCandidate winner,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            DesignTerrainCell cell) {
        if (covering.size() < 2) {
            return null;
        }
        List<ZoneCandidate> ranked = new ArrayList<>(covering);
        ranked.sort(Comparator
            .comparingInt(ZoneCandidate::priority).reversed()
            .thenComparingDouble(ZoneCandidate::area)
            .thenComparing(ZoneCandidate::zoneId, String.CASE_INSENSITIVE_ORDER));
        for (ZoneCandidate candidate : ranked) {
            if (candidate.zoneId().equals(winner.zoneId())) {
                continue;
            }
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator = zoneEvaluators.get(candidate.zoneId());
            if (evaluator != null) {
                return evaluator.evaluateAt(cell);
            }
        }
        return null;
    }

    private static double resolveBreaklineInfluenceDistance(CompositionPolicy policy) {
        int blendWidth = policy != null ? policy.getBlendWidthBlocks() : 0;
        return Math.max(1.0, blendWidth);
    }

    private static ZoneCandidate selectWinnerByPriority(List<ZoneCandidate> covering) {
        covering.sort(Comparator
            .comparingInt(ZoneCandidate::priority).reversed()
            .thenComparingDouble(ZoneCandidate::area)
            .thenComparing(ZoneCandidate::zoneId, String.CASE_INSENSITIVE_ORDER));
        return covering.getFirst();
    }

    private static List<ZoneCandidate> buildZoneCandidates(EarthworkSite site) {
        List<ZoneCandidate> candidates = new ArrayList<>();
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isSupportedInComposer()) {
                continue;
            }
            List<Vec2d> points = zone.getOuterPoints();
            if (points.size() < 3) {
                continue;
            }
            candidates.add(new ZoneCandidate(
                zone.getId(),
                zone.getPriority(),
                Math.abs(zone.computeArea()),
                EarthworkGeometryUtils.toPolygon(points)));
        }
        return candidates;
    }

    private record ZoneCandidate(String zoneId, int priority, double area, Polygon polygon) {
    }
}
