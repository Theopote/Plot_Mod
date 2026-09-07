package com.plot.plugin.powerline.engineering.optimization;

import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.engineering.selection.AutomaticTowerSelector;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

/** 基于工程报告提出优化方案（不自动应用）。 */
public final class LineOptimizationEngine {
    private LineOptimizationEngine() {
    }

    public static OptimizationResult propose(
            LineEngineeringReport report,
            PowerLineGeometrySites sites,
            PowerLineFootprint footprint,
            EngineeringRuleProfile profile,
            PoleDesignResolver designResolver) {
        OptimizationResult result = new OptimizationResult();
        if (report == null || footprint == null || profile == null) {
            return result;
        }

        TowerFamily family = footprint.hasTowerFamily()
            ? new TowerFamilyResolver().find(footprint.getTowerFamilyId())
            : null;
        AutomaticTowerSelector selector = designResolver != null
            ? new AutomaticTowerSelector(designResolver)
            : null;

        for (SpanAnalysis span : report.getSpans()) {
            for (var issue : span.getIssues()) {
                if (EngineeringRuleIds.SPAN_MAXIMUM.equals(issue.ruleId())) {
                    OptimizationAction action = new OptimizationAction();
                    action.setType(OptimizationActionType.INSERT_POLE);
                    action.setSpanId(span.getId());
                    action.setStationing(span.getHorizontalLength() * 0.5);
                    action.setMessage(String.format(
                        "Insert pole near station %.0f to split span %.0f blocks",
                        action.getStationing(),
                        span.getHorizontalLength()));
                    result.addAction(action);
                    result.setEstimatedErrorsResolved(result.getEstimatedErrorsResolved() + 1);
                } else if (EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(issue.ruleId())
                        && selector != null
                        && family != null
                        && sites != null) {
                    proposeTallerTower(result, span, sites, family, profile, selector, issue.location().stationing());
                }
            }
        }

        result.setRemainingErrors(Math.max(0, report.errorCount() - result.getEstimatedErrorsResolved()));
        return result;
    }

    private static void proposeTallerTower(
            OptimizationResult result,
            SpanAnalysis span,
            PowerLineGeometrySites sites,
            TowerFamily family,
            EngineeringRuleProfile profile,
            AutomaticTowerSelector selector,
            double stationingHint) {
        PowerPoleSite site = sites.findNearest(stationingHint);
        if (site == null || site.getPoleDesignOverrideId() != null) {
            OptimizationAction review = new OptimizationAction();
            review.setType(OptimizationActionType.MANUAL_REVIEW);
            review.setSpanId(span.getId());
            review.setMessage("Manual design override — review clearance manually");
            result.addAction(review);
            return;
        }

        TowerSelectionContext context = new TowerSelectionContext();
        context.setSite(site);
        context.setFamily(family);
        context.setProfile(profile);
        context.setDeflectionAngle(site.getDeflectionAngle());
        context.setRequiredGroundClearance(profile.getClearance().getMinimumGroundClearance());
        context.setRequiredAttachmentHeight(
            profile.getClearance().getMinimumGroundClearance()
                + profile.getTower().getPreferredHeightMargin()
                + 12.0);

        TowerSelectionResult selection = selector.select(context);
        if (!selection.hasSelection()) {
            return;
        }

        OptimizationAction action = new OptimizationAction();
        action.setType(OptimizationActionType.SELECT_TALLER_TOWER);
        action.setPoleSiteId(site.getId());
        action.setPoleIndex(indexOf(sites.sites, site) + 1);
        action.setStationing(site.getStationing());
        action.setSpanId(span.getId());
        action.setCurrentDesignId(currentDesignId(site, sites));
        action.setProposedDesignId(selection.getSelectedDesignId());
        action.setMessage("clearance");
        result.addAction(action);
        result.setEstimatedErrorsResolved(result.getEstimatedErrorsResolved() + 1);
    }

    /** 优化器使用的站点索引（避免循环依赖 geometry 包）。 */
    public static final class PowerLineGeometrySites {
        private final java.util.List<PowerPoleSite> sites;
        private final java.util.List<String> resolvedDesignIds;

        public PowerLineGeometrySites(java.util.List<PowerPoleSite> sites) {
            this(sites, java.util.List.of());
        }

        public PowerLineGeometrySites(
                java.util.List<PowerPoleSite> sites,
                java.util.List<String> resolvedDesignIds) {
            this.sites = sites != null ? sites : java.util.List.of();
            this.resolvedDesignIds = resolvedDesignIds != null ? resolvedDesignIds : java.util.List.of();
        }

        public java.util.List<PowerPoleSite> sites() {
            return sites;
        }

        public String resolvedDesignId(int index) {
            return index >= 0 && index < resolvedDesignIds.size() ? resolvedDesignIds.get(index) : null;
        }

        public PowerPoleSite findNearest(double stationing) {
            PowerPoleSite best = null;
            double bestDistance = Double.MAX_VALUE;
            for (PowerPoleSite site : sites) {
                double distance = Math.abs(site.getStationing() - stationing);
                if (distance < bestDistance) {
                    best = site;
                    bestDistance = distance;
                }
            }
            return best;
        }
    }

    private static int indexOf(java.util.List<PowerPoleSite> sites, PowerPoleSite site) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i) == site || sites.get(i).getId().equals(site.getId())) {
                return i;
            }
        }
        return 0;
    }

    private static String currentDesignId(PowerPoleSite site, PowerLineGeometrySites sites) {
        if (site.getPoleDesignOverrideId() != null) {
            return site.getPoleDesignOverrideId();
        }
        int index = indexOf(sites.sites, site);
        return sites.resolvedDesignId(index);
    }
}
