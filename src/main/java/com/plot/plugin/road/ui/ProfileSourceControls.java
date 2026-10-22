package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 适应地形模式下的纵断面来源选择：自动设计 / 使用编辑曲线。 */
final class ProfileSourceControls {

    private ProfileSourceControls() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_source_section"));
        ImGui.spacing();

        boolean hasStoredCurve = ResolvedProfileAuthority.hasStoredEditedCurve(road);
        ProfileSource current = road.getEffectiveProfileSource();
        boolean autoSelected = current == ProfileSource.AUTO;

        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_auto"),
                autoSelected)) {
            if (!autoSelected) {
                applyProfileSource(ctx, road, ProfileSource.AUTO);
            }
        }
        ImGui.sameLine();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_source_auto_hint"));

        ImGui.beginDisabled(!hasStoredCurve);
        boolean editedSelected = current == ProfileSource.EDITED;
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.profile_source_edited"),
                editedSelected)) {
            if (!editedSelected && hasStoredCurve) {
                applyProfileSource(ctx, road, ProfileSource.EDITED);
            }
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_source_edited_hint"));

        ImGui.spacing();
        renderStatus(network, road, hasStoredCurve);
    }

    private static void applyProfileSource(RoadUiContext ctx, Road road, ProfileSource source) {
        ctx.beginNetworkEdit(com.plot.plugin.road.manager.RoadChangeKind.VERTICAL_PROFILE);
        road.setProfileSource(source);
        if (source == ProfileSource.EDITED) {
            road.setEditedGeometrySignature(
                EditedProfileGeometry.signature(ctx.networkManager().getNetwork(), road));
        }
        ctx.finishNetworkEdit();
        if (ctx.previewManager() != null) {
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
    }

    private static void renderStatus(RoadNetwork network, Road road, boolean hasStoredCurve) {
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
            hasStoredCurve
                ? PlotI18n.tr("plugin.road.profile_source_status_auto_with_saved")
                : PlotI18n.tr("plugin.road.profile_source_status_auto_detail"));
    }
}
