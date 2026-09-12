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

    public record ResolvedLocalOffsets(double lateral, double vertical, double longitudinal) {
    }

    public static double conductorHangHeight(TowerArm arm) {
        return arm != null ? arm.getBaseHeight() : 12.0;
    }

    public static ResolvedLocalOffsets resolveLocalOffsets(
            ConductorAttachment attachment,
            TowerStructureDesign structure) {
        if (attachment == null) {
            return new ResolvedLocalOffsets(0.0, 1.0, 0.0);
        }
        if (attachment.isBound() && structure != null) {
            TowerArm arm = findArm(sortedArms(structure), attachment.getArmId());
            if (arm != null) {
                return resolveBoundOffsets(attachment, arm);
            }
        }
        return new ResolvedLocalOffsets(
            attachment.getLateralOffset(),
            attachment.getVerticalOffset(),
            attachment.getLongitudinalOffset());
    }

    public static void ensureV2Bindings(PoleDesign design) {
        if (design == null || !design.hasTowerStructure()) {
            return;
        }
        List<TowerArm> arms = sortedArms(design.getTowerStructure());
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.getBindingMode() == AttachmentBindingMode.BOUND) {
                continue;
            }
            if (attachment.getArmId() == null) {
                attachment.setBindingMode(AttachmentBindingMode.FREE);
                continue;
            }
            TowerArm arm = findArm(arms, attachment.getArmId());
            if (arm != null) {
                migrateLegacyToBound(attachment, arm);
            } else {
                attachment.setBindingMode(AttachmentBindingMode.FREE);
            }
        }
    }

    public static void bindToArm(TowerArm arm, ConductorAttachment attachment) {
        if (arm == null || attachment == null) {
            return;
        }
        attachment.setBindingMode(AttachmentBindingMode.BOUND);
        attachment.setArmId(arm.getId());
        attachment.setNormalizedPosition(0.0);
        attachment.setVerticalAnchorOffset(0.0);
        cacheResolvedOffsets(attachment, arm);
    }

    public static void syncBoundVerticalOffsets(TowerArm arm, Iterable<ConductorAttachment> attachments) {
        if (arm == null || attachments == null) {
            return;
        }
        double hang = conductorHangHeight(arm);
        for (ConductorAttachment attachment : attachments) {
            if (!arm.getId().equals(attachment.getArmId())) {
                continue;
            }
            if (attachment.isBound()) {
                continue;
            }
            attachment.setVerticalOffset(hang);
        }
    }

    public static void syncBoundLateralSpread(TowerArm arm, Iterable<ConductorAttachment> attachments) {
        if (arm == null || attachments == null) {
            return;
        }
        List<ConductorAttachment> bound = new ArrayList<>();
        for (ConductorAttachment attachment : attachments) {
            if (arm.getId().equals(attachment.getArmId())) {
                if (attachment.isBound()) {
                    continue;
                }
                bound.add(attachment);
            }
        }
        if (bound.isEmpty()) {
            return;
        }
        double targetReach = arm.getLateralReach() * DEFAULT_LATERAL_SCALE;
        if (targetReach < 1e-6) {
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
        String prefix = arm.getId() + "_";
        List<ConductorAttachment> deck = new ArrayList<>(3);
        deck.add(boundPhase(prefix, "A", AttachmentRole.PHASE_A, -1.0, arm));
        deck.add(boundPhase(prefix, "B", AttachmentRole.PHASE_B, 0.0, arm));
        deck.add(boundPhase(prefix, "C", AttachmentRole.PHASE_C, 1.0, arm));
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
            migrateLegacyToBound(attachment, arm);
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
                attachment.setBindingMode(AttachmentBindingMode.FREE);
            }
        }
    }

    public static void removeArmAttachments(PoleDesign design, String armId) {
        if (design == null || armId == null) {
            return;
        }
        design.removeAttachmentsByArmId(armId);
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
            ResolvedLocalOffsets local = resolveLocalOffsets(attachment, structure);
            TowerArm nearest = nearestArm(arms, local.vertical());
            if (nearest != null) {
                attachment.setArmId(nearest.getId());
            }
        }
        ensureV2Bindings(design);
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

    public static void refreshBoundCache(ConductorAttachment attachment, TowerStructureDesign structure) {
        if (!attachment.isBound() || structure == null) {
            return;
        }
        TowerArm arm = findArm(sortedArms(structure), attachment.getArmId());
        if (arm != null) {
            cacheResolvedOffsets(attachment, arm);
        }
    }

    public static List<TowerArm> sortedArms(TowerStructureDesign structure) {
        if (structure == null) {
            return List.of();
        }
        List<TowerArm> arms = new ArrayList<>(structure.getArms());
        arms.sort(Comparator.comparingDouble(TowerArm::getBaseHeight));
        return arms;
    }

    private static ResolvedLocalOffsets resolveBoundOffsets(ConductorAttachment attachment, TowerArm arm) {
        double lateral = arm.getLateralReach() * DEFAULT_LATERAL_SCALE * attachment.getNormalizedPosition();
        double vertical = conductorHangHeight(arm) + attachment.getVerticalAnchorOffset();
        return new ResolvedLocalOffsets(lateral, vertical, attachment.getLongitudinalOffset());
    }

    private static void migrateLegacyToBound(ConductorAttachment attachment, TowerArm arm) {
        double reach = arm.getLateralReach() * DEFAULT_LATERAL_SCALE;
        double normalized = reach > 1e-6
            ? attachment.getLateralOffset() / reach
            : 0.0;
        double originalLateral = attachment.getLateralOffset();
        double originalVertical = attachment.getVerticalOffset();
        attachment.setBindingMode(AttachmentBindingMode.BOUND);
        attachment.setArmId(arm.getId());
        attachment.setNormalizedPosition(normalized);
        attachment.setVerticalAnchorOffset(originalVertical - conductorHangHeight(arm));
        // If the normalized position was clamped, preserve the original offsets
        // to avoid permanently moving legacy attachments inward.
        if (Math.abs(normalized) > 1.0) {
            attachment.setLateralOffset(originalLateral);
            attachment.setVerticalOffset(originalVertical);
        } else {
            cacheResolvedOffsets(attachment, arm);
        }
    }

    private static void cacheResolvedOffsets(ConductorAttachment attachment, TowerArm arm) {
        ResolvedLocalOffsets local = resolveBoundOffsets(attachment, arm);
        attachment.setLateralOffset(local.lateral());
        attachment.setVerticalOffset(local.vertical());
        attachment.setLongitudinalOffset(local.longitudinal());
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

    private static ConductorAttachment boundPhase(
            String idPrefix,
            String name,
            AttachmentRole role,
            double normalizedPosition,
            TowerArm arm) {
        ConductorAttachment attachment = new ConductorAttachment(idPrefix + "phase_" + name.toLowerCase(), name);
        attachment.setRole(role);
        attachment.setBindingMode(AttachmentBindingMode.BOUND);
        attachment.setArmId(arm.getId());
        attachment.setNormalizedPosition(normalizedPosition);
        attachment.setVerticalAnchorOffset(0.0);
        cacheResolvedOffsets(attachment, arm);
        return attachment;
    }
}
