package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;


/** 土方插件顶部工具栏与落地进度控制。 */
public final class EarthworkToolbarPanel {
    private final EarthworkUiContext ctx;

    public EarthworkToolbarPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }

    private void renderToolbar() {
        float buttonWidth = Math.max(1f, (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f);

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.undo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            EarthworkUiWidgets.syncSelectedRegionAfterHistory(ctx);
            ctx.setRegionNameEditingRegionId("");
            ctx.clearPreview();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            EarthworkUiWidgets.syncSelectedRegionAfterHistory(ctx);
            ctx.setRegionNameEditingRegionId("");
            ctx.clearPreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, ctx.projectStatus());
        }
        ImGui.separator();
        renderWorkMode();
        ImGui.separator();
    }

    private void renderWorkMode() {
        EarthworkWorkMode current = ctx.config().getWorkMode();
        EarthworkWorkMode[] modes = EarthworkWorkMode.values();
        String[] labels = new String[modes.length];
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr(modes[i].i18nKey());
            if (modes[i] == current) {
                selected = i;
            }
        }
        ImInt index = ctx.workModeIndex();
        index.set(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.work_mode"), index, labels)) {
            int picked = index.get();
            if (picked >= 0 && picked < modes.length && modes[picked] != current) {
                // 工作模式只切换界面与操作路径；不写回/裁剪项目内高级合成参数。
                // 快速平整生成时由管线临时 clampedCopyForWorkMode，切回建造仍保留原设置。
                ctx.config().setWorkMode(modes[picked]);
                ctx.config().save();
                ctx.invalidatePreview();
            }
        }
    }

    private void renderActivePlacementControls() {
        PluginJobProgressUi.renderPlacementProgress(
            ctx.host().placement(),
            "plugin.earthwork.placement_progress",
            "plugin.earthwork.build_in_progress_hint",
            "plugin.earthwork.cancel_placement");
        if (ctx.host().placement().isBusy()) {
            ImGui.separator();
        }
    }
}
