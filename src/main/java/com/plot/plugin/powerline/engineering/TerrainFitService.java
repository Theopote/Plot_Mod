package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineOverrideUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.design.family.VisualTowerResolver;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;

import java.util.List;

/** 自动地形适配：塔基贴地、防导线穿地，必要时抬塔或补塔。 */
public final class TerrainFitService {
    public static final double SAFETY_MARGIN_BLOCKS = 1.5;
    private static final int MAX_FIX_ATTEMPTS = 4;

    private TerrainFitService() {
    }

    public record FitResult(boolean lineModified, boolean hasRemainingIssues) {
    }

    public interface PreviewRegenerator {
        PowerLineGenerationResult regenerate();
    }

    public static boolean hasTerrainIssues(PowerLineGeometryModel geometry, TerrainSampler terrain) {
        return analyze(geometry, terrain).hasIssues();
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
        if (!analysis.hasIssues()) {
            return new FitResult(false, false);
        }
        boolean modified = false;
        PowerLineGenerationResult current = result;
        for (int attempt = 0; attempt < MAX_FIX_ATTEMPTS; attempt++) {
            analysis = analyze(current.toGeometryModel(), terrain);
            if (!analysis.hasIssues()) {
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
        boolean remaining = analyze(current.toGeometryModel(), terrain).hasIssues();
        return new FitResult(modified, remaining);
    }

    public static String remainingIssuesHint() {
        return PlotI18n.tr("plugin.powerline.terrain.near_terrain_hint");
    }

    public static boolean applyOneFix(
            PowerLineFootprint line,
            TerrainCollisionAnalysis analysis,
            PowerLineGenerationResult result,
            ICoordinateService coordinates) {
        if (line == null || analysis == null || !analysis.hasIssues()) {
            return false;
        }
        if (tryTallerTower(line, analysis, result)) {
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
            PowerLineGenerationResult result) {
        if (!line.hasTowerFamily() || result == null) {
            return false;
        }
        TowerFamily family = new TowerFamilyResolver().find(line.getTowerFamilyId());
        if (family == null || !VisualTowerResolver.hasGradedSuspensionVariants(family)) {
            return false;
        }
        SpanAnalysis targetSpan = analysis.firstIssueSpan();
        if (targetSpan == null) {
            return false;
        }
        com.plot.plugin.powerline.model.PowerPoleSite site = nearestSiteForSpan(result, targetSpan);
        if (site == null || site.getPoleDesignOverrideId() != null) {
            return false;
        }
        String currentDesignId = currentDesignId(result, site);
        String nextDesignId = VisualTowerResolver.nextLargerGradedDesign(family, currentDesignId);
        if (nextDesignId == null || nextDesignId.equals(currentDesignId)) {
            return false;
        }
        PowerLineOverrideUtils.setDesignOverride(line, site.getStationing(), nextDesignId);
        return true;
    }

    private static boolean tryRaisePoleHeight(PowerLineFootprint line) {
        if (line.hasTowerFamily() || line.hasPoleDesign()) {
            return false;
        }
        double next = Math.min(64.0, line.getPoleHeight() + 2.0);
        if (next <= line.getPoleHeight()) {
            return false;
        }
        line.setPoleHeight(next);
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
        if (stationing < 0 || hasNearbyConstraint(line, stationing)) {
            return false;
        }
        line.addLayoutConstraint(new PoleLayoutConstraint(
            stationing,
            "plugin.powerline.route.auto_pole.reason.terrain"));
        return true;
    }

    private static com.plot.plugin.powerline.model.PowerPoleSite nearestSiteForSpan(
            PowerLineGenerationResult result,
            SpanAnalysis span) {
        if (result == null || span == null) {
            return null;
        }
        com.plot.plugin.powerline.model.PowerPoleSite start = findSiteById(result, span.getStartPoleSiteId());
        com.plot.plugin.powerline.model.PowerPoleSite end = findSiteById(result, span.getEndPoleSiteId());
        if (start == null || end == null) {
            return null;
        }
        double midpoint = (start.getStationing() + end.getStationing()) * 0.5;
        com.plot.plugin.powerline.model.PowerPoleSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var site : result.poleSites) {
            double distance = Math.abs(site.getStationing() - midpoint);
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
                return (start.getStationing() + end.getStationing()) * 0.5;
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

    private static boolean hasNearbyConstraint(PowerLineFootprint line, double stationing) {
        for (PoleLayoutConstraint constraint : line.getLayoutConstraints()) {
            if (Math.abs(constraint.getRequiredStationing() - stationing) < 4.0) {
                return true;
            }
        }
        return false;
    }
}
