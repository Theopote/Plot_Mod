package com.plot.plugin.powerline.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineOverrideUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.design.family.VisualTowerResolver;
import com.plot.plugin.powerline.geometry.SpanAnalysis;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.path.ClosedPathStationMath;
import com.plot.core.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;

import java.util.List;

/** 自动地形适配：塔基贴地、防导线穿地，必要时抬塔或补塔。 */
public final class TerrainFitService {
    public static final double SAFETY_MARGIN_BLOCKS = 1.5;
    private static final int MAX_FIX_ATTEMPTS = 4;

    private TerrainFitService() {
    }

    public record FitResult(boolean lineModified, boolean hasVisualConflict) {
    }

    public interface PreviewRegenerator {
        PowerLineGenerationResult regenerate();
    }

    public static boolean hasVisualConflict(PowerLineGeometryModel geometry, TerrainSampler terrain) {
        return analyze(geometry, terrain).hasVisualConflicts();
    }

    public static TerrainCollisionAnalysis analyze(PowerLineGeometryModel geometry, TerrainSampler terrain) {
        return TerrainCollisionAnalysis.analyze(geometry, terrain);
    }

    /**
     * 检测并在可能时自动修正地形冲突。
     *
     * @param regenerator 每次修改线路参数后重新生成预览
     */
    public static FitResult fit(
            PowerLineFootprint line,
            PowerLineGenerationResult result,
            TerrainSampler terrain,
            ICoordinateService coordinates,
            PreviewRegenerator regenerator) {
        if (line == null || result == null || terrain == null || coordinates == null || regenerator == null) {
            return new FitResult(false, false);
        }
        TerrainCollisionAnalysis analysis = analyze(result.toGeometryModel(), terrain);
        if (!analysis.hasVisualConflicts()) {
            return new FitResult(false, false);
        }
        boolean modified = false;
        PowerLineGenerationResult current = result;
        for (int attempt = 0; attempt < MAX_FIX_ATTEMPTS; attempt++) {
            analysis = analyze(current.toGeometryModel(), terrain);
            if (!analysis.hasVisualConflicts()) {
                return new FitResult(modified, false);
            }
            if (!applyOneFix(line, analysis, current, coordinates)) {
                return new FitResult(modified, true);
            }
            modified = true;
            current = regenerator.regenerate();
            if (current == null) {
                return new FitResult(modified, true);
            }
        }
        boolean remaining = analyze(current.toGeometryModel(), terrain).hasVisualConflicts();
        return new FitResult(modified, remaining);
    }

    public static String visualConflictHint() {
        return PlotI18n.tr("plugin.powerline.terrain.near_terrain_hint");
    }

    public static boolean applyOneFix(
            PowerLineFootprint line,
            TerrainCollisionAnalysis analysis,
            PowerLineGenerationResult result,
            ICoordinateService coordinates) {
        if (line == null || analysis == null || !analysis.hasVisualConflicts()) {
            return false;
        }
        if (tryTallerTower(line, analysis, result, coordinates)) {
            return true;
        }
        if (tryRaisePoleHeight(line)) {
            return true;
        }
        return tryInsertPole(line, analysis, result, coordinates);
    }

    private static boolean tryTallerTower(
            PowerLineFootprint line,
            TerrainCollisionAnalysis analysis,
            PowerLineGenerationResult result,
            ICoordinateService coordinates) {
        if (!line.hasTowerFamily() || result == null) {
            return false;
        }
        TowerFamily family = new TowerFamilyResolver().find(line.getTowerFamilyId());
        if (!VisualTowerResolver.hasGradedSuspensionVariants(family)) {
            return false;
        }
        SpanAnalysis targetSpan = analysis.firstIssueSpan();
        if (targetSpan == null) {
            return false;
        }
        com.plot.plugin.powerline.model.PowerPoleSite site = nearestSiteForSpan(line, result, targetSpan, coordinates);
        if (site == null || site.getPoleDesignOverrideId() != null) {
            return false;
        }
        String currentDesignId = currentDesignId(result, site);
        String nextDesignId = VisualTowerResolver.nextLargerGradedDesign(family, currentDesignId);
        if (nextDesignId == null || nextDesignId.equals(currentDesignId)) {
            return false;
        }
        PowerLineOverrideUtils.setDerivedDesignOverride(
            line.getDerivedLayout(),
            site.getStationing(),
            nextDesignId);
        return true;
    }

    private static boolean tryRaisePoleHeight(PowerLineFootprint line) {
        if (line.hasTowerFamily() || line.hasPoleDesign()) {
            return false;
        }
        double current = line.effectivePoleHeight();
        double next = Math.min(64.0, current + 2.0);
        if (next <= current) {
            return false;
        }
        line.getDerivedLayout().setPoleHeightOverride(next);
        return true;
    }

    private static boolean tryInsertPole(
            PowerLineFootprint line,
            TerrainCollisionAnalysis analysis,
            PowerLineGenerationResult result,
            ICoordinateService coordinates) {
        SpanAnalysis targetSpan = analysis.firstIssueSpan();
        if (targetSpan == null) {
            return false;
        }
        double stationing = stationingForSpan(line, targetSpan, result, coordinates);
        if (stationing < 0 || hasNearbyConstraint(line, stationing, coordinates)) {
            return false;
        }
        line.getDerivedLayout().addAutoLayoutConstraint(new PoleLayoutConstraint(
            stationing,
            "plugin.powerline.route.auto_pole.reason.terrain"));
        return true;
    }

    private static com.plot.plugin.powerline.model.PowerPoleSite nearestSiteForSpan(
            PowerLineFootprint line,
            PowerLineGenerationResult result,
            SpanAnalysis span,
            ICoordinateService coordinates) {
        if (result == null || span == null) {
            return null;
        }
        com.plot.plugin.powerline.model.PowerPoleSite start = findSiteById(result, span.getStartPoleSiteId());
        com.plot.plugin.powerline.model.PowerPoleSite end = findSiteById(result, span.getEndPoleSiteId());
        if (start == null || end == null) {
            return null;
        }
        boolean closedLoop = line != null && line.isClosedLoop();
        double perimeter = closedLoop && line != null
            ? line.resolveSourcePath().worldLength(coordinates)
            : 0.0;
        double midpoint = ClosedPathStationMath.midpoint(
            start.getStationing(),
            end.getStationing(),
            perimeter,
            closedLoop);
        com.plot.plugin.powerline.model.PowerPoleSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var site : result.poleSites) {
            double distance = ClosedPathStationMath.nearestDistance(
                site.getStationing(),
                midpoint,
                perimeter,
                closedLoop);
            if (distance < bestDistance) {
                best = site;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static com.plot.plugin.powerline.model.PowerPoleSite findSiteById(
            PowerLineGenerationResult result,
            String siteId) {
        if (result == null || siteId == null || siteId.isBlank()) {
            return null;
        }
        for (var site : result.poleSites) {
            if (siteId.equals(site.getId())) {
                return site;
            }
        }
        return null;
    }

    private static String currentDesignId(
            PowerLineGenerationResult result,
            com.plot.plugin.powerline.model.PowerPoleSite site) {
        if (site.getPoleDesignOverrideId() != null) {
            return site.getPoleDesignOverrideId();
        }
        for (int i = 0; i < result.poleSites.size(); i++) {
            if (result.poleSites.get(i).getId().equals(site.getId())) {
                return i < result.polePlacements.size()
                    ? result.polePlacements.get(i).resolvedDesignId()
                    : null;
            }
        }
        return null;
    }

    private static double stationingForSpan(
            PowerLineFootprint line,
            SpanAnalysis span,
            PowerLineGenerationResult result,
            ICoordinateService coordinates) {
        if (result != null
                && span.getStartPoleSiteId() != null
                && span.getEndPoleSiteId() != null) {
            com.plot.plugin.powerline.model.PowerPoleSite start =
                findSiteById(result, span.getStartPoleSiteId());
            com.plot.plugin.powerline.model.PowerPoleSite end =
                findSiteById(result, span.getEndPoleSiteId());
            if (start != null && end != null) {
                boolean closedLoop = line.isClosedLoop();
                double perimeter = closedLoop
                    ? line.resolveSourcePath().worldLength(coordinates)
                    : 0.0;
                return ClosedPathStationMath.midpoint(
                    start.getStationing(),
                    end.getStationing(),
                    perimeter,
                    closedLoop);
            }
        }
        Vec2d midpoint = midpointAlongPath(line.getPathPoints(), coordinates);
        if (midpoint == null) {
            return -1.0;
        }
        return PowerPoleLayoutUtils.computeStationing(line.getPathPoints(), midpoint, coordinates);
    }

    private static Vec2d midpointAlongPath(List<Vec2d> pathPoints, ICoordinateService coordinates) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return null;
        }
        double total = coordinates.pathWorldLength(pathPoints);
        double half = total * 0.5;
        double walked = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d a = pathPoints.get(i - 1);
            Vec2d b = pathPoints.get(i);
            double segment = coordinates.projectedDistance(a, b);
            if (walked + segment >= half) {
                double t = segment > 0 ? (half - walked) / segment : 0.0;
                return new Vec2d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
            }
            walked += segment;
        }
        return pathPoints.getLast().copy();
    }

    private static boolean hasNearbyConstraint(
            PowerLineFootprint line,
            double stationing,
            ICoordinateService coordinates) {
        boolean closedLoop = line.isClosedLoop();
        double perimeter = closedLoop
            ? line.resolveSourcePath().worldLength(coordinates)
            : 0.0;
        for (PoleLayoutConstraint constraint : line.effectiveLayoutConstraints()) {
            double distance = ClosedPathStationMath.nearestDistance(
                stationing,
                constraint.getRequiredStationing(),
                perimeter,
                closedLoop);
            if (distance < 4.0) {
                return true;
            }
        }
        return false;
    }
}
