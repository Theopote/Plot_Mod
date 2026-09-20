package com.plot.plugin.powerline.terrain;

import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.geometry.PowerLineIssue;
import com.plot.plugin.powerline.geometry.PowerLineIssueSeverity;
import com.plot.plugin.powerline.geometry.SpanAnalysis;
import com.plot.plugin.powerline.geometry.clearance.ClearanceAnalysis;
import com.plot.plugin.powerline.geometry.clearance.ClearanceChecker;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 导线与地形的碰撞分析（仅供地形避让算法使用）。 */
public final class TerrainCollisionAnalysis {
    private final List<SpanAnalysis> spans = new ArrayList<>();

    public static TerrainCollisionAnalysis analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain) {
        TerrainCollisionAnalysis analysis = new TerrainCollisionAnalysis();
        if (geometry == null || terrain == null) {
            return analysis;
        }
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            if (span.getSamples().isEmpty()) {
                continue;
            }
            SpanAnalysis spanAnalysis = beginSpanAnalysis(geometry, span);
            ClearanceAnalysis clearance = ClearanceChecker.analyzeSpan(span, terrain);
            spanAnalysis.setMinimumGroundClearance(clearance.getMinimumClearance());
            PowerLineIssue issue = ClearanceChecker.toIssue(
                clearance,
                TerrainFitService.SAFETY_MARGIN_BLOCKS,
                PowerLineIssueSeverity.ERROR);
            if (issue != null) {
                spanAnalysis.addIssue(issue);
            }
            analysis.spans.add(spanAnalysis);
        }
        return analysis;
    }

    public List<SpanAnalysis> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    public List<PowerLineIssue> getIssues() {
        List<PowerLineIssue> issues = new ArrayList<>();
        for (SpanAnalysis span : spans) {
            issues.addAll(span.getIssues());
        }
        return Collections.unmodifiableList(issues);
    }

    public boolean hasVisualConflicts() {
        return !getIssues().isEmpty();
    }

    public int issueCount() {
        return getIssues().size();
    }

    public SpanAnalysis firstIssueSpan() {
        for (SpanAnalysis span : spans) {
            if (!span.getIssues().isEmpty()) {
                return span;
            }
        }
        return null;
    }

    private static SpanAnalysis beginSpanAnalysis(
            PowerLineGeometryModel geometry,
            ConductorSpanGeometry span) {
        SpanAnalysis spanAnalysis = new SpanAnalysis();
        spanAnalysis.setId(span.getSpanId());
        spanAnalysis.setStartPoleSiteId(span.getStartPoleSiteId());
        spanAnalysis.setEndPoleSiteId(span.getEndPoleSiteId());
        spanAnalysis.setHorizontalLength(span.getSpanLength());
        if (span.getStartPoleIndex() >= 0
                && span.getEndPoleIndex() < geometry.getPlacements().size()) {
            PolePlacement start = geometry.getPlacements().get(span.getStartPoleIndex());
            PolePlacement end = geometry.getPlacements().get(span.getEndPoleIndex());
            spanAnalysis.setElevationDifference(Math.abs(
                conductorWorldY(start) - conductorWorldY(end)));
        }
        return spanAnalysis;
    }

    private static double conductorWorldY(PolePlacement placement) {
        if (placement.attachments() != null && !placement.attachments().isEmpty()) {
            return placement.attachments().getFirst().conductorWorldY();
        }
        return placement.legacyWireHangY();
    }
}
