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
        InsulatorType type = attachment.insulatorType() != null
            ? attachment.insulatorType()
            : InsulatorType.SUSPENSION;
        if (type == InsulatorType.STRAIN) {
            placeStrain(attachment, frame, footprint, result, projectionHandler);
        } else {
            placeSuspension(attachment, footprint, result, projectionHandler);
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
        for (int y = startY; y <= endY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(result, pos, blockId, projectionHandler);
        }
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
        for (BlockPos pos : blocks) {
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(result, pos, blockId, projectionHandler);
        }
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
