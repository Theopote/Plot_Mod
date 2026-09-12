package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.equipment.InsulatorAssembly;
import com.plot.plugin.powerline.equipment.InsulatorAssemblyCatalog;
import com.plot.plugin.powerline.equipment.InsulatorMountStyle;
import com.plot.plugin.powerline.equipment.InsulatorType;

import java.util.ArrayList;
import java.util.List;

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

        TowerStructureDesign structure = design.getTowerStructure();
        List<ResolvedAttachment> resolved = new ArrayList<>();
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            resolved.add(resolveOne(attachment, frame, structure));
        }
        return resolved;
    }

    private ResolvedAttachment resolveOne(
            ConductorAttachment attachment,
            PoleFrame frame,
            TowerStructureDesign structure) {
        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        Vec2d planPoint = frame.toPlanPoint(
            local.lateral(),
            local.longitudinal());
        double[] worldXz = planToWorldXz(planPoint);
        double conductorY = frame.groundY() + local.vertical();
        InsulatorAssembly assembly = InsulatorAssemblyCatalog.find(attachment.getInsulatorAssemblyId());
        InsulatorType insulatorType = assembly != null
            ? assembly.getType()
            : attachment.getInsulatorType();
        int insulatorLength = assembly != null
            ? assembly.getLength()
            : attachment.getInsulatorLength();
        MaterialMix insulatorMaterial = assembly != null
            ? assembly.getMaterial()
            : attachment.getInsulatorMaterial();
        InsulatorMountStyle mountStyle = assembly != null
            ? assembly.getMountStyle()
            : ResolvedAttachment.mountStyleFor(insulatorType);
        double structuralY;
        if (insulatorLength <= 0) {
            structuralY = conductorY;
        } else if (mountStyle == InsulatorMountStyle.COLUMN
                || mountStyle == InsulatorMountStyle.TWIN_COLUMN
                || mountStyle == InsulatorMountStyle.V_PAIR) {
            structuralY = conductorY + insulatorLength;
        } else {
            structuralY = conductorY - insulatorLength;
        }

        return new ResolvedAttachment(
            attachment.getId(),
            attachment.getName(),
            attachment.getRole(),
            planPoint,
            worldXz[0],
            conductorY,
            worldXz[1],
            structuralY,
            insulatorMaterial,
            insulatorLength,
            insulatorType,
            mountStyle,
            attachment.getBundleVisual());
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
