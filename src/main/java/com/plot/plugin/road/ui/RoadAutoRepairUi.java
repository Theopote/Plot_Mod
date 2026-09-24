package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.repair.RoadAutoRepair;
import com.plot.plugin.road.repair.RoadRepairDiagnosisCache;
import com.plot.plugin.road.repair.RoadRepairIssue;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 编辑 Tab 单路「一键自动修路」横幅。
 */
public final class RoadAutoRepairUi {

    private RoadAutoRepairUi() {
    }

    /** 路网或选中道路变更后使诊断缓存失效。 */
    public static void invalidateCache() {
        RoadRepairDiagnosisCache.invalidate();
    }

    public static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        renderCompact(ctx, network, road);
    }

    /** 样式 Tab 主界面：仅警告横幅与一键修复。 */
    public static void renderCompact(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return;
        }

        List<RoadRepairIssue> issues = RoadRepairDiagnosisCache.diagnose(ctx, network, road);
        if (issues.isEmpty()) {
            return;
        }

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, PluginUiColors.WARNING);
        ImGui.text("\u26a0 " + PlotI18n.tr("plugin.road.fix_road.needs_attention"));
        ImGui.popStyleColor();
        if (ImGui.button(PlotI18n.tr("plugin.road.fix_road.action") + "##fix_road_" + road.getId())) {
            executeFix(ctx, road);
        }
        ImGui.spacing();
    }

    /** 高级道路设计内：完整诊断列表与复检。 */
    public static void renderDetailed(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return;
        }

        List<RoadRepairIssue> issues = RoadRepairDiagnosisCache.diagnose(ctx, network, road);
        if (issues.isEmpty()) {
            return;
        }

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, PluginUiColors.WARNING);
        ImGui.text("\u26a0 " + PlotI18n.tr("plugin.road.fix_road.needs_attention"));
        ImGui.popStyleColor();
        ImGui.text(PlotI18n.tr("plugin.road.fix_road.issues_found", issues.size()));
        ImGui.indent();
        for (RoadRepairIssue issue : issues) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                "\u2022 " + PlotI18n.tr(issueKey(issue)));
        }
        ImGui.unindent();
        if (ImGui.button(PlotI18n.tr("plugin.road.fix_road.action") + "##fix_road_" + road.getId())) {
            executeFix(ctx, road);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.fix_road.recheck") + "##fix_road_recheck_" + road.getId())) {
            invalidateCache();
        }
        ImGui.spacing();
    }

    public static boolean executeFix(RoadUiContext ctx, Road road) {
        if (ctx == null || road == null) {
            return false;
        }
        RoadAutoRepair.Result result = ctx.networkManager().fixRoad(road);
        invalidateCache();
        ctx.onGenerationConfigChanged();

        if (result.fullyRepaired()) {
            ctx.status().success(PlotI18n.tr("plugin.road.fix_road.success"));
        } else if (result.changed()) {
            ctx.status().warning(PlotI18n.tr(
                "plugin.road.fix_road.partial",
                result.issuesAfter().size()));
        } else {
            ctx.status().info(PlotI18n.tr("plugin.road.fix_road.noop"));
        }

        if (result.roadId() != null && !result.roadId().isBlank()) {
            ctx.networkManager().selectRoad(result.roadId(), false);
        }
        return result.changed();
    }

    private static String issueKey(RoadRepairIssue issue) {
        return "plugin.road.fix_road.issue." + issue.name().toLowerCase();
    }
}
