package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.geometry.RetainingEdgeBreaklineAdapter;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.grading.BreaklineClassifier;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.solver.SiteWideBalanceAdjuster;
import com.plot.plugin.earthwork.terrain.TerrainBoundaryBlender;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将多分区设计面合成为 Site 级 {@link DesignTerrainGrid}。
 */
public final class DesignTerrainComposer {

    /** 平衡改变平台标高后，坡脚/日照线会移动；迭代重建直到偏移收敛或达到上限。 */
    static final int MAX_SITE_BALANCE_ITERATIONS = 4;

    private DesignTerrainComposer() {
    }

    public record ComposeResult(
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<String, ResolvedDesignSurface> resolvedSurfaces) {
        public ComposeResult {
            zoneEvaluators = zoneEvaluators != null ? Map.copyOf(zoneEvaluators) : Map.of();
            resolvedSurfaces = resolvedSurfaces != null ? Map.copyOf(resolvedSurfaces) : Map.of();
        }

        public ComposeResult(DesignTerrainGrid grid, Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
            this(grid, zoneEvaluators, Map.of());
        }
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
            return new ComposeResult(new DesignTerrainGrid(), Map.of(), Map.of());
        }

        DesignTerrainGrid grid = new DesignTerrainGrid();
        initializeCells(grid, terrain);

        applyExclusionZones(grid, site.getExclusionZones());
        Map<String, ResolvedDesignSurface> resolvedSurfaces =
            DesignSurfaceResolver.resolveZoneSurfaces(site, terrain, buildingLookup, roadLookup, transformer);
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators =
            ResolvedDesignSurface.toEvaluatorMap(resolvedSurfaces);
        List<Breakline> effectiveBreaklines = mergeEffectiveBreaklines(site);
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey =
            applyZoneCoverage(grid, site, zoneEvaluators, effectiveBreaklines);
        Map<Long, CellSnapshot> baseDesign = snapshotCells(grid);

        applyBoundaryConditions(
            grid,
            site,
            zoneEvaluators,
            coverageByCellKey,
            effectiveBreaklines);

        Map<String, Integer> cumulativeZoneOffsets = new LinkedHashMap<>();
        int cumulativeUniformOffset = iterateSiteBalance(
            grid,
            site,
            baseDesign,
            zoneEvaluators,
            resolvedSurfaces,
            coverageByCellKey,
            effectiveBreaklines,
            cumulativeZoneOffsets);
        recordBalanceOffsets(site, cumulativeZoneOffsets, cumulativeUniformOffset);

        Map<String, ResolvedDesignSurface> finalResolved =
            surfacesWithOffsets(site, resolvedSurfaces, cumulativeZoneOffsets, cumulativeUniformOffset);
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> finalEvaluators =
            ResolvedDesignSurface.toEvaluatorMap(finalResolved);
        grid.finalizeStats();
        return new ComposeResult(grid, finalEvaluators, finalResolved);
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
            if (exclusion.getGeometry().isEmpty()) {
                continue;
            }
            boolean preserveExisting = ExclusionZone.MODE_PRESERVE_EXISTING.equals(exclusion.getMode());
            boolean noTouch = ExclusionZone.MODE_NO_TOUCH.equals(exclusion.getMode());
            for (DesignTerrainCell cell : grid.cells().values()) {
                if (cell.excluded()) {
                    continue;
                }
                if (!exclusion.containsCanvasPoint(cell.center())) {
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
            ZoneCandidate winner = selectOverlapWinner(covering, policy);
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator = zoneEvaluators.get(winner.zoneId());
            if (evaluator == null) {
                continue;
            }
            int winnerTarget = evaluator.evaluateAt(cell);
            Integer runnerUpTarget = resolveRunnerUpTarget(covering, winner, zoneEvaluators, cell);
            String runnerUpZoneId = resolveRunnerUpZoneId(covering, winner);
            cell.setTargetY(winnerTarget);
            cell.setZoneId(winner.zoneId());
            coverageByCellKey.put(
                DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()),
                new TerrainBoundaryBlender.ZoneCoverage(winnerTarget, runnerUpTarget, runnerUpZoneId));
        }
        return coverageByCellKey;
    }

    private static int iterateSiteBalance(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<Long, CellSnapshot> baseDesign,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey,
            List<Breakline> effectiveBreaklines,
            Map<String, Integer> cumulativeZoneOffsets) {
        if (!shouldRunSiteBalance(site)) {
            return 0;
        }
        int cumulativeUniformOffset = 0;
        for (int iteration = 0; iteration < MAX_SITE_BALANCE_ITERATIONS; iteration++) {
            EarthworkOptimizationSolver.BalanceResult proposed =
                proposeSiteBalance(grid, site, resolvedSurfaces);
            if (proposed.isZero()) {
                break;
            }
            accumulateZoneOffsets(site, resolvedSurfaces, cumulativeZoneOffsets, proposed.zoneOffsets());
            cumulativeUniformOffset += proposed.residualUniformOffset();
            restoreCells(grid, baseDesign);
            applyAccumulatedOffsets(grid, site, cumulativeZoneOffsets, cumulativeUniformOffset);
            applyBoundaryConditions(
                grid,
                site,
                evaluatorsWithOffsets(site, zoneEvaluators, cumulativeZoneOffsets, cumulativeUniformOffset),
                coverageWithOffsets(site, coverageByCellKey, cumulativeZoneOffsets, cumulativeUniformOffset),
                effectiveBreaklines);
        }
        return cumulativeUniformOffset;
    }

    private static boolean shouldRunSiteBalance(EarthworkSite site) {
        return site != null
            && site.getCompositionPolicy().isVerticalOptimizationEnabled()
            && site.getZoneCount() >= 2;
    }

    private static EarthworkOptimizationSolver.BalanceResult proposeSiteBalance(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, ResolvedDesignSurface> resolvedSurfaces) {
        if (site.getCompositionPolicy().isConstrainedZoneOptimization()) {
            return EarthworkOptimizationSolver.propose(grid, site, resolvedSurfaces);
        }
        return new EarthworkOptimizationSolver.BalanceResult(
            Map.of(),
            SiteWideBalanceAdjuster.findBalancedVerticalOffset(grid, site, resolvedSurfaces));
    }

    private static void applyBoundaryConditions(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey,
            List<Breakline> effectiveBreaklines) {
        TerrainBoundaryBlender.apply(grid, site, coverageByCellKey, effectiveBreaklines);
        ZoneBoundarySlopeApplicator.apply(
            grid,
            new ArrayList<>(site.getGradingZones().values()),
            zoneEvaluators);
    }

    private static void recordBalanceOffsets(
            EarthworkSite site,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        if (!shouldRunSiteBalance(site)) {
            site.clearLastZoneVerticalOffsets();
            site.clearLastSiteWideVerticalOffset();
            return;
        }
        if (site.getCompositionPolicy().isConstrainedZoneOptimization()) {
            site.setLastZoneVerticalOffsets(zoneOffsets);
        } else {
            site.clearLastZoneVerticalOffsets();
        }
        if (uniformOffset != 0) {
            site.setLastSiteWideVerticalOffset(uniformOffset);
        } else {
            site.clearLastSiteWideVerticalOffset();
        }
    }

    private static Map<Long, CellSnapshot> snapshotCells(DesignTerrainGrid grid) {
        Map<Long, CellSnapshot> snapshot = new HashMap<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            snapshot.put(
                DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()),
                new CellSnapshot(cell.targetY(), cell.zoneId()));
        }
        return snapshot;
    }

    private static void restoreCells(DesignTerrainGrid grid, Map<Long, CellSnapshot> baseDesign) {
        for (DesignTerrainCell cell : grid.cells().values()) {
            CellSnapshot snapshot = baseDesign.get(DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()));
            if (snapshot == null) {
                continue;
            }
            cell.setTargetY(snapshot.targetY());
            cell.setZoneId(snapshot.zoneId());
        }
    }

    private static void applyAccumulatedOffsets(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        Map<String, Integer> safeZone = zoneOffsets != null ? zoneOffsets : Map.of();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork() || cell.zoneId() == null || cell.zoneId().isBlank()) {
                continue;
            }
            int delta = offsetDelta(site, cell.zoneId(), safeZone, uniformOffset);
            if (delta != 0) {
                cell.setTargetY(cell.targetY() + delta);
            }
        }
    }

    private static void accumulateZoneOffsets(
            EarthworkSite site,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            Map<String, Integer> cumulative,
            Map<String, Integer> delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : delta.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() == 0) {
                continue;
            }
            if (!isSolverVariableZone(site, resolvedSurfaces, entry.getKey())) {
                continue;
            }
            cumulative.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private static boolean isSolverVariableZone(
            EarthworkSite site,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            String zoneId) {
        if (resolvedSurfaces != null) {
            ResolvedDesignSurface resolved = resolvedSurfaces.get(zoneId);
            if (resolved != null) {
                return resolved.isSolverVariable();
            }
        }
        return site == null || !site.isElevationLocked(zoneId);
    }

    private static int offsetDelta(
            EarthworkSite site,
            String zoneId,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        int zone = zoneOffsets.getOrDefault(zoneId, 0);
        if (site == null) {
            return uniformOffset + zone;
        }
        return site.applyProposedVerticalOffset(zoneId, zone, uniformOffset);
    }

    private static Map<String, ResolvedDesignSurface> surfacesWithOffsets(
            EarthworkSite site,
            Map<String, ResolvedDesignSurface> base,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        if (base == null || base.isEmpty()) {
            return Map.of();
        }
        if ((zoneOffsets == null || zoneOffsets.isEmpty()) && uniformOffset == 0) {
            return base;
        }
        Map<String, Integer> safeZone = zoneOffsets != null ? zoneOffsets : Map.of();
        Map<String, ResolvedDesignSurface> wrapped = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedDesignSurface> entry : base.entrySet()) {
            int delta = offsetDelta(site, entry.getKey(), safeZone, uniformOffset);
            ResolvedDesignSurface surface = entry.getValue();
            wrapped.put(entry.getKey(), surface.withVerticalOffset(delta));
        }
        return wrapped;
    }

    private static Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluatorsWithOffsets(
            EarthworkSite site,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> base,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        if (base == null || base.isEmpty()) {
            return Map.of();
        }
        if ((zoneOffsets == null || zoneOffsets.isEmpty()) && uniformOffset == 0) {
            return base;
        }
        Map<String, Integer> safeZone = zoneOffsets != null ? zoneOffsets : Map.of();
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> wrapped = new HashMap<>();
        for (Map.Entry<String, DesignSurfaceResolver.ZoneTargetEvaluator> entry : base.entrySet()) {
            int delta = offsetDelta(site, entry.getKey(), safeZone, uniformOffset);
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator = entry.getValue();
            wrapped.put(
                entry.getKey(),
                delta == 0 ? evaluator : cell -> evaluator.evaluateAt(cell) + delta);
        }
        return wrapped;
    }

    private static Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageWithOffsets(
            EarthworkSite site,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> base,
            Map<String, Integer> zoneOffsets,
            int uniformOffset) {
        if (base == null || base.isEmpty()) {
            return Map.of();
        }
        if ((zoneOffsets == null || zoneOffsets.isEmpty()) && uniformOffset == 0) {
            return base;
        }
        Map<String, Integer> safeZone = zoneOffsets != null ? zoneOffsets : Map.of();
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> shifted = new HashMap<>();
        for (Map.Entry<Long, TerrainBoundaryBlender.ZoneCoverage> entry : base.entrySet()) {
            TerrainBoundaryBlender.ZoneCoverage coverage = entry.getValue();
            Integer runner = coverage.runnerUpTargetY();
            if (runner != null) {
                runner = runner + offsetDelta(site, coverage.runnerUpZoneId(), safeZone, uniformOffset);
            }
            shifted.put(
                entry.getKey(),
                new TerrainBoundaryBlender.ZoneCoverage(
                    coverage.winnerTargetY(),
                    runner,
                    coverage.runnerUpZoneId()));
        }
        return shifted;
    }

    private static String resolveRunnerUpZoneId(List<ZoneCandidate> covering, ZoneCandidate winner) {
        if (covering.size() < 2) {
            return null;
        }
        List<ZoneCandidate> ranked = new ArrayList<>(covering);
        ranked.sort(Comparator
            .comparingInt(ZoneCandidate::priority).reversed()
            .thenComparingDouble(ZoneCandidate::area)
            .thenComparing(ZoneCandidate::zoneId, String.CASE_INSENSITIVE_ORDER));
        for (ZoneCandidate candidate : ranked) {
            if (!candidate.zoneId().equals(winner.zoneId())) {
                return candidate.zoneId();
            }
        }
        return null;
    }


    private static List<ZoneCandidate> collectCoveringCandidates(
            List<ZoneCandidate> candidates,
            DesignTerrainCell cell) {
        List<ZoneCandidate> covering = new ArrayList<>();
        for (ZoneCandidate candidate : candidates) {
            if (candidate.geometry().contains(cell.center())) {
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

    private static ZoneCandidate selectOverlapWinner(
            List<ZoneCandidate> covering,
            CompositionPolicy policy) {
        if (covering.isEmpty()) {
            return null;
        }
        if (CompositionPolicy.OVERLAP_LARGEST_ZONE_WINS.equals(policy.getOverlapResolution())) {
            return selectWinnerByLargestArea(covering);
        }
        return selectWinnerByPriority(covering);
    }

    private static ZoneCandidate selectWinnerByLargestArea(List<ZoneCandidate> covering) {
        return covering.stream()
            .max(Comparator
                .comparingDouble(ZoneCandidate::area)
                .thenComparingInt(ZoneCandidate::priority)
                .thenComparing(ZoneCandidate::zoneId, String.CASE_INSENSITIVE_ORDER))
            .orElse(covering.getFirst());
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
            RegionGeometry geometry = zone.getGeometry();
            if (geometry.isEmpty()) {
                continue;
            }
            candidates.add(new ZoneCandidate(
                zone.getId(),
                zone.getPriority(),
                geometry.area(),
                geometry));
        }
        return candidates;
    }

    private record ZoneCandidate(String zoneId, int priority, double area, RegionGeometry geometry) {
    }

    private record CellSnapshot(int targetY, String zoneId) {
    }
}
