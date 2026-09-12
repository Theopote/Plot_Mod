package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.ui.PowerLineUiWidgets;
import com.plot.utils.PlotI18n;

/** Expert-mode manual structure editing (stations, arms, decorations). */
public final class TowerManualStructurePanel {

    @FunctionalInterface
    public interface Renderer {
        void render(TowerDesignerContext context);
    }

    public void renderIfVisible(TowerDesignerContext context, Renderer renderer) {
        if (!shouldShow(context)) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.tower_expert_mode"));
        renderer.render(context);
    }

    private boolean shouldShow(TowerDesignerContext context) {
        var draft = context.draft();
        if (draft.isManualLegacyMode()) {
            return draft.hasTowerStructure();
        }
        if (draft.isParametricMode()) {
            return context.uiState().showAdvancedStructure.get() && draft.hasTowerStructure();
        }
        return draft.hasTowerStructure();
    }
}
