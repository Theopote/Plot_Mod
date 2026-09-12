package com.plot.plugin.powerline.equipment;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.ConductorMaterialPolicy;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineWireRasterizer;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 转角塔、中间终端塔、耐张塔上的局部跳线生成。 */
public final class JumperWireGenerator {
    private static final int WIRE_SAMPLES_PER_BLOCK = 1;
    private static final double JUMPER_ARM_LENGTH = 2.0;

    private JumperWireGenerator() {
    }

    public static void generateForAngleTower(
            PolePlacement placement,
            Vec2d incomingDirection,
            Vec2d outgoingDirection,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        if (placement == null
                || footprint == null
                || result == null
                || !needsJumper(placement.role())
                || !placement.usesAttachmentConductors()) {
            return;
        }
        if (incomingDirection == null || outgoingDirection == null) {
            return;
        }
        Vec2d incoming = normalize(incomingDirection);
        Vec2d outgoing = normalize(outgoingDirection);

        List<ResolvedAttachment> attachments = placement.attachments();
        if (attachments == null) {
            return;
        }
        for (ResolvedAttachment attachment : attachments) {
            if (attachment.role() == AttachmentRole.TOP_WIRE
                    || attachment.role() == AttachmentRole.AUXILIARY) {
                continue;
            }
            generateJumper(
                attachment,
                incoming,
                outgoing,
                footprint,
                result,
                projectionHandler);
        }
    }

    private static void generateJumper(
            ResolvedAttachment attachment,
            Vec2d incoming,
            Vec2d outgoing,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        Vec2d planStart = attachment.planPoint().add(incoming.multiply(-JUMPER_ARM_LENGTH));
        Vec2d planEnd = attachment.planPoint().add(outgoing.multiply(JUMPER_ARM_LENGTH));
        double wireY = attachment.conductorWorldY();
        double sagRatio = footprint.getSagRatio() * 0.5;

        double spanLength = planStart.distance(planEnd);
        if (spanLength < 1e-6) {
            return;
        }

        int sampleCount = PowerLineWireRasterizer.computeWireSampleCount(spanLength, WIRE_SAMPLES_PER_BLOCK);
        int segmentCount = sampleCount - 1;
        double rawSagDepth = spanLength * sagRatio;
        double maxSagDepth = com.plot.plugin.powerline.PowerLineSagPolicy.resolveMaxSagDepth(footprint);
        double sagDepth = maxSagDepth > 0.0
            ? Math.min(rawSagDepth, maxSagDepth)
            : rawSagDepth;

        MaterialMix wireMaterial = ConductorMaterialPolicy.materialFor(attachment.role(), footprint);
        Set<BlockPos> wireBlocks = new LinkedHashSet<>();

        for (int i = 0; i < segmentCount; i++) {
            double t0 = (double) i / segmentCount;
            double t1 = (double) (i + 1) / segmentCount;
            Vec2d xz0 = planStart.lerp(planEnd, t0);
            Vec2d xz1 = planStart.lerp(planEnd, t1);
            double y0 = wireY - sagDepthAt(t0, sagDepth);
            double y1 = wireY - sagDepthAt(t1, sagDepth);
            wireBlocks.addAll(PowerLineWireRasterizer.rasterizeLine3D(
                xz0.x, y0, xz0.y,
                xz1.x, y1, xz1.y));
        }

        for (BlockPos pos : wireBlocks) {
            String blockId = MaterialMixResolver.resolve(wireMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId, projectionHandler);
        }
    }

    private static boolean needsJumper(TowerRole role) {
        return role == TowerRole.ANGLE
            || role == TowerRole.TERMINAL
            || role == TowerRole.DEAD_END;
    }

    private static double sagDepthAt(double t, double sagDepth) {
        return sagDepth * 4.0 * t * (1.0 - t);
    }

    private static Vec2d normalize(Vec2d direction) {
        if (direction == null || direction.lengthSquared() < 1e-12) {
            return new Vec2d(1, 0);
        }
        return direction.normalize();
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
