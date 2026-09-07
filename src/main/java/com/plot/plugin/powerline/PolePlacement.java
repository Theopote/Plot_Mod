package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;

import java.util.List;

/** 单座杆塔的结构与挂点解析结果。 */
public record PolePlacement(
        Vec2d planPosition,
        PoleFrame frame,
        PoleDesign design,
        List<ResolvedAttachment> attachments,
        int legacyWireHangY,
        boolean usesAttachmentConductors) {
}
