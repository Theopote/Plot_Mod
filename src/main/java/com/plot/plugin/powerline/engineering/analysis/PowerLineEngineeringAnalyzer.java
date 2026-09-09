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
import com.plot.plugin.powerline.engineering.validation.ValidationLimits;
import com.plot.plugin.powerline.engineering.clearance.ClearanceAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.road.terrain.TerrainSampler;

/** 只读线路检查器（不修改模型）。 */
public final class PowerLineEngineeringAnalyzer {
    private PowerLineEngineeringAnalyzer() {
    }

    public static LineEngineeringReport analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile) {
        ValidationLimits limits = profile != null
            ? ValidationLimits.fromFootprint(null, profile)
            : null;
        return analyze(geometry, terrain, profile, limits);
    }

    public static LineEngineeringReport analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            ValidationLimits limits) {
        LineEngineeringReport report = new LineEngineeringReport();
        if (geometry == null || profile == null) {
            return report;
        }
        ValidationLimits effectiveLimits = limits != null
            ? limits
            : ValidationLimits.fromFootprint(null, profile);
        report.setProfileId(profile.getId());
        report.setProfileName(profile.getName());

        analyzeSpans(geometry, terrain, profile, effectiveLimits, report);
        analyzePoles(geometry, terrain, profile, effectiveLimits, report);
        return report;
    }

    private static void analyzeSpans(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            ValidationLimits limits,
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

            if (span.getSpanLength() > limits.maximumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    EngineeringSeverity.ERROR,
                    EngineeringRuleIds.SPAN_MAXIMUM,
                    EngineeringIssueLocation.at(midpoint(span), 0.0),
                    span.getSpanLength(),
                    limits.maximumSpan()));
            } else if (span.getSpanLength() < limits.minimumSpan()) {
                spanAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.SPAN_MINIMUM,
                    EngineeringSeverity.WARNING,
                    EngineeringRuleIds.SPAN_MINIMUM,
                    EngineeringIssueLocation.at(midpoint(span), 0.0),
                    span.getSpanLength(),
                    limits.minimumSpan()));
            }

            if (terrain != null && !span.getSamples().isEmpty()) {
                ClearanceAnalysis clearance = ClearanceChecker.analyzeSpan(span, terrain);
                spanAnalysis.setMinimumGroundClearance(clearance.getMinimumClearance());
                EngineeringIssue clearanceIssue = ClearanceChecker.toIssue(
                    clearance,
                    limits.minimumGroundClearance(),
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
            ValidationLimits limits,
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
                    && site.getDeflectionAngle() > limits.suspensionAngleWarning()
                    && i > 0
                    && i < geometry.getSites().size() - 1) {
                poleAnalysis.addIssue(new SimpleEngineeringIssue(
                    EngineeringRuleIds.TOWER_ROLE_ANGLE,
                    EngineeringSeverity.WARNING,
                    EngineeringRuleIds.TOWER_ROLE_ANGLE,
                    EngineeringIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                    site.getDeflectionAngle(),
                    limits.suspensionAngleWarning()));
            }

            if (i < geometry.getPlacements().size()) {
                checkWireOverlap(
                    geometry.getPlacements().get(i),
                    site,
                    limits,
                    poleAnalysis);
            }

            report.addPole(poleAnalysis);
        }
    }

    private static void checkWireOverlap(
            PolePlacement placement,
            PowerPoleSite site,
            ValidationLimits limits,
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
                if (sameConductorGroup(left, right)) {
                    continue;
                }
                double distance = distance3d(left, right);
                if (distance < limits.wireOverlapThreshold()) {
                    String ruleId = left.role() == AttachmentRole.GROUND_WIRE
                            || right.role() == AttachmentRole.GROUND_WIRE
                        ? EngineeringRuleIds.CONDUCTOR_SEPARATION_GROUND
                        : EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE;
                    poleAnalysis.addIssue(new SimpleEngineeringIssue(
                        ruleId,
                        EngineeringSeverity.WARNING,
                        ruleId,
                        EngineeringIssueLocation.at(site.getPlanPosition(), site.getStationing()),
                        distance,
                        limits.wireOverlapThreshold(),
                        left.id(),
                        right.id()));
                }
            }
        }
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

    private static Vec2d midpoint(ConductorSpanGeometry span) {
        if (span.getSamples().isEmpty()) {
            return new Vec2d(0, 0);
        }
        int mid = span.getSamples().size() / 2;
        return span.getSamples().get(mid).planPoint().copy();
    }
}
