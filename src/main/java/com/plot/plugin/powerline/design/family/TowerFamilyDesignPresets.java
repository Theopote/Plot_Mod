package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.engineering.TowerEngineeringMetadata;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.EnumSet;
import java.util.List;

/** 塔型族内各角色的 PoleDesign 预设。 */
public final class TowerFamilyDesignPresets {
    public static final String LATTICE_SUSPENSION_ID = "preset/lattice_suspension";
    public static final String LATTICE_ANGLE_ID = "preset/lattice_angle";
    public static final String LATTICE_DEAD_END_ID = "preset/lattice_dead_end";
    public static final String LATTICE_TERMINAL_ID = "preset/lattice_terminal";
    public static final String LATTICE_SUSPENSION_SMALL_ID = "preset/lattice_suspension_s";
    public static final String LATTICE_SUSPENSION_MEDIUM_ID = "preset/lattice_suspension_m";
    public static final String LATTICE_SUSPENSION_TALL_ID = "preset/lattice_suspension_l";
    public static final String HV_TRANSMISSION_SUSPENSION_ID = "preset/hv_transmission_suspension";
    public static final String HV_TRANSMISSION_ANGLE_ID = "preset/hv_transmission_angle";
    public static final String HV_TRANSMISSION_DEAD_END_ID = "preset/hv_transmission_dead_end";
    public static final String HV_TRANSMISSION_TERMINAL_ID = "preset/hv_transmission_terminal";
    public static final String MEGA_LATTICE_SUSPENSION_ID = "preset/mega_lattice_suspension";
    public static final String MEGA_LATTICE_ANGLE_ID = "preset/mega_lattice_angle";
    public static final String MEGA_LATTICE_DEAD_END_ID = "preset/mega_lattice_dead_end";
    public static final String MEGA_LATTICE_TERMINAL_ID = "preset/mega_lattice_terminal";
    public static final String HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID = "preset/heavy_double_circuit_suspension";
    public static final String HEAVY_DOUBLE_CIRCUIT_ANGLE_ID = "preset/heavy_double_circuit_angle";
    public static final String HEAVY_DOUBLE_CIRCUIT_DEAD_END_ID = "preset/heavy_double_circuit_dead_end";
    public static final String HEAVY_DOUBLE_CIRCUIT_TERMINAL_ID = "preset/heavy_double_circuit_terminal";
    public static final String INDUSTRIAL_PORTAL_SUSPENSION_ID = "preset/industrial_portal_suspension";
    public static final String INDUSTRIAL_PORTAL_ANGLE_ID = "preset/industrial_portal_angle";
    public static final String INDUSTRIAL_PORTAL_DEAD_END_ID = "preset/industrial_portal_dead_end";
    public static final String INDUSTRIAL_PORTAL_TERMINAL_ID = "preset/industrial_portal_terminal";
    public static final String MONSTER_PYLON_SUSPENSION_ID = "preset/monster_pylon_suspension";
    public static final String MONSTER_PYLON_ANGLE_ID = "preset/monster_pylon_angle";
    public static final String MONSTER_PYLON_DEAD_END_ID = "preset/monster_pylon_dead_end";
    public static final String MONSTER_PYLON_TERMINAL_ID = "preset/monster_pylon_terminal";
    /** 塔顶装饰线挂点 id（视觉顶线，非电气接地）。 */
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
            2,
            metadata(TowerRole.SUSPENSION, 18, 40, 5));
    }

    public static PoleDesign latticeSuspensionSmall() {
        return buildRoleDesign(
            LATTICE_SUSPENSION_SMALL_ID,
            "Lattice Suspension S",
            4,
            5,
            2,
            16,
            InsulatorType.SUSPENSION,
            2,
            metadata(TowerRole.SUSPENSION, 16, 30, 5));
    }

    public static PoleDesign latticeSuspensionMedium() {
        return buildRoleDesign(
            LATTICE_SUSPENSION_MEDIUM_ID,
            "Lattice Suspension M",
            5,
            6,
            2,
            20,
            InsulatorType.SUSPENSION,
            2,
            metadata(TowerRole.SUSPENSION, 20, 40, 5));
    }

    public static PoleDesign latticeSuspensionTall() {
        return buildRoleDesign(
            LATTICE_SUSPENSION_TALL_ID,
            "Lattice Suspension L",
            6,
            7,
            2,
            26,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 26, 50, 5));
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
            3,
            metadata(TowerRole.ANGLE, 20, 45, 60));
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
            4,
            metadata(TowerRole.DEAD_END, 22, 45, 90));
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
            3,
            metadata(TowerRole.TERMINAL, 20, 40, 90));
    }

    public static PoleDesign hvTransmissionSuspension() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_SUSPENSION_ID,
            "HV Transmission Suspension",
            8,
            10,
            3,
            24,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 24, 60, 5));
    }

    public static PoleDesign hvTransmissionAngle() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_ANGLE_ID,
            "HV Transmission Angle",
            8,
            10,
            3,
            24,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.ANGLE, 26, 55, 60));
    }

    public static PoleDesign hvTransmissionDeadEnd() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_DEAD_END_ID,
            "HV Transmission Dead-End",
            8,
            10,
            3,
            24,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.DEAD_END, 28, 55, 90));
    }

    public static PoleDesign hvTransmissionTerminal() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_TERMINAL_ID,
            "HV Transmission Terminal",
            8,
            10,
            3,
            24,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.TERMINAL, 24, 50, 90));
    }

    public static PoleDesign megaLatticeSuspension() {
        return buildMegaIndustrialRoleDesign(
            MEGA_LATTICE_SUSPENSION_ID,
            "Mega Lattice Suspension",
            TowerStructurePresets.megaLatticeTower(),
            TowerConductorArrangement.megaIndustrial(),
            50,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 52, 90, 5));
    }

    public static PoleDesign megaLatticeAngle() {
        return buildMegaIndustrialRoleDesign(
            MEGA_LATTICE_ANGLE_ID,
            "Mega Lattice Angle",
            TowerStructurePresets.megaLatticeTower(),
            TowerConductorArrangement.megaIndustrial(),
            50,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 54, 85, 60));
    }

    public static PoleDesign megaLatticeDeadEnd() {
        return buildMegaIndustrialRoleDesign(
            MEGA_LATTICE_DEAD_END_ID,
            "Mega Lattice Dead-End",
            TowerStructurePresets.megaLatticeTower(),
            TowerConductorArrangement.megaIndustrial(),
            50,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 56, 85, 90));
    }

    public static PoleDesign megaLatticeTerminal() {
        return buildMegaIndustrialRoleDesign(
            MEGA_LATTICE_TERMINAL_ID,
            "Mega Lattice Terminal",
            TowerStructurePresets.megaLatticeTower(),
            TowerConductorArrangement.megaIndustrial(),
            50,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 52, 80, 90));
    }

    public static PoleDesign heavyDoubleCircuitSuspension() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID,
            "Heavy Double-Circuit Suspension",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            48,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 54, 100, 5));
    }

    public static PoleDesign heavyDoubleCircuitAngle() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_ANGLE_ID,
            "Heavy Double-Circuit Angle",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            48,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 56, 95, 60));
    }

    public static PoleDesign heavyDoubleCircuitDeadEnd() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_DEAD_END_ID,
            "Heavy Double-Circuit Dead-End",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            48,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 58, 95, 90));
    }

    public static PoleDesign heavyDoubleCircuitTerminal() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_TERMINAL_ID,
            "Heavy Double-Circuit Terminal",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            48,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 54, 90, 90));
    }

    public static PoleDesign industrialPortalSuspension() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_SUSPENSION_ID,
            "Industrial Portal Suspension",
            TowerStructurePresets.industrialPortalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            38,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 50, 110, 5));
    }

    public static PoleDesign industrialPortalAngle() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_ANGLE_ID,
            "Industrial Portal Angle",
            TowerStructurePresets.industrialPortalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            38,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 52, 105, 60));
    }

    public static PoleDesign industrialPortalDeadEnd() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_DEAD_END_ID,
            "Industrial Portal Dead-End",
            TowerStructurePresets.industrialPortalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            38,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 54, 105, 90));
    }

    public static PoleDesign industrialPortalTerminal() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_TERMINAL_ID,
            "Industrial Portal Terminal",
            TowerStructurePresets.industrialPortalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            38,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 50, 100, 90));
    }

    public static PoleDesign monsterPylonSuspension() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_SUSPENSION_ID,
            "Monster Pylon Suspension",
            TowerStructurePresets.monsterPylonTower(),
            ConductorArrangement.monsterQuadCircuit(),
            72,
            InsulatorType.SUSPENSION,
            5,
            metadata(TowerRole.SUSPENSION, 82, 140, 5));
    }

    public static PoleDesign monsterPylonAngle() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_ANGLE_ID,
            "Monster Pylon Angle",
            TowerStructurePresets.monsterPylonTower(),
            ConductorArrangement.monsterQuadCircuit(),
            72,
            InsulatorType.STRAIN,
            6,
            metadata(TowerRole.ANGLE, 86, 130, 60));
    }

    public static PoleDesign monsterPylonDeadEnd() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_DEAD_END_ID,
            "Monster Pylon Dead-End",
            TowerStructurePresets.monsterPylonTower(),
            ConductorArrangement.monsterQuadCircuit(),
            72,
            InsulatorType.STRAIN,
            6,
            metadata(TowerRole.DEAD_END, 88, 130, 90));
    }

    public static PoleDesign monsterPylonTerminal() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_TERMINAL_ID,
            "Monster Pylon Terminal",
            TowerStructurePresets.monsterPylonTower(),
            ConductorArrangement.monsterQuadCircuit(),
            72,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.TERMINAL, 82, 120, 90));
    }

    private static TowerEngineeringMetadata metadata(
            TowerRole role,
            double height,
            double maxSpan,
            double maxAngle) {
        TowerEngineeringMetadata metadata = new TowerEngineeringMetadata();
        metadata.setNominalHeight(height);
        metadata.setPreferredSpan(Math.min(maxSpan, 20));
        metadata.setMaxRecommendedSpan(maxSpan);
        metadata.setMaxRecommendedDeflectionAngle(maxAngle);
        metadata.setSupportedRoles(EnumSet.of(role));
        return metadata;
    }

    private static PoleDesign buildRoleDesign(
            String id,
            String name,
            double baseWidth,
            double armReach,
            int insulatorLength,
            double attachmentHeight,
            InsulatorType insulatorType,
            int verticalDrop,
            TowerEngineeringMetadata metadata) {
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

        design.setAttachments(TowerConductorArrangement.classicLattice().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildHeavyTransmissionRoleDesign(
            String id,
            String name,
            double baseWidth,
            double armReach,
            int insulatorLength,
            double attachmentHeight,
            InsulatorType insulatorType,
            int verticalDrop,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        TowerStructureDesign structure = TowerStructurePresets.taperedLatticeTower();
        List<TowerStation> stations = structure.sortedStations();
        for (TowerStation station : stations) {
            station.setHalfWidth(baseWidth * station.getHalfWidth() / 5.0);
            station.setHalfDepth(baseWidth * station.getHalfDepth() / 5.0);
        }
        structure.getArms().clear();
        TowerArm lowerArm = new TowerArm("arm_lower", attachmentHeight - 7, armReach);
        lowerArm.setSide(TowerArmSide.BOTH);
        lowerArm.setVerticalDrop(verticalDrop);
        structure.addArm(lowerArm);
        TowerArm upperArm = new TowerArm("arm_upper", attachmentHeight - 1, armReach * 0.85);
        upperArm.setSide(TowerArmSide.BOTH);
        upperArm.setVerticalDrop(verticalDrop);
        structure.addArm(upperArm);
        design.setTowerStructure(structure);

        design.setAttachments(TowerConductorArrangement.heavyTransmission().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildMegaIndustrialRoleDesign(
            String id,
            String name,
            TowerStructureDesign structure,
            TowerConductorArrangement arrangement,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        design.setTowerStructure(structure.copy());
        design.setAttachments(arrangement.createAttachments(attachmentHeight, insulatorType, insulatorLength));
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildArrangementRoleDesign(
            String id,
            String name,
            TowerStructureDesign structure,
            ConductorArrangement arrangement,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        design.setTowerStructure(structure.copy());
        design.setAttachments(arrangement.toAttachments(attachmentHeight, insulatorType, insulatorLength));
        design.setEngineeringMetadata(metadata);
        return design;
    }
}
