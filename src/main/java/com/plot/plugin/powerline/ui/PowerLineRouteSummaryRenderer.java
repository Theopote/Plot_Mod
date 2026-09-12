package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 线路 Tab 顶部紧凑摘要区。 */
public final class PowerLineRouteSummaryRenderer {
    private PowerLineRouteSummaryRenderer() {
    }

    public static void render(PowerLineUiContext ctx, PowerLineFootprint line) {
        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        int poleCount = line.estimatePoleCount(ctx.coordinates());
        double avgSpacing = typicalSpanBlocks(worldLength, poleCount, line.getMaxPoleSpacing());

        PowerLineStylePreset preset = PowerLineStyleEditor.basePreset(line);
        String styleLabel = preset != null
            ? PlotI18n.tr(preset.getLabelKey())
            : PlotI18n.tr("plugin.powerline.build.style_custom");

        ImGui.beginChild("powerline_route_summary", 0, 0, true, imgui.flag.ImGuiWindowFlags.None);
        renderMetricRow(
            PlotI18n.tr("plugin.powerline.route.summary.length"),
            PlotI18n.tr("plugin.powerline.route.summary.length_value", formatBlocks(worldLength)));
        renderMetricRow(
            PlotI18n.tr("plugin.powerline.route.summary.poles"),
            String.valueOf(poleCount));
        renderMetricRow(
            PlotI18n.tr("plugin.powerline.route.summary.avg_spacing"),
            PlotI18n.tr("plugin.powerline.route.summary.spacing_value", formatBlocks(avgSpacing)));
        renderMetricRow(
            PlotI18n.tr("plugin.powerline.route.summary.style"),
            styleLabel);
        renderStatusRow(line, ctx);
        ImGui.endChild();
        ImGui.spacing();
    }

    private static void renderMetricRow(String label, String value) {
        ImGui.text(label);
        ImGui.sameLine(160f);
        PowerLineUiWidgets.text(value);
    }

    private static void renderStatusRow(PowerLineFootprint line, PowerLineUiContext ctx) {
        PowerLineFriendlyStatus.SpacingEvaluation spacing =
            PowerLineFriendlyStatus.evaluateSpacing(line, ctx.coordinates());
        String statusKey = spacing.kind() == PowerLineFriendlyStatus.SpacingKind.OK
            || spacing.kind() == PowerLineFriendlyStatus.SpacingKind.SETTINGS_ONLY
            ? "plugin.powerline.route.summary.status_ready"
            : "plugin.powerline.route.summary.status_review";
        int color = spacing.kind() == PowerLineFriendlyStatus.SpacingKind.OK
            || spacing.kind() == PowerLineFriendlyStatus.SpacingKind.SETTINGS_ONLY
            ? PluginUiColors.STATUS_OK
            : PluginUiColors.WARNING;
        ImGui.text(PlotI18n.tr("plugin.powerline.route.summary.status"));
        ImGui.sameLine(160f);
        PowerLineUiWidgets.textColored(color, PlotI18n.tr(statusKey));
    }

    private static double typicalSpanBlocks(double worldLength, int poleCount, double maxPoleSpacing) {
        if (poleCount > 1 && worldLength > 0.0) {
            return worldLength / (poleCount - 1);
        }
        return maxPoleSpacing;
    }

    private static String formatBlocks(double blocks) {
        return String.format("%.0f", blocks);
    }
}
