package com.plot.plugin.powerline.design;

import java.util.ArrayList;
import java.util.List;

/** 常用导线挂点预设。 */
public final class ConductorAttachmentPresets {
    public static final String PHASE_A_ID = "phase_a";
    public static final String PHASE_B_ID = "phase_b";
    public static final String PHASE_C_ID = "phase_c";
    public static final String TOP_WIRE_L_ID = "top_wire_l";
    public static final String TOP_WIRE_R_ID = "top_wire_r";

    private ConductorAttachmentPresets() {
    }

    public static List<ConductorAttachment> threePhaseHorizontal(double verticalOffset) {
        return threePhaseHorizontal(verticalOffset, -3.0, 0.0, 3.0);
    }

    public static List<ConductorAttachment> threePhaseHorizontal(
            double verticalOffset,
            double lateralA,
            double lateralB,
            double lateralC) {
        List<ConductorAttachment> attachments = new ArrayList<>(3);
        attachments.add(createPhase(PHASE_A_ID, "A", AttachmentRole.PHASE_A, lateralA, verticalOffset));
        attachments.add(createPhase(PHASE_B_ID, "B", AttachmentRole.PHASE_B, lateralB, verticalOffset));
        attachments.add(createPhase(PHASE_C_ID, "C", AttachmentRole.PHASE_C, lateralC, verticalOffset));
        return attachments;
    }

    public static List<ConductorAttachment> threePhaseVertical(double verticalOffset) {
        List<ConductorAttachment> attachments = new ArrayList<>(3);
        attachments.add(createPhase(PHASE_A_ID, "A", AttachmentRole.PHASE_A, 0.0, verticalOffset - 1.0));
        attachments.add(createPhase(PHASE_B_ID, "B", AttachmentRole.PHASE_B, 0.0, verticalOffset));
        attachments.add(createPhase(PHASE_C_ID, "C", AttachmentRole.PHASE_C, 0.0, verticalOffset + 1.0));
        return attachments;
    }

    public static List<ConductorAttachment> singleConductor(double verticalOffset) {
        List<ConductorAttachment> attachments = new ArrayList<>(1);
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setRole(AttachmentRole.PHASE_A);
        attachment.setLateralOffset(0.0);
        attachment.setVerticalOffset(verticalOffset);
        attachments.add(attachment);
        return attachments;
    }

    /** 塔顶双顶线（视觉轮廓线，非电气接地）。 */
    public static List<ConductorAttachment> twinTopWires(double verticalOffset, double halfSeparation) {
        List<ConductorAttachment> wires = new ArrayList<>(2);
        wires.add(topWire(TOP_WIRE_L_ID, "TW-L", -halfSeparation, verticalOffset));
        wires.add(topWire(TOP_WIRE_R_ID, "TW-R", halfSeparation, verticalOffset));
        return wires;
    }

    public static List<ConductorAttachment> bundledThreePhaseHorizontal(
            double verticalOffset,
            double lateralA,
            double lateralB,
            double lateralC,
            int bundleCount,
            double bundleSpacing) {
        List<ConductorAttachment> attachments = new ArrayList<>();
        attachments.addAll(bundlePhase(PHASE_A_ID, "A", AttachmentRole.PHASE_A, lateralA, verticalOffset, bundleCount, bundleSpacing));
        attachments.addAll(bundlePhase(PHASE_B_ID, "B", AttachmentRole.PHASE_B, lateralB, verticalOffset, bundleCount, bundleSpacing));
        attachments.addAll(bundlePhase(PHASE_C_ID, "C", AttachmentRole.PHASE_C, lateralC, verticalOffset, bundleCount, bundleSpacing));
        return attachments;
    }

    /**
     * 左右双回路：左三相 + 右三相 + 双顶线（8 挂点）。
     * ID 前缀 {@code left_} / {@code right_} 保证跨塔匹配稳定。
     */
    public static List<ConductorAttachment> doubleCircuitHorizontal(
            double verticalOffset,
            double leftA,
            double leftB,
            double leftC,
            double rightA,
            double rightB,
            double rightC) {
        List<ConductorAttachment> attachments = new ArrayList<>(8);
        attachments.add(createPhase("left_phase_a", "LA", AttachmentRole.PHASE_A, leftA, verticalOffset));
        attachments.add(createPhase("left_phase_b", "LB", AttachmentRole.PHASE_B, leftB, verticalOffset));
        attachments.add(createPhase("left_phase_c", "LC", AttachmentRole.PHASE_C, leftC, verticalOffset));
        attachments.add(createPhase("right_phase_a", "RA", AttachmentRole.PHASE_A, rightA, verticalOffset));
        attachments.add(createPhase("right_phase_b", "RB", AttachmentRole.PHASE_B, rightB, verticalOffset));
        attachments.add(createPhase("right_phase_c", "RC", AttachmentRole.PHASE_C, rightC, verticalOffset));
        attachments.addAll(twinTopWires(verticalOffset + 6, 2.5));
        return attachments;
    }

    /**
     * 四层四回路：上下左右各三相 + 双顶线（14 挂点）。
     */
    public static List<ConductorAttachment> quadCircuitWithTwinTop(
            double lowerDeckOffset,
            double upperDeckOffset,
            double leftA,
            double leftB,
            double leftC,
            double rightA,
            double rightB,
            double rightC) {
        List<ConductorAttachment> attachments = new ArrayList<>(14);
        attachments.add(createPhase("ll_phase_a", "LLA", AttachmentRole.PHASE_A, leftA, lowerDeckOffset));
        attachments.add(createPhase("ll_phase_b", "LLB", AttachmentRole.PHASE_B, leftB, lowerDeckOffset));
        attachments.add(createPhase("ll_phase_c", "LLC", AttachmentRole.PHASE_C, leftC, lowerDeckOffset));
        attachments.add(createPhase("lr_phase_a", "LRA", AttachmentRole.PHASE_A, rightA, lowerDeckOffset));
        attachments.add(createPhase("lr_phase_b", "LRB", AttachmentRole.PHASE_B, rightB, lowerDeckOffset));
        attachments.add(createPhase("lr_phase_c", "LRC", AttachmentRole.PHASE_C, rightC, lowerDeckOffset));
        attachments.add(createPhase("ul_phase_a", "ULA", AttachmentRole.PHASE_A, leftA, upperDeckOffset));
        attachments.add(createPhase("ul_phase_b", "ULB", AttachmentRole.PHASE_B, leftB, upperDeckOffset));
        attachments.add(createPhase("ul_phase_c", "ULC", AttachmentRole.PHASE_C, leftC, upperDeckOffset));
        attachments.add(createPhase("ur_phase_a", "URA", AttachmentRole.PHASE_A, rightA, upperDeckOffset));
        attachments.add(createPhase("ur_phase_b", "URB", AttachmentRole.PHASE_B, rightB, upperDeckOffset));
        attachments.add(createPhase("ur_phase_c", "URC", AttachmentRole.PHASE_C, rightC, upperDeckOffset));
        attachments.addAll(twinTopWires(upperDeckOffset + 8, 3.0));
        return attachments;
    }

    private static List<ConductorAttachment> bundlePhase(
            String baseId,
            String name,
            AttachmentRole role,
            double centerLateral,
            double verticalOffset,
            int bundleCount,
            double bundleSpacing) {
        int count = Math.max(1, bundleCount);
        List<ConductorAttachment> bundle = new ArrayList<>(count);
        if (count == 1) {
            bundle.add(createPhase(baseId, name, role, centerLateral, verticalOffset));
            return bundle;
        }
        double totalSpan = (count - 1) * bundleSpacing;
        double start = centerLateral - totalSpan / 2.0;
        for (int i = 0; i < count; i++) {
            String id = baseId + "_" + (i + 1);
            bundle.add(createPhase(id, name + (i + 1), role, start + i * bundleSpacing, verticalOffset));
        }
        return bundle;
    }

    private static ConductorAttachment topWire(String id, String name, double lateral, double vertical) {
        ConductorAttachment attachment = new ConductorAttachment(id, name);
        attachment.setRole(AttachmentRole.GROUND_WIRE);
        attachment.setLateralOffset(lateral);
        attachment.setVerticalOffset(vertical);
        attachment.setInsulatorLength(1);
        return attachment;
    }

    private static ConductorAttachment createPhase(
            String id,
            String name,
            AttachmentRole role,
            double lateral,
            double verticalOffset) {
        ConductorAttachment attachment = new ConductorAttachment(id, name);
        attachment.setRole(role);
        attachment.setLateralOffset(lateral);
        attachment.setVerticalOffset(verticalOffset);
        return attachment;
    }
}
