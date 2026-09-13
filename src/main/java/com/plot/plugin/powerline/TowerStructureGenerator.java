package com.plot.plugin.powerline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.placement.DirectionalBlockSpecs;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmPlacement;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerMemberProfile;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 参数化塔体结构生成（与导线拓扑独立）。 */
public final class TowerStructureGenerator {
    private static final double UNEVEN_BASE_WARNING_THRESHOLD = 2.0;

    private TowerStructureGenerator() {
    }

    public static int generate(
            TowerStructureDesign structure,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            ICoordinateService coordinates,
            IBlockProjectionService projection,
            TerrainSampler terrain) {
        if (structure == null || frame == null || footprint == null || result == null) {
            return frame != null ? frame.groundY() : 0;
        }

        for (TowerValidationIssue issue : TowerStructureValidator.validate(
                wrapForValidation(structure))) {
            if (issue.severity() == com.plot.plugin.powerline.design.structure.TowerValidationSeverity.ERROR) {
                result.warnings.add(issue.localizedMessage());
            } else if (issue.severity() == com.plot.plugin.powerline.design.structure.TowerValidationSeverity.WARNING) {
                result.warnings.add(issue.localizedMessage());
            }
        }

        TowerStructureTransform transform = new TowerStructureTransform(frame, coordinates);
        GenerationCounters counters = new GenerationCounters();
        List<TowerStation> stations = structure.sortedStations();
        if (stations.size() < 2) {
            return frame.groundY() + (int) Math.round(structure.maxHeight());
        }

        checkBaseTerrain(stations.get(0), transform, terrain, result);

        for (int i = 1; i < stations.size(); i++) {
            TowerStation lower = stations.get(i - 1);
            TowerStation upper = stations.get(i);
            TowerBay bay = structure.findBay(lower.getId(), upper.getId());
            if (bay == null) {
                bay = new TowerBay(lower.getId(), upper.getId());
                bay.setFrontBackBracing(BracingPattern.X);
                bay.setSideBracing(BracingPattern.X);
                bay.setHorizontalRing(true);
            }

            generateLegs(lower, upper, structure, transform, footprint, result, projection, counters);
            generateFaceBracing(
                lower,
                upper,
                bay.getFrontBackBracing(),
                TowerStructureGeometry.frontCorners(),
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                true);
            generateFaceBracing(
                lower,
                upper,
                bay.getSideBracing(),
                TowerStructureGeometry.rightCorners(),
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                false);
            if (bay.isHorizontalRing()) {
                generateHorizontalRing(upper, structure, transform, footprint, result, projection, counters);
            }
            if (bay.isPlanDiagonalBracing()) {
                generatePlanDiagonalBracing(upper, structure, transform, footprint, result, projection, counters);
            }
        }

        for (TowerArm arm : structure.getArms()) {
            generateArm(arm, structure, transform, footprint, result, projection, counters);
        }

        for (TowerDecoration decoration : structure.getDecorations()) {
            generateDecoration(decoration, structure, transform, footprint, result, projection, counters);
        }

        result.structureBlockCount += counters.total();
        result.braceBlockCount += counters.braceBlocks;
        result.armBlockCount += counters.armBlocks;
        return frame.groundY() + (int) Math.round(stations.get(stations.size() - 1).getHeight());
    }

    private static com.plot.plugin.powerline.design.PoleDesign wrapForValidation(
            TowerStructureDesign structure) {
        com.plot.plugin.powerline.design.PoleDesign design =
            new com.plot.plugin.powerline.design.PoleDesign("validation");
        design.setTowerStructure(structure);
        return design;
    }

    private static void generateLegs(
            TowerStation lower,
            TowerStation upper,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix material = structure.getPrimaryMaterial();
        int thickness = structure.getLegProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            TowerLocalPoint start = TowerStructureGeometry.cornerPoint(lower, corner);
            TowerLocalPoint end = TowerStructureGeometry.cornerPoint(upper, corner);
            placeMember(start, end, material, thickness, transform, footprint, result, projection, counters, MemberKind.LEG);
        }
    }

    private static void generateFaceBracing(
            TowerStation lower,
            TowerStation upper,
            BracingPattern pattern,
            int[] corners,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            boolean frontBack) {
        if (pattern == BracingPattern.NONE || corners.length < 2) {
            return;
        }
        int a = corners[0];
        int b = corners[1];
        TowerLocalPoint aLower = TowerStructureGeometry.cornerPoint(lower, a);
        TowerLocalPoint bLower = TowerStructureGeometry.cornerPoint(lower, b);
        TowerLocalPoint aUpper = TowerStructureGeometry.cornerPoint(upper, a);
        TowerLocalPoint bUpper = TowerStructureGeometry.cornerPoint(upper, b);

        if (pattern == BracingPattern.X) {
            placeBrace(aLower, bUpper, structure, transform, footprint, result, projection, counters);
            placeBrace(bLower, aUpper, structure, transform, footprint, result, projection, counters);
        } else if (pattern == BracingPattern.K) {
            TowerLocalPoint centerUpper = midpoint(aUpper, bUpper);
            placeBrace(aLower, centerUpper, structure, transform, footprint, result, projection, counters);
            placeBrace(bLower, centerUpper, structure, transform, footprint, result, projection, counters);
        } else if (pattern == BracingPattern.SINGLE_DIAGONAL) {
            placeBrace(aLower, bUpper, structure, transform, footprint, result, projection, counters);
        } else if (pattern == BracingPattern.V) {
            TowerLocalPoint centerLower = midpoint(aLower, bLower);
            placeBrace(aUpper, centerLower, structure, transform, footprint, result, projection, counters);
            placeBrace(bUpper, centerLower, structure, transform, footprint, result, projection, counters);
        }
    }

    private static TowerLocalPoint midpoint(TowerLocalPoint a, TowerLocalPoint b) {
        return TowerLocalPoint.of(
            (a.lateral() + b.lateral()) / 2.0,
            (a.vertical() + b.vertical()) / 2.0,
            (a.longitudinal() + b.longitudinal()) / 2.0);
    }

    private static void generateHorizontalRing(
            TowerStation station,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            int next = (corner + 1) % TowerStructureGeometry.CORNER_COUNT;
            TowerLocalPoint start = TowerStructureGeometry.cornerPoint(station, corner);
            TowerLocalPoint end = TowerStructureGeometry.cornerPoint(station, next);
            placeMember(start, end, material, thickness, transform, footprint, result, projection, counters, MemberKind.BRACE);
        }
    }

    /** 水平面内对角斜撑：连接对角塔腿，保持左右/前后对称。 */
    private static void generatePlanDiagonalBracing(
            TowerStation station,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        placeMember(
            TowerStructureGeometry.cornerPoint(station, 0),
            TowerStructureGeometry.cornerPoint(station, 2),
            material,
            thickness,
            transform,
            footprint,
            result,
            projection,
            counters,
            MemberKind.BRACE);
        placeMember(
            TowerStructureGeometry.cornerPoint(station, 1),
            TowerStructureGeometry.cornerPoint(station, 3),
            material,
            thickness,
            transform,
            footprint,
            result,
            projection,
            counters,
            MemberKind.BRACE);
    }

    private static void generateArm(
            TowerArm arm,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix chordMaterial = arm.getMaterial() != null
            ? arm.getMaterial()
            : structure.getPrimaryMaterial();
        MaterialMix braceMaterial = arm.getMaterial() != null
            ? arm.getMaterial()
            : structure.getBraceMaterial();
        TowerArmPlacement.placeArm(
            arm,
            chordMaterial,
            braceMaterial,
            (lateralStart, lateralEnd, height, longHalf, material) ->
                placeArmChord(lateralStart, lateralEnd, height, longHalf, material,
                    transform, footprint, result, projection, counters),
            (start, end, material) ->
                placeArmBrace(start, end, material, transform, footprint, result, projection, counters));
    }

    private static void placeArmChord(
            double lateralStart,
            double lateralEnd,
            double height,
            double longHalf,
            MaterialMix material,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        if (longHalf <= 0) {
            placeMember(
                TowerLocalPoint.of(lateralStart, height, 0),
                TowerLocalPoint.of(lateralEnd, height, 0),
                material, 1, transform, footprint, result, projection, counters, MemberKind.ARM);
            return;
        }
        placeMember(
            TowerLocalPoint.of(lateralStart, height, -longHalf),
            TowerLocalPoint.of(lateralEnd, height, -longHalf),
            material, 1, transform, footprint, result, projection, counters, MemberKind.ARM);
        placeMember(
            TowerLocalPoint.of(lateralStart, height, longHalf),
            TowerLocalPoint.of(lateralEnd, height, longHalf),
            material, 1, transform, footprint, result, projection, counters, MemberKind.ARM);
    }

    private static void generateDecoration(
            TowerDecoration decoration,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        if (decoration == null || !decoration.isEnabled()) {
            return;
        }
        switch (decoration.getKind()) {
            case BEACON -> placeDecorationBlock(
                decoration,
                "minecraft:beacon",
                transform,
                footprint,
                result,
                projection,
                counters);
            case WARNING_LIGHT -> placeDecorationBlock(
                decoration,
                defaultMaterialId(decoration, "minecraft:sea_lantern"),
                transform,
                footprint,
                result,
                projection,
                counters);
            case ANTENNA -> generateAntenna(decoration, transform, footprint, result, projection, counters);
            case PLATFORM -> generatePlatform(decoration, transform, footprint, result, projection, counters);
            default -> { }
        }
    }

    private static void generateAntenna(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix mastMaterial = decoration.getMaterial() != null
            ? decoration.getMaterial()
            : MaterialMix.single("minecraft:iron_bars");
        int mastHeight = Math.max(2, (int) Math.round(decoration.getSize()));
        double base = decoration.getBaseHeight();
        TowerLocalPoint bottom = TowerLocalPoint.of(
            decoration.getLateralOffset(),
            base,
            decoration.getLongitudinalOffset());
        TowerLocalPoint top = TowerLocalPoint.of(
            decoration.getLateralOffset(),
            base + mastHeight,
            decoration.getLongitudinalOffset());
        placeMember(bottom, top, mastMaterial, 1, transform, footprint, result, projection, counters, MemberKind.DECORATION);
        placeDecorationBlockAt(
            decoration.getLateralOffset(),
            base + mastHeight + 1,
            decoration.getLongitudinalOffset(),
            DirectionalBlockSpecs.verticalLightningRod().toSetBlockArgument(),
            transform,
            footprint,
            result,
            projection,
            counters);
    }

    private static void generatePlatform(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix material = decoration.getMaterial() != null
            ? decoration.getMaterial()
            : MaterialMix.single("minecraft:iron_block");
        int radius = Math.max(1, (int) Math.round(decoration.getSize()));
        double height = decoration.getBaseHeight();
        double lateralCenter = decoration.getLateralOffset();
        double longitudinalCenter = decoration.getLongitudinalOffset();
        placeDecorationBlockAt(
            lateralCenter,
            height,
            longitudinalCenter,
            null,
            transform,
            footprint,
            result,
            projection,
            counters,
            material);
        for (int step = 1; step <= radius; step++) {
            placeDecorationBlockAt(
                lateralCenter + step, height, longitudinalCenter, null,
                transform, footprint, result, projection, counters, material);
            placeDecorationBlockAt(
                lateralCenter - step, height, longitudinalCenter, null,
                transform, footprint, result, projection, counters, material);
            placeDecorationBlockAt(
                lateralCenter, height, longitudinalCenter + step, null,
                transform, footprint, result, projection, counters, material);
            placeDecorationBlockAt(
                lateralCenter, height, longitudinalCenter - step, null,
                transform, footprint, result, projection, counters, material);
        }
    }

    private static void placeDecorationBlock(
            TowerDecoration decoration,
            String blockId,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        placeDecorationBlockAt(
            decoration.getLateralOffset(),
            decoration.getBaseHeight(),
            decoration.getLongitudinalOffset(),
            blockId,
            transform,
            footprint,
            result,
            projection,
            counters);
    }

    private static void placeDecorationBlockAt(
            double lateral,
            double height,
            double longitudinal,
            String blockId,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        placeDecorationBlockAt(
            lateral,
            height,
            longitudinal,
            blockId,
            transform,
            footprint,
            result,
            projection,
            counters,
            MaterialMix.single(blockId));
    }

    private static void placeDecorationBlockAt(
            double lateral,
            double height,
            double longitudinal,
            String blockId,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            MaterialMix material) {
        BlockPos pos = transform.toBlock(TowerLocalPoint.of(lateral, height, longitudinal));
        String resolved = blockId != null
            ? blockId
            : MaterialMixResolver.resolve(material, pos, footprint.getId());
        recordBlock(result, pos, resolved, projection);
        counters.addDecoration(1);
    }

    private static String defaultMaterialId(TowerDecoration decoration, String fallback) {
        if (decoration.getMaterial() != null) {
            return decoration.getMaterial().getPrimaryMaterial();
        }
        return fallback;
    }

    private static void placeArmBrace(
            TowerLocalPoint start,
            TowerLocalPoint end,
            MaterialMix material,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        placeMember(start, end, material, 1, transform, footprint, result, projection, counters, MemberKind.ARM);
    }

    private static void placeBrace(
            TowerLocalPoint start,
            TowerLocalPoint end,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        placeMember(
            start,
            end,
            structure.getBraceMaterial(),
            structure.getBraceProfile().getThickness(),
            transform,
            footprint,
            result,
            projection,
            counters,
            MemberKind.BRACE);
    }

    private static void placeMember(
            TowerLocalPoint start,
            TowerLocalPoint end,
            MaterialMix material,
            int thickness,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            MemberKind kind) {
        double[] worldStart = transform.toWorld(start);
        double[] worldEnd = transform.toWorld(end);
        Set<BlockPos> blocks = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            worldStart[0], worldStart[1], worldStart[2],
            worldEnd[0], worldEnd[1], worldEnd[2]));

        if (thickness >= 2) {
            expandThickness(
                blocks,
                worldEnd[0] - worldStart[0],
                worldEnd[1] - worldStart[1],
                worldEnd[2] - worldStart[2]);
        }

        if (blocks.isEmpty()) {
            return;
        }
        for (BlockPos pos : blocks) {
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(result, pos, memberPlacementId(blockId, start, end, transform), projection);
        }
        switch (kind) {
            case LEG -> counters.addLeg(blocks.size());
            case BRACE -> counters.addBrace(blocks.size());
            case ARM -> counters.addArm(blocks.size());
            case DECORATION -> counters.addDecoration(blocks.size());
            default -> { }
        }
    }

    private static void expandThickness(
            Set<BlockPos> blocks,
            double deltaX,
            double deltaY,
            double deltaZ) {
        int[] axes = crossSectionAxes(deltaX, deltaY, deltaZ);
        Set<BlockPos> expanded = new LinkedHashSet<>(blocks);
        for (BlockPos pos : blocks) {
            BlockPos first = offset(pos, axes[0]);
            BlockPos second = offset(pos, axes[1]);
            expanded.add(first);
            expanded.add(second);
            expanded.add(offset(first, axes[1]));
        }
        blocks.clear();
        blocks.addAll(expanded);
    }

    /** Choose the two voxel axes most perpendicular to the member direction. */
    private static int[] crossSectionAxes(double deltaX, double deltaY, double deltaZ) {
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absX >= absY && absX >= absZ) {
            return new int[] {1, 2};
        }
        if (absY >= absZ) {
            return new int[] {0, 2};
        }
        return new int[] {0, 1};
    }

    private static BlockPos offset(BlockPos pos, int axis) {
        return switch (axis) {
            case 0 -> pos.up();
            case 1 -> pos.east();
            case 2 -> pos.south();
            default -> pos;
        };
    }

    private static void checkBaseTerrain(
            TowerStation baseStation,
            TowerStructureTransform transform,
            TerrainSampler terrain,
            PowerLineGenerationResult result) {
        if (terrain == null || baseStation == null) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            TowerLocalPoint cornerPoint = TowerStructureGeometry.cornerPoint(baseStation, corner);
            double[] world = transform.toWorld(cornerPoint);
            int groundY = terrain.sampleSurfaceY(new com.plot.api.geometry.Vec2d(world[0], world[2]));
            minY = Math.min(minY, groundY);
            maxY = Math.max(maxY, groundY);
        }
        if (maxY - minY > UNEVEN_BASE_WARNING_THRESHOLD) {
            result.warnings.add(PowerLineGenerationI18n.towerBaseUneven(maxY - minY));
        }
    }

    private static String memberPlacementId(
            String blockId,
            TowerLocalPoint memberStart,
            TowerLocalPoint memberEnd,
            TowerStructureTransform transform) {
        if (memberStart != null && memberEnd != null && transform != null) {
            double[] worldStart = transform.toWorld(memberStart);
            double[] worldEnd = transform.toWorld(memberEnd);
            return DirectionalBlockSpecs.resolveMemberPlacement(
                blockId,
                worldEnd[0] - worldStart[0],
                worldEnd[1] - worldStart[1],
                worldEnd[2] - worldStart[2]).toSetBlockArgument();
        }
        return DirectionalBlockSpecs.resolveMemberPlacement(blockId, null, null, null).toSetBlockArgument();
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

    private static final class GenerationCounters {
        int legBlocks;
        int braceBlocks;
        int armBlocks;
        int decorationBlocks;

        void addLeg(int count) {
            legBlocks += count;
        }

        void addBrace(int count) {
            braceBlocks += count;
        }

        void addArm(int count) {
            armBlocks += count;
        }

        void addDecoration(int count) {
            decorationBlocks += count;
        }

        int total() {
            return legBlocks + braceBlocks + armBlocks + decorationBlocks;
        }
    }

    private enum MemberKind {
        LEG,
        BRACE,
        ARM,
        DECORATION
    }
}
