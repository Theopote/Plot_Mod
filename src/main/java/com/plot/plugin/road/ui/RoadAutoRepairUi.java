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
 * 编辑 Tab / 诊断窗「一键自动修路」UI。
 */
public final class RoadAutoRepairUi {

    private static String lastFeedback = "";
    private static int lastFeedbackColor = PluginUiColors.HINT_GRAY;

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
        renderFixButton(ctx, road, issues);
        ImGui.spacing();
    }

    /** 诊断窗：完整问题列表（标题 + 原因）与复检。 */
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
        ImGui.spacing();

        boolean hasManualOnly = false;
        for (RoadRepairIssue issue : issues) {
            renderIssueCard(issue);
            if (!issue.isAutoFixable()) {
                hasManualOnly = true;
            }
        }

        if (hasManualOnly) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.fix_road.manual_only_hint"));
        }

        renderFixButton(ctx, road, issues);
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.fix_road.recheck") + "##fix_road_recheck_" + road.getId())) {
            invalidateCache();
            lastFeedback = "";
        }

        if (!lastFeedback.isBlank()) {
            ImGui.spacing();
            RoadUiWidgets.textWrappedColored(lastFeedbackColor, lastFeedback);
        }
        ImGui.spacing();
    }

    private static void renderIssueCard(RoadRepairIssue issue) {
        ImGui.pushStyleColor(
            imgui.flag.ImGuiCol.Text,
            issue.isAutoFixable() ? PluginUiColors.WARNING : PluginUiColors.ERROR_SOFT);
        ImGui.text("\u2022 " + PlotI18n.tr(issue.titleKey()));
        ImGui.popStyleColor();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(issue.detailKey())
                + (issue.isAutoFixable()
                    ? ""
                    : " " + PlotI18n.tr("plugin.road.fix_road.manual_only_tag")));
        ImGui.spacing();
    }

    private static void renderFixButton(RoadUiContext ctx, Road road, List<RoadRepairIssue> issues) {
        boolean anyAuto = false;
        for (RoadRepairIssue issue : issues) {
            if (issue.isAutoFixable()) {
                anyAuto = true;
                break;
            }
        }
        if (!anyAuto) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.fix_road.action") + "##fix_road_" + road.getId())) {
            executeFix(ctx, road);
        }
        if (!anyAuto) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.fix_road.auto_fixable_none"));
            }
        }
    }

    public static boolean executeFix(RoadUiContext ctx, Road road) {
        if (ctx == null || road == null) {
            return false;
        }
        List<RoadRepairIssue> before = RoadRepairDiagnosisCache.diagnose(
            ctx, ctx.networkManager().getNetwork(), road);
        boolean anyAuto = false;
        for (RoadRepairIssue issue : before) {
            if (issue.isAutoFixable()) {
                anyAuto = true;
                break;
            }
        }
        if (!anyAuto) {
            lastFeedback = PlotI18n.tr("plugin.road.fix_road.auto_fixable_none");
            lastFeedbackColor = PluginUiColors.HINT_GRAY;
            ctx.status().info(lastFeedback);
            return false;
        }

        RoadAutoRepair.Result result = ctx.networkManager().fixRoad(road);
        invalidateCache();
        ctx.onGenerationConfigChanged();
        ctx.requestOverlayRefresh();

        if (result.fullyRepaired()) {
            lastFeedback = PlotI18n.tr("plugin.road.fix_road.success");
            lastFeedbackColor = PluginUiColors.STATUS_OK;
            ctx.status().success(lastFeedback);
        } else if (result.changed()) {
            lastFeedback = PlotI18n.tr(
                "plugin.road.fix_road.partial",
                result.issuesAfter().size());
            lastFeedbackColor = PluginUiColors.WARNING;
            ctx.status().warning(lastFeedback);
        } else {
            lastFeedback = PlotI18n.tr("plugin.road.fix_road.noop");
            lastFeedbackColor = PluginUiColors.HINT_GRAY;
            ctx.status().info(lastFeedback);
        }

        if (result.roadId() != null && !result.roadId().isBlank()) {
            ctx.networkManager().selectRoad(result.roadId(), false);
        }
        return result.changed();
    }

    /** 测试用：最近一次修复反馈文案。 */
    static String lastFeedbackForTest() {
        return lastFeedback;
    }
}
