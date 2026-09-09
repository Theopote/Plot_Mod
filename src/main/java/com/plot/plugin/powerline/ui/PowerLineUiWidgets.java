package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.PowerLineSagPolicy;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/** 电力线路插件共享 ImGui 控件。 */
public final class PowerLineUiWidgets {
    private PowerLineUiWidgets() {
    }

    public static void renderLineSelector(PowerLineUiContext ctx) {
        if (ctx.project().getLineCount() == 0) {
            return;
        }
        List<PowerLineFootprint> lines = new ArrayList<>(ctx.project().getLines().values());
        String[] labels = lines.stream().map(PowerLineFootprint::getName).toArray(String[]::new);
        String[] ids = lines.stream().map(PowerLineFootprint::getId).toArray(String[]::new);
        String primaryId = ctx.selection().primaryId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(primaryId)) {
                current = i;
                break;
            }
        }
        imgui.type.ImInt index = new imgui.type.ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.powerline.select_line"), index, labels)) {
            ctx.selectLine(ids[index.get()], false);
        }
    }

    public static void renderMaterialMixPicker(
            PowerLineUiContext ctx,
            String id,
            String label,
            MaterialMix current,
            MaterialMix defaultMix,
            java.util.function.Consumer<MaterialMix> onChange) {
        UIUtils.renderMaterialMixPicker(
            id,
            label,
            current,
            defaultMix,
            onChange::accept,
            () -> ctx.pushEditSnapshot());
    }

    /**
     * 工程规则配置控件（Profile / 分析开关 / 自动选塔）。
     *
     * @param includeOverlayToggle 是否在 Engineering 标签页显示画布叠加层开关
     */
    public static void renderLineCheckControls(
            PowerLineUiContext ctx,
            PowerLineFootprint line,
            boolean includeOverlayToggle) {
        renderLineCheckControls(ctx, line, includeOverlayToggle, false);
    }

    /** @deprecated use {@link #renderLineCheckControls} */
    @Deprecated
    public static void renderEngineeringProfileControls(
            PowerLineUiContext ctx,
            PowerLineFootprint line,
            boolean includeOverlayToggle) {
        renderLineCheckControls(ctx, line, includeOverlayToggle);
    }

    public static void renderAdvancedEngineeringSection(PowerLineUiContext ctx, PowerLineFootprint line) {
        ImGui.separator();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.engineering.advanced_section"),
                ImGuiTreeNodeFlags.None)) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.engineering.advanced_hint"));
            renderLineCheckControls(ctx, line, false, true);
        }
    }

    private static void renderLineCheckControls(
            PowerLineUiContext ctx,
            PowerLineFootprint line,
            boolean includeOverlayToggle,
            boolean nestedInSection) {
        if (!nestedInSection) {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.powerline.validation.section"));
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.hint"));

        boolean visualChecks = line.isVisualChecksEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.validation.visual_checks"), visualChecks)) {
            ctx.pushEditSnapshot();
            line.setVisualChecksEnabled(!visualChecks);
            ctx.invalidatePreview();
        }
        ImGui.indent();
        boolean lineChecks = line.isLineChecksEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.validation.line_checks"), lineChecks)) {
            ctx.pushEditSnapshot();
            line.setLineChecksEnabled(!lineChecks);
            ctx.invalidatePreview();
        }
        boolean terrainChecks = line.isTerrainAvoidanceEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.validation.terrain_checks"), terrainChecks)) {
            ctx.pushEditSnapshot();
            line.setTerrainAvoidanceEnabled(!terrainChecks);
            ctx.invalidatePreview();
        }
        ImGui.unindent();
        boolean autoSelect = line.isAutomaticTowerSelectionEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.auto_select"), autoSelect)) {
            ctx.pushEditSnapshot();
            line.setAutomaticTowerSelectionEnabled(!autoSelect);
            ctx.invalidatePreview();
        }
        if (includeOverlayToggle) {
            boolean overlay = ctx.state().getValidationState().isOverlayEnabled();
            if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.overlay"), overlay)) {
                ctx.state().getValidationState().setOverlayEnabled(!overlay);
            }
        }
        EngineeringRuleProfile activeProfile = new EngineeringRuleProfileResolver()
            .find(line.effectiveSagDefaultsId());
        renderSagDepthControls(ctx, line, activeProfile);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.disclaimer"));
    }

    private static void renderSagDepthControls(
            PowerLineUiContext ctx,
            PowerLineFootprint line,
            EngineeringRuleProfile profile) {
        double effective = PowerLineSagPolicy.resolveMaxSagDepth(line, profile);
        if (effective > 0.0) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.engineering.effective_max_sag", effective));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.engineering.effective_max_sag_unlimited"));
        }
        boolean unlimited = line.isMaxSagDepthUnlimited();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.max_sag_depth_unlimited"), unlimited)) {
            ctx.pushEditSnapshot();
            PowerLineUiPresets.applyMaxSagDepth(
                line,
                PowerLineUiPresets.displayMaxSagDepth(line),
                !unlimited);
            ctx.invalidatePreview();
        }
        if (!line.isMaxSagDepthUnlimited()) {
            float[] maxDepth = {PowerLineUiPresets.displayMaxSagDepth(line)};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.max_sag_depth", maxDepth[0]),
                    maxDepth,
                    1f,
                    PowerLineUiPresets.ADVANCED_MAX_SAG_DEPTH_MAX,
                    "%.0f")) {
                PowerLineUiPresets.applyMaxSagDepth(line, maxDepth[0], false);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        }
    }

    /** 杆塔角色统计（仅 Advanced 区展示）。 */
    public static void renderTowerRoleStats(PowerLineGenerationResult result) {
        if (result == null || result.poleCount <= 0) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.powerline.build.role_stats_section"));
        if (result.roleCount(TowerRole.SUSPENSION) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_suspension",
                result.roleCount(TowerRole.SUSPENSION)));
        }
        if (result.roleCount(TowerRole.ANGLE) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_angle",
                result.roleCount(TowerRole.ANGLE)));
        }
        if (result.roleCount(TowerRole.DEAD_END) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_dead_end",
                result.roleCount(TowerRole.DEAD_END)));
        }
        if (result.roleCount(TowerRole.TERMINAL) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_terminal",
                result.roleCount(TowerRole.TERMINAL)));
        }
        if (result.roleCount(TowerRole.SPECIAL) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_special",
                result.roleCount(TowerRole.SPECIAL)));
        }
    }

    /**
     * Deferred modal popup: {@code openRequest} only triggers {@link ImGui#openPopup} once;
     * ImGui keeps the modal open until the user closes it.
     *
     * @return true when the modal is visible this frame (caller must {@link ImGui#endPopup()})
     */
    public static boolean beginDeferredPopupModal(
            String popupId,
            boolean openRequest,
            Runnable onOpenRequested) {
        if (openRequest) {
            ImGui.openPopup(popupId);
            if (onOpenRequested != null) {
                onOpenRequested.run();
            }
        }
        return ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize);
    }
}
