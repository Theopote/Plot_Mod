package com.plot.plugin.road.ui;


import com.plot.ui.component.UIUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.repair.RoadAutoRepair;
import com.plot.plugin.road.repair.RoadRepairIssue;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 编辑 Tab / 诊断窗「一键自动修路」UI。
 * 反馈文案与诊断缓存挂在 {@link RoadUiContext}，随上下文生命周期管理。
 */
public final class RoadAutoRepairUi {

    private RoadAutoRepairUi() {
    }

    /** 路网或选中道路变更后使诊断缓存失效。 */
    public static void invalidateCache(RoadUiContext ctx) {
        if (ctx != null) {
            ctx.repairDiagnosisCache().invalidate();
        }
    }

    public static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        renderCompact(ctx, network, road);
    }

    /** 样式 Tab 主界面：仅警告横幅与一键修复。 */
    public static void renderCompact(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return;
        }

        List<RoadRepairIssue> issues = ctx.repairDiagnosisCache().diagnose(ctx, network, road);
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

        List<RoadRepairIssue> issues = ctx.repairDiagnosisCache().diagnose(ctx, network, road);
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
            invalidateCache(ctx);
            ctx.clearAutoRepairFeedback();
        }

        String feedback = ctx.autoRepairFeedback();
        if (!feedback.isBlank()) {
            ImGui.spacing();
            RoadUiWidgets.textWrappedColored(ctx.autoRepairFeedbackColor(), feedback);
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
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.fix_road.auto_fixable_none"));
            }
        }
    }

    public static boolean executeFix(RoadUiContext ctx, Road road) {
        if (ctx == null || road == null) {
            return false;
        }
        List<RoadRepairIssue> before = ctx.repairDiagnosisCache().diagnose(
            ctx, ctx.networkManager().getNetwork(), road);
        boolean anyAuto = false;
        for (RoadRepairIssue issue : before) {
            if (issue.isAutoFixable()) {
                anyAuto = true;
                break;
            }
        }
        if (!anyAuto) {
            String message = PlotI18n.tr("plugin.road.fix_road.auto_fixable_none");
            ctx.setAutoRepairFeedback(message, PluginUiColors.HINT_GRAY);
            ctx.status().info(message);
            return false;
        }

        RoadAutoRepair.Result result = ctx.networkManager().fixRoad(road);
        invalidateCache(ctx);
        ctx.onGenerationConfigChanged();
        ctx.requestOverlayRefresh();

        if (result.fullyRepaired()) {
            String message = PlotI18n.tr("plugin.road.fix_road.success");
            ctx.setAutoRepairFeedback(message, PluginUiColors.STATUS_OK);
            ctx.status().success(message);
        } else if (result.changed()) {
            String message = PlotI18n.tr(
                "plugin.road.fix_road.partial",
                result.issuesAfter().size());
            ctx.setAutoRepairFeedback(message, PluginUiColors.WARNING);
            ctx.status().warning(message);
        } else {
            String message = PlotI18n.tr("plugin.road.fix_road.noop");
            ctx.setAutoRepairFeedback(message, PluginUiColors.HINT_GRAY);
            ctx.status().info(message);
        }

        if (result.roadId() != null && !result.roadId().isBlank()) {
            ctx.networkManager().selectRoad(result.roadId(), false);
        }
        return result.changed();
    }

    /** 测试用：最近一次修复反馈文案。 */
    static String lastFeedbackForTest(RoadUiContext ctx) {
        return ctx != null ? ctx.autoRepairFeedback() : "";
    }
}
