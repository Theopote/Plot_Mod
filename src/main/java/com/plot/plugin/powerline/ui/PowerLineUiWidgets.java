package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineSagPolicy;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 电力线路插件共享 ImGui 控件。 */
public final class PowerLineUiWidgets {
    private PowerLineUiWidgets() {
    }

    /** 当前内容区右边界，供自动换行使用。 */
    public static float wrapPos() {
        return ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();
    }

    public static void text(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ImGui.pushTextWrapPos(wrapPos());
        ImGui.textWrapped(text);
        ImGui.popTextWrapPos();
    }

    public static void textColored(int color, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ImGui.pushTextWrapPos(wrapPos());
        ImGui.textColored(color, text);
        ImGui.popTextWrapPos();
    }

    /**
     * 稳定 ImGui 控件 ID：可见标签可含 i18n 文本，{@code ##id} 后缀在拖动时保持不变。
     * 当前值应通过 slider 的 {@code format} 显示，不要写进标签字符串。
     */
    public static String stableLabel(String i18nKey, String idSuffix) {
        return PlotI18n.tr(i18nKey) + "##" + idSuffix;
    }

    public static String stableSelectableLabel(String visibleLabel, String idSuffix) {
        return visibleLabel + "##" + idSuffix;
    }

    /**
     * 稳定 ID 的 slider：拖动时实时改值，松手后仅触发一次 {@code onCommit}（通常 invalidate preview）。
     * 工作区撤销在首次 value change 时 capture，focus  alone 不产生空 Undo。
     */
    public static boolean sliderFloatStable(
            PowerLineUiContext ctx,
            String idSuffix,
            String labelI18nKey,
            float[] value,
            float min,
            float max,
            String format,
            Consumer<Float> onLiveChange,
            Runnable onCommit) {
        boolean changed = ImGui.sliderFloat(
            stableLabel(labelI18nKey, idSuffix),
            value,
            min,
            max,
            format);
        if (ctx != null) {
            ctx.trackPendingEdit(
                idSuffix,
                ImGui.isItemActivated(),
                changed,
                ImGui.isItemDeactivatedAfterEdit());
        }
        if (changed && onLiveChange != null) {
            onLiveChange.accept(value[0]);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && onCommit != null) {
            onCommit.run();
        }
        return changed;
    }

    /** 线路编辑 slider：snapshot on activate，live 改 footprint，松手 invalidate preview 一次。 */
    public static boolean sliderFloatStableLineEdit(
            PowerLineUiContext ctx,
            String idSuffix,
            String labelI18nKey,
            float[] value,
            float min,
            float max,
            String format,
            Consumer<Float> onLiveChange) {
        return sliderFloatStable(
            ctx,
            idSuffix,
            labelI18nKey,
            value,
            min,
            max,
            format,
            onLiveChange,
            ctx::invalidatePreview);
    }

    /** 线路编辑 inputFloat：激活时 snapshot，编辑中 live 改值，结束编辑后 invalidate preview。 */
    public static boolean inputFloatStableLineEdit(
            PowerLineUiContext ctx,
            String idSuffix,
            float[] value,
            float step,
            float stepFast,
            String format,
            float min,
            float max,
            Consumer<Float> onLiveChange) {
        imgui.type.ImFloat input = new imgui.type.ImFloat(value[0]);
        boolean changed = ImGui.inputFloat("##" + idSuffix, input, step, stepFast, format);
        ctx.trackPendingEdit(
            idSuffix,
            ImGui.isItemActivated(),
            changed,
            ImGui.isItemDeactivatedAfterEdit());
        if (changed) {
            float clamped = Math.max(min, Math.min(max, input.get()));
            value[0] = clamped;
            if (onLiveChange != null) {
                onLiveChange.accept(clamped);
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ctx.invalidatePreview();
        }
        return changed;
    }

    /** 线路编辑 inputInt：激活时 snapshot，编辑中 live 改值，结束编辑后 invalidate preview。 */
    public static boolean inputIntStableLineEdit(
            PowerLineUiContext ctx,
            String idSuffix,
            int[] value,
            int step,
            int stepFast,
            Consumer<Integer> onLiveChange) {
        imgui.type.ImInt input = new imgui.type.ImInt(value[0]);
        boolean changed = ImGui.inputInt("##" + idSuffix, input, step, stepFast);
        ctx.trackPendingEdit(
            idSuffix,
            ImGui.isItemActivated(),
            changed,
            ImGui.isItemDeactivatedAfterEdit());
        if (changed) {
            value[0] = input.get();
            if (onLiveChange != null) {
                onLiveChange.accept(value[0]);
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ctx.invalidatePreview();
        }
        return changed;
    }

    /** 线路编辑 inputText：激活时 snapshot，编辑中 live 改值。 */
    public static boolean inputTextStableLineEdit(
            PowerLineUiContext ctx,
            String labelI18nKey,
            String idSuffix,
            imgui.type.ImString buffer,
            Consumer<String> onLiveChange) {
        boolean changed = ImGui.inputText(stableLabel(labelI18nKey, idSuffix), buffer);
        ctx.trackPendingEdit(
            idSuffix,
            ImGui.isItemActivated(),
            changed,
            ImGui.isItemDeactivatedAfterEdit());
        if (changed && onLiveChange != null) {
            onLiveChange.accept(buffer.get());
        }
        return changed;
    }

    public static boolean renderLineSelector(PowerLineUiContext ctx) {
        return renderLineSelector(ctx, true);
    }

    /**
     * @param showLabel {@code false} 时仅渲染 {@code ##select_line}，由页面提供「当前线路」等标题
     * @return 是否已选中一条线路
     */
    public static boolean renderLineSelector(PowerLineUiContext ctx, boolean showLabel) {
        if (ctx.project().getLineCount() == 0) {
            return false;
        }
        List<PowerLineFootprint> lines = new ArrayList<>(ctx.project().getLines().values());
        int itemCount = lines.size() + 1;
        String[] labels = new String[itemCount];
        String[] ids = new String[itemCount];
        labels[0] = PlotI18n.tr("plugin.powerline.select_line_none");
        ids[0] = "";
        for (int i = 0; i < lines.size(); i++) {
            labels[i + 1] = lines.get(i).getName();
            ids[i + 1] = lines.get(i).getId();
        }
        int current = resolveLineSelectorIndex(ctx.selection().primaryId(), lines);
        imgui.type.ImInt index = new imgui.type.ImInt(current);
        String comboLabel = showLabel
            ? stableLabel("plugin.powerline.select_line", "select_line")
            : "##select_line";
        if (ImGui.combo(comboLabel, index, labels)) {
            String selectedId = ids[index.get()];
            if (selectedId == null || selectedId.isBlank()) {
                ctx.clearSelection();
            } else {
                ctx.selectLine(selectedId, false);
            }
        }
        return current != 0;
    }

    /** 多选时阻断单线路编辑区，并提示用户先归一为单选。 */
    public static boolean renderMultiLineEditBlocked(PowerLineUiContext ctx) {
        if (!ctx.isMultiLineSelection()) {
            return false;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.powerline.selection.multi_edit_blocked", ctx.selection().size()));
        return true;
    }

    static int resolveLineSelectorIndex(String primaryId, List<PowerLineFootprint> lines) {
        if (primaryId == null || primaryId.isBlank()) {
            return 0;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getId().equals(primaryId)) {
                return i + 1;
            }
        }
        return 0;
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
            () -> ctx.pushEditSnapshot(),
            (activated, changed, deactivatedAfterEdit) -> ctx.trackPendingEdit(
                id + "_accent",
                activated,
                changed,
                deactivatedAfterEdit));
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
        ImGui.separator();
        text(PlotI18n.tr("plugin.powerline.validation.section"));
        textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.hint"));

        boolean lineChecks = line.isLineChecksEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.validation.line_checks"), lineChecks)) {
            ctx.pushEditSnapshot();
            boolean enabling = !lineChecks;
            line.setLineChecksEnabled(enabling);
            if (enabling) {
                ctx.state().getValidationState().setOverlayEnabled(true);
            }
            ctx.clearAnalysisReports();
        }
        if (includeOverlayToggle) {
            boolean overlay = ctx.state().getValidationState().isOverlayEnabled();
            if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.overlay"), overlay)) {
                ctx.state().getValidationState().setOverlayEnabled(!overlay);
            }
        }
        boolean autoSelect = line.isAutomaticTowerSelectionEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.auto_select"), autoSelect)) {
            ctx.pushEditSnapshot();
            line.setAutomaticTowerSelectionEnabled(!autoSelect);
            ctx.invalidatePreview();
        }
        renderEffectiveCheckConditions(line);
        textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.disclaimer"));
    }

    private static void renderEffectiveCheckConditions(PowerLineFootprint line) {
        ImGui.spacing();
        text(PlotI18n.tr("plugin.powerline.validation.check_conditions"));
        if (line.isTerrainAvoidanceEnabled()) {
            textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.powerline.validation.terrain_checks_enabled"));
        } else {
            textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.validation.terrain_checks_disabled"));
        }
        textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.terrain_checks_route_hint"));
        double effective = PowerLineSagPolicy.resolveMaxSagDepth(line);
        if (effective > 0.0) {
            textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.engineering.effective_max_sag", effective));
        } else {
            textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.engineering.effective_max_sag_unlimited"));
        }
        textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.validation.sag_style_hint"));
    }

    /** 杆塔角色统计（仅 Advanced 区展示）。 */
    public static void renderTowerRoleStats(PowerLineGenerationResult result) {
        if (result == null || result.poleCount <= 0) {
            return;
        }
        text(PlotI18n.tr("plugin.powerline.build.role_stats_section"));
        if (result.roleCount(TowerRole.SUSPENSION) > 0) {
            text(PlotI18n.tr(
                "plugin.powerline.role_stats_suspension",
                result.roleCount(TowerRole.SUSPENSION)));
        }
        if (result.roleCount(TowerRole.ANGLE) > 0) {
            text(PlotI18n.tr(
                "plugin.powerline.role_stats_angle",
                result.roleCount(TowerRole.ANGLE)));
        }
        if (result.roleCount(TowerRole.DEAD_END) > 0) {
            text(PlotI18n.tr(
                "plugin.powerline.role_stats_dead_end",
                result.roleCount(TowerRole.DEAD_END)));
        }
        if (result.roleCount(TowerRole.TERMINAL) > 0) {
            text(PlotI18n.tr(
                "plugin.powerline.role_stats_terminal",
                result.roleCount(TowerRole.TERMINAL)));
        }
        if (result.roleCount(TowerRole.SPECIAL) > 0) {
            text(PlotI18n.tr(
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
