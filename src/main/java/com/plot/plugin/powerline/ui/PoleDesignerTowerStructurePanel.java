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

    private PendingStructurePreset pendingPreset;
    private boolean replaceConfirmPending;

    void renderInspectSummary(PoleDesign draft) {
        TowerStructureDesign structure = draft.getTowerStructure();
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.design.tower_structure_inspect_stations",
            structure.getStations().size()));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.design.tower_structure_inspect_arms",
            structure.getArms().size()));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.design.tower_structure_inspect_decorations",
            structure.getDecorations().size()));
    }

    void renderInspectDetails(PoleDesign draft) {
        TowerStructureDesign structure = draft.getTowerStructure();
        List<TowerStation> stations = structure.sortedStations();
        for (int i = 0; i < stations.size(); i++) {
            TowerStation station = stations.get(i);
            PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                "plugin.powerline.design.tower_structure_inspect_station_row",
                i + 1,
                (int) Math.round(station.getHeight()),
                station.getHalfWidth(),
                station.getHalfDepth()));
        }
        List<TowerArm> arms = TowerArmAttachmentBinding.sortedArms(structure);
        for (int i = 0; i < arms.size(); i++) {
            TowerArm arm = arms.get(i);
            PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                "plugin.powerline.design.tower_structure_inspect_arm_row",
                i + 1,
                (int) Math.round(arm.getBaseHeight()),
                arm.getLateralReach()));
        }
    }

    void render(PoleDesign draft, Runnable pushDraftSnapshot) {
        TowerStructureDesign structure = draft.getTowerStructure();
        renderReplaceStructurePresets(draft, pushDraftSnapshot);

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_stations"));
        List<TowerStation> sortedStations = structure.sortedStations();
        for (int i = 0; i < sortedStations.size(); i++) {
            TowerStation station = sortedStations.get(i);
            ImGui.pushID("station_" + station.getId());
            renderStationRow(station, structure, i, sortedStations.size(), pushDraftSnapshot);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_station"), 0, 0)) {
            pushDraftSnapshot.run();
            double nextHeight = structure.maxHeight() + 8;
            structure.addStation(new TowerStation(null, nextHeight, 2, 2));
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

    private void renderReplaceStructurePresets(PoleDesign draft, Runnable pushDraftSnapshot) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_replace_header"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_lattice"), 0, 0)) {
            requestReplacePreset(PendingStructurePreset.LATTICE);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_mega"), 0, 0)) {
            requestReplacePreset(PendingStructurePreset.MEGA);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_monster"), 0, 0)) {
            requestReplacePreset(PendingStructurePreset.MONSTER);
        }
        renderReplaceConfirmPopup(draft, pushDraftSnapshot);
    }

    private void requestReplacePreset(PendingStructurePreset preset) {
        pendingPreset = preset;
        replaceConfirmPending = true;
        ImGui.openPopup("##tower_replace_structure_confirm");
    }

    private void renderReplaceConfirmPopup(PoleDesign draft, Runnable pushDraftSnapshot) {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##tower_replace_structure_confirm",
                replaceConfirmPending,
                () -> replaceConfirmPending = false)) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_replace_confirm"));
        if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
            applyPendingPreset(draft, pushDraftSnapshot);
            pendingPreset = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            pendingPreset = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void applyPendingPreset(PoleDesign draft, Runnable pushDraftSnapshot) {
        if (pendingPreset == null) {
            return;
        }
        pushDraftSnapshot.run();
        switch (pendingPreset) {
            case LATTICE -> {
                draft.setTowerStructure(TowerStructurePresets.taperedLatticeTower());
                syncLatticePresetAttachments(draft, draft.getTowerStructure());
            }
            case MEGA -> {
                draft.setTowerStructure(TowerStructurePresets.megaLatticeTower());
                TowerArmAttachmentBinding.inferArmBindings(draft);
            }
            case MONSTER -> {
                draft.setTowerStructure(TowerStructurePresets.monsterPylonTower());
                TowerArmAttachmentBinding.inferArmBindings(draft);
            }
            default -> {
            }
        }
    }

    private enum PendingStructurePreset {
        LATTICE,
        MEGA,
        MONSTER
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
                PowerLineUiFormat.SLIDER)) {
            decoration.setBaseHeight(baseHeight[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        if (decoration.getKind() == TowerDecorationKind.ANTENNA
                || decoration.getKind() == TowerDecorationKind.PLATFORM
                || decoration.getKind() == TowerDecorationKind.GEAR_RING
                || decoration.getKind() == TowerDecorationKind.HANGING_CHAIN) {
            float[] size = {(float) decoration.getSize()};
            String sizeKey = switch (decoration.getKind()) {
                case PLATFORM -> "plugin.powerline.design.decoration_platform_radius";
                case GEAR_RING -> "plugin.powerline.design.decoration_gear_radius";
                case HANGING_CHAIN -> "plugin.powerline.design.decoration_chain_drop";
                default -> "plugin.powerline.design.decoration_antenna_height";
            };
            if (PoleDesignerFormRows.sliderFloat(sizeKey, "##deco_size", size, 0.5f, 32f, PowerLineUiFormat.SLIDER)) {
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
            || decoration.getKind() == TowerDecorationKind.GEAR_RING
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
                PowerLineUiFormat.SLIDER)) {
            arm.setBaseHeight(baseHeight[0]);
            TowerArmAttachmentBinding.syncAttachmentsForArm(arm, draft.getAttachments(), structure);
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
                PowerLineUiFormat.SLIDER)) {
            arm.setLateralReach(reach[0]);
            TowerArmAttachmentBinding.syncAttachmentsForArm(arm, draft.getAttachments(), structure);
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
                PowerLineUiFormat.SLIDER)) {
            arm.setVerticalDrop(verticalDrop[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_height") + "##sync_h", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.syncAttachmentsForArm(arm, draft.getAttachments(), structure);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_spread") + "##sync_s", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.syncBoundLateralSpread(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot.run();
            TowerArmAttachmentBinding.releaseAttachmentsFromArm(
                structure,
                draft.getAttachments(),
                arm.getId());
            structure.removeArm(arm.getId());
        }
        DialogLayoutHelper.endForm();
    }

    private void renderStationRow(
            TowerStation station,
            TowerStructureDesign structure,
            int sortedIndex,
            int stationCount,
            Runnable pushDraftSnapshot) {
        if (!DialogLayoutHelper.beginForm("##station_form")) {
            return;
        }
        float[] height = {(float) station.getHeight()};
        float minHeight = stationHeightMin(structure, sortedIndex);
        float maxHeight = stationHeightMax(structure, sortedIndex, stationCount);
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_height", "##h", height, minHeight, maxHeight, PowerLineUiFormat.SLIDER)) {
            station.setHeight(height[0]);
            structure.rebuildOrReconcileBays();
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] width = {(float) station.getHalfWidth()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_width", "##w", width, 0.5f, 16f, PowerLineUiFormat.SLIDER)) {
            station.setHalfWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }

        float[] depth = {(float) station.getHalfDepth()};
        if (PoleDesignerFormRows.sliderFloat(
                "plugin.powerline.design.station_depth", "##d", depth, 0.5f, 16f, PowerLineUiFormat.SLIDER)) {
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

    private static float stationHeightMin(TowerStructureDesign structure, int sortedIndex) {
        if (sortedIndex <= 0) {
            return 1f;
        }
        return (float) structure.sortedStations().get(sortedIndex - 1).getHeight() + 1f;
    }

    private static float stationHeightMax(TowerStructureDesign structure, int sortedIndex, int stationCount) {
        if (sortedIndex >= stationCount - 1) {
            return 256f;
        }
        return (float) structure.sortedStations().get(sortedIndex + 1).getHeight() - 1f;
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
