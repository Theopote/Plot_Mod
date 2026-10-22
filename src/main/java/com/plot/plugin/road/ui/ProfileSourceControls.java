package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 适应地形模式下的纵断面来源选择：自动设计 / 使用编辑曲线。 */
final class ProfileSourceControls {

    private ProfileSourceControls() {
    }

    /** 路径 Tab：完整可编辑来源控件。 */
    static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_source_section"));
        ImGui.spacing();

        boolean hasStoredCurve = ResolvedProfileAuthority.hasStoredEditedCurve(road);
        boolean canActivateEdited = ResolvedProfileAuthority.canActivateEditedProfile(network, road);
        ProfileSource current = road.getEffectiveProfileSource();
        boolean autoSelected = current == ProfileSource.AUTO;

        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_auto"),
                autoSelected)) {
            if (!autoSelected) {
                applyProfileSource(ctx, network, road, ProfileSource.AUTO);
            }
        }
        ImGui.sameLine();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_source_auto_hint"));

        ImGui.beginDisabled(!canActivateEdited);
        boolean editedSelected = current == ProfileSource.EDITED;
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_edited"),
                editedSelected)) {
            if (!editedSelected && canActivateEdited) {
                applyProfileSource(ctx, network, road, ProfileSource.EDITED);
            }
        }
        if (!canActivateEdited && hasStoredCurve && ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_stale_reconfirm"));
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_source_edited_hint"));

        ImGui.spacing();
        renderStatus(network, road, hasStoredCurve, canActivateEdited);
    }

    /**
     * 生成 Tab：只读来源摘要与跳转入口（不在此页编辑来源）。
     */
    static void renderGenerateSummary(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Runnable openProfileEditor) {
        if (road == null || network == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_source_generate_summary"));
        ImGui.spacing();

        boolean hasStoredCurve = ResolvedProfileAuthority.hasStoredEditedCurve(road);
        boolean canActivateEdited = ResolvedProfileAuthority.canActivateEditedProfile(network, road);
        renderStatus(network, road, hasStoredCurve, canActivateEdited);

        if (hasStoredCurve && !canActivateEdited) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_source_stale_reconfirm"));
            if (openProfileEditor != null
                    && ImGui.button(PlotI18n.tr("plugin.road.profile_source_open_editor") + "##gen_profile_src")) {
                openProfileEditor.run();
            }
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_source_goto_path_tab") + "##gen_profile_src_path")) {
            ctx.requestTab(RoadUiTab.PATH);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_goto_path_tab_hint"));
        }
    }

    private static void applyProfileSource(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ProfileSource source) {
        if (source == ProfileSource.EDITED
                && !ResolvedProfileAuthority.canActivateEditedProfile(network, road)) {
            return;
        }
        ctx.beginNetworkEdit(com.plot.plugin.road.manager.RoadChangeKind.VERTICAL_PROFILE);
        road.setProfileSource(source);
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
    }

    private static void renderStatus(
            RoadNetwork network,
            Road road,
            boolean hasStoredCurve,
            boolean canActivateEdited) {
        ProfileSource current = road.getEffectiveProfileSource();
        if (current == ProfileSource.EDITED) {
            if (ResolvedProfileAuthority.hasValidEditedProfile(network, road)) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.STATUS_OK,
                    PlotI18n.tr("plugin.road.profile_source_status_edited"));
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.profile_source_status_edited_detail"));
            } else {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.profile_source_status_stale"));
            }
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.STATUS_OK,
            PlotI18n.tr("plugin.road.profile_source_status_auto"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            hasStoredCurve && !canActivateEdited
                ? PlotI18n.tr("plugin.road.profile_source_status_auto_with_stale_saved")
                : hasStoredCurve
                    ? PlotI18n.tr("plugin.road.profile_source_status_auto_with_saved")
                    : PlotI18n.tr("plugin.road.profile_source_status_auto_detail"));
    }
}
