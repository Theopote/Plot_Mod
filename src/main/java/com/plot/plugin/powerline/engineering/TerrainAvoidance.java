package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineOverrideUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.engineering.optimization.LineOptimizationEngine;
import com.plot.plugin.powerline.engineering.optimization.OptimizationAction;
import com.plot.plugin.powerline.engineering.optimization.OptimizationActionType;
import com.plot.plugin.powerline.engineering.optimization.OptimizationResult;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/** 装饰性地形避让：检测导线碰地/穿山，并尝试自动修正。 */
public final class TerrainAvoidance {
    public static final double SAFETY_MARGIN_BLOCKS = 1.5;
    public static final String PROFILE_ID = "terrain/decorative";

    private TerrainAvoidance() {
    }

    /** 只检测导线与地形表面的碰撞（不含完整工程规则）。 */
    public static PowerLineValidationReport analyzeCollisions(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain) {
        return com.plot.plugin.powerline.engineering.validation.TerrainCollisionCheck
            .analyzeTerrainOnly(geometry, terrain);
    }

    /**
     * 尝试一次自动修正：更高杆塔 → 加高电杆 → 插入中间杆塔。
     *
     * @return 是否修改了线路参数
     */
    public static boolean applyOneFix(
            PowerLineFootprint line,
            PowerLineValidationReport report,
            PowerLineGenerationResult result,
            PoleDesignResolver designResolver) {
        if (line == null || report == null || !hasTerrainIssues(report)) {
            return false;
        }
        if (tryTallerTower(line, report, result, designResolver)) {
            return true;
        }
        if (tryRaisePoleHeight(line)) {
            return true;
        }
        return tryInsertPole(line, report, result);
    }

    public static boolean hasTerrainIssues(PowerLineValidationReport report) {
        return countTerrainIssues(report) > 0;
    }

    /** 地形碰撞 / 净空问题数量。 */
    public static int countTerrainIssues(PowerLineValidationReport report) {
        if (report == null) {
            return 0;
        }
        int count = 0;
        for (PowerLineIssue issue : report.getIssues()) {
            if (EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(issue.ruleId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 自动修正结束后的状态文案：完全修复 / 部分修复 / 需手动。
     *
     * @return i18n 状态字符串；未做任何修正且无问题时返回 {@code null}
     */
    public static String resolveStatusMessage(int fixesApplied, int remainingIssues) {
        if (remainingIssues <= 0) {
            return fixesApplied > 0
                ? com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.auto_fixed")
                : null;
        }
        if (fixesApplied > 0) {
            return com.plot.utils.PlotI18n.tr(
                "plugin.powerline.terrain.partially_fixed",
                fixesApplied,
                remainingIssues);
        }
        return com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.manual_needed");
    }

    private static boolean tryTallerTower(
            PowerLineFootprint line,
            PowerLineValidationReport report,
            PowerLineGenerationResult result,
            PoleDesignResolver designResolver) {
        if (!line.hasTowerFamily() || designResolver == null || result == null) {
            return false;
        }
        TowerFamily family = new TowerFamilyResolver().find(line.getTowerFamilyId());
        if (family == null) {
            return false;
        }
        List<String> resolvedIds = new ArrayList<>();
        for (var placement : result.polePlacements) {
            resolvedIds.add(placement.resolvedDesignId());
        }
        LineOptimizationEngine.PowerLineGeometrySites sites =
            new LineOptimizationEngine.PowerLineGeometrySites(result.poleSites, resolvedIds);
        OptimizationResult optimization = LineOptimizationEngine.propose(
            report,
            sites,
            line,
            SAFETY_MARGIN_BLOCKS,
            com.plot.plugin.powerline.engineering.validation.ValidationLimits.TOWER_PREFERRED_HEIGHT_MARGIN,
            designResolver);
        for (OptimizationAction action : optimization.getActions()) {
            if (action.getType() == OptimizationActionType.SELECT_TALLER_TOWER
                    && action.getProposedDesignId() != null) {
                PowerLineOverrideUtils.setDesignOverride(
                    line,
                    action.getStationing(),
                    action.getProposedDesignId());
                return true;
            }
        }
        return false;
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
            PowerLineValidationReport report,
            PowerLineGenerationResult result) {
        SpanAnalysis targetSpan = firstTerrainSpan(report);
        if (targetSpan == null) {
            return false;
        }
        double stationing = stationingForSpan(line, targetSpan, result);
        if (stationing < 0 || hasNearbyConstraint(line, stationing)) {
            return false;
        }
        line.addLayoutConstraint(new PoleLayoutConstraint(
            stationing,
            "plugin.powerline.route.auto_pole.reason.terrain"));
        return true;
    }

    private static SpanAnalysis firstTerrainSpan(PowerLineValidationReport report) {
        for (SpanAnalysis span : report.getSpans()) {
            for (PowerLineIssue issue : span.getIssues()) {
                if (EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(issue.ruleId())) {
                    return span;
                }
            }
        }
        return null;
    }

    private static double stationingForSpan(
            PowerLineFootprint line,
            SpanAnalysis span,
            PowerLineGenerationResult result) {
        if (result != null
                && span.getStartPoleSiteId() != null
                && span.getEndPoleSiteId() != null) {
            com.plot.plugin.powerline.model.PowerPoleSite start = null;
            com.plot.plugin.powerline.model.PowerPoleSite end = null;
            for (var site : result.poleSites) {
                if (span.getStartPoleSiteId().equals(site.getId())) {
                    start = site;
                }
                if (span.getEndPoleSiteId().equals(site.getId())) {
                    end = site;
                }
            }
            if (start != null && end != null) {
                return (start.getStationing() + end.getStationing()) * 0.5;
            }
        }
        Vec2d midpoint = midpointAlongPath(line.getPathPoints());
        return PowerPoleLayoutUtils.computeStationing(line.getPathPoints(), midpoint);
    }

    private static Vec2d midpointAlongPath(List<Vec2d> pathPoints) {
        double total = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            total += pathPoints.get(i - 1).distance(pathPoints.get(i));
        }
        double half = total * 0.5;
        double walked = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d a = pathPoints.get(i - 1);
            Vec2d b = pathPoints.get(i);
            double segment = a.distance(b);
            if (walked + segment >= half) {
                double t = segment > 0 ? (half - walked) / segment : 0.0;
                return new Vec2d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
            }
            walked += segment;
        }
        return pathPoints.get(pathPoints.size() - 1).copy();
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
