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
        return designs;
    }
}
