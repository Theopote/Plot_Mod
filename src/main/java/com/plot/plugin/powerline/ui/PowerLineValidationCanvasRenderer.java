package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.PowerLineIssue;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
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

/** 线路检查结果的画布叠加层（绿/黄/红 + 净空关键点）。 */
public final class PowerLineValidationCanvasRenderer {
    private static final float SPAN_THICKNESS = 3.5f;
    private static final float POLE_RADIUS = 6.0f;
    private static final int PASS_COLOR = PluginUiColors.STATUS_OK;
    private static final int WARNING_COLOR = PluginUiColors.WARNING;
    private static final int ERROR_COLOR = PluginUiColors.ERROR;

    private PowerLineValidationCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            PowerLinePluginState state,
            PowerLineFootprint line) {
        if (drawList == null || camera == null || state == null || line == null) {
            return;
        }
        PowerLineValidationUiState validation = state.getValidationState();
        if (!line.isLineChecksEnabled() || !validation.isOverlayEnabled()) {
            return;
        }
        PowerLineValidationReport report = validation.getLastEngineeringReport();
        PowerLinePreviewKey previewKey = state.getPreviewKey();
        PowerLineAnalysisKey reportKey = validation.getEngineeringReportKey();
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
        Map<String, PowerLineIssueSeverity> poleSeverity = poleSeverityBySiteId(report);
        Map<String, PowerLineIssueSeverity> spanSeverity = spanSeverityByPolePair(report, geometry);

        renderSpans(drawList, camera, geometry, spanSeverity);
        renderPoles(drawList, camera, geometry.getSites(), poleSeverity);
        renderCriticalPoints(drawList, camera, report);
    }

    private static Map<String, PowerLineIssueSeverity> poleSeverityBySiteId(PowerLineValidationReport report) {
        Map<String, PowerLineIssueSeverity> severity = new HashMap<>();
        for (PoleSiteAnalysis pole : report.getPoles()) {
            severity.put(pole.getPoleSiteId(), worstSeverity(pole.getIssues()));
        }
        return severity;
    }

    private static Map<String, PowerLineIssueSeverity> spanSeverityByPolePair(
            PowerLineValidationReport report,
            PowerLineGeometryModel geometry) {
        Map<String, PowerLineIssueSeverity> bySpanId = spanSeverityById(report);
        Map<String, PowerLineIssueSeverity> byPolePair = new HashMap<>();
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            String pairKey = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            PowerLineIssueSeverity severity = bySpanId.getOrDefault(span.getSpanId(), PowerLineIssueSeverity.INFO);
            PowerLineIssueSeverity existing = byPolePair.get(pairKey);
            byPolePair.put(pairKey, worstOf(existing, severity));
        }
        return byPolePair;
    }

    private static PowerLineIssueSeverity worstOf(PowerLineIssueSeverity left, PowerLineIssueSeverity right) {
        if (left == PowerLineIssueSeverity.ERROR || right == PowerLineIssueSeverity.ERROR) {
            return PowerLineIssueSeverity.ERROR;
        }
        if (left == PowerLineIssueSeverity.WARNING || right == PowerLineIssueSeverity.WARNING) {
            return PowerLineIssueSeverity.WARNING;
        }
        return PowerLineIssueSeverity.INFO;
    }

    private static Map<String, PowerLineIssueSeverity> spanSeverityById(PowerLineValidationReport report) {
        Map<String, PowerLineIssueSeverity> severity = new HashMap<>();
        for (SpanAnalysis span : report.getSpans()) {
            severity.put(span.getId(), worstSeverity(span.getIssues()));
        }
        return severity;
    }

    private static PowerLineIssueSeverity worstSeverity(List<PowerLineIssue> issues) {
        PowerLineIssueSeverity worst = PowerLineIssueSeverity.INFO;
        for (PowerLineIssue issue : issues) {
            if (issue.severity() == PowerLineIssueSeverity.ERROR) {
                return PowerLineIssueSeverity.ERROR;
            }
            if (issue.severity() == PowerLineIssueSeverity.WARNING) {
                worst = PowerLineIssueSeverity.WARNING;
            }
        }
        return worst;
    }

    private static void renderSpans(
            ImDrawList drawList,
            CanvasCamera camera,
            PowerLineGeometryModel geometry,
            Map<String, PowerLineIssueSeverity> spanSeverity) {
        Map<String, ConductorSpanGeometry> uniqueSpans = new LinkedHashMap<>();
        for (ConductorSpanGeometry span : geometry.getConductorSpans()) {
            String key = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            uniqueSpans.putIfAbsent(key, span);
        }
        for (ConductorSpanGeometry span : uniqueSpans.values()) {
            String pairKey = span.getStartPoleIndex() + ":" + span.getEndPoleIndex();
            PowerLineIssueSeverity severity = spanSeverity.getOrDefault(pairKey, PowerLineIssueSeverity.INFO);
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
            Map<String, PowerLineIssueSeverity> poleSeverity) {
        for (int i = 0; i < sites.size(); i++) {
            PowerPoleSite site = sites.get(i);
            PowerLineIssueSeverity severity = poleSeverity.getOrDefault(site.getId(), PowerLineIssueSeverity.INFO);
            int color = colorFor(severity);
            Vec2d screen = camera.worldToScreen(site.getPlanPosition());
            float x = (float) screen.x;
            float y = (float) screen.y;
            drawList.addTriangleFilled(x, y - POLE_RADIUS, x - POLE_RADIUS, y + POLE_RADIUS, x + POLE_RADIUS, y + POLE_RADIUS, color);
            drawList.addTriangle(x, y - POLE_RADIUS, x - POLE_RADIUS, y + POLE_RADIUS, x + POLE_RADIUS, y + POLE_RADIUS, PluginUiColors.RING_DARK, 1.5f);
        }
    }

    private static void renderCriticalPoints(ImDrawList drawList, CanvasCamera camera, PowerLineValidationReport report) {
        for (PowerLineIssue issue : report.getIssues()) {
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

    private static int colorFor(PowerLineIssueSeverity severity) {
        return switch (severity) {
            case ERROR -> ERROR_COLOR;
            case WARNING -> WARNING_COLOR;
            default -> PASS_COLOR;
        };
    }
}
