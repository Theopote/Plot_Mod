package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.equipment.InsulatorType;

/** 单根导线的视觉挂点描述（无电气工程语义）。 */
public record ConductorChannel(
        String id,
        String label,
        AttachmentRole role,
        double lateralOffset,
        /** 相对 {@link ConductorArrangement#toAttachments} 基准高度的垂直偏移（格）。 */
        double verticalOffset,
        InsulatorType insulatorType,
        int insulatorLength,
        BundleVisual bundleVisual) {

    public ConductorChannel {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Conductor channel id required");
        }
        if (role == null) {
            role = AttachmentRole.PHASE_A;
        }
        if (insulatorType == null) {
            insulatorType = InsulatorType.SUSPENSION;
        }
        if (insulatorLength < 0) {
            insulatorLength = 0;
        }
        if (bundleVisual == null) {
            bundleVisual = BundleVisual.SINGLE;
        }
    }

    public ConductorChannel(String id, String label, AttachmentRole role, double lateralOffset, double verticalOffset) {
        this(id, label, role, lateralOffset, verticalOffset, InsulatorType.SUSPENSION, 2, BundleVisual.SINGLE);
    }

    public ConductorChannel(
            String id,
            String label,
            AttachmentRole role,
            double lateralOffset,
            double verticalOffset,
            InsulatorType insulatorType,
            int insulatorLength) {
        this(id, label, role, lateralOffset, verticalOffset, insulatorType, insulatorLength, BundleVisual.SINGLE);
    }

    public ConductorAttachment toAttachment(double baseHeight) {
        ConductorAttachment attachment = new ConductorAttachment(id, label != null ? label : id);
        attachment.setRole(role);
        attachment.setLateralOffset(lateralOffset);
        attachment.setVerticalOffset(baseHeight + verticalOffset);
        attachment.setInsulatorType(insulatorType);
        attachment.setInsulatorLength(insulatorLength);
        attachment.setBundleVisual(bundleVisual);
        return attachment;
    }
}
