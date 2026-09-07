package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 相邻杆塔挂点匹配与多导线 span 生成。 */
public final class ConductorSpanGenerator {
    private static final int CLEARANCE_MARGIN = 1;
    private static final int WIRE_SAMPLES_PER_BLOCK = 1;

    private ConductorSpanGenerator() {
    }

    public static void generateBetween(
            PolePlacement start,
            PolePlacement end,
            int startPoleIndex,
            int endPoleIndex,
            String startSiteId,
            String endSiteId,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        if (start == null || end == null || footprint == null || result == null) {
            return;
        }

        if (start.usesAttachmentConductors() || end.usesAttachmentConductors()) {
            generateAttachmentConductors(
                start,
                end,
                startPoleIndex,
                endPoleIndex,
                startSiteId,
                endSiteId,
                footprint,
                terrain,
                result,
                projectionHandler);
            return;
        }

        generateLegacyCenterConductor(
            start,
            end,
            startPoleIndex,
            endPoleIndex,
            startSiteId,
            endSiteId,
            footprint,
            terrain,
            result,
            coordinateTransformer,
            projectionHandler);
    }

    private static void generateAttachmentConductors(
            PolePlacement start,
            PolePlacement end,
            int startPoleIndex,
            int endPoleIndex,
            String startSiteId,
            String endSiteId,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        Map<String, ResolvedAttachment> startById = indexById(start.attachments());
        Map<String, ResolvedAttachment> endById = indexById(end.attachments());

        for (String id : startById.keySet()) {
            ResolvedAttachment startAttachment = startById.get(id);
            ResolvedAttachment endAttachment = endById.get(id);
            if (endAttachment == null) {
                result.warnings.add(String.format(
                    "Missing attachment '%s' (%s) on downstream pole at (%.1f, %.1f)",
                    startAttachment.name(),
                    id,
                    end.planPosition().x,
                    end.planPosition().y));
                continue;
            }
            if (startAttachment.role() != endAttachment.role()) {
                result.warnings.add(String.format(
                    "Attachment role mismatch for '%s': %s vs %s — skipping span",
                    id,
                    startAttachment.role(),
                    endAttachment.role()));
                continue;
            }
            generateConductorSpan(
                startAttachment,
                endAttachment,
                startPoleIndex,
                endPoleIndex,
                startSiteId,
                endSiteId,
                footprint,
                terrain,
                result,
                projectionHandler);
        }

        for (String id : endById.keySet()) {
            if (!startById.containsKey(id)) {
                ResolvedAttachment endAttachment = endById.get(id);
                result.warnings.add(String.format(
                    "Missing attachment '%s' (%s) on upstream pole at (%.1f, %.1f)",
                    endAttachment.name(),
                    id,
                    start.planPosition().x,
                    start.planPosition().y));
            }
        }
    }

    private static Map<String, ResolvedAttachment> indexById(List<ResolvedAttachment> attachments) {
        java.util.LinkedHashMap<String, ResolvedAttachment> indexed = new java.util.LinkedHashMap<>();
        if (attachments == null) {
            return indexed;
        }
        for (ResolvedAttachment attachment : attachments) {
            indexed.put(attachment.id(), attachment);
        }
        return indexed;
    }

    static void generateConductorSpan(
            ResolvedAttachment start,
            ResolvedAttachment end,
            int startPoleIndex,
            int endPoleIndex,
            String startSiteId,
            String endSiteId,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        double spanLength = start.planPoint().distance(end.planPoint());
        if (spanLength < 1e-6) {
            return;
        }
        result.wireLength += spanLength;

        int sampleCount = PowerLineWireRasterizer.computeWireSampleCount(spanLength, WIRE_SAMPLES_PER_BLOCK);
        int segmentCount = sampleCount - 1;
        List<Double> sagProfile = PowerLineSagUtils.computeSagProfile(
            spanLength,
            start.conductorWorldY(),
            end.conductorWorldY(),
            footprint.getSagRatio(),
            sampleCount);

        ConductorSpanGeometry geometry = new ConductorSpanGeometry();
        geometry.setSpanId(startSiteId + "->" + endSiteId + ":" + start.id());
        geometry.setAttachmentId(start.id());
        geometry.setRole(start.role());
        geometry.setStartPoleIndex(startPoleIndex);
        geometry.setEndPoleIndex(endPoleIndex);
        geometry.setStartPoleSiteId(startSiteId);
        geometry.setEndPoleSiteId(endSiteId);
        geometry.setSpanLength(spanLength);

        double[] worldX = new double[sampleCount];
        double[] worldY = new double[sampleCount];
        double[] worldZ = new double[sampleCount];
        Vec2d[] planPoints = new Vec2d[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / segmentCount;
            planPoints[i] = start.planPoint().lerp(end.planPoint(), t);
            worldX[i] = lerp(start.worldX(), end.worldX(), t);
            worldZ[i] = lerp(start.worldZ(), end.worldZ(), t);
            worldY[i] = sagProfile.get(i);
            geometry.addSample(new ConductorSample(worldX[i], worldY[i], worldZ[i], planPoints[i].copy()));
        }

        MaterialMix wireMaterial = ConductorMaterialPolicy.materialFor(start.role(), footprint);
        LinkedHashSet<BlockPos> wireBlocks = new LinkedHashSet<>();
        for (int i = 0; i < segmentCount; i++) {
            wireBlocks.addAll(PowerLineWireRasterizer.rasterizeLine3D(
                worldX[i], worldY[i], worldZ[i],
                worldX[i + 1], worldY[i + 1], worldZ[i + 1]));
        }

        for (int i = 0; i < sampleCount; i++) {
            checkClearance(planPoints[i], (int) Math.round(worldY[i]), terrain, result);
        }

        for (BlockPos pos : wireBlocks) {
            String blockId = MaterialMixResolver.resolve(wireMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId, projectionHandler);
        }

        result.conductorSpans.add(geometry);
    }

    private static void generateLegacyCenterConductor(
            PolePlacement start,
            PolePlacement end,
            int startPoleIndex,
            int endPoleIndex,
            String startSiteId,
            String endSiteId,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        double spanLength = start.planPosition().distance(end.planPosition());
        if (spanLength < 1e-6) {
            return;
        }
        result.wireLength += spanLength;

        int sampleCount = PowerLineWireRasterizer.computeWireSampleCount(spanLength, WIRE_SAMPLES_PER_BLOCK);
        int segmentCount = sampleCount - 1;
        List<Double> sagProfile = PowerLineSagUtils.computeSagProfile(
            spanLength,
            start.legacyWireHangY(),
            end.legacyWireHangY(),
            footprint.getSagRatio(),
            sampleCount);

        double[] worldX = new double[sampleCount];
        double[] worldY = new double[sampleCount];
        double[] worldZ = new double[sampleCount];
        Vec2d[] planPoints = new Vec2d[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / segmentCount;
            planPoints[i] = start.planPosition().lerp(end.planPosition(), t);
            double[] worldXz = planToWorldXz(planPoints[i], coordinateTransformer);
            worldX[i] = worldXz[0];
            worldZ[i] = worldXz[1];
            worldY[i] = sagProfile.get(i);
        }

        ConductorSpanGeometry geometry = new ConductorSpanGeometry();
        geometry.setSpanId(startSiteId + "->" + endSiteId + ":legacy");
        geometry.setAttachmentId("legacy_center");
        geometry.setRole(AttachmentRole.AUXILIARY);
        geometry.setStartPoleIndex(startPoleIndex);
        geometry.setEndPoleIndex(endPoleIndex);
        geometry.setStartPoleSiteId(startSiteId);
        geometry.setEndPoleSiteId(endSiteId);
        geometry.setSpanLength(spanLength);
        for (int i = 0; i < sampleCount; i++) {
            geometry.addSample(new ConductorSample(worldX[i], worldY[i], worldZ[i], planPoints[i].copy()));
        }

        MaterialMix wireMaterial = footprint.getWireMaterial();
        LinkedHashSet<BlockPos> wireBlocks = new LinkedHashSet<>();
        for (int i = 0; i < segmentCount; i++) {
            wireBlocks.addAll(PowerLineWireRasterizer.rasterizeLine3D(
                worldX[i], worldY[i], worldZ[i],
                worldX[i + 1], worldY[i + 1], worldZ[i + 1]));
        }

        for (int i = 0; i < sampleCount; i++) {
            checkClearance(planPoints[i], (int) Math.round(worldY[i]), terrain, result);
        }

        for (BlockPos pos : wireBlocks) {
            String blockId = MaterialMixResolver.resolve(wireMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId, projectionHandler);
        }
        result.conductorSpans.add(geometry);
    }
    @Deprecated
    public static void placeInsulator(
            ResolvedAttachment attachment,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projectionHandler) {
        com.plot.plugin.powerline.equipment.LineEquipmentGenerator.place(
            attachment,
            null,
            footprint,
            result,
            projectionHandler);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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

    private static void checkClearance(
            Vec2d planPoint,
            int wireY,
            TerrainSampler terrain,
            PowerLineGenerationResult result) {
        if (terrain == null) {
            return;
        }
        int groundY = terrain.sampleSurfaceY(planPoint);
        if (wireY < groundY + CLEARANCE_MARGIN) {
            result.warnings.add(String.format(
                "Clearance warning at (%.1f, %.1f): wire Y=%d, ground Y=%d",
                planPoint.x,
                planPoint.y,
                wireY,
                groundY));
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
