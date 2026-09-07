package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;

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
        int insulatorLength) {

    public double conductorWorldY() {
        return worldY;
    }
}
