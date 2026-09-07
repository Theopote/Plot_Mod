package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

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
    public static void renderEngineeringProfileControls(
            PowerLineUiContext ctx,
            PowerLineFootprint line,
            boolean includeOverlayToggle) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.profile_section"));

        EngineeringRuleProfileResolver resolver = new EngineeringRuleProfileResolver();
        var profiles = resolver.listAll();
        String[] labels = new String[profiles.size()];
        String[] ids = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).getName();
            ids[i] = profiles.get(i).getId();
        }
        String currentId = line.effectiveEngineeringProfileId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(currentId)) {
                current = i;
                break;
            }
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.engineering.profile"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], current == i)) {
                    ctx.pushEditSnapshot();
                    line.setEngineeringProfileId(ids[i]);
                }
            }
            ImGui.endCombo();
        }

        boolean analysisEnabled = line.isEngineeringAnalysisEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.enabled"), analysisEnabled)) {
            ctx.pushEditSnapshot();
            line.setEngineeringAnalysisEnabled(!analysisEnabled);
        }
        boolean autoSelect = line.isAutomaticTowerSelectionEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.auto_select"), autoSelect)) {
            ctx.pushEditSnapshot();
            line.setAutomaticTowerSelectionEnabled(!autoSelect);
            ctx.invalidatePreview();
        }
        if (includeOverlayToggle) {
            boolean overlay = ctx.state().getEngineeringState().isOverlayEnabled();
            if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.overlay"), overlay)) {
                ctx.state().getEngineeringState().setOverlayEnabled(!overlay);
            }
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.engineering.disclaimer"));
    }
}
