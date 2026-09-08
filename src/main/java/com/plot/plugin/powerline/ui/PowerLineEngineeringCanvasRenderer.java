package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.PoleSiteAnalysis;
import com.plot.plugin.powerline.engineering.analysis.SpanAnalysis;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工程分析结果的画布叠加层（绿/黄/红 + 净空关键点）。 */
public final class PowerLineEngineeringCanvasRenderer {
    private static final float SPAN_THICKNESS = 3.5f;
    private static final float POLE_RADIUS = 6.0f;
    private static final int PASS_COLOR = PluginUiColors.STATUS_OK;
    private static final int WARNING_COLOR = PluginUiColors.WARNING;
    private static final int ERROR_COLOR = PluginUiColors.ERROR;

    private PowerLineEngineeringCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            PowerLinePluginState state,
            PowerLineFootprint line) {
        if (drawList == null || camera == null || state == null || line == null) {
            return;
        }
        PowerLineEngineeringUiState engineering = state.getEngineeringState();
        if (!line.isEngineeringAnalysisEnabled() || !engineering.isOverlayEnabled()) {
            return;
        }
        LineEngineeringReport report = engineering.getLastEngineeringReport();
        PowerLinePreviewKey previewKey = state.getPreviewKey();
        PowerLineAnalysisKey reportKey = engineering.getEngineeringReportKey();
        if (report == null
                || reportKey == null
                || previewKey == null
                || !reportKey.matches(line, state.getDesignProject(), previewKey)) {
            return;
        }
        PowerLineGenerationResult generation = state.getLastGenerationResult();
        if (generation == null || generation.footprint == null
                || !line.getId().equals(generation.footprint.getId())) {
            return;
        }

        PowerLineGeometryModel geometry = generation.toGeometryModel();
        Map<String, EngineeringSeverity> poleSeverity = poleSeverityBySiteId(report);
        Map<String, EngineeringSeverity> spanSeverity = spanSeverityByPolePair(report, geometry);

        renderSpans(drawList, camera, geometry, spanSeverity);
        renderPoles(drawList, camera, geometry.getSites(), poleSeverity);
        renderCriticalPoints(drawList, camera, report);
    }

    private static Map<String, EngineeringSeverity> poleSeverityBySiteId(LineEngineeringReport report) {
        Map<String, EngineeringSeverity> severity = new HashMap<>();
        for (PoleSiteAnalysis pole : report.getPoles()) {
            severity.put(pole.getPoleSiteId(), worstSeverity(pole.getIssues()));
        }
        return severity;
    }

    private static Map<String, EngineeringSeverity> spanSeverityByPolePair(
            LineEngineeringReport report,
            PowerLineGeometryModel geometry) {
        Map<String, EngineeringSeverity> bySpanId = spanSeverityById(report);
        Map<String, EngineeringSeverity> byPolePair = new HashMap<>();
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            String pairKey = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            EngineeringSeverity severity = bySpanId.getOrDefault(span.getSpanId(), EngineeringSeverity.INFO);
            EngineeringSeverity existing = byPolePair.get(pairKey);
            byPolePair.put(pairKey, worstOf(existing, severity));
        }
        return byPolePair;
    }

    private static EngineeringSeverity worstOf(EngineeringSeverity left, EngineeringSeverity right) {
        if (left == EngineeringSeverity.ERROR || right == EngineeringSeverity.ERROR) {
            return EngineeringSeverity.ERROR;
        }
        if (left == EngineeringSeverity.WARNING || right == EngineeringSeverity.WARNING) {
            return EngineeringSeverity.WARNING;
        }
        return EngineeringSeverity.INFO;
    }

    private static Map<String, EngineeringSeverity> spanSeverityById(LineEngineeringReport report) {
        Map<String, EngineeringSeverity> severity = new HashMap<>();
        for (SpanAnalysis span : report.getSpans()) {
            severity.put(span.getId(), worstSeverity(span.getIssues()));
        }
        return severity;
    }

    private static EngineeringSeverity worstSeverity(List<EngineeringIssue> issues) {
        EngineeringSeverity worst = EngineeringSeverity.INFO;
        for (EngineeringIssue issue : issues) {
            if (issue.severity() == EngineeringSeverity.ERROR) {
                return EngineeringSeverity.ERROR;
            }
            if (issue.severity() == EngineeringSeverity.WARNING) {
                worst = EngineeringSeverity.WARNING;
            }
        }
        return worst;
    }

    private static void renderSpans(
            ImDrawList drawList,
            CanvasCamera camera,
            PowerLineGeometryModel geometry,
            Map<String, EngineeringSeverity> spanSeverity) {
        Map<String, ConductorSpanGeometry> uniqueSpans = new LinkedHashMap<>();
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            String key = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            uniqueSpans.putIfAbsent(key, span);
        }
        for (ConductorSpanGeometry span : uniqueSpans.values()) {
            String pairKey = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            EngineeringSeverity severity = spanSeverity.getOrDefault(pairKey, EngineeringSeverity.INFO);
            int color = colorFor(severity);
            drawSpanPath(drawList, camera, span, color);
        }
    }

    private static void drawSpanPath(
            ImDrawList drawList,
            CanvasCamera camera,
            ConductorSpanGeometry span,
            int color) {
        List<ConductorSample> samples = span.getSamples();
        if (samples.size() < 2) {
            return;
        }
        for (int i = 0; i < samples.size() - 1; i++) {
            Vec2d a = samples.get(i).planPoint();
            Vec2d b = samples.get(i + 1).planPoint();
            Vec2d screenA = camera.worldToScreen(a);
            Vec2d screenB = camera.worldToScreen(b);
            drawList.addLine(
                (float) screenA.x,
                (float) screenA.y,
                (float) screenB.x,
                (float) screenB.y,
                color,
                SPAN_THICKNESS);
        }
    }

    private static void renderPoles(
            ImDrawList drawList,
            CanvasCamera camera,
            List<PowerPoleSite> sites,
            Map<String, EngineeringSeverity> poleSeverity) {
        for (int i = 0; i < sites.size(); i++) {
            PowerPoleSite site = sites.get(i);
            EngineeringSeverity severity = poleSeverity.getOrDefault(site.getId(), EngineeringSeverity.INFO);
            int color = colorFor(severity);
            Vec2d screen = camera.worldToScreen(site.getPlanPosition());
            float x = (float) screen.x;
            float y = (float) screen.y;
            drawList.addTriangleFilled(x, y - POLE_RADIUS, x - POLE_RADIUS, y + POLE_RADIUS, x + POLE_RADIUS, y + POLE_RADIUS, color);
            drawList.addTriangle(x, y - POLE_RADIUS, x - POLE_RADIUS, y + POLE_RADIUS, x + POLE_RADIUS, y + POLE_RADIUS, PluginUiColors.RING_DARK, 1.5f);
        }
    }

    private static void renderCriticalPoints(ImDrawList drawList, CanvasCamera camera, LineEngineeringReport report) {
        for (EngineeringIssue issue : report.getIssues()) {
            if (!EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(issue.ruleId())) {
                continue;
            }
            Vec2d plan = issue.location().planPoint();
            Vec2d screen = camera.worldToScreen(plan);
            float x = (float) screen.x;
            float y = (float) screen.y;
            drawList.addText(x - 4f, y - 14f, ERROR_COLOR, "!");
            String label = String.format("%.1f / %.1f", issue.actual(), issue.required());
            drawList.addText(x + 6f, y - 6f, PluginUiColors.HINT_GRAY, label);
        }
    }

    private static int colorFor(EngineeringSeverity severity) {
        return switch (severity) {
            case ERROR -> ERROR_COLOR;
            case WARNING -> WARNING_COLOR;
            default -> PASS_COLOR;
        };
    }
}
