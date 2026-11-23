package com.plot.plugin.road.ui;


import com.plot.ui.component.UIUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
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
        // 标题由外层 PluginSectionCard（##vp_sec_profile_source）承担。

        boolean canActivateEdited = ResolvedProfileAuthority.canActivateEditedProfile(network, road);
        boolean hasStoredCurve = ResolvedProfileAuthority.hasStoredEditedCurve(road);
        // 路径几何已变（签名非 0 但不匹配）→ 过期提示；无曲线或未绑定签名 → 引导去编辑器确认。
        boolean staleGeometry = hasStoredCurve
            && road.getEditedGeometrySignature() != EditedProfileGeometry.UNBOUND_SIGNATURE
            && !canActivateEdited;
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
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.profile_source_auto_hint"));
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
            if (canActivateEdited) {
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.profile_source_edited_hint"));
            } else if (staleGeometry) {
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.profile_source_stale_reconfirm"));
            } else {
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.profile_source_edited_unavailable"));
            }
        }
        ImGui.endDisabled();
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
}
