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

    /** 将已关联横担但仍为 FREE 的挂点提升为 BOUND。 */
    public static void promoteArmLinkedAttachmentsToBound(PoleDesign design) {
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
                promoteToBound(attachment, arm);
            } else {
                attachment.setBindingMode(AttachmentBindingMode.FREE);
            }
        }
    }

    public static void bindToArm(TowerArm arm, ConductorAttachment attachment) {
        bindToArm(arm, attachment, null);
    }

    public static void bindToArm(
            TowerArm arm,
            ConductorAttachment attachment,
            TowerStructureDesign structure) {
        if (arm == null || attachment == null) {
            return;
        }
        ResolvedLocalOffsets local = resolveLocalOffsets(attachment, structure);
        attachment.setLateralOffset(local.lateral());
        attachment.setVerticalOffset(local.vertical());
        attachment.setLongitudinalOffset(local.longitudinal());
        promoteToBound(attachment, arm);
    }

    /** 横担有效挂线伸出（格），为 {@link #DEFAULT_LATERAL_SCALE} 作用后再取整。 */
    public static double blockEffectiveReach(TowerArm arm) {
        if (arm == null) {
            return 0.0;
        }
        return ConductorAttachment.snapBlockOffset(arm.getLateralReach() * DEFAULT_LATERAL_SCALE);
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
        double targetReach = blockEffectiveReach(arm);
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
        String prefix = arm.getId() + "_";
        List<ConductorAttachment> deck = new ArrayList<>(3);
        deck.add(boundBundledPhase(prefix, "A", AttachmentRole.PHASE_A, -1.0, arm, bundleCount));
        deck.add(boundBundledPhase(prefix, "B", AttachmentRole.PHASE_B, 0.0, arm, bundleCount));
        deck.add(boundBundledPhase(prefix, "C", AttachmentRole.PHASE_C, 1.0, arm, bundleCount));
        return deck;
    }

    public static void clearArmBindings(Iterable<ConductorAttachment> attachments, String armId) {
        releaseAttachmentsFromArm(null, attachments, armId);
    }

    /**
     * 删除横担前：将 BOUND 挂点烘焙为当前解析位置下的 FREE 偏移。
     */
    public static void releaseAttachmentsFromArm(
            TowerStructureDesign structure,
            Iterable<ConductorAttachment> attachments,
            String armId) {
        if (attachments == null || armId == null) {
            return;
        }
        for (ConductorAttachment attachment : attachments) {
            if (!armId.equals(attachment.getArmId())) {
                continue;
            }
            if (structure != null) {
                ResolvedLocalOffsets resolved = resolveLocalOffsets(attachment, structure);
                attachment.setLateralOffset(resolved.lateral());
                attachment.setVerticalOffset(resolved.vertical());
                attachment.setLongitudinalOffset(resolved.longitudinal());
            }
            attachment.setArmId(null);
            attachment.setBindingMode(AttachmentBindingMode.FREE);
            attachment.setNormalizedPosition(0.0);
            attachment.setVerticalAnchorOffset(0.0);
        }
    }

    public static void removeArmAttachments(PoleDesign design, String armId) {
        if (design == null || armId == null) {
            return;
        }
        design.removeAttachmentsByArmId(armId);
    }

    /**
     * 切到分层模式前：把 BOUND 挂点烘焙为 FREE 局部偏移，避免结构清空后预览/编辑错位。
     */
    public static void releaseBoundAttachmentsForLayerMode(PoleDesign design) {
        if (design == null || design.getTowerStructure() == null) {
            return;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isBound()) {
                continue;
            }
            ResolvedLocalOffsets resolved = resolveLocalOffsets(attachment, structure);
            attachment.setLateralOffset(resolved.lateral());
            attachment.setVerticalOffset(resolved.vertical());
            attachment.setLongitudinalOffset(resolved.longitudinal());
            attachment.setArmId(null);
            attachment.setBindingMode(AttachmentBindingMode.FREE);
            attachment.setNormalizedPosition(0.0);
            attachment.setVerticalAnchorOffset(0.0);
        }
    }

    /** 按横担高度推断未绑定 arm 的挂点。 */
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
        promoteArmLinkedAttachmentsToBound(design);
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

    /**
     * 塔体几何变化后同步挂点：补全 arm 绑定、迁移 BOUND，并按横担刷新相对位置缓存。
     */
    public static void syncAfterStructureChange(PoleDesign design) {
        if (design == null || !design.hasTowerStructure()) {
            return;
        }
        inferArmBindings(design);
        TowerStructureDesign structure = design.getTowerStructure();
        for (TowerArm arm : sortedArms(structure)) {
            syncAttachmentsForArm(arm, design.getAttachments(), structure);
        }
    }

    /** 分层模式：横担悬挂高度（相对塔腿地面，与 {@link PoleDesign#wireHangHeightFromGround(int)} 同局部坐标）。 */
    public static double layerModeCrossarmHangHeight(PoleDesign design) {
        if (design == null || design.hasTowerStructure()) {
            return 0.0;
        }
        return design.wireHangHeightFromGround(0);
    }

    /** 分层模式体素高度变化后，保持 FREE 挂点相对最高横担的竖向偏移。 */
    public static void shiftFreeAttachmentHeights(PoleDesign design, double deltaY) {
        if (design == null || Math.abs(deltaY) < 1e-6) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.isBound()) {
                continue;
            }
            attachment.setVerticalOffset(attachment.getVerticalOffset() + deltaY);
        }
    }

    /** 单根横担高度/伸出变化后，刷新其 deck 挂点的相对解析位置。 */
    public static void syncAttachmentsForArm(
            TowerArm arm,
            Iterable<ConductorAttachment> attachments,
            TowerStructureDesign structure) {
        if (arm == null || attachments == null) {
            return;
        }
        for (ConductorAttachment attachment : attachments) {
            if (!arm.getId().equals(attachment.getArmId())) {
                continue;
            }
            if (attachment.isBound()) {
                refreshBoundCache(attachment, structure);
            } else {
                attachment.setVerticalOffset(conductorHangHeight(arm));
            }
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
        double lateral = ConductorAttachment.snapBlockOffset(
            blockEffectiveReach(arm) * attachment.getNormalizedPosition());
        double vertical = ConductorAttachment.snapBlockOffset(
            conductorHangHeight(arm) + attachment.getVerticalAnchorOffset());
        return new ResolvedLocalOffsets(lateral, vertical, attachment.getLongitudinalOffset());
    }

    private static void promoteToBound(ConductorAttachment attachment, TowerArm arm) {
        if (attachment.isBound() && arm.getId().equals(attachment.getArmId())) {
            cacheResolvedOffsets(attachment, arm);
            return;
        }
        double reach = blockEffectiveReach(arm);
        double normalized = reach > 1e-6 ? attachment.getLateralOffset() / reach : 0.0;
        attachment.setBindingMode(AttachmentBindingMode.BOUND);
        attachment.setArmId(arm.getId());
        attachment.setNormalizedPosition(normalized);
        attachment.setVerticalAnchorOffset(attachment.getVerticalOffset() - conductorHangHeight(arm));
        cacheResolvedOffsets(attachment, arm);
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
        double reach = blockEffectiveReach(arm);
        if (bound.size() == 3) {
            bound.get(0).setLateralOffset(-reach);
            bound.get(1).setLateralOffset(0);
            bound.get(2).setLateralOffset(reach);
        }
    }

    private static ConductorAttachment boundBundledPhase(
            String idPrefix,
            String name,
            AttachmentRole role,
            double normalizedPosition,
            TowerArm arm,
            int bundleCount) {
        ConductorAttachment attachment = boundPhase(idPrefix, name, role, normalizedPosition, arm);
        attachment.setBundleVisual(BundleVisual.forSubconductorCount(bundleCount));
        return attachment;
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
