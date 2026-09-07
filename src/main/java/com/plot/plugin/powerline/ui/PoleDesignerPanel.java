package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.UUID;

/** 杆塔分层设计器独立窗口。 */
public final class PoleDesignerPanel {
    private final PowerLineUiContext ctx;
    private PoleDesign draft;
    private final ImString designNameBuffer = new ImString(64);
    private final ImString saveAsNameBuffer = new ImString(64);
    private String pendingPresetId = "";
    private boolean presetConfirmPending = false;

    public PoleDesignerPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void open(String designId) {
        PoleDesignResolver resolver = ctx.designResolver();
        PoleDesign source = designId != null ? resolver.find(designId) : null;
        if (source != null) {
            draft = source.copy();
            ctx.state().setPoleDesignerEditingId(source.getId());
        } else {
            draft = newBlankDesign();
            ctx.state().setPoleDesignerEditingId("");
        }
        designNameBuffer.set(draft.getName());
        ctx.state().setPoleDesignerOpen(true);
    }

    public void render() {
        if (!ctx.state().isPoleDesignerOpen() || draft == null) {
            return;
        }

        ImGui.setNextWindowSize(480, 560, imgui.flag.ImGuiCond.FirstUseEver);
        if (!ImGui.begin(
                PlotI18n.tr("plugin.powerline.design.window", draft.getName()),
                ImGuiWindowFlags.None)) {
            ImGui.end();
            return;
        }

        renderPresetSelector();
        ImGui.separator();
        renderLayerList();
        ImGui.separator();
        PoleDesignPreviewRenderer.render(draft);
        ImGui.text(PlotI18n.tr("plugin.powerline.design.total_height", draft.totalHeight()));
        ImGui.separator();
        renderSaveActions();

        renderPresetConfirmPopup();
        ImGui.end();
    }

    private void renderPresetSelector() {
        List<PoleDesign> presets = PoleDesignCatalog.defaultDesigns();
        String[] labels = presets.stream()
            .map(d -> PlotI18n.tr("plugin.powerline.design.preset_label", d.getName()))
            .toArray(String[]::new);
        String[] ids = presets.stream().map(PoleDesign::getId).toArray(String[]::new);
        ImInt selected = new ImInt(0);
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.design.load_preset"), labels[selected.get()])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], selected.get() == i)) {
                    pendingPresetId = ids[i];
                    presetConfirmPending = true;
                }
            }
            ImGui.endCombo();
        }
    }

    private void renderLayerList() {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.layers"));
        for (int i = 0; i < draft.getLayers().size(); i++) {
            PoleLayer layer = draft.getLayers().get(i);
            ImGui.pushID(i);
            renderLayerRow(layer, i);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_layer"), 0, 0)) {
            draft.getLayers().add(new PoleLayer(
                PoleLayer.Shape.COLUMN,
                1,
                MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        }
    }

    private void renderLayerRow(PoleLayer layer, int index) {
        String[] shapeLabels = {
            PlotI18n.tr("plugin.powerline.design.shape.column"),
            PlotI18n.tr("plugin.powerline.design.shape.crossarm"),
            PlotI18n.tr("plugin.powerline.design.shape.cap")
        };
        ImInt shapeIndex = new ImInt(layer.getShape().ordinal());
        ImGui.setNextItemWidth(90);
        if (ImGui.combo("##shape", shapeIndex, shapeLabels)) {
            layer.setShape(PoleLayer.Shape.values()[shapeIndex.get()]);
        }
        ImGui.sameLine();

        ImInt height = new ImInt(layer.getHeight());
        ImGui.setNextItemWidth(60);
        if (ImGui.inputInt("##height", height)) {
            layer.setHeight(height.get());
        }
        ImGui.sameLine();

        if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
            ImInt armLength = new ImInt(layer.getCrossarmLength());
            ImGui.setNextItemWidth(60);
            if (ImGui.inputInt("##arm", armLength)) {
                layer.setCrossarmLength(armLength.get());
            }
            ImGui.sameLine();
        }

        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "layer_mat_" + index,
            "",
            layer.getMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            layer::setMaterial);
        ImGui.sameLine();

        if (index > 0 && ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.move_up"))) {
            PoleLayer current = draft.getLayers().remove(index);
            draft.getLayers().add(index - 1, current);
        }
        ImGui.sameLine();
        if (index < draft.getLayers().size() - 1 && ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.move_down"))) {
            PoleLayer current = draft.getLayers().remove(index);
            draft.getLayers().add(index + 1, current);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
            draft.getLayers().remove(index);
        }
    }

    private void renderSaveActions() {
        if (ImGui.inputText(PlotI18n.tr("plugin.powerline.design.name"), designNameBuffer)) {
            draft.setName(designNameBuffer.get());
        }

        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save"), 0, 0)) {
            saveDraft(false);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save_as"), 0, 0)) {
            saveAsNameBuffer.set(draft.getName() + " Copy");
            ImGui.openPopup("##pole_design_save_as");
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 0, 0)) {
            ctx.state().setPoleDesignerOpen(false);
        }

        if (ImGui.beginPopup("##pole_design_save_as")) {
            ImGui.inputText(PlotI18n.tr("plugin.powerline.design.save_as_name"), saveAsNameBuffer);
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                draft.setName(saveAsNameBuffer.get());
                designNameBuffer.set(draft.getName());
                saveDraft(true);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void saveDraft(boolean forceNewId) {
        if (forceNewId || PoleDesignCatalog.isBuiltinId(draft.getId())) {
            PoleDesign saved = new PoleDesign(draft.getName());
            saved.setLayers(draft.getLayers());
            ctx.actions().savePoleDesign(saved);
            ctx.state().setPoleDesignerEditingId(saved.getId());
            draft = saved.copy();
        } else {
            ctx.actions().savePoleDesign(draft);
        }
        designNameBuffer.set(draft.getName());
    }

    private void renderPresetConfirmPopup() {
        if (!presetConfirmPending) {
            return;
        }
        ImGui.openPopup("##pole_preset_confirm");
        presetConfirmPending = false;
        if (ImGui.beginPopupModal("##pole_preset_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.powerline.design.preset_confirm"));
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                PoleDesign preset = PoleDesignCatalog.findBuiltin(pendingPresetId);
                if (preset != null) {
                    draft = preset.copy();
                    designNameBuffer.set(draft.getName());
                    ctx.state().setPoleDesignerEditingId("");
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private static PoleDesign newBlankDesign() {
        PoleDesign design = new PoleDesign(PlotI18n.tr("plugin.powerline.design.new_name"));
        design.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        return design;
    }
}
