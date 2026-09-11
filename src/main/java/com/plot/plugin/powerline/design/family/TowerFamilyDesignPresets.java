package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.equipment.InsulatorAssemblyCatalog;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.engineering.TowerEngineeringMetadata;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.EnumSet;

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
    public static final String TRIPLE_ARM_SUSPENSION_ID = "preset/triple_arm_suspension";
    public static final String TRIPLE_ARM_ANGLE_ID = "preset/triple_arm_angle";
    public static final String TRIPLE_ARM_DEAD_END_ID = "preset/triple_arm_dead_end";
    public static final String TRIPLE_ARM_TERMINAL_ID = "preset/triple_arm_terminal";
    public static final String CUP_TOWER_SUSPENSION_ID = "preset/cup_tower_suspension";
    public static final String CUP_TOWER_ANGLE_ID = "preset/cup_tower_angle";
    public static final String CUP_TOWER_DEAD_END_ID = "preset/cup_tower_dead_end";
    public static final String CUP_TOWER_TERMINAL_ID = "preset/cup_tower_terminal";
    /** 塔顶装饰线挂点 id（视觉顶线，非电气接地）。 */
    public static final String TOP_WIRE_ID = "ground_wire";

    private TowerFamilyDesignPresets() {
    }

    public static PoleDesign latticeSuspension() {
        return buildClassicLatticeRoleDesign(
            LATTICE_SUSPENSION_ID,
            "Lattice Suspension Tower",
            5,
            32,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 32, 60, 5));
    }

    public static PoleDesign latticeSuspensionSmall() {
        return buildSmallLatticeRoleDesign(
            LATTICE_SUSPENSION_SMALL_ID,
            "Lattice Suspension S",
            4,
            18,
            InsulatorType.SUSPENSION,
            2,
            metadata(TowerRole.SUSPENSION, 20, 35, 5));
    }

    public static PoleDesign latticeSuspensionMedium() {
        return buildClassicLatticeRoleDesign(
            LATTICE_SUSPENSION_MEDIUM_ID,
            "Lattice Suspension M",
            5,
            30,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 30, 55, 5));
    }

    public static PoleDesign latticeSuspensionTall() {
        return buildArrangementRoleDesign(
            LATTICE_SUSPENSION_TALL_ID,
            "Lattice Suspension L",
            TowerStructurePresets.tripleArmTower(),
            ConductorArrangement.doubleCircuitThreeDeck(),
            36,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 48, 80, 5));
    }

    public static PoleDesign latticeAngle() {
        return buildClassicLatticeRoleDesign(
            LATTICE_ANGLE_ID,
            "Lattice Angle Tower",
            6,
            32,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.ANGLE, 34, 55, 60));
    }

    public static PoleDesign latticeDeadEnd() {
        return buildClassicLatticeRoleDesign(
            LATTICE_DEAD_END_ID,
            "Lattice Dead-End Tower",
            6,
            32,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.DEAD_END, 34, 55, 90));
    }

    public static PoleDesign latticeTerminal() {
        return buildClassicLatticeRoleDesign(
            LATTICE_TERMINAL_ID,
            "Lattice Terminal Tower",
            5,
            30,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.TERMINAL, 32, 50, 90));
    }

    public static PoleDesign hvTransmissionSuspension() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_SUSPENSION_ID,
            "HV Transmission Suspension",
            30,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 30, 65, 5));
    }

    public static PoleDesign hvTransmissionAngle() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_ANGLE_ID,
            "HV Transmission Angle",
            30,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.ANGLE, 32, 60, 60));
    }

    public static PoleDesign hvTransmissionDeadEnd() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_DEAD_END_ID,
            "HV Transmission Dead-End",
            30,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.DEAD_END, 32, 60, 90));
    }

    public static PoleDesign hvTransmissionTerminal() {
        return buildHeavyTransmissionRoleDesign(
            HV_TRANSMISSION_TERMINAL_ID,
            "HV Transmission Terminal",
            30,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.TERMINAL, 30, 55, 90));
    }

    public static PoleDesign megaLatticeSuspension() {
        return buildArrangementRoleDesign(
            MEGA_LATTICE_SUSPENSION_ID,
            "Mega Lattice Suspension",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.megaThreeDeck(),
            38,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 52, 90, 5));
    }

    public static PoleDesign megaLatticeAngle() {
        return buildArrangementRoleDesign(
            MEGA_LATTICE_ANGLE_ID,
            "Mega Lattice Angle",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.megaThreeDeck(),
            38,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 54, 85, 60));
    }

    public static PoleDesign megaLatticeDeadEnd() {
        return buildArrangementRoleDesign(
            MEGA_LATTICE_DEAD_END_ID,
            "Mega Lattice Dead-End",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.megaThreeDeck(),
            38,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 56, 85, 90));
    }

    public static PoleDesign megaLatticeTerminal() {
        return buildArrangementRoleDesign(
            MEGA_LATTICE_TERMINAL_ID,
            "Mega Lattice Terminal",
            TowerStructurePresets.megaLatticeTower(),
            ConductorArrangement.megaThreeDeck(),
            38,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 52, 80, 90));
    }

    public static PoleDesign heavyDoubleCircuitSuspension() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID,
            "Heavy Double-Circuit Suspension",
            TowerStructurePresets.doubleCircuitDrumTower(),
            ConductorArrangement.doubleCircuitDrum(),
            44,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 54, 100, 5));
    }

    public static PoleDesign heavyDoubleCircuitAngle() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_ANGLE_ID,
            "Heavy Double-Circuit Angle",
            TowerStructurePresets.doubleCircuitDrumTower(),
            ConductorArrangement.doubleCircuitDrum(),
            44,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 56, 95, 60));
    }

    public static PoleDesign heavyDoubleCircuitDeadEnd() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_DEAD_END_ID,
            "Heavy Double-Circuit Dead-End",
            TowerStructurePresets.doubleCircuitDrumTower(),
            ConductorArrangement.doubleCircuitDrum(),
            44,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 58, 95, 90));
    }

    public static PoleDesign heavyDoubleCircuitTerminal() {
        return buildArrangementRoleDesign(
            HEAVY_DOUBLE_CIRCUIT_TERMINAL_ID,
            "Heavy Double-Circuit Terminal",
            TowerStructurePresets.doubleCircuitDrumTower(),
            ConductorArrangement.doubleCircuitDrum(),
            44,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 54, 90, 90));
    }

    public static PoleDesign industrialPortalSuspension() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_SUSPENSION_ID,
            "Industrial Portal Suspension",
            TowerStructurePresets.portalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            26,
            InsulatorType.SUSPENSION,
            4,
            metadata(TowerRole.SUSPENSION, 50, 110, 5));
    }

    public static PoleDesign industrialPortalAngle() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_ANGLE_ID,
            "Industrial Portal Angle",
            TowerStructurePresets.portalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            26,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.ANGLE, 52, 105, 60));
    }

    public static PoleDesign industrialPortalDeadEnd() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_DEAD_END_ID,
            "Industrial Portal Dead-End",
            TowerStructurePresets.portalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            26,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.DEAD_END, 54, 105, 90));
    }

    public static PoleDesign industrialPortalTerminal() {
        return buildArrangementRoleDesign(
            INDUSTRIAL_PORTAL_TERMINAL_ID,
            "Industrial Portal Terminal",
            TowerStructurePresets.portalTower(),
            ConductorArrangement.heavyDoubleCircuit(),
            26,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.TERMINAL, 50, 100, 90));
    }

    public static PoleDesign monsterPylonSuspension() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_SUSPENSION_ID,
            "Monster Pylon Suspension",
            TowerStructurePresets.uhvGiantTower(),
            ConductorArrangement.uhvThreeDeck(),
            56,
            InsulatorType.SUSPENSION,
            5,
            metadata(TowerRole.SUSPENSION, 80, 140, 5));
    }

    public static PoleDesign monsterPylonAngle() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_ANGLE_ID,
            "Monster Pylon Angle",
            TowerStructurePresets.uhvGiantTower(),
            ConductorArrangement.uhvThreeDeck(),
            56,
            InsulatorType.STRAIN,
            6,
            metadata(TowerRole.ANGLE, 86, 130, 60));
    }

    public static PoleDesign monsterPylonDeadEnd() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_DEAD_END_ID,
            "Monster Pylon Dead-End",
            TowerStructurePresets.uhvGiantTower(),
            ConductorArrangement.uhvThreeDeck(),
            56,
            InsulatorType.STRAIN,
            6,
            metadata(TowerRole.DEAD_END, 84, 130, 90));
    }

    public static PoleDesign monsterPylonTerminal() {
        return buildArrangementRoleDesign(
            MONSTER_PYLON_TERMINAL_ID,
            "Monster Pylon Terminal",
            TowerStructurePresets.uhvGiantTower(),
            ConductorArrangement.uhvThreeDeck(),
            56,
            InsulatorType.STRAIN,
            5,
            metadata(TowerRole.TERMINAL, 82, 120, 90));
    }

    public static PoleDesign tripleArmSuspension() {
        return buildArrangementRoleDesign(
            TRIPLE_ARM_SUSPENSION_ID,
            "Triple Arm Suspension",
            TowerStructurePresets.tripleArmTower(),
            ConductorArrangement.doubleCircuitThreeDeck(),
            36,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 48, 95, 5));
    }

    public static PoleDesign tripleArmAngle() {
        return buildArrangementRoleDesign(
            TRIPLE_ARM_ANGLE_ID,
            "Triple Arm Angle",
            TowerStructurePresets.tripleArmTower(),
            ConductorArrangement.doubleCircuitThreeDeck(),
            36,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.ANGLE, 50, 90, 60));
    }

    public static PoleDesign tripleArmDeadEnd() {
        return buildArrangementRoleDesign(
            TRIPLE_ARM_DEAD_END_ID,
            "Triple Arm Dead-End",
            TowerStructurePresets.tripleArmTower(),
            ConductorArrangement.doubleCircuitThreeDeck(),
            36,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.DEAD_END, 50, 90, 90));
    }

    public static PoleDesign tripleArmTerminal() {
        return buildArrangementRoleDesign(
            TRIPLE_ARM_TERMINAL_ID,
            "Triple Arm Terminal",
            TowerStructurePresets.tripleArmTower(),
            ConductorArrangement.doubleCircuitThreeDeck(),
            36,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.TERMINAL, 48, 85, 90));
    }

    public static PoleDesign cupTowerSuspension() {
        return buildCupTowerRoleDesign(
            CUP_TOWER_SUSPENSION_ID,
            "Cup Tower Suspension",
            32,
            InsulatorType.SUSPENSION,
            3,
            metadata(TowerRole.SUSPENSION, 38, 75, 5));
    }

    public static PoleDesign cupTowerAngle() {
        return buildCupTowerRoleDesign(
            CUP_TOWER_ANGLE_ID,
            "Cup Tower Angle",
            32,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.ANGLE, 40, 70, 60));
    }

    public static PoleDesign cupTowerDeadEnd() {
        return buildCupTowerRoleDesign(
            CUP_TOWER_DEAD_END_ID,
            "Cup Tower Dead-End",
            32,
            InsulatorType.STRAIN,
            4,
            metadata(TowerRole.DEAD_END, 40, 70, 90));
    }

    public static PoleDesign cupTowerTerminal() {
        return buildCupTowerRoleDesign(
            CUP_TOWER_TERMINAL_ID,
            "Cup Tower Terminal",
            32,
            InsulatorType.STRAIN,
            3,
            metadata(TowerRole.TERMINAL, 38, 65, 90));
    }

    private static TowerEngineeringMetadata metadata(
            TowerRole role,
            double height,
            double maxSpan,
            double maxAngle) {
        TowerEngineeringMetadata metadata = new TowerEngineeringMetadata();
        metadata.setNominalHeight(height);
        metadata.setMaxRecommendedSpan(maxSpan);
        metadata.setMaxRecommendedDeflectionAngle(maxAngle);
        metadata.setSupportedRoles(EnumSet.of(role));
        return metadata;
    }

    private static PoleDesign buildClassicLatticeRoleDesign(
            String id,
            String name,
            double baseWidth,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower().copy();
        scaleStationFootprint(structure, baseWidth, TowerStructurePresets.CLASSIC_BASE_HALF_WIDTH);
        design.setTowerStructure(structure);
        design.setAttachments(TowerConductorArrangement.classicLattice().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        InsulatorAssemblyCatalog.applyStandardDefaults(design);
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildSmallLatticeRoleDesign(
            String id,
            String name,
            double baseWidth,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        TowerStructureDesign structure = TowerStructurePresets.smallLatticeTower().copy();
        scaleStationFootprint(structure, baseWidth, TowerStructurePresets.SMALL_BASE_HALF_WIDTH);
        design.setTowerStructure(structure);
        design.setAttachments(TowerConductorArrangement.classicLattice().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        InsulatorAssemblyCatalog.applyStandardDefaults(design);
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildTripleArmRoleDesign(
            String id,
            String name,
            double baseWidth,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        TowerStructureDesign structure = TowerStructurePresets.tripleArmTower().copy();
        scaleStationFootprint(structure, baseWidth, 8.0);
        design.setTowerStructure(structure);
        design.setAttachments(TowerConductorArrangement.heavyTransmission().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        InsulatorAssemblyCatalog.applyMegaDefaults(design, insulatorType);
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildHeavyTransmissionRoleDesign(
            String id,
            String name,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        design.setTowerStructure(TowerStructurePresets.heavyTransmissionTower().copy());
        design.setAttachments(TowerConductorArrangement.heavyTransmission().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        InsulatorAssemblyCatalog.applyMegaDefaults(design, insulatorType);
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static PoleDesign buildCupTowerRoleDesign(
            String id,
            String name,
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength,
            TowerEngineeringMetadata metadata) {
        PoleDesign design = new PoleDesign(id, name);
        design.setTowerStructure(TowerStructurePresets.cupTower().copy());
        design.setAttachments(TowerConductorArrangement.heavyTransmission().createAttachments(
            attachmentHeight, insulatorType, insulatorLength));
        InsulatorAssemblyCatalog.applyStandardDefaults(design);
        design.setEngineeringMetadata(metadata);
        return design;
    }

    private static void scaleStationFootprint(
            TowerStructureDesign structure,
            double targetBaseHalfWidth,
            double referenceBaseHalfWidth) {
        if (structure == null || referenceBaseHalfWidth <= 0) {
            return;
        }
        double scale = targetBaseHalfWidth / referenceBaseHalfWidth;
        for (TowerStation station : structure.sortedStations()) {
            station.setHalfWidth(station.getHalfWidth() * scale);
            station.setHalfDepth(station.getHalfDepth() * scale);
        }
        for (TowerArm arm : structure.getArms()) {
            arm.setLateralReach(arm.getLateralReach() * scale);
            arm.setLongitudinalHalfWidth(arm.getLongitudinalHalfWidth() * scale);
        }
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
        InsulatorAssemblyCatalog.applyMegaDefaults(design, insulatorType);
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
        if (id.contains("monster_pylon")) {
            InsulatorAssemblyCatalog.applyMonsterDefaults(design, insulatorType);
        } else {
            InsulatorAssemblyCatalog.applyMegaDefaults(design, insulatorType);
        }
        design.setEngineeringMetadata(metadata);
        return design;
    }
}
