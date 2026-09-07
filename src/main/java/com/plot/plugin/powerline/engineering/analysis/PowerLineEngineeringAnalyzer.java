package com.plot.plugin.powerline.engineering.analysis;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringIssueLocation;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.SimpleEngineeringIssue;
import com.plot.plugin.powerline.engineering.clearance.ClearanceAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.road.terrain.TerrainSampler;

/** 只读工程分析器（不修改模型）。 */
public final class PowerLineEngineeringAnalyzer {
    private PowerLineEngineeringAnalyzer() {
    }

    public static LineEngineeringReport analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile) {
        LineEngineeringReport report = new LineEngineeringReport();
        if (geometry == null || profile == null) {
            return report;
        }
        report.setProfileId(profile.getId());
        report.setProfileName(profile.getName());

        analyzeSpans(geometry, terrain, profile, report);
        analyzePoles(geometry, terrain, profile, report);
        return report;
    }

    private static void analyzeSpans(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            LineEngineeringReport report) {
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            SpanAnalysis spanAnalysis = new SpanAnalysis();
            spanAnalysis.setId(span.getSpanId());
            spanAnalysis.setStartPoleSiteId(span.getStartPoleSiteId());
            spanAnalysis.setEndPoleSiteId(span.getEndPoleSiteId());
            spanAnalysis.setHorizontalLength(span.getSpanLength());

            if (span.getStartPoleIndex() >= 0
                    && span.getEndPoleIndex() < geometry.getPlacements().size()) {
                PolePlacement start = geometry.getPlacements().get(span.getStartPoleIndex());
                PolePlacement end = geometry.getPlacements().get(span.getEndPoleIndex());
                spanAnalysis.setElevationDifference(
                    Math.abs(start.legacyWireHangY() - end.legacyWireHangY()));
            }

            if (span.getSpanLength() > profile.getSpan().getMaximumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    EngineeringSeverity.ERROR,
                    String.format(
                        "Span length %.1f exceeds maximum %.1f blocks",
                        span.getSpanLength(),
                        profile.getSpan().getMaximumSpan()),
                    EngineeringIssueLocation.at(midpoint(span), 0.0),
                    span.getSpanLength(),
                    profile.getSpan().getMaximumSpan()));
            } else if (span.getSpanLength() < profile.getSpan().getMinimumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MINIMUM,
                    EngineeringSeverity.WARNING,
                    String.format(
                        "Span length %.1f below minimum %.1f blocks",
                        span.getSpanLength(),
                        profile.getSpan().getMinimumSpan()),
                    EngineeringIssueLocation.at(midpoint(span), 0.0),
                    span.getSpanLength(),
                    profile.getSpan().getMinimumSpan()));
            }

            if (terrain != null && !span.getSamples().isEmpty()) {
                ClearanceAnalysis clearance = ClearanceChecker.analyzeSpan(span, terrain);
                spanAnalysis.setMinimumGroundClearance(clearance.getMinimumClearance());
                EngineeringIssue clearanceIssue = ClearanceChecker.toIssue(
                    clearance,
                    profile.getClearance().getMinimumGroundClearance(),
                    EngineeringSeverity.ERROR);
                if (clearanceIssue != null) {
                    spanAnalysis.addIssue(clearanceIssue);
                }
            }

            report.addSpan(spanAnalysis);
        }
    }

    private static void analyzePoles(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            LineEngineeringReport report) {
        for (int i = 0; i < geometry.getSites().size(); i++) {
            PowerPoleSite site = geometry.getSites().get(i);
            PoleSiteAnalysis poleAnalysis = new PoleSiteAnalysis();
            poleAnalysis.setPoleSiteId(site.getId());
            poleAnalysis.setRole(site.getRole());
            poleAnalysis.setStationing(site.getStationing());
            poleAnalysis.setDeflectionAngle(site.getDeflectionAngle());

            if (i < geometry.getPlacements().size()) {
                poleAnalysis.setResolvedDesignId(geometry.getPlacements().get(i).resolvedDesignId());
            }

            if (site.getRole() == TowerRole.SUSPENSION
                    && site.getDeflectionAngle() > profile.getAngle().getSuspensionMaxAngle()
                    && i > 0
                    && i < geometry.getSites().size() - 1) {
                poleAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.TOWER_ROLE_ANGLE,
                    EngineeringSeverity.WARNING,
                    String.format(
                        "Suspension tower at %.0f° route deflection (max %.0f°)",
                        site.getDeflectionAngle(),
                        profile.getAngle().getSuspensionMaxAngle()),
                    EngineeringIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                    site.getDeflectionAngle(),
                    profile.getAngle().getSuspensionMaxAngle()));
            }

            if (i < geometry.getPlacements().size()) {
                checkAttachmentSeparation(
                    geometry.getPlacements().get(i),
                    site,
                    profile,
                    poleAnalysis);
            }

            if (terrain != null && i < geometry.getPlacements().size()) {
                PolePlacement placement = geometry.getPlacements().get(i);
                if (placement.design() != null) {
                    checkUnevenBase(site, placement, terrain, profile, poleAnalysis);
                }
            }

            report.addPole(poleAnalysis);
        }
    }

    private static void checkAttachmentSeparation(
            PolePlacement placement,
            PowerPoleSite site,
            EngineeringRuleProfile profile,
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
                if (!isPhaseOrGround(right.role())) {
                    continue;
                }
                double distance = distance3d(left, right);
                double required = separationRequired(left.role(), right.role(), profile);
                if (distance < required) {
                    String ruleId = left.role() == AttachmentRole.GROUND_WIRE
                            || right.role() == AttachmentRole.GROUND_WIRE
                        ? EngineeringRuleIds.CONDUCTOR_SEPARATION_GROUND
                        : EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE;
                    poleAnalysis.addIssue(new SimpleEngineeringIssue(
                        ruleId,
                        EngineeringSeverity.WARNING,
                        String.format(
                            "Attachment separation %.1f < required %.1f blocks (%s ↔ %s)",
                            distance,
                            required,
                            left.id(),
                            right.id()),
                        EngineeringIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                        distance,
                        required));
                }
            }
        }
    }

    private static void checkUnevenBase(
            PowerPoleSite site,
            PolePlacement placement,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            PoleSiteAnalysis poleAnalysis) {
        int groundY = terrain.sampleSurfaceY(site.getPlanPosition());
        int hangY = placement.usesAttachmentConductors()
            ? (int) Math.round(maxConductorY(placement))
            : placement.legacyWireHangY();
        double unevenness = Math.abs(hangY - groundY - placement.design().totalHeight());
        if (unevenness > profile.getTower().getMaximumBaseUnevenness()) {
            poleAnalysis.addIssue(new SimpleEngineeringIssue(
                EngineeringRuleIds.TOWER_BASE_UNEVEN,
                EngineeringSeverity.WARNING,
                String.format(
                    "Uneven tower base: ground offset variation %.1f blocks",
                    unevenness),
                EngineeringIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                unevenness,
                profile.getTower().getMaximumBaseUnevenness()));
        }
    }

    private static double maxConductorY(PolePlacement placement) {
        double max = 0.0;
        for (ResolvedAttachment attachment : placement.attachments()) {
            max = Math.max(max, attachment.conductorWorldY());
        }
        return max;
    }

    private static double separationRequired(
            AttachmentRole left,
            AttachmentRole right,
            EngineeringRuleProfile profile) {
        if (left == AttachmentRole.GROUND_WIRE || right == AttachmentRole.GROUND_WIRE) {
            return profile.getClearance().getMinimumGroundWireSeparation();
        }
        return profile.getClearance().getMinimumConductorSeparation();
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

    private static Vec2d midpoint(ConductorSpanGeometry span) {
        if (span.getSamples().isEmpty()) {
            return new Vec2d(0, 0);
        }
        int mid = span.getSamples().size() / 2;
        return span.getSamples().get(mid).planPoint().copy();
    }
}
