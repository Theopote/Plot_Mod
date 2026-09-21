package com.plot.plugin.powerline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.placement.DirectionalBlockSpecs;
import com.plot.plugin.powerline.placement.PlacementCategory;
import com.plot.plugin.powerline.placement.PlacementWriter;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmPlacement;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStationDensifier;
import com.plot.plugin.powerline.design.structure.TowerStationFootprintTaper;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 参数化塔体结构生成（与导线拓扑独立）。生成阶段只读输入，不修改调用方持有的 {@link TowerStructureDesign}。 */
public final class TowerStructureGenerator {
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
        return generate(structure, frame, footprint, result, coordinates, projection, terrain, null);
    }

    public static int generate(
            TowerStructureDesign structure,
            PoleFrame frame,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            ICoordinateService coordinates,
            IBlockProjectionService projection,
            TerrainSampler terrain,
            Set<BlockPos> structureScratch) {
        if (structure == null || frame == null || footprint == null || result == null) {
            return frame != null ? frame.groundY() : 0;
        }

        // 生成阶段只读：规范化/缺省补全在 Compiler 完成，此处仅消费副本，避免预览或 Build 改写用户模型。
        structure = structure.copy();
        TowerStationFootprintTaper.applySideDepthTaper(structure);

        boolean hasBlockingErrors = false;
        for (TowerValidationIssue issue : TowerStructureValidator.validate(
                wrapForValidation(structure))) {
            if (issue.severity() == com.plot.plugin.powerline.design.structure.TowerValidationSeverity.ERROR) {
                hasBlockingErrors = true;
                result.warnings.add(issue.localizedMessage());
            } else if (issue.severity() == com.plot.plugin.powerline.design.structure.TowerValidationSeverity.WARNING) {
                result.warnings.add(issue.localizedMessage());
            }
        }
        if (hasBlockingErrors) {
            return frame.groundY() + (int) Math.round(structure.maxHeight());
        }

        TowerStructureTransform transform = new TowerStructureTransform(frame, coordinates);
        GenerationCounters counters = new GenerationCounters();
        List<TowerStation> macroStations = structure.sortedStations();
        if (macroStations.size() < 2) {
            return frame.groundY() + (int) Math.round(structure.maxHeight());
        }

        List<TowerStation> legStations = TowerStationDensifier.densifyForLegs(macroStations);

        int panelIndex = 0;
        for (int i = 1; i < macroStations.size(); i++) {
            TowerStation lower = macroStations.get(i - 1);
            TowerStation upper = macroStations.get(i);
            TowerBay bay = structure.findBay(lower.getId(), upper.getId());
            if (bay == null) {
                bay = new TowerBay(lower.getId(), upper.getId());
                bay.setFrontBackBracing(BracingPattern.X);
                bay.setSideBracing(BracingPattern.X);
                bay.setHorizontalRing(true);
            }

            panelIndex = generatePanelizedBayBracing(
                lower,
                upper,
                bay,
                panelIndex,
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
            if (bay.isHorizontalRing()) {
                generateHorizontalRing(upper, structure, transform, footprint, result, projection, counters, structureScratch);
            }
        }

        for (int i = 1; i < legStations.size(); i++) {
            generateLegs(
                legStations.get(i - 1),
                legStations.get(i),
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
        }

        Set<Long> armSupportRingHeights = new HashSet<>();
        for (TowerArm arm : structure.getArms()) {
            generateArm(
                arm,
                structure,
                macroStations,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch,
                armSupportRingHeights);
        }

        for (TowerDecoration decoration : structure.getDecorations()) {
            generateDecoration(decoration, structure, transform, footprint, result, projection, counters, structureScratch);
        }

        result.structureBlockCount += counters.total();
        result.braceBlockCount += counters.braceBlocks;
        result.armBlockCount += counters.armBlocks;
        return frame.groundY() + (int) Math.round(macroStations.getLast().getHeight());
    }

    private static com.plot.plugin.powerline.design.PoleDesign wrapForValidation(
            TowerStructureDesign structure) {
        com.plot.plugin.powerline.design.PoleDesign design =
            new com.plot.plugin.powerline.design.PoleDesign("validation");
        design.setTowerStructure(structure);
        return design;
    }

    private static int generatePanelizedBayBracing(
            TowerStation lower,
            TowerStation upper,
            TowerBay bay,
            int startPanelIndex,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        List<TowerStation> panels = TowerStationDensifier.densifyForLegs(List.of(lower, upper));
        int panelIndex = startPanelIndex;
        for (int panel = 1; panel < panels.size(); panel++) {
            panelIndex++;
            TowerStation panelLower = panels.get(panel - 1);
            TowerStation panelUpper = panels.get(panel);
            generateFaceBracing(
                panelLower,
                panelUpper,
                bay.getFrontBackBracing(),
                TowerStructureGeometry.frontCorners(),
                panelIndex,
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
            generateFaceBracing(
                panelLower,
                panelUpper,
                bay.getFrontBackBracing(),
                TowerStructureGeometry.backCorners(),
                panelIndex,
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
            generateFaceBracing(
                panelLower,
                panelUpper,
                bay.getSideBracing(),
                TowerStructureGeometry.rightCorners(),
                panelIndex,
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
            generateFaceBracing(
                panelLower,
                panelUpper,
                bay.getSideBracing(),
                TowerStructureGeometry.leftCorners(),
                panelIndex,
                structure,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
        }
        return panelIndex;
    }

    private static void generateLegs(
            TowerStation lower,
            TowerStation upper,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        MaterialMix material = structure.getPrimaryMaterial();
        int thickness = structure.getLegProfile().getThickness();
        TowerLocalPoint start = TowerStructureGeometry.cornerPoint(lower, 0);
        TowerLocalPoint end = TowerStructureGeometry.cornerPoint(upper, 0);
        placeMirroredLegMember(
            start,
            end,
            material,
            thickness,
            transform,
            footprint,
            result,
            projection,
            counters,
            structureScratch);
    }

    private static void generateFaceBracing(
            TowerStation lower,
            TowerStation upper,
            BracingPattern pattern,
            int[] corners,
            int panelIndex,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
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
            placeBrace(aLower, bUpper, structure, transform, footprint, result, projection, counters, structureScratch);
            placeBrace(bLower, aUpper, structure, transform, footprint, result, projection, counters, structureScratch);
        } else if (pattern == BracingPattern.K) {
            TowerLocalPoint centerUpper = midpoint(aUpper, bUpper);
            placeBrace(aLower, centerUpper, structure, transform, footprint, result, projection, counters, structureScratch);
            placeBrace(bLower, centerUpper, structure, transform, footprint, result, projection, counters, structureScratch);
        } else if (pattern == BracingPattern.SINGLE_DIAGONAL) {
            // 按 densifier panel 序号交替斜向；全塔累计 index 保证跨 macro bay 连续 /\/\/\。
            boolean reverse = singleDiagonalReversed(panelIndex);
            if (reverse) {
                placeBrace(bLower, aUpper, structure, transform, footprint, result, projection, counters, structureScratch);
            } else {
                placeBrace(aLower, bUpper, structure, transform, footprint, result, projection, counters, structureScratch);
            }
        } else if (pattern == BracingPattern.V) {
            TowerLocalPoint centerLower = midpoint(aLower, bLower);
            placeBrace(aUpper, centerLower, structure, transform, footprint, result, projection, counters, structureScratch);
            placeBrace(bUpper, centerLower, structure, transform, footprint, result, projection, counters, structureScratch);
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
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            int next = (corner + 1) % TowerStructureGeometry.CORNER_COUNT;
            TowerLocalPoint start = TowerStructureGeometry.cornerPoint(station, corner);
            TowerLocalPoint end = TowerStructureGeometry.cornerPoint(station, next);
            placeMember(
                start, end, material, thickness, transform, footprint, result, projection, counters,
                MemberKind.BRACE, structureScratch);
        }
    }

    private static void generateArm(
            TowerArm arm,
            TowerStructureDesign structure,
            List<TowerStation> stations,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch,
            Set<Long> armSupportRingHeights) {
        MaterialMix chordMaterial = arm.getMaterial() != null
            ? arm.getMaterial()
            : structure.getPrimaryMaterial();
        MaterialMix braceMaterial = arm.getBraceMaterial() != null
            ? arm.getBraceMaterial()
            : structure.getBraceMaterial();
        TowerArmPlacement.placeArm(
            arm,
            chordMaterial,
            braceMaterial,
            (lateralStart, lateralEnd, height, longHalf, material) ->
                placeArmChord(
                    lateralStart, lateralEnd, height, longHalf, material,
                    transform, footprint, result, projection, counters, structureScratch),
            (start, end, material) ->
                placeArmBrace(
                    start, end, material, transform, footprint, result, projection, counters, structureScratch));
        generateArmSupportRing(
            arm,
            structure,
            stations,
            transform,
            footprint,
            result,
            projection,
            counters,
            structureScratch,
            armSupportRingHeights);
    }

    /**
     * 在横担高度放置插值水平环，把四根主柱在塔身截面处连成一体。
     * 横担常落在 station 之间；没有这层环时，横担与塔身会在 voxel 层断裂。
     */
    private static void generateArmSupportRing(
            TowerArm arm,
            TowerStructureDesign structure,
            List<TowerStation> stations,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch,
            Set<Long> armSupportRingHeights) {
        if (arm == null || stations == null || stations.size() < 2) {
            return;
        }
        double height = arm.getBaseHeight();
        long heightKey = Math.round(height * 1000.0);
        if (!armSupportRingHeights.add(heightKey)) {
            return;
        }
        TowerStructureGeometry.Footprint footprintAtHeight =
            TowerStructureGeometry.interpolatedFootprintAtHeight(stations, height);
        if (footprintAtHeight.halfWidth() < 0.5 || footprintAtHeight.halfDepth() < 0.5) {
            return;
        }
        generateHorizontalRingAtHeight(
            height,
            footprintAtHeight,
            structure,
            transform,
            footprint,
            result,
            projection,
            counters,
            structureScratch);
    }

    private static void generateHorizontalRingAtHeight(
            double height,
            TowerStructureGeometry.Footprint footprintAtHeight,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            int next = (corner + 1) % TowerStructureGeometry.CORNER_COUNT;
            TowerLocalPoint start = TowerStructureGeometry.cornerPointAt(
                height,
                corner,
                footprintAtHeight.halfWidth(),
                footprintAtHeight.halfDepth());
            TowerLocalPoint end = TowerStructureGeometry.cornerPointAt(
                height,
                next,
                footprintAtHeight.halfWidth(),
                footprintAtHeight.halfDepth());
            placeMember(
                start,
                end,
                material,
                thickness,
                transform,
                footprint,
                result,
                projection,
                counters,
                MemberKind.BRACE,
                structureScratch);
        }
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
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        if (longHalf <= 0) {
            placeMember(
                TowerLocalPoint.of(lateralStart, height, 0),
                TowerLocalPoint.of(lateralEnd, height, 0),
                material, 1, transform, footprint, result, projection, counters, MemberKind.ARM, structureScratch);
            return;
        }
        placeMember(
            TowerLocalPoint.of(lateralStart, height, -longHalf),
            TowerLocalPoint.of(lateralEnd, height, -longHalf),
            material, 1, transform, footprint, result, projection, counters, MemberKind.ARM, structureScratch);
        placeMember(
            TowerLocalPoint.of(lateralStart, height, longHalf),
            TowerLocalPoint.of(lateralEnd, height, longHalf),
            material, 1, transform, footprint, result, projection, counters, MemberKind.ARM, structureScratch);
    }

    private static void generateDecoration(
            TowerDecoration decoration,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
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
                counters,
                structureScratch);
            case WARNING_LIGHT -> placeDecorationBlock(
                decoration,
                defaultMaterialId(decoration, "minecraft:sea_lantern"),
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch);
            case ANTENNA -> generateAntenna(
                decoration, transform, footprint, result, projection, counters, structureScratch);
            case PLATFORM -> generatePlatform(
                decoration, transform, footprint, result, projection, counters, structureScratch);
            case GEAR_RING -> generateGearRing(
                decoration, transform, footprint, result, projection, counters, structureScratch);
            case MECHANICAL_RING -> generateMechanicalRing(
                decoration, structure, transform, footprint, result, projection, counters, structureScratch);
            case HANGING_CHAIN -> generateHangingChain(
                decoration, transform, footprint, result, projection, counters, structureScratch);
            default -> { }
        }
    }

    private static void generateAntenna(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
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
        placeMember(
            bottom, top, mastMaterial, 1, transform, footprint, result, projection, counters,
            MemberKind.DECORATION, structureScratch);
        placeDecorationBlockAt(
            decoration.getLateralOffset(),
            base + mastHeight + 1,
            decoration.getLongitudinalOffset(),
            DirectionalBlockSpecs.verticalLightningRod().toSetBlockArgument(),
            transform,
            footprint,
            result,
            projection,
            counters,
            structureScratch);
    }

    private static void generatePlatform(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
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
            structureScratch,
            material);
        for (int step = 1; step <= radius; step++) {
            placeDecorationBlockAt(
                lateralCenter + step, height, longitudinalCenter, null,
                transform, footprint, result, projection, counters, structureScratch, material);
            placeDecorationBlockAt(
                lateralCenter - step, height, longitudinalCenter, null,
                transform, footprint, result, projection, counters, structureScratch, material);
            placeDecorationBlockAt(
                lateralCenter, height, longitudinalCenter + step, null,
                transform, footprint, result, projection, counters, structureScratch, material);
            placeDecorationBlockAt(
                lateralCenter, height, longitudinalCenter - step, null,
                transform, footprint, result, projection, counters, structureScratch, material);
        }
    }

    private static void generateGearRing(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        MaterialMix material = decoration.getMaterial() != null
            ? decoration.getMaterial()
            : MaterialMix.single("minecraft:gold_block");
        int radius = Math.max(2, (int) Math.round(decoration.getSize()));
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
            structureScratch,
            material);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double dist = Math.hypot(dx, dz);
                if (dist >= radius - 0.75 && dist <= radius + 0.25) {
                    placeDecorationBlockAt(
                        lateralCenter + dx,
                        height,
                        longitudinalCenter + dz,
                        null,
                        transform,
                        footprint,
                        result,
                        projection,
                        counters,
                        structureScratch,
                        material);
                }
            }
        }
        int toothRadius = radius + 1;
        int[][] toothOffsets = {
            {toothRadius, 0},
            {-toothRadius, 0},
            {0, toothRadius},
            {0, -toothRadius},
            {toothRadius - 1, toothRadius - 1},
            {toothRadius - 1, -(toothRadius - 1)},
            {-(toothRadius - 1), toothRadius - 1},
            {-(toothRadius - 1), -(toothRadius - 1)}
        };
        for (int[] offset : toothOffsets) {
            placeDecorationBlockAt(
                lateralCenter + offset[0],
                height,
                longitudinalCenter + offset[1],
                null,
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch,
                material);
        }
    }

    private static void generateMechanicalRing(
            TowerDecoration decoration,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        if (structure == null || structure.getStations().size() < 2) {
            return;
        }
        MaterialMix material = decoration.getMaterial() != null
            ? decoration.getMaterial()
            : MaterialMix.single("minecraft:cut_copper");
        double height = decoration.getBaseHeight();
        TowerStructureGeometry.Footprint ringFootprint =
            TowerStructureGeometry.interpolatedFootprintAtHeight(structure.getStations(), height);
        if (ringFootprint.halfWidth() < 0.5 || ringFootprint.halfDepth() < 0.5) {
            return;
        }
        double halfWidth = ringFootprint.halfWidth();
        double halfDepth = ringFootprint.halfDepth();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            int next = (corner + 1) % TowerStructureGeometry.CORNER_COUNT;
            TowerLocalPoint start = TowerStructureGeometry.cornerPointAt(height, corner, halfWidth, halfDepth);
            TowerLocalPoint end = TowerStructureGeometry.cornerPointAt(height, next, halfWidth, halfDepth);
            placeMember(
                start,
                end,
                material,
                1,
                transform,
                footprint,
                result,
                projection,
                counters,
                MemberKind.DECORATION,
                structureScratch);
        }
        placeDecorationBlockAt(halfWidth + 1.0, height, 0.0, null, transform, footprint, result, projection, counters, structureScratch, material);
        placeDecorationBlockAt(-halfWidth - 1.0, height, 0.0, null, transform, footprint, result, projection, counters, structureScratch, material);
        placeDecorationBlockAt(0.0, height, halfDepth + 1.0, null, transform, footprint, result, projection, counters, structureScratch, material);
        placeDecorationBlockAt(0.0, height, -halfDepth - 1.0, null, transform, footprint, result, projection, counters, structureScratch, material);
    }

    private static void generateHangingChain(
            TowerDecoration decoration,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        MaterialMix material = decoration.getMaterial() != null
            ? decoration.getMaterial()
            : MaterialMix.single("minecraft:chain");
        int length = Math.max(1, (int) Math.round(decoration.getSize()));
        double top = decoration.getBaseHeight();
        double lateral = decoration.getLateralOffset();
        double longitudinal = decoration.getLongitudinalOffset();
        for (int step = 0; step < length; step++) {
            placeDecorationBlockAt(
                lateral,
                top - step,
                longitudinal,
                DirectionalBlockSpecs.verticalChain().toSetBlockArgument(),
                transform,
                footprint,
                result,
                projection,
                counters,
                structureScratch,
                material);
        }
    }

    private static void placeDecorationBlock(
            TowerDecoration decoration,
            String blockId,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        placeDecorationBlockAt(
            decoration.getLateralOffset(),
            decoration.getBaseHeight(),
            decoration.getLongitudinalOffset(),
            blockId,
            transform,
            footprint,
            result,
            projection,
            counters,
            structureScratch);
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
            Set<BlockPos> structureScratch) {
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
            structureScratch,
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
            Set<BlockPos> structureScratch,
            MaterialMix material) {
        BlockPos pos = transform.toBlock(TowerLocalPoint.of(lateral, height, longitudinal));
        String resolved = blockId != null
            ? blockId
            : MaterialMixResolver.resolve(material, pos, footprint.getId());
        recordBlock(result, pos, resolved, projection, PlacementCategory.DECORATION, structureScratch);
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
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        placeMember(
            start, end, material, 1, transform, footprint, result, projection, counters,
            MemberKind.ARM, structureScratch);
    }

    private static void placeBrace(
            TowerLocalPoint start,
            TowerLocalPoint end,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
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
            MemberKind.BRACE,
            structureScratch);
    }

    private static void placeMirroredLegMember(
            TowerLocalPoint canonicalStart,
            TowerLocalPoint canonicalEnd,
            MaterialMix material,
            int thickness,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters,
            Set<BlockPos> structureScratch) {
        double[] worldStart = transform.toWorld(canonicalStart);
        double[] worldEnd = transform.toWorld(canonicalEnd);
        MemberVoxelRaster raster = TowerMemberVoxelRasterizer.rasterizeMemberDetailed(
            worldStart[0], worldStart[1], worldStart[2],
            worldEnd[0], worldEnd[1], worldEnd[2],
            thickness);
        if (raster.allBlocks().isEmpty()) {
            return;
        }

        Set<BlockPos> placed = new HashSet<>();
        for (BlockPos source : raster.allBlocks()) {
            TowerLocalPoint sourceLocal = transform.fromWorld(
                source.getX(),
                source.getY(),
                source.getZ());
            BlockPos thicknessCore = raster.thicknessAnchors().get(source);
            TowerLocalPoint coreLocal = thicknessCore != null
                ? transform.fromWorld(thicknessCore.getX(), thicknessCore.getY(), thicknessCore.getZ())
                : null;

            for (int latSign : MIRROR_SIGNS) {
                for (int longSign : MIRROR_SIGNS) {
                    TowerLocalPoint legStart = mirrorLocalPoint(canonicalStart, latSign, longSign);
                    TowerLocalPoint legEnd = mirrorLocalPoint(canonicalEnd, latSign, longSign);
                    TowerLocalPoint mirroredLocal = mirrorLocalPoint(sourceLocal, latSign, longSign);
                    BlockPos mirroredPos = transform.toBlock(mirroredLocal);
                    if (!placed.add(mirroredPos)) {
                        continue;
                    }
                    String blockId = MaterialMixResolver.resolve(material, mirroredPos, footprint.getId());
                    String placementId;
                    if (isIronBars(blockId) && coreLocal != null) {
                        BlockPos mirroredCore = transform.toBlock(mirrorLocalPoint(coreLocal, latSign, longSign));
                        placementId = DirectionalBlockSpecs.ironBarsTowardCore(mirroredPos, mirroredCore)
                            .toSetBlockArgument();
                    } else {
                        placementId = memberPlacementId(
                            blockId,
                            null,
                            -1,
                            mirroredPos,
                            null,
                            legStart,
                            legEnd,
                            transform);
                    }
                    recordBlock(
                        result,
                        mirroredPos,
                        placementId,
                        projection,
                        PlacementCategory.LEG,
                        structureScratch);
                }
            }
        }
        counters.addLeg(placed.size());
    }

    private static final int[] MIRROR_SIGNS = {-1, 1};

    private static TowerLocalPoint mirrorLocalPoint(TowerLocalPoint point, int latSign, int longSign) {
        return TowerLocalPoint.of(
            latSign * Math.abs(point.lateral()),
            point.vertical(),
            longSign * Math.abs(point.longitudinal()));
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
            MemberKind kind,
            Set<BlockPos> structureScratch) {
        double[] worldStart = transform.toWorld(start);
        double[] worldEnd = transform.toWorld(end);
        MemberVoxelRaster raster = TowerMemberVoxelRasterizer.rasterizeMemberDetailed(
            worldStart[0], worldStart[1], worldStart[2],
            worldEnd[0], worldEnd[1], worldEnd[2],
            thickness);

        if (raster.allBlocks().isEmpty()) {
            return;
        }
        PlacementCategory category = switch (kind) {
            case LEG -> PlacementCategory.LEG;
            case ARM -> PlacementCategory.ARM;
            case BRACE -> PlacementCategory.BRACE;
            case DECORATION -> PlacementCategory.DECORATION;
        };
        List<BlockPos> centerline = raster.centerline();
        for (int i = 0; i < centerline.size(); i++) {
            BlockPos pos = centerline.get(i);
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(
                result,
                pos,
                memberPlacementId(blockId, raster.centerline(), i, pos, null, start, end, transform),
                projection,
                category,
                structureScratch);
        }
        for (var entry : raster.thicknessAnchors().entrySet()) {
            BlockPos pos = entry.getKey();
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(
                result,
                pos,
                memberPlacementId(blockId, null, -1, pos, entry.getValue(), start, end, transform),
                projection,
                category,
                structureScratch);
        }
        int blockCount = raster.allBlocks().size();
        switch (kind) {
            case LEG -> counters.addLeg(blockCount);
            case BRACE -> counters.addBrace(blockCount);
            case ARM -> counters.addArm(blockCount);
            case DECORATION -> counters.addDecoration(blockCount);
            default -> { }
        }
    }

    private static String memberPlacementId(
            String blockId,
            List<BlockPos> centerline,
            int centerIndex,
            BlockPos pos,
            BlockPos thicknessCore,
            TowerLocalPoint memberStart,
            TowerLocalPoint memberEnd,
            TowerStructureTransform transform) {
        if (usesPathDirectedPlacement(blockId)) {
            if (centerline != null && centerIndex >= 0) {
                return DirectionalBlockSpecs.resolveWirePlacementAlongPath(blockId, centerline, centerIndex)
                    .toSetBlockArgument();
            }
            if (isIronBars(blockId) && pos != null && thicknessCore != null) {
                return DirectionalBlockSpecs.ironBarsTowardCore(pos, thicknessCore).toSetBlockArgument();
            }
        }
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

    private static boolean isIronBars(String blockId) {
        return blockId != null && "minecraft:iron_bars".equals(BlockSpec.parse(blockId).blockId());
    }

    private static boolean usesPathDirectedPlacement(String blockId) {
        if (blockId == null) {
            return false;
        }
        String baseId = BlockSpec.parse(blockId).blockId();
        return isIronBars(blockId)
            || "minecraft:lightning_rod".equals(baseId)
            || DirectionalBlockSpecs.usesAxisChainPlacement(baseId);
    }

    private static void recordBlock(
            PowerLineGenerationResult result,
            BlockPos pos,
            String newBlockId,
            IBlockProjectionService projectionHandler,
            PlacementCategory category,
            Set<BlockPos> structureScratch) {
        PlacementWriter.put(result, projectionHandler, pos, newBlockId, category, structureScratch);
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

    static boolean singleDiagonalReversed(int panelIndex) {
        return (panelIndex & 1) == 1;
    }
}
