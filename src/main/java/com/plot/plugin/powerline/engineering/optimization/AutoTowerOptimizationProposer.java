package com.plot.plugin.powerline.engineering.optimization;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.engineering.selection.AutomaticTowerSelector;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionResult;
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

        var profile = new EngineeringRuleProfileResolver().find(footprint.effectiveEngineeringProfileId());
        AutomaticTowerSelector selector = new AutomaticTowerSelector(designResolver);
        var sites = generation.poleSites;
        var placements = generation.polePlacements;

        for (int i = 0; i < sites.size(); i++) {
            PowerPoleSite site = sites.get(i);
            if (site.getPoleDesignOverrideId() != null && !site.getPoleDesignOverrideId().isBlank()) {
                continue;
            }
            String currentId = i < placements.size() ? placements.get(i).resolvedDesignId() : null;
            TowerSelectionContext context = buildContext(site, sites, i, footprint, family, profile);
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
            action.setMessage(buildReason(site, selection));
            result.addAction(action);
        }
        return result;
    }

    private static TowerSelectionContext buildContext(
            PowerPoleSite site,
            java.util.List<PowerPoleSite> sites,
            int index,
            PowerLineFootprint footprint,
            com.plot.plugin.powerline.design.family.TowerFamily family,
            com.plot.plugin.powerline.engineering.EngineeringRuleProfile profile) {
        TowerSelectionContext context = new TowerSelectionContext();
        context.setSite(site);
        context.setFamily(family);
        context.setProfile(profile);
        context.setDeflectionAngle(site.getDeflectionAngle());
        if (index > 0) {
            context.setIncomingSpan(site.getPlanPosition().distance(sites.get(index - 1).getPlanPosition()));
        }
        if (index < sites.size() - 1) {
            context.setOutgoingSpan(site.getPlanPosition().distance(sites.get(index + 1).getPlanPosition()));
        }
        context.setRequiredGroundClearance(profile.getClearance().getMinimumGroundClearance());
        context.setRequiredAttachmentHeight(
            profile.getClearance().getMinimumGroundClearance()
                + profile.getTower().getPreferredHeightMargin()
                + 12.0);
        return context;
    }

    private static String buildReason(PowerPoleSite site, TowerSelectionResult selection) {
        if (site.getDeflectionAngle() > 5.0) {
            return String.format("%.0f° deflection", site.getDeflectionAngle());
        }
        if (!selection.getReasons().isEmpty()) {
            return selection.getReasons().getFirst();
        }
        return "engineering suitability";
    }

    public static String designLabel(PoleDesignResolver resolver, String designId) {
        if (designId == null || designId.isBlank()) {
            return "?";
        }
        PoleDesign design = resolver.find(designId);
        return design != null && design.getName() != null ? design.getName() : designId;
    }
}
