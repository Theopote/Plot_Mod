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
