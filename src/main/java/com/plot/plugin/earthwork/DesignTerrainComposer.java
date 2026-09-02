package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
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

    public record ComposeResult(DesignTerrainGrid grid, Map<String, GradingPlane> zonePlanes) {
    }

    public static ComposeResult compose(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        if (site == null || terrain == null || terrain.isEmpty()) {
            return new ComposeResult(new DesignTerrainGrid(), Map.of());
        }

        DesignTerrainGrid grid = new DesignTerrainGrid();
        initializeCells(grid, terrain);

        applyExclusionZones(grid, site.getExclusionZones());
        Map<String, GradingPlane> zonePlanes = resolveZonePlanes(site, terrain, transformer);
        applyZoneCoverage(grid, site, zonePlanes);
        grid.finalizeStats();
        return new ComposeResult(grid, zonePlanes);
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

    private static Map<String, GradingPlane> resolveZonePlanes(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        Map<String, GradingPlane> planes = new HashMap<>();
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled() || !zone.getType().isSupportedInMvp()) {
                continue;
            }
            ZoneSamples samples = collectZoneSamples(terrain, zone.getOuterPoints());
            GradingSurfaceResolver.ResolvedSurface surface = GradingSurfaceResolver.resolve(
                zone.getRegion(),
                samples.centers(),
                samples.heights(),
                transformer);
            planes.put(zone.getId(), surface.plane());
        }
        return planes;
    }

    private static void applyZoneCoverage(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<String, GradingPlane> zonePlanes) {
        List<ZoneCandidate> candidates = buildZoneCandidates(site);
        CompositionPolicy policy = site.getCompositionPolicy();
        boolean highestPriorityWins = CompositionPolicy.OVERLAP_HIGHEST_PRIORITY_WINS
            .equals(policy.getOverlapResolution());

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell.excluded()) {
                continue;
            }
            List<ZoneCandidate> covering = new ArrayList<>();
            for (ZoneCandidate candidate : candidates) {
                if (candidate.polygon.contains(cell.center())) {
                    covering.add(candidate);
                }
            }
            if (covering.isEmpty()) {
                continue;
            }
            ZoneCandidate winner = highestPriorityWins
                ? selectWinnerByPriority(covering)
                : covering.getFirst();
            GradingPlane plane = zonePlanes.get(winner.zoneId());
            if (plane == null) {
                continue;
            }
            cell.setTargetY(plane.evaluateAt(cell.worldX(), cell.worldZ()));
            cell.setZoneId(winner.zoneId());
        }
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
            if (zone == null || !zone.isEnabled() || !zone.getType().isSupportedInMvp()) {
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

    private static ZoneSamples collectZoneSamples(TerrainSnapshot terrain, List<Vec2d> zonePolygon) {
        List<Vec2d> centers = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (!EarthworkGeometryUtils.containsCanvasPoint(zonePolygon, column.center())) {
                continue;
            }
            centers.add(column.center());
            heights.add(column.groundY());
        }
        return new ZoneSamples(centers, heights);
    }

    private record ZoneCandidate(String zoneId, int priority, double area, Polygon polygon) {
    }

    private record ZoneSamples(List<Vec2d> centers, List<Integer> heights) {
    }
}
