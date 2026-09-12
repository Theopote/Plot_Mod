package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.List;

/** 单座杆塔的结构与挂点解析结果。 */
public record PolePlacement(
        Vec2d planPosition,
        PoleFrame frame,
        PoleDesign design,
        List<ResolvedAttachment> attachments,
        int legacyWireHangY,
        boolean usesAttachmentConductors,
        TowerRole role,
        String resolvedDesignId,
        double stationing,
        boolean valid) {

    public PolePlacement(
            Vec2d planPosition,
            PoleFrame frame,
            PoleDesign design,
            List<ResolvedAttachment> attachments,
            int legacyWireHangY,
            boolean usesAttachmentConductors) {
        this(
            planPosition,
            frame,
            design,
            attachments,
            legacyWireHangY,
            usesAttachmentConductors,
            TowerRole.SUSPENSION,
            null,
            0.0,
            true);
    }

    public PolePlacement(
            Vec2d planPosition,
            PoleFrame frame,
            PoleDesign design,
            List<ResolvedAttachment> attachments,
            int legacyWireHangY,
            boolean usesAttachmentConductors,
            TowerRole role,
            String resolvedDesignId,
            double stationing) {
        this(
            planPosition,
            frame,
            design,
            attachments,
            legacyWireHangY,
            usesAttachmentConductors,
            role,
            resolvedDesignId,
            stationing,
            true);
    }

    public boolean isValid() {
        return valid;
    }
}
