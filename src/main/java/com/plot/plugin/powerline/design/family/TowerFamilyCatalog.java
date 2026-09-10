package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置塔型族目录。 */
public final class TowerFamilyCatalog {
    private TowerFamilyCatalog() {
    }

    public static List<TowerFamily> defaultFamilies() {
        List<TowerFamily> families = new ArrayList<>();
        families.add(standardLattice3Phase());
        families.add(gradedLattice3Phase());
        families.add(heavyTransmission());
        families.add(tripleArm3Phase());
        families.add(cupTower());
        families.add(megaLattice());
        families.add(heavyDoubleCircuit());
        families.add(industrialPortal());
        families.add(monsterPylon());
        return families;
    }

    public static Map<String, TowerFamily> indexById() {
        Map<String, TowerFamily> indexed = new LinkedHashMap<>();
        for (TowerFamily family : defaultFamilies()) {
            indexed.put(family.getId(), family);
        }
        return indexed;
    }

    public static TowerFamily findBuiltin(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }

    public static TowerFamily standardLattice3Phase() {
        TowerFamily family = new TowerFamily(
            TowerFamily.STANDARD_LATTICE_3_PHASE_ID,
            "Standard Lattice 3-Phase");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.LATTICE_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.LATTICE_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.LATTICE_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        return family;
    }

    public static boolean isBuiltinId(String id) {
        return id != null && id.startsWith("family/");
    }

    public static TowerFamily heavyTransmission() {
        TowerFamily family = new TowerFamily(
            TowerFamily.HEAVY_TRANSMISSION_ID,
            "Heavy Transmission");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.HV_TRANSMISSION_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.HV_TRANSMISSION_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.HV_TRANSMISSION_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily tripleArm3Phase() {
        TowerFamily family = new TowerFamily(
            TowerFamily.TRIPLE_ARM_3_PHASE_ID,
            "Triple Arm 3-Phase");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.TRIPLE_ARM_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.TRIPLE_ARM_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.TRIPLE_ARM_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.TRIPLE_ARM_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.TRIPLE_ARM_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily cupTower() {
        TowerFamily family = new TowerFamily(TowerFamily.CUP_TOWER_ID, "Cup Tower");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.CUP_TOWER_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.CUP_TOWER_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.CUP_TOWER_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.CUP_TOWER_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.CUP_TOWER_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily megaLattice() {
        TowerFamily family = new TowerFamily(TowerFamily.MEGA_LATTICE_ID, "Mega Lattice");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.MEGA_LATTICE_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.MEGA_LATTICE_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.MEGA_LATTICE_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily heavyDoubleCircuit() {
        TowerFamily family = new TowerFamily(TowerFamily.HEAVY_DOUBLE_CIRCUIT_ID, "Heavy Double Circuit");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily industrialPortal() {
        TowerFamily family = new TowerFamily(TowerFamily.INDUSTRIAL_PORTAL_ID, "Industrial Portal");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily monsterPylon() {
        TowerFamily family = new TowerFamily(TowerFamily.MONSTER_PYLON_ID, "Monster Pylon");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.MONSTER_PYLON_ANGLE_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.MONSTER_PYLON_DEAD_END_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.MONSTER_PYLON_TERMINAL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID);
        return family;
    }

    public static TowerFamily gradedLattice3Phase() {
        TowerFamily family = new TowerFamily(
            TowerFamily.GRADED_LATTICE_3_PHASE_ID,
            "Graded Lattice 3-Phase");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID);
        family.setDesignId(TowerRole.SPECIAL, TowerFamilyDesignPresets.LATTICE_SUSPENSION_MEDIUM_ID);
        family.setDesignId(TowerRole.DEAD_END, TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID);
        family.setDesignId(TowerRole.ANGLE, TowerFamilyDesignPresets.LATTICE_ANGLE_ID);
        family.setDesignId(TowerRole.TERMINAL, TowerFamilyDesignPresets.LATTICE_TERMINAL_ID);
        return family;
    }

    public static List<com.plot.plugin.powerline.design.PoleDesign> familyDesigns() {
        List<com.plot.plugin.powerline.design.PoleDesign> designs = new ArrayList<>();
        designs.add(TowerFamilyDesignPresets.latticeSuspension());
        designs.add(TowerFamilyDesignPresets.latticeSuspensionSmall());
        designs.add(TowerFamilyDesignPresets.latticeSuspensionMedium());
        designs.add(TowerFamilyDesignPresets.latticeSuspensionTall());
        designs.add(TowerFamilyDesignPresets.latticeAngle());
        designs.add(TowerFamilyDesignPresets.latticeDeadEnd());
        designs.add(TowerFamilyDesignPresets.latticeTerminal());
        designs.add(TowerFamilyDesignPresets.hvTransmissionSuspension());
        designs.add(TowerFamilyDesignPresets.hvTransmissionAngle());
        designs.add(TowerFamilyDesignPresets.hvTransmissionDeadEnd());
        designs.add(TowerFamilyDesignPresets.hvTransmissionTerminal());
        designs.add(TowerFamilyDesignPresets.tripleArmSuspension());
        designs.add(TowerFamilyDesignPresets.tripleArmAngle());
        designs.add(TowerFamilyDesignPresets.tripleArmDeadEnd());
        designs.add(TowerFamilyDesignPresets.tripleArmTerminal());
        designs.add(TowerFamilyDesignPresets.cupTowerSuspension());
        designs.add(TowerFamilyDesignPresets.cupTowerAngle());
        designs.add(TowerFamilyDesignPresets.cupTowerDeadEnd());
        designs.add(TowerFamilyDesignPresets.cupTowerTerminal());
        designs.add(TowerFamilyDesignPresets.megaLatticeSuspension());
        designs.add(TowerFamilyDesignPresets.megaLatticeAngle());
        designs.add(TowerFamilyDesignPresets.megaLatticeDeadEnd());
        designs.add(TowerFamilyDesignPresets.megaLatticeTerminal());
        designs.add(TowerFamilyDesignPresets.heavyDoubleCircuitSuspension());
        designs.add(TowerFamilyDesignPresets.heavyDoubleCircuitAngle());
        designs.add(TowerFamilyDesignPresets.heavyDoubleCircuitDeadEnd());
        designs.add(TowerFamilyDesignPresets.heavyDoubleCircuitTerminal());
        designs.add(TowerFamilyDesignPresets.industrialPortalSuspension());
        designs.add(TowerFamilyDesignPresets.industrialPortalAngle());
        designs.add(TowerFamilyDesignPresets.industrialPortalDeadEnd());
        designs.add(TowerFamilyDesignPresets.industrialPortalTerminal());
        designs.add(TowerFamilyDesignPresets.monsterPylonSuspension());
        designs.add(TowerFamilyDesignPresets.monsterPylonAngle());
        designs.add(TowerFamilyDesignPresets.monsterPylonDeadEnd());
        designs.add(TowerFamilyDesignPresets.monsterPylonTerminal());
        return designs;
    }
}
