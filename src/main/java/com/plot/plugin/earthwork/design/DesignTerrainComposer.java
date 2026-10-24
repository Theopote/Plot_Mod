package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.RetainingEdgeBreaklineAdapter;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.grading.BreaklineClassifier;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkBalanceStatus;
import com.plot.plugin.earthwork.solver.EarthworkElevationVolumeCurve;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.solver.SlopeCoupledVerticalSearch;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.terrain.TerrainBoundaryBlender;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.design.ResolutionResult;
import com.plot.plugin.earthwork.design.ResolvedDesignSource;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;

import java.util.ArrayList;
import java.util.Objects;
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

    /**
     * 本次合成的求解偏移诊断（不写回 {@link EarthworkSite}）。
     * {@code siteWideVerticalOffset} 与 {@link #balanceBestOffset} 同义。
     */
    public record BalanceOffsets(Map<String, Integer> zoneVerticalOffsets, int siteWideVerticalOffset) {
        public BalanceOffsets {
            zoneVerticalOffsets = zoneVerticalOffsets != null ? Map.copyOf(zoneVerticalOffsets) : Map.of();
        }

        public static BalanceOffsets none() {
            return new BalanceOffsets(Map.of(), 0);
        }
    }

    public record ComposeResult(
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            long residualImbalance,
            EarthworkBalanceStatus balanceStatus,
            int balanceSearchHalfRange,
            int balanceBestOffset,
            boolean balanceSearchHitBoundary,
            boolean hasFixedElevationConstraints,
            List<String> warnings,
            Map<String, Integer> zoneVerticalOffsets,
            EarthworkElevationVolumeCurve slopeCoupledCurve) {
        public ComposeResult {
            zoneEvaluators = zoneEvaluators != null ? Map.copyOf(zoneEvaluators) : Map.of();
            resolvedSurfaces = resolvedSurfaces != null ? Map.copyOf(resolvedSurfaces) : Map.of();
            balanceStatus = balanceStatus != null ? balanceStatus : EarthworkBalanceStatus.NOT_REQUESTED;
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
            zoneVerticalOffsets = zoneVerticalOffsets != null ? Map.copyOf(zoneVerticalOffsets) : Map.of();
            slopeCoupledCurve = slopeCoupledCurve != null ? slopeCoupledCurve : EarthworkElevationVolumeCurve.EMPTY;
        }

        public ComposeResult(DesignTerrainGrid grid, Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
            this(grid, zoneEvaluators, Map.of(), 0L, EarthworkBalanceStatus.NOT_REQUESTED,
                0, 0, false, false, List.of(), Map.of(), EarthworkElevationVolumeCurve.EMPTY);
        }

        public ComposeResult(
                DesignTerrainGrid grid,
                Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
                Map<String, ResolvedDesignSurface> resolvedSurfaces) {
            this(grid, zoneEvaluators, resolvedSurfaces, 0L, EarthworkBalanceStatus.NOT_REQUESTED,
                0, 0, false, false, List.of(), Map.of(), EarthworkElevationVolumeCurve.EMPTY);
        }

        public int siteWideVerticalOffset() {
            return balanceBestOffset;
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
        return compose(site, terrain, transformer, buildingLookup, roadLookup, EarthworkWorkMode.QUICK);
    }

    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            EarthworkWorkMode workMode) {
        CompositionPolicy policy = site != null ? site.getCompositionPolicy() : null;
        return compose(site, terrain, transformer, buildingLookup, roadLookup, workMode, policy);
    }

    /**
     * @param effectivePolicy 本次计算采用的合成策略（可与项目保存策略不同，例如 Quick 的裁剪副本）。
     *                        不得写回 {@link EarthworkSite}。
     */
    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            EarthworkWorkMode workMode,
            CompositionPolicy effectivePolicy) {
        if (site == null || terrain == null || terrain.isEmpty()) {
            return new ComposeResult(
                new DesignTerrainGrid(), Map.of(), Map.of(), 0L,
                EarthworkBalanceStatus.NOT_REQUESTED, 0, 0, false, false, List.of(), Map.of(),
                EarthworkElevationVolumeCurve.EMPTY);
        }
        EarthworkWorkMode safeWorkMode = workMode != null ? workMode : EarthworkWorkMode.QUICK;
        CompositionPolicy policy = effectivePolicy != null ? effectivePolicy : site.getCompositionPolicy();
        Objects.requireNonNull(transformer, "transformer");
        EarthworkCanvasScale canvasScale = EarthworkCanvasScale.capture(transformer, site.getSiteBoundary());

        DesignTerrainGrid grid = new DesignTerrainGrid();
        initializeCells(grid, terrain);

        applyExclusionZones(grid, site.getExclusionZones());
        Map<String, ResolvedDesignSurface> resolvedSurfaces =
            DesignSurfaceResolver.resolveZoneSurfaces(
                site, terrain, buildingLookup, roadLookup, transformer, canvasScale, policy);
        requireResolvableRoadCorridors(resolvedSurfaces);
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators =
            ResolvedDesignSurface.toEvaluatorMap(resolvedSurfaces);
        List<Breakline> effectiveBreaklines = mergeEffectiveBreaklines(site);
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey =
            applyZoneCoverage(grid, site, zoneEvaluators, effectiveBreaklines, canvasScale, policy);
        Map<Long, CellSnapshot> baseDesign = snapshotCells(grid);

        applyBoundaryConditions(
            grid,
            site,
            zoneEvaluators,
            coverageByCellKey,
            effectiveBreaklines,
            canvasScale,
            policy);

        Map<String, Integer> cumulativeZoneOffsets = new LinkedHashMap<>();
        BalanceSearchOutcome balanceOutcome = iterateSiteBalance(
            grid,
            site,
            baseDesign,
            zoneEvaluators,
            resolvedSurfaces,
            coverageByCellKey,
            effectiveBreaklines,
            cumulativeZoneOffsets,
            safeWorkMode,
            canvasScale,
            policy);
        BalanceOffsets balanceOffsets = resolveBalanceOffsets(
            site, cumulativeZoneOffsets, balanceOutcome.uniformOffset(), policy);

        Map<String, ResolvedDesignSurface> finalResolved =
            surfacesWithOffsets(site, resolvedSurfaces, cumulativeZoneOffsets, balanceOutcome.uniformOffset());
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> finalEvaluators =
            ResolvedDesignSurface.toEvaluatorMap(finalResolved);
        grid.finalizeStats();
        boolean hasFixed = hasConstraintLimitedSurfaces(resolvedSurfaces);
        EarthworkBalanceStatus status = EarthworkBalanceStatus.classify(
            balanceOutcome.ran(),
            balanceOutcome.hasValidSolution(),
            balanceOutcome.residualImbalance(),
            hasFixed);
        List<String> warnings = new ArrayList<>();
        if (status == EarthworkBalanceStatus.BEST_EFFORT
            || status == EarthworkBalanceStatus.CONSTRAINT_LIMITED) {
            warnings.add("plugin.earthwork.warning.balance_incomplete");
        } else if (status == EarthworkBalanceStatus.SEARCH_FAILED) {
            warnings.add("plugin.earthwork.warning.balance_search_failed");
        }
        if (balanceOutcome.hitSearchBoundary()) {
            warnings.add("plugin.earthwork.warning.balance_search_boundary");
        }
        // 未运行 → 残余 0；已运行但无有效解 → INVALID（禁止伪装成已平衡的 0）
        long residualForReport = !balanceOutcome.ran()
            ? 0L
            : (balanceOutcome.hasValidSolution()
                ? balanceOutcome.residualImbalance()
                : SlopeCoupledVerticalSearch.INVALID_OBJECTIVE);
        return new ComposeResult(
            grid,
            finalEvaluators,
            finalResolved,
            residualForReport,
            status,
            balanceOutcome.searchHalfRange(),
            balanceOffsets.siteWideVerticalOffset(),
            balanceOutcome.hitSearchBoundary(),
            hasFixed,
            warnings,
            balanceOffsets.zoneVerticalOffsets(),
            balanceOutcome.slopeCoupledCurve());
    }

    private record BalanceSearchOutcome(
            int uniformOffset,
            long residualImbalance,
            boolean ran,
            int searchHalfRange,
            boolean hitSearchBoundary,
            boolean hasValidSolution,
            EarthworkElevationVolumeCurve slopeCoupledCurve) {
        static BalanceSearchOutcome skipped() {
            return new BalanceSearchOutcome(
                0, 0L, false, 0, false, false, EarthworkElevationVolumeCurve.EMPTY);
        }
    }

    private static boolean hasConstraintLimitedSurfaces(Map<String, ResolvedDesignSurface> surfaces) {
        if (surfaces == null || surfaces.isEmpty()) {
            return false;
        }
        for (ResolvedDesignSurface surface : surfaces.values()) {
            if (surface != null && !surface.isSolverVariable()) {
                return true;
            }
        }
        return false;
    }

    private static List<Breakline> mergeEffectiveBreaklines(EarthworkSite site) {
        List<Breakline> breaklines = new ArrayList<>(site.getBreaklines());
        breaklines.addAll(RetainingEdgeBreaklineAdapter.toNoBlendBreaklines(site.getRetainingEdges()));
        breaklines.addAll(ZoneBoundaryRetainingEdgeAdapter.toNoBlendBreaklines(site.getGradingZones().values()));
        return breaklines;
    }

    private static void requireResolvableRoadCorridors(Map<String, ResolvedDesignSurface> resolvedSurfaces) {
        if (resolvedSurfaces == null || resolvedSurfaces.isEmpty()) {
            return;
        }
        for (ResolvedDesignSurface surface : resolvedSurfaces.values()) {
            if (surface == null || surface.source() != ResolvedDesignSource.ROAD_CORRIDOR) {
                continue;
            }
            if (surface.status() == ResolutionResult.Status.MISSING_REFERENCE
                || surface.status() == ResolutionResult.Status.INVALID_REFERENCE) {
                String message = surface.detail() != null && !surface.detail().isBlank()
                    ? surface.detail()
                    : "Road corridor design surface is not resolved";
                throw new RoadCorridorSurfaceResolver.UnresolvedRoadDesignSurfaceException(message);
            }
        }
    }

    private static void initializeCells(DesignTerrainGrid grid, TerrainSnapshot terrain) {
        for (TerrainSnapshot.Column column : terrain.columns()) {
            DesignTerrainCell cell = new DesignTerrainCell(
                column.worldX(),
                column.worldZ(),
                column.center(),
                column.groundY(),
                column.chunkLoaded());
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
            List<Breakline> breaklines,
            EarthworkCanvasScale canvasScale,
            CompositionPolicy policy) {
        Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey = new HashMap<>();
        List<ZoneCandidate> candidates = buildZoneCandidates(site);
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
                covering = filterByBreaklineSide(
                    cell.center(), covering, breaklines, breaklineInfluence, canvasScale);
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

    private static BalanceSearchOutcome iterateSiteBalance(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<Long, CellSnapshot> baseDesign,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey,
            List<Breakline> effectiveBreaklines,
            Map<String, Integer> cumulativeZoneOffsets,
            EarthworkWorkMode workMode,
            EarthworkCanvasScale canvasScale,
            CompositionPolicy policy) {
        if (!shouldRunSiteBalance(site, policy)) {
            return BalanceSearchOutcome.skipped();
        }

        // 统一 ΔY 或单区自动平衡：每个候选完整重建坡面后再比 |Cut−Fill|。
        if (usesSlopeCoupledUniformSearch(site, policy)) {
            return applySlopeCoupledUniformSearch(
                grid,
                site,
                baseDesign,
                zoneEvaluators,
                resolvedSurfaces,
                coverageByCellKey,
                effectiveBreaklines,
                Map.of(),
                workMode,
                canvasScale,
                policy);
        }

        // CONSTRAINED_ZONE_OPTIMIZATION：先启发分区 ΔY，再对残余统一偏移做边坡耦合离散搜索。
        int cumulativeUniformOffset = 0;
        BalanceSearchOutcome polish = BalanceSearchOutcome.skipped();
        if (policy.isConstrainedZoneOptimization()) {
            for (int iteration = 0; iteration < MAX_SITE_BALANCE_ITERATIONS; iteration++) {
                EarthworkOptimizationSolver.BalanceResult proposed =
                    EarthworkOptimizationSolver.proposeZoneOffsetsOnly(grid, site, resolvedSurfaces);
                if (proposed.zoneOffsets() == null || proposed.zoneOffsets().isEmpty()
                    || allZero(proposed.zoneOffsets())) {
                    break;
                }
                accumulateZoneOffsets(site, resolvedSurfaces, cumulativeZoneOffsets, proposed.zoneOffsets());
                restoreCells(grid, baseDesign);
                applyAccumulatedOffsets(grid, site, cumulativeZoneOffsets, cumulativeUniformOffset);
                applyBoundaryConditions(
                    grid,
                    site,
                    evaluatorsWithOffsets(site, zoneEvaluators, cumulativeZoneOffsets, cumulativeUniformOffset),
                    coverageWithOffsets(site, coverageByCellKey, cumulativeZoneOffsets, cumulativeUniformOffset),
                    effectiveBreaklines,
                    canvasScale,
                    policy);
            }
        }

        if (policy.isBalanceResidualUniformPolish()) {
            polish = applySlopeCoupledUniformSearch(
                grid,
                site,
                baseDesign,
                zoneEvaluators,
                resolvedSurfaces,
                coverageByCellKey,
                effectiveBreaklines,
                Map.copyOf(cumulativeZoneOffsets),
                workMode,
                canvasScale,
                policy);
            cumulativeUniformOffset = polish.uniformOffset();
        } else {
            long residual = SlopeCoupledVerticalSearch.materialImbalanceObjective(grid, site);
            polish = new BalanceSearchOutcome(
                cumulativeUniformOffset, residual, true, 0, false, true,
                EarthworkElevationVolumeCurve.EMPTY);
        }
        return new BalanceSearchOutcome(
            cumulativeUniformOffset,
            polish.residualImbalance(),
            true,
            polish.searchHalfRange(),
            polish.hitSearchBoundary(),
            polish.hasValidSolution(),
            polish.slopeCoupledCurve());
    }

    /**
     * 对累计分区偏移之上的统一 ΔY 做边坡耦合离散搜索，并把网格留在最优候选状态。
     * Quick：半幅覆盖已加载地面 [minY, maxY]；Builder：策略范围 + ±16 封顶。
     */
    private static BalanceSearchOutcome applySlopeCoupledUniformSearch(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<Long, CellSnapshot> baseDesign,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey,
            List<Breakline> effectiveBreaklines,
            Map<String, Integer> zoneOffsets,
            EarthworkWorkMode workMode,
            EarthworkCanvasScale canvasScale,
            CompositionPolicy policy) {
        Map<String, Integer> safeZone = zoneOffsets != null ? zoneOffsets : Map.of();
        int referenceY = resolveBaseReferenceElevation(grid, baseDesign);
        int halfRange = resolveUniformSearchHalfRange(grid, site, resolvedSurfaces, workMode);
        Map<Integer, EarthworkElevationVolumeCurve.Sample> curveSamples = new java.util.TreeMap<>();
        Map<Map<String, Integer>, Long> objectives = new HashMap<>();
        SlopeCoupledVerticalSearch.SearchResult search = SlopeCoupledVerticalSearch.searchUniform(halfRange, dy -> {
            Map<String, Integer> appliedOffsets = new LinkedHashMap<>();
            for (GradingZone zone : site.getGradingZones().values()) {
                appliedOffsets.put(zone.getId(), offsetDelta(site, zone.getId(), safeZone, dy));
            }
            Long cached = objectives.get(appliedOffsets);
            if (cached != null) {
                return cached;
            }
            restoreCells(grid, baseDesign);
            applyAccumulatedOffsets(grid, site, safeZone, dy);
            applyBoundaryConditions(
                grid,
                site,
                evaluatorsWithOffsets(site, zoneEvaluators, safeZone, dy),
                coverageWithOffsets(site, coverageByCellKey, safeZone, dy),
                effectiveBreaklines,
                canvasScale,
                policy);
            SiteEarthworkReport report = EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
            EarthworkVolumeReport totals = report.totals();
            long cut = totals != null ? totals.geometricCutVolume() : 0L;
            long fill = totals != null ? totals.geometricFillVolume() : 0L;
            // 只有实际统一平移才可用单一标高作横轴；限幅/权重后的偏移才是有效高度。
            if (appliedOffsets.values().stream().distinct().count() == 1) {
                int actualOffset = appliedOffsets.values().iterator().next();
                int elevation = referenceY + actualOffset;
                curveSamples.put(elevation, new EarthworkElevationVolumeCurve.Sample(elevation, cut, fill));
            }
            long objective = Math.abs(cut - fill);
            objectives.put(Map.copyOf(appliedOffsets), objective);
            return objective;
        });

        int best = search.hasValidSolution() ? search.bestOffset() : 0;
        restoreCells(grid, baseDesign);
        applyAccumulatedOffsets(grid, site, safeZone, best);
        applyBoundaryConditions(
            grid,
            site,
            evaluatorsWithOffsets(site, zoneEvaluators, safeZone, best),
            coverageWithOffsets(site, coverageByCellKey, safeZone, best),
            effectiveBreaklines,
            canvasScale,
            policy);
        long residual = search.hasValidSolution()
            ? search.bestObjective()
            : SlopeCoupledVerticalSearch.INVALID_OBJECTIVE;
        EarthworkElevationVolumeCurve curve = EarthworkElevationVolumeCurve.fromSlopeCoupledSamples(
            new ArrayList<>(curveSamples.values()), referenceY, grid);
        return new BalanceSearchOutcome(
            best,
            residual,
            true,
            halfRange,
            search.hitSearchBoundary(),
            search.hasValidSolution(),
            curve);
    }

    private static int resolveBaseReferenceElevation(
            DesignTerrainGrid grid, Map<Long, CellSnapshot> baseDesign) {
        long sum = 0;
        int count = 0;
        for (DesignTerrainCell cell : grid.cells().values()) {
            CellSnapshot base = baseDesign.get(DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()));
            if (cell.participatesInEarthwork() && base != null && base.zoneId() != null
                    && !base.zoneId().isBlank()) {
                sum += base.targetY();
                count++;
            }
        }
        return count > 0 ? (int) Math.round(sum / (double) count) : 64;
    }

    private static int resolveUniformReferenceElevation(DesignTerrainGrid grid) {
        if (grid == null) {
            return 64;
        }
        long sum = 0L;
        int count = 0;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork()) {
                continue;
            }
            sum += cell.targetY();
            count++;
        }
        return count > 0 ? (int) Math.round(sum / (double) count) : 64;
    }

    private static int resolveUniformSearchHalfRange(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, ResolvedDesignSurface> resolvedSurfaces,
            EarthworkWorkMode workMode) {
        EarthworkWorkMode safeMode = workMode != null ? workMode : EarthworkWorkMode.QUICK;
        if (safeMode == EarthworkWorkMode.QUICK && grid != null) {
            int loadedMin = Integer.MAX_VALUE;
            int loadedMax = Integer.MIN_VALUE;
            int loadedCount = 0;
            for (DesignTerrainCell cell : grid.cells().values()) {
                if (!cell.chunkLoaded()) {
                    continue;
                }
                loadedMin = Math.min(loadedMin, cell.existingGroundY());
                loadedMax = Math.max(loadedMax, cell.existingGroundY());
                loadedCount++;
            }
            if (loadedCount > 0) {
                int referenceY = resolveUniformReferenceElevation(grid);
                int halfRange = Math.max(
                    Math.abs(referenceY - loadedMin),
                    Math.abs(loadedMax - referenceY));
                halfRange = Math.max(halfRange + 2, SlopeCoupledVerticalSearch.DEFAULT_HALF_RANGE);
                return Math.min(halfRange, 64);
            }
        }

        int halfRange = SlopeCoupledVerticalSearch.DEFAULT_HALF_RANGE;
        if (site == null) {
            return Math.min(halfRange, VerticalAdjustmentPolicy.maxAutoBalanceHalfRange(safeMode));
        }
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            if (resolvedSurfaces != null) {
                ResolvedDesignSurface resolved = resolvedSurfaces.get(zone.getId());
                if (resolved != null && !resolved.isSolverVariable()) {
                    continue;
                }
            } else if (zone.isElevationLocked()) {
                continue;
            }
            var zonePolicy = zone.getVerticalAdjustmentPolicy();
            halfRange = Math.max(halfRange, Math.abs(zonePolicy.getMinOffset()));
            halfRange = Math.max(halfRange, Math.abs(zonePolicy.getMaxOffset()));
        }
        return Math.min(halfRange, VerticalAdjustmentPolicy.maxAutoBalanceHalfRange(safeMode));
    }

    private static boolean allZero(Map<String, Integer> offsets) {
        for (Integer value : offsets.values()) {
            if (value != null && value != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldRunSiteBalance(EarthworkSite site, CompositionPolicy policy) {
        if (site == null || !hasAdjustableZone(site)) {
            return false;
        }
        if (policy != null && policy.isVerticalOptimizationEnabled()) {
            return true;
        }
        return site.getZoneCount() == 1 && isSingleZoneMinecraftPadBalance(site);
    }

    private static boolean usesSlopeCoupledUniformSearch(EarthworkSite site, CompositionPolicy policy) {
        if (policy != null && policy.isUniformVerticalShift()) {
            return true;
        }
        return isSingleZoneMinecraftPadBalance(site)
            && (policy == null || !policy.isConstrainedZoneOptimization());
    }

    private static boolean hasAdjustableZone(EarthworkSite site) {
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone != null && zone.isEnabled() && zone.allowsVerticalAdjustment()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSingleZoneMinecraftPadBalance(EarthworkSite site) {
        if (site == null || site.getZoneCount() != 1) {
            return false;
        }
        GradingZone zone = site.getGradingZones().values().iterator().next();
        if (zone == null || !zone.isEnabled() || !zone.getRegion().isAutoBalance()) {
            return false;
        }
        if (!zone.allowsVerticalAdjustment()) {
            return false;
        }
        GradingZoneType type = zone.getType();
        return type == GradingZoneType.FLAT || type == GradingZoneType.SLOPED;
    }

    private static void applyBoundaryConditions(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            Map<Long, TerrainBoundaryBlender.ZoneCoverage> coverageByCellKey,
            List<Breakline> effectiveBreaklines,
            EarthworkCanvasScale canvasScale,
            CompositionPolicy policy) {
        TerrainBoundaryBlender.apply(
            grid, site, coverageByCellKey, effectiveBreaklines, canvasScale, policy);
        ZoneBoundarySlopeApplicator.apply(
            grid,
            new ArrayList<>(site.getGradingZones().values()),
            zoneEvaluators,
            canvasScale);
    }

    /** 汇总本次求解偏移；不修改 {@link EarthworkSite}。 */
    private static BalanceOffsets resolveBalanceOffsets(
            EarthworkSite site,
            Map<String, Integer> zoneOffsets,
            int uniformOffset,
            CompositionPolicy policy) {
        if (!shouldRunSiteBalance(site, policy)) {
            return BalanceOffsets.none();
        }
        Map<String, Integer> zones = (policy != null && policy.isConstrainedZoneOptimization())
            ? (zoneOffsets != null ? zoneOffsets : Map.of())
            : Map.of();
        return new BalanceOffsets(zones, uniformOffset);
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
            double influenceDistance,
            EarthworkCanvasScale canvasScale) {
        String mandatedZoneId = BreaklineClassifier.resolveMandatedZoneId(
            point, breaklines, influenceDistance, canvasScale);
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
