package com.plot.plugin.powerline.equipment;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.PlacementCategory;
import com.plot.plugin.powerline.placement.WireBlockPlacement;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** 绝缘子等线路设备的体素放置。 */
public final class LineEquipmentGenerator {
    private LineEquipmentGenerator() {
    }

    public static void place(
            ResolvedAttachment attachment,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        if (attachment == null || attachment.insulatorLength() <= 0) {
            return;
        }
        InsulatorMountStyle style = attachment.mountStyle() != null
            ? attachment.mountStyle()
            : ResolvedAttachment.mountStyleFor(attachment.insulatorType());
        switch (style) {
            case HORIZONTAL -> placeStrain(attachment, frame, footprint, result, projectionHandler);
            case TWIN_COLUMN -> placeTwinColumn(attachment, frame, footprint, result, projectionHandler);
            case V_PAIR -> placeVPair(attachment, frame, footprint, result, projectionHandler);
            default -> placeSuspension(attachment, footprint, result, projectionHandler);
        }
    }

    private static void placeSuspension(
            ResolvedAttachment attachment,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        int startY = (int) Math.floor(attachment.structuralWorldY());
        int endY = (int) Math.floor(attachment.conductorWorldY()) - 1;
        int x = (int) Math.floor(attachment.worldX());
        int z = (int) Math.floor(attachment.worldZ());
        MaterialMix material = attachment.insulatorMaterial();
        placeMemberPath(
            material,
            footprint,
            result,
            projectionHandler,
            VoxelLineRasterizer.rasterizeLine3D(x, startY, z, x, endY, z));
    }

    private static void placeTwinColumn(
            ResolvedAttachment attachment,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        int startY = (int) Math.floor(attachment.structuralWorldY());
        int endY = (int) Math.floor(attachment.conductorWorldY()) - 1;
        int baseX = (int) Math.floor(attachment.worldX());
        int baseZ = (int) Math.floor(attachment.worldZ());
        Vec2d right = frame != null && frame.right().lengthSquared() > 1e-12
            ? frame.right().normalize()
            : new Vec2d(1, 0);
        MaterialMix material = attachment.insulatorMaterial();
        for (int offset : new int[] {0, 1}) {
            int offsetX = (int) Math.round(right.x * offset);
            int offsetZ = (int) Math.round(right.y * offset);
            placeMemberPath(
                material,
                footprint,
                result,
                projectionHandler,
                VoxelLineRasterizer.rasterizeLine3D(
                    baseX + offsetX, startY, baseZ + offsetZ,
                    baseX + offsetX, endY, baseZ + offsetZ));
        }
    }

    private static void placeVPair(
            ResolvedAttachment attachment,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        int x = (int) Math.floor(attachment.worldX());
        int z = (int) Math.floor(attachment.worldZ());
        Vec2d right = frame != null && frame.right().lengthSquared() > 1e-12
            ? frame.right().normalize()
            : new Vec2d(1, 0);
        double conductorY = attachment.conductorWorldY();
        double structuralY = attachment.structuralWorldY();
        double midY = structuralY + (conductorY - structuralY) * 0.55;
        MaterialMix material = attachment.insulatorMaterial();

        double leftX = x - right.x;
        double leftZ = z - right.y;
        double rightX = x + right.x;
        double rightZ = z + right.y;

        List<BlockPos> leftLeg = VoxelLineRasterizer.rasterizeLine3D(
            leftX, structuralY, leftZ,
            x, midY, z);
        List<BlockPos> rightLeg = VoxelLineRasterizer.rasterizeLine3D(
            rightX, structuralY, rightZ,
            x, midY, z);
        List<BlockPos> drop = VoxelLineRasterizer.rasterizeLine3D(
            x, midY, z,
            x, conductorY, z);

        placeMemberPath(material, footprint, result, projectionHandler, leftLeg);
        placeMemberPath(material, footprint, result, projectionHandler, rightLeg);
        placeMemberPath(material, footprint, result, projectionHandler, drop);
    }

    private static void placeStrain(
            ResolvedAttachment attachment,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        Vec2d forward = frame != null ? frame.forward() : new Vec2d(1, 0);
        if (forward.lengthSquared() < 1e-12) {
            forward = new Vec2d(1, 0);
        } else {
            forward = forward.normalize();
        }

        double structuralX = attachment.worldX() - forward.x * attachment.insulatorLength();
        double structuralZ = attachment.worldZ() - forward.y * attachment.insulatorLength();
        double y = attachment.conductorWorldY();

        List<BlockPos> blocks = VoxelLineRasterizer.rasterizeLine3D(
            structuralX,
            y,
            structuralZ,
            attachment.worldX(),
            y,
            attachment.worldZ());

        MaterialMix material = attachment.insulatorMaterial();
        placeMemberPath(material, footprint, result, projectionHandler, blocks);
    }

    private static void placeMemberPath(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler,
            List<BlockPos> path) {
        WireBlockPlacement.placeAlongPath(
            material,
            footprint,
            result,
            projectionHandler,
            path,
            PlacementCategory.INSULATOR);
    }

}
