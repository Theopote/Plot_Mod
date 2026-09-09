package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.analysis.PoleSiteAnalysis;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;

/** 悬垂塔处于大转角（应使用转角塔）。 */
public final class CornerCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, PowerLineValidationReport report) {
        if (context.geometry() == null || context.limits() == null) {
            return;
        }
        for (int i = 0; i < context.geometry().getSites().size(); i++) {
            PowerPoleSite site = context.geometry().getSites().get(i);
            PoleSiteAnalysis poleAnalysis = beginPoleAnalysis(context.geometry(), i, site, report);
            if (site.getRole() == TowerRole.SUSPENSION
                    && site.getDeflectionAngle() > context.limits().suspensionAngleWarning()
                    && i > 0
                    && i < context.geometry().getSites().size() - 1) {
                poleAnalysis.addIssue(new SimplePowerLineIssue(
                    EngineeringRuleIds.TOWER_ROLE_ANGLE,
                    PowerLineIssueSeverity.WARNING,
                    EngineeringRuleIds.TOWER_ROLE_ANGLE,
                    PowerLineIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                    site.getDeflectionAngle(),
                    context.limits().suspensionAngleWarning()));
            }
        }
    }

    private static PoleSiteAnalysis beginPoleAnalysis(
            com.plot.plugin.powerline.geometry.PowerLineGeometryModel geometry,
            int index,
            PowerPoleSite site,
            PowerLineValidationReport report) {
        for (PoleSiteAnalysis existing : report.getPoles()) {
            if (site.getId() != null && site.getId().equals(existing.getPoleSiteId())) {
                return existing;
            }
        }
        PoleSiteAnalysis poleAnalysis = new PoleSiteAnalysis();
        poleAnalysis.setPoleSiteId(site.getId());
        poleAnalysis.setRole(site.getRole());
        poleAnalysis.setStationing(site.getStationing());
        poleAnalysis.setDeflectionAngle(site.getDeflectionAngle());
        if (index < geometry.getPlacements().size()) {
            poleAnalysis.setResolvedDesignId(geometry.getPlacements().get(index).resolvedDesignId());
        }
        report.addPole(poleAnalysis);
        return poleAnalysis;
    }
}
