package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 适应地形模式下的纵断面来源选择：自动设计 / 使用编辑曲线。 */
final class ProfileSourceControls {

    private ProfileSourceControls() {
    }

    /** 保存的编辑曲线与当前路径不匹配，需打开编辑器复核后再绑定。 */
    static boolean needsEditorReview(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        return ResolvedProfileAuthority.hasStoredEditedCurve(road)
            && !ResolvedProfileAuthority.canActivateEditedProfile(network, road);
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_source_section"));

        boolean hasStoredCurve = ResolvedProfileAuthority.hasStoredEditedCurve(road);
        boolean canActivateEdited = ResolvedProfileAuthority.canActivateEditedProfile(network, road);
        boolean staleSaved = hasStoredCurve && !canActivateEdited;
        ProfileSource current = road.getEffectiveProfileSource();
        boolean autoSelected = current == ProfileSource.AUTO;

        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_auto"),
                autoSelected)) {
            if (!autoSelected) {
                applyProfileSource(ctx, network, road, ProfileSource.AUTO);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_auto_hint"));
        }

        ImGui.beginDisabled(!canActivateEdited);
        boolean editedSelected = current == ProfileSource.EDITED;
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_edited"),
                editedSelected)) {
            if (!editedSelected && canActivateEdited) {
                applyProfileSource(ctx, network, road, ProfileSource.EDITED);
            }
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            if (staleSaved) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_stale_reconfirm"));
            } else {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_edited_hint"));
            }
        }
        ImGui.endDisabled();

        ImGui.spacing();
        renderCompactStatus(network, road, hasStoredCurve, canActivateEdited, staleSaved);
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
        if (source == ProfileSource.AUTO) {
            VerticalProfileGenerationPolicy.applyExplicitAutoProfileSelection(network, road.getId());
        }
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
    }

    /** 一行状态；细节进 tooltip，过期只保留一条橙字（不再叠灰字+橙字）。 */
    private static void renderCompactStatus(
            RoadNetwork network,
            Road road,
            boolean hasStoredCurve,
            boolean canActivateEdited,
            boolean staleSaved) {
        ProfileSource current = road.getEffectiveProfileSource();
        if (current == ProfileSource.EDITED) {
            if (ResolvedProfileAuthority.hasValidEditedProfile(network, road)) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.STATUS_OK,
                    PlotI18n.tr("plugin.road.profile_source_status_edited"));
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_source_status_edited_detail"));
                }
            } else {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.profile_source_status_stale"));
            }
            return;
        }
        if (staleSaved) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_source_stale_reconfirm"));
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.STATUS_OK,
            PlotI18n.tr("plugin.road.profile_source_status_auto"));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(
                hasStoredCurve && canActivateEdited
                    ? "plugin.road.profile_source_status_auto_with_saved"
                    : "plugin.road.profile_source_status_auto_detail"));
        }
    }
}
