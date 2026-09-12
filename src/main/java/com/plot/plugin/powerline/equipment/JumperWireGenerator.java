package com.plot.plugin.powerline.equipment;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
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
import com.plot.plugin.powerline.placement.DirectionalBlockSpecs;
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
            ICoordinateService coordinateTransformer,
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
                coordinateTransformer,
                projectionHandler);
        }
    }

    private static void generateJumper(
            ResolvedAttachment attachment,
            Vec2d incoming,
            Vec2d outgoing,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        Vec2d planStart = attachment.planPoint().add(incoming.multiply(-JUMPER_ARM_LENGTH));
        Vec2d planEnd = attachment.planPoint().add(outgoing.multiply(JUMPER_ARM_LENGTH));
        double wireY = attachment.conductorWorldY();
        double sagRatio = footprint.getSagRatio() * 0.5;

        double spanLength = spanLengthBlocks(planStart, planEnd, coordinateTransformer);
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
        double[] worldStart = planToWorldXz(planStart, coordinateTransformer);
        double[] worldEnd = planToWorldXz(planEnd, coordinateTransformer);

        for (int i = 0; i < segmentCount; i++) {
            double t0 = (double) i / segmentCount;
            double t1 = (double) (i + 1) / segmentCount;
            double x0 = lerp(worldStart[0], worldEnd[0], t0);
            double z0 = lerp(worldStart[1], worldEnd[1], t0);
            double x1 = lerp(worldStart[0], worldEnd[0], t1);
            double z1 = lerp(worldStart[1], worldEnd[1], t1);
            double y0 = wireY - sagDepthAt(t0, sagDepth);
            double y1 = wireY - sagDepthAt(t1, sagDepth);
            Set<BlockPos> wireBlocks = new LinkedHashSet<>(PowerLineWireRasterizer.rasterizeLine3D(
                x0, y0, z0,
                x1, y1, z1));
            placeDirectedWireBlocks(
                wireMaterial,
                footprint,
                result,
                projectionHandler,
                wireBlocks,
                x1 - x0,
                y1 - y0,
                z1 - z0);
        }
    }

    private static void placeDirectedWireBlocks(
            MaterialMix wireMaterial,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler,
            Set<BlockPos> wireBlocks,
            double deltaX,
            double deltaY,
            double deltaZ) {
        if (wireBlocks == null || wireBlocks.isEmpty()) {
            return;
        }
        BlockPos samplePos = wireBlocks.iterator().next();
        String sampleBlockId = MaterialMixResolver.resolve(wireMaterial, samplePos, footprint.getId());
        String placementId = DirectionalBlockSpecs.resolveMemberPlacement(
            sampleBlockId, deltaX, deltaY, deltaZ).toSetBlockArgument();
        for (BlockPos pos : wireBlocks) {
            recordBlock(result, pos, placementId, projectionHandler);
        }
    }

    private static boolean needsJumper(TowerRole role) {
        return role == TowerRole.ANGLE
            || role == TowerRole.TERMINAL
            || role == TowerRole.DEAD_END;
    }

    private static double spanLengthBlocks(
            Vec2d planStart,
            Vec2d planEnd,
            ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            return coordinateTransformer.projectedDistance(planStart, planEnd);
        }
        return planStart.distance(planEnd);
    }

    private static double[] planToWorldXz(Vec2d planPoint, ICoordinateService coordinateTransformer) {
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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
