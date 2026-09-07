package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 PoleDesign 中的局部挂点解析为世界坐标。 */
public final class PowerLineAttachmentResolver {
    private final ICoordinateService coordinateTransformer;

    public PowerLineAttachmentResolver(ICoordinateService coordinateTransformer) {
        this.coordinateTransformer = coordinateTransformer;
    }

    public List<ResolvedAttachment> resolve(PoleDesign design, PoleFrame frame) {
        if (design == null || frame == null || !design.hasEnabledAttachments()) {
            return List.of();
        }

        List<ResolvedAttachment> resolved = new ArrayList<>();
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            resolved.add(resolveOne(attachment, frame));
        }
        return resolved;
    }

    public Map<String, ResolvedAttachment> resolveById(PoleDesign design, PoleFrame frame) {
        Map<String, ResolvedAttachment> indexed = new LinkedHashMap<>();
        for (ResolvedAttachment attachment : resolve(design, frame)) {
            indexed.put(attachment.id(), attachment);
        }
        return indexed;
    }

    private ResolvedAttachment resolveOne(ConductorAttachment attachment, PoleFrame frame) {
        Vec2d planPoint = frame.toPlanPoint(
            attachment.getLateralOffset(),
            attachment.getLongitudinalOffset());
        double[] worldXz = planToWorldXz(planPoint);
        double conductorY = frame.groundY() + attachment.getVerticalOffset();
        int insulatorLength = attachment.getInsulatorLength();
        double structuralY = insulatorLength > 0
            ? conductorY - insulatorLength
            : conductorY;

        return new ResolvedAttachment(
            attachment.getId(),
            attachment.getName(),
            attachment.getRole(),
            planPoint,
            worldXz[0],
            conductorY,
            worldXz[1],
            structuralY,
            attachment.getInsulatorMaterial(),
            insulatorLength);
    }

    private double[] planToWorldXz(Vec2d planPoint) {
        if (planPoint == null) {
            return new double[] {0.0, 0.0};
        }
        if (coordinateTransformer != null) {
            Vec2d worldPos = coordinateTransformer.canvasToMinecraftWorld(planPoint);
            if (worldPos != null) {
                return new double[] {worldPos.x, worldPos.y};
            }
        }
        return new double[] {planPoint.x, planPoint.y};
    }
}
