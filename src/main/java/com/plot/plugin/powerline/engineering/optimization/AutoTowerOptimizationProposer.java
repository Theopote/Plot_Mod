package com.plot.plugin.powerline.engineering.optimization;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.engineering.PowerLineValidationI18n;
import com.plot.plugin.powerline.engineering.selection.AutomaticTowerSelector;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionResult;
import com.plot.plugin.powerline.engineering.validation.ValidationLimits;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

/** 为整条线路提出自动选塔方案（不修改模型）。 */
public final class AutoTowerOptimizationProposer {
    private AutoTowerOptimizationProposer() {
    }

    public static OptimizationResult propose(
            PowerLineGenerationResult generation,
            PowerLineFootprint footprint,
            PoleDesignResolver designResolver) {
        OptimizationResult result = new OptimizationResult();
        if (generation == null || footprint == null || !footprint.isAutomaticTowerSelectionEnabled()) {
            return result;
        }
        if (!footprint.hasTowerFamily() || designResolver == null) {
            return result;
        }

        var family = new TowerFamilyResolver().find(footprint.getTowerFamilyId());
        if (family == null) {
            return result;
        }

        AutomaticTowerSelector selector = new AutomaticTowerSelector(designResolver);
        var sites = generation.poleSites;
        var placements = generation.polePlacements;
        double groundClearance = ValidationLimits.DEFAULT_MIN_GROUND_CLEARANCE;
        double heightMargin = ValidationLimits.TOWER_PREFERRED_HEIGHT_MARGIN;

        for (int i = 0; i < sites.size(); i++) {
            PowerPoleSite site = sites.get(i);
            if (site.getPoleDesignOverrideId() != null && !site.getPoleDesignOverrideId().isBlank()) {
                continue;
            }
            String currentId = i < placements.size() ? placements.get(i).resolvedDesignId() : null;
            TowerSelectionContext context = buildContext(
                site,
                sites,
                i,
                footprint.isClosedLoop(),
                family,
                groundClearance,
                heightMargin);
            TowerSelectionResult selection = selector.select(context);
            if (!selection.hasSelection()) {
                continue;
            }
            String proposedId = selection.getSelectedDesignId();
            if (proposedId == null || proposedId.equals(currentId)) {
                continue;
            }

            OptimizationAction action = new OptimizationAction();
            action.setType(OptimizationActionType.SELECT_TALLER_TOWER);
            action.setPoleSiteId(site.getId());
            action.setPoleIndex(i + 1);
            action.setStationing(site.getStationing());
            action.setCurrentDesignId(currentId);
            action.setProposedDesignId(proposedId);
            action.setMessageKey(buildReasonKey(site, selection), buildReasonArgs(site, selection));
            result.addAction(action);
        }
        return result;
    }

    private static TowerSelectionContext buildContext(
            PowerPoleSite site,
            java.util.List<PowerPoleSite> sites,
            int index,
            boolean closedLoop,
            com.plot.plugin.powerline.design.family.TowerFamily family,
            double groundClearance,
            double heightMargin) {
        TowerSelectionContext context = new TowerSelectionContext();
        context.setSite(site);
        context.setFamily(family);
        context.setDeflectionAngle(site.getDeflectionAngle());
        if (closedLoop && sites.size() > 1) {
            int previousIndex = (index - 1 + sites.size()) % sites.size();
            int nextIndex = (index + 1) % sites.size();
            context.setIncomingSpan(com.plot.plugin.powerline.PowerPoleLayoutUtils.worldSpanBlocks(
                sites.get(previousIndex), site));
            context.setOutgoingSpan(com.plot.plugin.powerline.PowerPoleLayoutUtils.worldSpanBlocks(
                site, sites.get(nextIndex)));
        } else {
            if (index > 0) {
                context.setIncomingSpan(com.plot.plugin.powerline.PowerPoleLayoutUtils.worldSpanBlocks(
                    sites.get(index - 1), site));
            }
            if (index < sites.size() - 1) {
                context.setOutgoingSpan(com.plot.plugin.powerline.PowerPoleLayoutUtils.worldSpanBlocks(
                    site, sites.get(index + 1)));
            }
        }
        context.setRequiredGroundClearance(groundClearance);
        context.setRequiredAttachmentHeight(groundClearance + heightMargin + 12.0);
        return context;
    }

    private static String buildReasonKey(PowerPoleSite site, TowerSelectionResult selection) {
        if (site.getDeflectionAngle() > 5.0) {
            return "plugin.powerline.engineering.reason.deflection";
        }
        if (!selection.getReasons().isEmpty()) {
            return "plugin.powerline.engineering.reason.selection_summary";
        }
        return "plugin.powerline.engineering.reason.suitability";
    }

    private static Object[] buildReasonArgs(PowerPoleSite site, TowerSelectionResult selection) {
        if (site.getDeflectionAngle() > 5.0) {
            return new Object[] {site.getDeflectionAngle()};
        }
        if (!selection.getReasons().isEmpty()) {
            return new Object[] {PowerLineValidationI18n.selectionReasonToken(selection.getReasons().getFirst())};
        }
        return new Object[0];
    }

    public static String designLabel(PoleDesignResolver resolver, String designId) {
        if (designId == null || designId.isBlank()) {
            return "?";
        }
        PoleDesign design = resolver.find(designId);
        return design != null && design.getName() != null ? design.getName() : designId;
    }
}
