package com.plot.plugin.powerline.equipment;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.DirectionalBlockSpecs;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

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
        int deltaY = endY >= startY ? 1 : -1;
        MaterialMix material = attachment.insulatorMaterial();
        for (int y = startY; y <= endY; y++) {
            recordDirectedMemberBlock(
                material,
                footprint,
                result,
                projectionHandler,
                new BlockPos(x, y, z),
                0.0,
                deltaY,
                0.0);
        }
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
        int deltaY = endY >= startY ? 1 : -1;
        for (int offset : new int[] {0, 1}) {
            int offsetX = (int) Math.round(right.x * offset);
            int offsetZ = (int) Math.round(right.y * offset);
            for (int y = startY; y <= endY; y++) {
                recordDirectedMemberBlock(
                    material,
                    footprint,
                    result,
                    projectionHandler,
                    new BlockPos(baseX + offsetX, y, baseZ + offsetZ),
                    0.0,
                    deltaY,
                    0.0);
            }
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

        Set<BlockPos> leftLeg = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            leftX, structuralY, leftZ,
            x, midY, z));
        Set<BlockPos> rightLeg = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            rightX, structuralY, rightZ,
            x, midY, z));
        Set<BlockPos> drop = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            x, midY, z,
            x, conductorY, z));

        placeDirectedMemberBlocks(
            material, footprint, result, projectionHandler, leftLeg,
            leftX, structuralY, leftZ, x, midY, z);
        placeDirectedMemberBlocks(
            material, footprint, result, projectionHandler, rightLeg,
            rightX, structuralY, rightZ, x, midY, z);
        placeDirectedMemberBlocks(
            material, footprint, result, projectionHandler, drop,
            x, midY, z, x, conductorY, z);
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

        Set<BlockPos> blocks = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            structuralX,
            y,
            structuralZ,
            attachment.worldX(),
            y,
            attachment.worldZ()));

        MaterialMix material = attachment.insulatorMaterial();
        placeDirectedMemberBlocks(
            material,
            footprint,
            result,
            projectionHandler,
            blocks,
            structuralX,
            y,
            structuralZ,
            attachment.worldX(),
            y,
            attachment.worldZ());
    }

    private static void placeDirectedMemberBlocks(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler,
            Set<BlockPos> blocks,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        BlockPos samplePos = blocks.iterator().next();
        String sampleBlockId = MaterialMixResolver.resolve(material, samplePos, footprint.getId());
        String placementId = DirectionalBlockSpecs.resolveMemberPlacement(
            sampleBlockId,
            endX - startX,
            endY - startY,
            endZ - startZ).toSetBlockArgument();
        for (BlockPos pos : blocks) {
            recordBlock(result, pos, placementId, projectionHandler);
        }
    }

    private static void recordDirectedMemberBlock(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler,
            BlockPos pos,
            double deltaX,
            double deltaY,
            double deltaZ) {
        String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
        String placementId = DirectionalBlockSpecs.resolveMemberPlacement(
            blockId, deltaX, deltaY, deltaZ).toSetBlockArgument();
        recordBlock(result, pos, placementId, projectionHandler);
    }

    private static void recordBlock(
            PowerLineGenerationResult result,
            BlockPos pos,
            String newBlockId,
            IBlockProjectionService projectionHandler) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projectionHandler != null
            ? projectionHandler.getBlockIdAt(pos)
            : "minecraft:air";
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
