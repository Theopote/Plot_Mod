package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.equipment.InsulatorMountStyle;
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
        InsulatorType insulatorType,
        InsulatorMountStyle mountStyle) {

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
            InsulatorType.SUSPENSION,
            InsulatorMountStyle.COLUMN);
    }

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
            int insulatorLength,
            InsulatorType insulatorType) {
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
            insulatorType,
            mountStyleFor(insulatorType));
    }

    public static InsulatorMountStyle mountStyleFor(InsulatorType type) {
        if (type == InsulatorType.STRAIN) {
            return InsulatorMountStyle.HORIZONTAL;
        }
        if (type == InsulatorType.VERTICAL) {
            return InsulatorMountStyle.TWIN_COLUMN;
        }
        return InsulatorMountStyle.COLUMN;
    }

    public double conductorWorldY() {
        return worldY;
    }
}
