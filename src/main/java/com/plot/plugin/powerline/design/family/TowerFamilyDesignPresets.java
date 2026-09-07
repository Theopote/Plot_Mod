package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.List;

/** 塔型族内各角色的 PoleDesign 预设。 */
public final class TowerFamilyDesignPresets {
    public static final String LATTICE_SUSPENSION_ID = "preset/lattice_suspension";
    public static final String LATTICE_ANGLE_ID = "preset/lattice_angle";
    public static final String LATTICE_DEAD_END_ID = "preset/lattice_dead_end";
    public static final String LATTICE_TERMINAL_ID = "preset/lattice_terminal";
    public static final String GROUND_WIRE_ID = "ground_wire";

    private TowerFamilyDesignPresets() {
    }

    public static PoleDesign latticeSuspension() {
        return buildRoleDesign(
            LATTICE_SUSPENSION_ID,
            "Lattice Suspension Tower",
            5,
            6,
            2,
            18,
            InsulatorType.SUSPENSION,
            2);
    }

    public static PoleDesign latticeAngle() {
        return buildRoleDesign(
            LATTICE_ANGLE_ID,
            "Lattice Angle Tower",
            6,
            7,
            3,
            18,
            InsulatorType.STRAIN,
            3);
    }

    public static PoleDesign latticeDeadEnd() {
        return buildRoleDesign(
            LATTICE_DEAD_END_ID,
            "Lattice Dead-End Tower",
            6,
            7,
            3,
            18,
            InsulatorType.STRAIN,
            4);
    }

    public static PoleDesign latticeTerminal() {
        return buildRoleDesign(
            LATTICE_TERMINAL_ID,
            "Lattice Terminal Tower",
            5,
            6,
            3,
            18,
            InsulatorType.STRAIN,
            3);
    }

    private static PoleDesign buildRoleDesign(
            String id,
            String name,
            double baseWidth,
            double armReach,
            int insulatorLength,
            double attachmentHeight,
            InsulatorType insulatorType,
            int verticalDrop) {
        PoleDesign design = new PoleDesign(id, name);
        TowerStructureDesign structure = TowerStructurePresets.taperedLatticeTower();
        List<TowerStation> stations = structure.sortedStations();
        for (TowerStation station : stations) {
            station.setHalfWidth(baseWidth * station.getHalfWidth() / 5.0);
            station.setHalfDepth(baseWidth * station.getHalfDepth() / 5.0);
        }
        structure.getArms().clear();
        TowerArm arm = new TowerArm("arm_main", attachmentHeight - 1, armReach);
        arm.setSide(TowerArmSide.BOTH);
        arm.setVerticalDrop(verticalDrop);
        structure.addArm(arm);
        design.setTowerStructure(structure);

        List<ConductorAttachment> attachments = new ArrayList<>(
            ConductorAttachmentPresets.threePhaseHorizontal(attachmentHeight, -6, 0, 6));
        ConductorAttachment ground = new ConductorAttachment(GROUND_WIRE_ID, "GW");
        ground.setRole(AttachmentRole.GROUND_WIRE);
        ground.setVerticalOffset(attachmentHeight + 4);
        ground.setInsulatorLength(1);
        ground.setInsulatorType(insulatorType);
        attachments.add(ground);

        for (ConductorAttachment attachment : attachments) {
            attachment.setInsulatorLength(insulatorLength);
            attachment.setInsulatorType(insulatorType);
        }
        design.setAttachments(attachments);
        return design;
    }
}
