package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 横担与导线挂点的绑定与同步（视觉布局，无电气语义）。 */
public final class TowerArmAttachmentBinding {
    public static final double ARM_MATCH_TOLERANCE = 12.0;
    public static final double DEFAULT_LATERAL_SCALE = 0.85;

    private TowerArmAttachmentBinding() {
    }

    public static double conductorHangHeight(TowerArm arm) {
        return arm != null ? arm.getBaseHeight() : 12.0;
    }

    public static void bindToArm(TowerArm arm, ConductorAttachment attachment) {
        if (arm == null || attachment == null) {
            return;
        }
        attachment.setArmId(arm.getId());
        attachment.setVerticalOffset(conductorHangHeight(arm));
    }

    public static void syncBoundVerticalOffsets(TowerArm arm, Iterable<ConductorAttachment> attachments) {
        if (arm == null || attachments == null) {
            return;
        }
        double hang = conductorHangHeight(arm);
        for (ConductorAttachment attachment : attachments) {
            if (arm.getId().equals(attachment.getArmId())) {
                attachment.setVerticalOffset(hang);
            }
        }
    }

    public static void syncBoundLateralSpread(TowerArm arm, Iterable<ConductorAttachment> attachments) {
        if (arm == null || attachments == null) {
            return;
        }
        double targetReach = arm.getLateralReach() * DEFAULT_LATERAL_SCALE;
        if (targetReach < 1e-6) {
            return;
        }
        List<ConductorAttachment> bound = new ArrayList<>();
        for (ConductorAttachment attachment : attachments) {
            if (arm.getId().equals(attachment.getArmId())) {
                bound.add(attachment);
            }
        }
        if (bound.isEmpty()) {
            return;
        }
        double maxAbsLateral = 0.0;
        for (ConductorAttachment attachment : bound) {
            maxAbsLateral = Math.max(maxAbsLateral, Math.abs(attachment.getLateralOffset()));
        }
        if (maxAbsLateral < 1e-6) {
            applyThreePhaseSpread(arm, bound);
            return;
        }
        double scale = targetReach / maxAbsLateral;
        for (ConductorAttachment attachment : bound) {
            attachment.setLateralOffset(attachment.getLateralOffset() * scale);
        }
    }

    public static List<ConductorAttachment> createThreePhaseDeck(TowerArm arm) {
        return createThreePhaseDeck(arm, DEFAULT_LATERAL_SCALE);
    }

    public static List<ConductorAttachment> createThreePhaseDeck(TowerArm arm, double lateralScale) {
        if (arm == null) {
            return List.of();
        }
        double reach = arm.getLateralReach() * lateralScale;
        double hang = conductorHangHeight(arm);
        String prefix = arm.getId() + "_";
        List<ConductorAttachment> deck = new ArrayList<>(3);
        deck.add(phaseWithArm(prefix, "A", AttachmentRole.PHASE_A, -reach, hang, arm.getId()));
        deck.add(phaseWithArm(prefix, "B", AttachmentRole.PHASE_B, 0, hang, arm.getId()));
        deck.add(phaseWithArm(prefix, "C", AttachmentRole.PHASE_C, reach, hang, arm.getId()));
        return deck;
    }

    public static List<ConductorAttachment> createBundledThreePhaseDeck(TowerArm arm, int bundleCount) {
        if (arm == null) {
            return List.of();
        }
        double reach = arm.getLateralReach() * DEFAULT_LATERAL_SCALE;
        double hang = conductorHangHeight(arm);
        String prefix = arm.getId() + "_";
        List<ConductorAttachment> deck = ConductorAttachmentPresets.bundledThreePhaseHorizontal(
            hang,
            -reach,
            0,
            reach,
            bundleCount,
            0.7);
        for (ConductorAttachment attachment : deck) {
            attachment.setId(prefix + attachment.getId());
            attachment.setArmId(arm.getId());
        }
        return deck;
    }

    public static void clearArmBindings(Iterable<ConductorAttachment> attachments, String armId) {
        if (attachments == null || armId == null) {
            return;
        }
        for (ConductorAttachment attachment : attachments) {
            if (armId.equals(attachment.getArmId())) {
                attachment.setArmId(null);
            }
        }
    }

    public static void removeArmAttachments(PoleDesign design, String armId) {
        if (design == null || armId == null) {
            return;
        }
        design.getAttachments().removeIf(attachment -> armId.equals(attachment.getArmId()));
    }

    /** 按横担高度推断未绑定的挂点（打开设计器时补全 legacy 设计）。 */
    public static void inferArmBindings(PoleDesign design) {
        if (design == null || !design.hasTowerStructure()) {
            return;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        List<TowerArm> arms = sortedArms(structure);
        if (arms.isEmpty()) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.getArmId() != null && findArm(arms, attachment.getArmId()) != null) {
                continue;
            }
            TowerArm nearest = nearestArm(arms, attachment.getVerticalOffset());
            if (nearest != null) {
                attachment.setArmId(nearest.getId());
            }
        }
    }

    public static Map<String, List<ConductorAttachment>> groupByArm(PoleDesign design) {
        Map<String, List<ConductorAttachment>> grouped = new LinkedHashMap<>();
        if (design == null) {
            return grouped;
        }
        List<TowerArm> arms = design.hasTowerStructure()
            ? sortedArms(design.getTowerStructure())
            : List.of();
        for (TowerArm arm : arms) {
            grouped.put(arm.getId(), new ArrayList<>());
        }
        List<ConductorAttachment> unassigned = new ArrayList<>();
        for (ConductorAttachment attachment : design.getAttachments()) {
            String armId = attachment.getArmId();
            if (armId != null && grouped.containsKey(armId)) {
                grouped.get(armId).add(attachment);
            } else {
                unassigned.add(attachment);
            }
        }
        if (!unassigned.isEmpty()) {
            grouped.put(null, unassigned);
        }
        return grouped;
    }

    public static List<TowerArm> sortedArms(TowerStructureDesign structure) {
        if (structure == null) {
            return List.of();
        }
        List<TowerArm> arms = new ArrayList<>(structure.getArms());
        arms.sort(Comparator.comparingDouble(TowerArm::getBaseHeight));
        return arms;
    }

    private static TowerArm findArm(List<TowerArm> arms, String armId) {
        for (TowerArm arm : arms) {
            if (armId.equals(arm.getId())) {
                return arm;
            }
        }
        return null;
    }

    private static TowerArm nearestArm(List<TowerArm> arms, double verticalOffset) {
        TowerArm nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (TowerArm arm : arms) {
            double distance = Math.abs(verticalOffset - conductorHangHeight(arm));
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = arm;
            }
        }
        return bestDistance <= ARM_MATCH_TOLERANCE ? nearest : null;
    }

    private static void applyThreePhaseSpread(TowerArm arm, List<ConductorAttachment> bound) {
        double reach = arm.getLateralReach() * DEFAULT_LATERAL_SCALE;
        if (bound.size() == 3) {
            bound.get(0).setLateralOffset(-reach);
            bound.get(1).setLateralOffset(0);
            bound.get(2).setLateralOffset(reach);
        }
    }

    private static ConductorAttachment phaseWithArm(
            String idPrefix,
            String name,
            AttachmentRole role,
            double lateral,
            double vertical,
            String armId) {
        ConductorAttachment attachment = new ConductorAttachment(idPrefix + "phase_" + name.toLowerCase(), name);
        attachment.setRole(role);
        attachment.setLateralOffset(lateral);
        attachment.setVerticalOffset(vertical);
        attachment.setArmId(armId);
        return attachment;
    }
}
