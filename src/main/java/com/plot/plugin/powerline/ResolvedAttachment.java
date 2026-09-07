package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.equipment.InsulatorType;

/**
 * 世界坐标系下已解析的导线挂点。
 */
public record ResolvedAttachment(
        String id,
        String name,
        AttachmentRole role,
        Vec2d planPoint,
        double worldX,
        double worldY,
        double worldZ,
        double structuralWorldY,
        MaterialMix insulatorMaterial,
        int insulatorLength,
        InsulatorType insulatorType) {

    public ResolvedAttachment(
            String id,
            String name,
            AttachmentRole role,
            Vec2d planPoint,
            double worldX,
            double worldY,
            double worldZ,
            double structuralWorldY,
            MaterialMix insulatorMaterial,
            int insulatorLength) {
        this(
            id,
            name,
            role,
            planPoint,
            worldX,
            worldY,
            worldZ,
            structuralWorldY,
            insulatorMaterial,
            insulatorLength,
            InsulatorType.SUSPENSION);
    }

    public double conductorWorldY() {
        return worldY;
    }
}
