package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
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
            ctx.selection().select(ids[index.get()], false);
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
            () -> ctx.projectHistory().push(ctx.project()));
    }
}
