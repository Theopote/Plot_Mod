package com.plot.plugin.powerline.design;

import java.util.ArrayList;
import java.util.List;

/** 常用导线挂点预设。 */
public final class ConductorAttachmentPresets {
    public static final String PHASE_A_ID = "phase_a";
    public static final String PHASE_B_ID = "phase_b";
    public static final String PHASE_C_ID = "phase_c";

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
