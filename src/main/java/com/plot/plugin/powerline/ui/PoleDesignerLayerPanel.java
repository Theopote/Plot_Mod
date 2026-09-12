package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.ui.component.UIUtils;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/** Legacy 杆塔分层编辑器。 */
final class PoleDesignerLayerPanel {
    private final List<LayerAction> pendingLayerActions = new ArrayList<>();

    void render(PoleDesign draft, Runnable pushDraftSnapshot) {
        pendingLayerActions.clear();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.layers"));
        for (int i = 0; i < draft.getLayers().size(); i++) {
            PoleLayer layer = draft.getLayers().get(i);
            ImGui.pushID("layer_" + i);
            renderLayerRow(layer, i, draft, pushDraftSnapshot);
            ImGui.popID();
        }
        applyPendingLayerActions(draft, pushDraftSnapshot);
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_layer"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.addLayer(new PoleLayer(
                PoleLayer.Shape.COLUMN,
                1,
                MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        }
    }

    private void renderLayerRow(PoleLayer layer, int index, PoleDesign draft, Runnable pushDraftSnapshot) {
        if (index > 0) {
            ImGui.separator();
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.layer_index", index + 1));
        if (!DialogLayoutHelper.beginForm("##layer_form")) {
            return;
        }

        String[] shapeLabels = {
            PlotI18n.tr("plugin.powerline.design.shape.column"),
            PlotI18n.tr("plugin.powerline.design.shape.crossarm"),
            PlotI18n.tr("plugin.powerline.design.shape.cap")
        };
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.layer_shape"));
        ImInt shapeIndex = new ImInt(layer.getShape().ordinal());
        if (ImGui.combo("##shape", shapeIndex, shapeLabels)) {
            pushDraftSnapshot.run();
            layer.setShape(PoleLayer.Shape.values()[shapeIndex.get()]);
        }

        int[] height = {layer.getHeight()};
        if (PoleDesignerFormRows.sliderInt("plugin.powerline.design.layer_height", "##height", height, 1, 64)) {
            layer.setHeight(height[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
            int[] span = {layer.getCrossarmLength()};
            if (PoleDesignerFormRows.sliderInt(
                    "plugin.powerline.design.layer_crossarm_span",
                    "##span",
                    span,
                    1,
                    32)) {
                layer.setCrossarmLength(span[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }
        }

        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.layer_material"));
        UIUtils.renderMaterialMixPickerControl(
            "pick",
            layer.getMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            layer::setMaterial,
            pushDraftSnapshot);

        MaterialMix mix = layer.getMaterial();
        if (mix != null
                && mix.getAccentMaterial() != null
                && !mix.getAccentMaterial().isBlank()) {
            DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.material.accent_ratio"));
            UIUtils.renderAccentRatioSliderControl(mix, layer::setMaterial, "accent", pushDraftSnapshot);
        }

        DialogLayoutHelper.endForm();
        renderLayerActionButtons(index, draft);
        DialogLayoutHelper.subsectionGap();
    }

    private void renderLayerActionButtons(int index, PoleDesign draft) {
        float contentWidth = DialogStyleManager.getContentWidth();
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(
            contentWidth,
            3,
            PlotI18n.tr("plugin.powerline.design.move_up"),
            PlotI18n.tr("plugin.powerline.design.move_down"),
            PlotI18n.tr("plugin.powerline.design.delete_layer"));
        if (index > 0) {
            if (ImGui.button(
                    PlotI18n.tr("plugin.powerline.design.move_up") + "##up",
                    buttonWidth,
                    0)) {
                pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_UP, index));
            }
            ImGui.sameLine();
        }
        if (index < draft.getLayers().size() - 1) {
            if (ImGui.button(
                    PlotI18n.tr("plugin.powerline.design.move_down") + "##down",
                    buttonWidth,
                    0)) {
                pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_DOWN, index));
            }
            ImGui.sameLine();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete",
                buttonWidth,
                0)) {
            pendingLayerActions.add(new LayerAction(LayerAction.Type.DELETE, index));
        }
    }

    private void applyPendingLayerActions(PoleDesign draft, Runnable pushDraftSnapshot) {
        if (pendingLayerActions.isEmpty()) {
            return;
        }
        pushDraftSnapshot.run();
        for (LayerAction action : pendingLayerActions) {
            switch (action.type()) {
                case MOVE_UP -> moveLayer(draft, action.index(), -1);
                case MOVE_DOWN -> moveLayer(draft, action.index(), 1);
                case DELETE -> draft.removeLayerAt(action.index());
            }
        }
    }

    private static void moveLayer(PoleDesign draft, int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= draft.getLayers().size()) {
            return;
        }
        draft.moveLayer(index, target);
    }

    private record LayerAction(Type type, int index) {
        enum Type {
            MOVE_UP,
            MOVE_DOWN,
            DELETE
        }
    }
}
