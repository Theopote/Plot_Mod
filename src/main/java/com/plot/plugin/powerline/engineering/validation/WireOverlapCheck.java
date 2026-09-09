package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.analysis.PoleSiteAnalysis;
import com.plot.plugin.powerline.model.PowerPoleSite;

/** 不同相导线明显重叠。 */
public final class WireOverlapCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, PowerLineValidationReport report) {
        if (context.geometry() == null || context.limits() == null) {
            return;
        }
        for (int i = 0; i < context.geometry().getSites().size(); i++) {
            if (i >= context.geometry().getPlacements().size()) {
                continue;
            }
            PowerPoleSite site = context.geometry().getSites().get(i);
            PolePlacement placement = context.geometry().getPlacements().get(i);
            PoleSiteAnalysis poleAnalysis = findOrCreatePoleAnalysis(
                context.geometry(), i, site, report);
            checkOverlap(placement, site, context.limits().wireOverlapThreshold(), poleAnalysis);
        }
    }

    private static void checkOverlap(
            PolePlacement placement,
            PowerPoleSite site,
            double overlapThreshold,
            PoleSiteAnalysis poleAnalysis) {
        if (placement.attachments() == null || placement.attachments().size() < 2) {
            return;
        }
        for (int a = 0; a < placement.attachments().size(); a++) {
            ResolvedAttachment left = placement.attachments().get(a);
            if (!isPhaseOrGround(left.role())) {
                continue;
            }
            for (int b = a + 1; b < placement.attachments().size(); b++) {
                ResolvedAttachment right = placement.attachments().get(b);
                if (!isPhaseOrGround(right.role()) || sameConductorGroup(left, right)) {
                    continue;
                }
                double distance = distance3d(left, right);
                if (distance < overlapThreshold) {
                    String ruleId = left.role() == AttachmentRole.GROUND_WIRE
                            || right.role() == AttachmentRole.GROUND_WIRE
                        ? EngineeringRuleIds.CONDUCTOR_SEPARATION_GROUND
                        : EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE;
                    poleAnalysis.addIssue(new SimplePowerLineIssue(
                        ruleId,
                        PowerLineIssueSeverity.WARNING,
                        ruleId,
                        PowerLineIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                        distance,
                        overlapThreshold,
                        left.id(),
                        right.id()));
                }
            }
        }
    }

    private static PoleSiteAnalysis findOrCreatePoleAnalysis(
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

    private static boolean sameConductorGroup(ResolvedAttachment left, ResolvedAttachment right) {
        if (left.role() != right.role()) {
            return false;
        }
        return left.role() == AttachmentRole.PHASE_A
            || left.role() == AttachmentRole.PHASE_B
            || left.role() == AttachmentRole.PHASE_C
            || left.role() == AttachmentRole.GROUND_WIRE;
    }

    private static boolean isPhaseOrGround(AttachmentRole role) {
        return role == AttachmentRole.PHASE_A
            || role == AttachmentRole.PHASE_B
            || role == AttachmentRole.PHASE_C
            || role == AttachmentRole.NEUTRAL
            || role == AttachmentRole.GROUND_WIRE;
    }

    private static double distance3d(ResolvedAttachment a, ResolvedAttachment b) {
        double dx = a.worldX() - b.worldX();
        double dy = a.conductorWorldY() - b.conductorWorldY();
        double dz = a.worldZ() - b.worldZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
