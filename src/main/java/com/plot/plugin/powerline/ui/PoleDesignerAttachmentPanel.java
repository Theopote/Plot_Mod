package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.AttachmentBindingMode;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;
import java.util.Map;

/** 导线挂点编辑器。 */
final class PoleDesignerAttachmentPanel {

    void render(PoleDesign draft, Runnable pushDraftSnapshot) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.attachments"));
        if (draft.hasTowerStructure()) {
            renderTowerAttachmentDecks(draft, pushDraftSnapshot);
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_single"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setAttachments(ConductorAttachmentPresets.singleConductor(12.0));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_3phase_h"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12.0));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_3phase_v"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setAttachments(ConductorAttachmentPresets.threePhaseVertical(12.0));
        }

        for (int i = 0; i < draft.getAttachments().size(); i++) {
            ConductorAttachment attachment = draft.getAttachments().get(i);
            ImGui.pushID("att_" + attachment.getId());
            renderAttachmentRow(draft, attachment, null, pushDraftSnapshot);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_attachment"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.addAttachment(new ConductorAttachment());
        }
    }

    private void renderTowerAttachmentDecks(PoleDesign draft, Runnable pushDraftSnapshot) {
        Map<String, List<ConductorAttachment>> grouped = TowerArmAttachmentBinding.groupByArm(draft);
        TowerStructureDesign structure = draft.getTowerStructure();
        List<TowerArm> arms = TowerArmAttachmentBinding.sortedArms(structure);
        for (TowerArm arm : arms) {
            List<ConductorAttachment> deck = grouped.getOrDefault(arm.getId(), List.of());
            ImGui.pushID("deck_" + arm.getId());
            if (ImGui.treeNode(PlotI18n.tr(
                "plugin.powerline.design.arm_attachment_deck",
                arm.getId(),
                deck.size()))) {
                for (ConductorAttachment deckAttachment : deck) {
                    ImGui.pushID("att_" + deckAttachment.getId());
                    renderAttachmentRow(draft, deckAttachment, arm, pushDraftSnapshot);
                    ImGui.popID();
                }
                ImGui.treePop();
            }
            ImGui.popID();
        }
        List<ConductorAttachment> unassigned = grouped.get(null);
        if (unassigned != null && !unassigned.isEmpty()) {
            if (ImGui.treeNode(PlotI18n.tr(
                "plugin.powerline.design.arm_unassigned_attachments",
                unassigned.size()))) {
                for (ConductorAttachment freeAttachment : unassigned) {
                    ImGui.pushID("free_" + freeAttachment.getId());
                    renderAttachmentRow(draft, freeAttachment, null, pushDraftSnapshot);
                    ImGui.popID();
                }
                ImGui.treePop();
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_add_top_wires"), 0, 0)) {
            pushDraftSnapshot.run();
            double lift = structure.maxHeight() - 4;
            for (ConductorAttachment wire : ConductorAttachmentPresets.twinTopWires(lift, 2.5)) {
                draft.addAttachment(wire);
            }
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_attachment"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.addAttachment(new ConductorAttachment());
        }
    }

    private void renderAttachmentRow(
            PoleDesign draft,
            ConductorAttachment attachment,
            TowerArm boundArm,
            Runnable pushDraftSnapshot) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.attachment_row", attachment.getName()));
        if (!DialogLayoutHelper.beginForm("##attachment_form")) {
            return;
        }
        if (draft.hasTowerStructure()) {
            renderAttachmentArmBindingRow(draft, attachment, boundArm, pushDraftSnapshot);
        }

        if (attachment.isBound()) {
            float[] normalized = {(float) attachment.getNormalizedPosition()};
            if (PoleDesignerFormRows.sliderFloat(
                    "plugin.powerline.design.attachment_normalized_position",
                    "normalized",
                    normalized,
                    -1f,
                    1f,
                    "%.2f")) {
                attachment.setNormalizedPosition(normalized[0]);
                TowerArmAttachmentBinding.refreshBoundCache(attachment, draft.getTowerStructure());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }

            float[] anchor = {(float) attachment.getVerticalAnchorOffset()};
            if (PoleDesignerFormRows.sliderFloat(
                    "plugin.powerline.design.attachment_vertical_anchor",
                    "anchor",
                    anchor,
                    -8f,
                    8f,
                    "%.1f")) {
                attachment.setVerticalAnchorOffset(anchor[0]);
                TowerArmAttachmentBinding.refreshBoundCache(attachment, draft.getTowerStructure());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }

            TowerArmAttachmentBinding.ResolvedLocalOffsets resolved =
                TowerArmAttachmentBinding.resolveLocalOffsets(attachment, draft.getTowerStructure());
            PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                "plugin.powerline.design.attachment_resolved_offsets",
                resolved.lateral(),
                resolved.vertical()));
        } else {
            float[] lateral = {(float) attachment.getLateralOffset()};
            if (PoleDesignerFormRows.sliderFloat(
                    "plugin.powerline.design.attachment_lateral",
                    "lateral",
                    lateral,
                    -8f,
                    8f,
                    "%.1f")) {
                attachment.setLateralOffset(lateral[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }

            float[] vertical = {(float) attachment.getVerticalOffset()};
            if (PoleDesignerFormRows.sliderFloat(
                    "plugin.powerline.design.attachment_vertical",
                    "vertical",
                    vertical,
                    1f,
                    64f,
                    "%.1f")) {
                attachment.setVerticalOffset(vertical[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }
        }

        float[] longitudinal = {(float) attachment.getLongitudinalOffset()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.attachment_longitudinal",
                "longitudinal",
                longitudinal,
                -4f,
                4f,
                "%.1f")) {
            attachment.setLongitudinalOffset(longitudinal[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        int[] insulatorLength = {attachment.getInsulatorLength()};
        if (PoleDesignerFormRows.sliderInt(
                "plugin.powerline.design.attachment_insulator",
                "##insulator",
                insulatorLength,
                0,
                16)) {
            attachment.setInsulatorLength(insulatorLength[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot.run();
            draft.removeAttachment(attachment.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
    }

    private void renderAttachmentArmBindingRow(
            PoleDesign draft,
            ConductorAttachment attachment,
            TowerArm boundArm,
            Runnable pushDraftSnapshot) {
        if (boundArm != null) {
            DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.attachment_arm_bind"));
            PowerLineUiWidgets.textColored(0xFF90CAF9, PlotI18n.tr(
                "plugin.powerline.design.attachment_bound_arm",
                boundArm.getId()));
            return;
        }
        List<TowerArm> arms = TowerArmAttachmentBinding.sortedArms(draft.getTowerStructure());
        if (arms.isEmpty()) {
            return;
        }
        int selected = 0;
        String currentArmId = attachment.getArmId();
        for (int i = 0; i < arms.size(); i++) {
            if (arms.get(i).getId().equals(currentArmId)) {
                selected = i + 1;
                break;
            }
        }
        String[] labels = new String[arms.size() + 1];
        labels[0] = PlotI18n.tr("plugin.powerline.design.attachment_arm_none");
        for (int i = 0; i < arms.size(); i++) {
            labels[i + 1] = PlotI18n.tr(
                "plugin.powerline.design.attachment_arm_option",
                i + 1,
                (int) Math.round(arms.get(i).getBaseHeight()));
        }
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.attachment_arm_bind"));
        ImInt armIndex = new ImInt(selected);
        if (ImGui.combo("##arm_bind", armIndex, labels)) {
            pushDraftSnapshot.run();
            if (armIndex.get() == 0) {
                attachment.setArmId(null);
                attachment.setBindingMode(AttachmentBindingMode.FREE);
            } else {
                TowerArm arm = arms.get(armIndex.get() - 1);
                TowerArmAttachmentBinding.bindToArm(arm, attachment);
            }
        }
    }
}
