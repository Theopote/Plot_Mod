package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationCatalog;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.List;

/** 杆塔手动结构编辑器（站、臂、装饰）。 */
final class PoleDesignerTowerStructurePanel {

    void render(PoleDesign draft, Runnable pushDraftSnapshot) {
        TowerStructureDesign structure = draft.getTowerStructure();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_lattice"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setTowerStructure(TowerStructurePresets.taperedLatticeTower());
            syncLatticePresetAttachments(draft, draft.getTowerStructure());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_mega"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setTowerStructure(TowerStructurePresets.megaLatticeTower());
            TowerArmAttachmentBinding.inferArmBindings(draft);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_monster"), 0, 0)) {
            pushDraftSnapshot.run();
            draft.setTowerStructure(TowerStructurePresets.monsterPylonTower());
            TowerArmAttachmentBinding.inferArmBindings(draft);
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_stations"));
        for (int i = 0; i < structure.getStations().size(); i++) {
            TowerStation station = structure.getStations().get(i);
            ImGui.pushID("station_" + i);
            renderStationRow(station, structure, pushDraftSnapshot);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_station"), 0, 0)) {
            pushDraftSnapshot.run();
            double nextHeight = structure.maxHeight() + 8;
            structure.addStation(new TowerStation(null, nextHeight, 2, 2));
            structure.setBays(TowerStructurePresets.defaultBaysForStations(structure.getStations()));
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_arms"));
        List<TowerArm> sortedArms = TowerArmAttachmentBinding.sortedArms(structure);
        for (int i = 0; i < sortedArms.size(); i++) {
            TowerArm arm = sortedArms.get(i);
            ImGui.pushID("arm_" + i);
            int boundCount = countAttachmentsForArm(draft, arm.getId());
            String armHeader = PlotI18n.tr(
                "plugin.powerline.design.arm_deck_header",
                i + 1,
                (int) Math.round(arm.getBaseHeight()),
                boundCount);
            ImGui.setNextItemOpen(i == 0, imgui.flag.ImGuiCond.FirstUseEver);
            if (ImGui.collapsingHeader(armHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
                renderArmControls(draft, structure, arm, pushDraftSnapshot);
                renderArmDeckActions(draft, arm, pushDraftSnapshot);
            }
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_arm"), 0, 0)) {
            pushDraftSnapshot.run();
            TowerArm arm = new TowerArm("arm_" + structure.getArms().size(), structure.maxHeight() - 2, 6);
            arm.setVerticalDrop(4);
            structure.addArm(arm);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_arm_with_deck"), 0, 0)) {
            pushDraftSnapshot.run();
            TowerArm arm = new TowerArm("arm_" + structure.getArms().size(), structure.maxHeight() - 2, 6);
            arm.setVerticalDrop(4);
            structure.addArm(arm);
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(arm)) {
                draft.addAttachment(attachment);
            }
        }

        renderDecorationSection(draft, structure, pushDraftSnapshot);
    }

    private void renderDecorationSection(
            PoleDesign draft,
            TowerStructureDesign structure,
            Runnable pushDraftSnapshot) {
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_decorations"));
        for (int i = 0; i < structure.getDecorations().size(); i++) {
            TowerDecoration decoration = structure.getDecorations().get(i);
            ImGui.pushID("deco_" + i);
            renderDecorationRow(draft, decoration, pushDraftSnapshot);
            ImGui.popID();
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_beacon"))) {
            pushDraftSnapshot.run();
            structure.addDecoration(TowerDecorationCatalog.beaconAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_antenna"))) {
            pushDraftSnapshot.run();
            structure.addDecoration(TowerDecorationCatalog.antennaAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_platform"))) {
            pushDraftSnapshot.run();
            structure.addDecoration(TowerDecorationCatalog.platformAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_warning_light"))) {
            pushDraftSnapshot.run();
            structure.addDecoration(TowerDecorationCatalog.warningLightAtTop(structure.maxHeight()));
        }
    }

    private void renderDecorationRow(
            PoleDesign draft,
            TowerDecoration decoration,
            Runnable pushDraftSnapshot) {
        if (!DialogLayoutHelper.beginForm("##deco_form")) {
            return;
        }
        TowerDecorationKind[] kinds = TowerDecorationKind.values();
        String[] kindLabels = new String[kinds.length];
        int selectedKind = 0;
        for (int i = 0; i < kinds.length; i++) {
            kindLabels[i] = PlotI18n.tr(kinds[i].labelKey());
            if (decoration.getKind() == kinds[i]) {
                selectedKind = i;
            }
        }
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.decoration_kind"));
        ImInt kindIndex = new ImInt(selectedKind);
        if (ImGui.combo("##deco_kind", kindIndex, kindLabels)) {
            pushDraftSnapshot.run();
            decoration.setKind(kinds[kindIndex.get()]);
        }

        float[] baseHeight = {(float) decoration.getBaseHeight()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.structure_arm_height",
                "##deco_height",
                baseHeight,
                0f,
                256f,
                "%.1f")) {
            decoration.setBaseHeight(baseHeight[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        if (decoration.getKind() == TowerDecorationKind.ANTENNA
                || decoration.getKind() == TowerDecorationKind.PLATFORM) {
            float[] size = {(float) decoration.getSize()};
            String sizeKey = decoration.getKind() == TowerDecorationKind.PLATFORM
                ? "plugin.powerline.design.decoration_platform_radius"
                : "plugin.powerline.design.decoration_antenna_height";
            if (PoleDesignerFormRows.sliderFloat(sizeKey, "##deco_size", size, 0.5f, 32f, "%.1f")) {
                decoration.setSize(size[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot.run();
            }
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.decoration_snap_top") + "##snap", 0, 0)) {
            pushDraftSnapshot.run();
            decoration.setBaseHeight(structureTopSnapHeight(draft, decoration));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot.run();
            draft.getTowerStructure().removeDecoration(decoration.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
    }

    private double structureTopSnapHeight(PoleDesign draft, TowerDecoration decoration) {
        TowerStructureDesign structure = draft.getTowerStructure();
        double top = structure.maxHeight();
        return decoration.getKind() == TowerDecorationKind.ANTENNA
            || decoration.getKind() == TowerDecorationKind.PLATFORM
            ? top
            : top + 1;
    }

    private void renderArmControls(
            PoleDesign draft,
            TowerStructureDesign structure,
            TowerArm arm,
            Runnable pushDraftSnapshot) {
        if (!DialogLayoutHelper.beginForm("##arm_form")) {
            return;
        }
        float[] baseHeight = {(float) arm.getBaseHeight()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.structure_arm_height",
                "##arm_height",
                baseHeight,
                0f,
                256f,
                "%.1f")) {
            arm.setBaseHeight(baseHeight[0]);
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] reach = {(float) arm.getLateralReach()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.structure_arm_reach",
                "##arm_reach",
                reach,
                1f,
                32f,
                "%.1f")) {
            arm.setLateralReach(reach[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] verticalDrop = {(float) arm.getVerticalDrop()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.structure_arm_drop",
                "##arm_drop",
                verticalDrop,
                0f,
                32f,
                "%.1f")) {
            arm.setVerticalDrop(verticalDrop[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_height") + "##sync_h", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_spread") + "##sync_s", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.syncBoundLateralSpread(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.clearArmBindings(draft.getAttachments(), arm.getId());
            structure.removeArm(arm.getId());
        }
        DialogLayoutHelper.endForm();
    }

    private void renderStationRow(
            TowerStation station,
            TowerStructureDesign structure,
            Runnable pushDraftSnapshot) {
        if (!DialogLayoutHelper.beginForm("##station_form")) {
            return;
        }
        float[] height = {(float) station.getHeight()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_height", "##h", height, 1f, 256f, "%.1f")) {
            station.setHeight(height[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] width = {(float) station.getHalfWidth()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_width", "##w", width, 0.5f, 16f, "%.1f")) {
            station.setHalfWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] depth = {(float) station.getHalfDepth()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_depth", "##d", depth, 0.5f, 16f, "%.1f")) {
            station.setHalfDepth(depth[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot.run();
            structure.removeStation(station.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
    }

    private void renderArmDeckActions(PoleDesign draft, TowerArm arm, Runnable pushDraftSnapshot) {
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_add_3phase"))) {
            pushDraftSnapshot.run();
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(arm)) {
                draft.addAttachment(attachment);
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_add_bundled_3phase"))) {
            pushDraftSnapshot.run();
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createBundledThreePhaseDeck(arm, 2)) {
                draft.addAttachment(attachment);
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_clear_deck"))) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.removeArmAttachments(draft, arm.getId());
        }
    }

    private int countAttachmentsForArm(PoleDesign draft, String armId) {
        int count = 0;
        for (ConductorAttachment attachment : draft.getAttachments()) {
            if (armId.equals(attachment.getArmId())) {
                count++;
            }
        }
        return count;
    }

    private void syncLatticePresetAttachments(PoleDesign draft, TowerStructureDesign structure) {
        draft.clearAttachments();
        TowerArm mainArm = structure.getArms().isEmpty() ? null : structure.getArms().getFirst();
        if (mainArm != null) {
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(mainArm)) {
                draft.addAttachment(attachment);
            }
        } else {
            draft.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(18.0, -6, 0, 6));
        }
    }
}
