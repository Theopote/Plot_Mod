package com.plot.plugin.powerline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerMemberProfile;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
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
            if (issue.severity().name().equals("ERROR")) {
                result.warnings.add(issue.message());
            } else if (issue.severity().name().equals("WARNING")) {
                result.warnings.add(issue.message());
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
        }

        for (TowerArm arm : structure.getArms()) {
            generateArm(arm, structure, transform, footprint, result, projection, counters);
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

    private static void generateArm(
            TowerArm arm,
            TowerStructureDesign structure,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        MaterialMix material = arm.getMaterial() != null
            ? arm.getMaterial()
            : structure.getPrimaryMaterial();
        double topHeight = arm.getBaseHeight();
        double bottomHeight = Math.max(0.0, topHeight - arm.getVerticalDrop());
        double reach = arm.getLateralReach();
        double longHalf = arm.getLongitudinalHalfWidth();
        BracingPattern bracing = arm.getBracing();
        switch (arm.getSide()) {
            case BOTH -> generateArmTruss(
                -reach, reach, topHeight, bottomHeight, longHalf, bracing,
                material, material, transform, footprint, result, projection, counters);
            case LEFT -> generateArmTruss(
                -reach, 0, topHeight, bottomHeight, longHalf, bracing,
                material, material, transform, footprint, result, projection, counters);
            case RIGHT -> generateArmTruss(
                0, reach, topHeight, bottomHeight, longHalf, bracing,
                material, material, transform, footprint, result, projection, counters);
            default -> { }
        }
    }

    private static void generateArmTruss(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern bracing,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        if (Math.abs(lateralEnd - lateralStart) < 1e-6) {
            return;
        }

        placeArmChord(lateralStart, lateralEnd, topHeight, longHalf, chordMaterial,
            transform, footprint, result, projection, counters);
        if (bottomHeight + 1e-6 < topHeight) {
            placeArmChord(lateralStart, lateralEnd, bottomHeight, longHalf, chordMaterial,
                transform, footprint, result, projection, counters);
            generateArmBracing(
                lateralStart,
                lateralEnd,
                topHeight,
                bottomHeight,
                longHalf,
                bracing,
                braceMaterial,
                transform,
                footprint,
                result,
                projection,
                counters);
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

    private static void generateArmBracing(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern pattern,
            MaterialMix braceMaterial,
            TowerStructureTransform transform,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            GenerationCounters counters) {
        if (pattern == BracingPattern.NONE) {
            return;
        }

        double[] longitudes = longHalf <= 0 ? new double[] {0.0} : new double[] {-longHalf, longHalf};
        for (double longitudinal : longitudes) {
            TowerLocalPoint topLeft = TowerLocalPoint.of(lateralStart, topHeight, longitudinal);
            TowerLocalPoint topRight = TowerLocalPoint.of(lateralEnd, topHeight, longitudinal);
            TowerLocalPoint bottomLeft = TowerLocalPoint.of(lateralStart, bottomHeight, longitudinal);
            TowerLocalPoint bottomRight = TowerLocalPoint.of(lateralEnd, bottomHeight, longitudinal);

            if (pattern == BracingPattern.X) {
                placeArmBrace(bottomLeft, topRight, braceMaterial, transform, footprint, result, projection, counters);
                placeArmBrace(bottomRight, topLeft, braceMaterial, transform, footprint, result, projection, counters);
            } else if (pattern == BracingPattern.K) {
                TowerLocalPoint centerTop = midpoint(topLeft, topRight);
                placeArmBrace(bottomLeft, centerTop, braceMaterial, transform, footprint, result, projection, counters);
                placeArmBrace(bottomRight, centerTop, braceMaterial, transform, footprint, result, projection, counters);
            } else if (pattern == BracingPattern.SINGLE_DIAGONAL) {
                placeArmBrace(bottomLeft, topRight, braceMaterial, transform, footprint, result, projection, counters);
            }
        }
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
            expandThickness(blocks);
        }

        for (BlockPos pos : blocks) {
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            recordBlock(result, pos, blockId, projection);
        }
        switch (kind) {
            case LEG -> counters.addLeg(blocks.size());
            case BRACE -> counters.addBrace(blocks.size());
            case ARM -> counters.addArm(blocks.size());
            default -> { }
        }
    }

    private static void expandThickness(Set<BlockPos> blocks) {
        Set<BlockPos> expanded = new LinkedHashSet<>(blocks);
        for (BlockPos pos : blocks) {
            expanded.add(pos.east());
            expanded.add(pos.south());
        }
        blocks.clear();
        blocks.addAll(expanded);
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
            result.warnings.add(String.format(
                "Tower base terrain uneven by %d blocks",
                maxY - minY));
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

    private static final class GenerationCounters {
        int legBlocks;
        int braceBlocks;
        int armBlocks;

        void addLeg(int count) {
            legBlocks += count;
        }

        void addBrace(int count) {
            braceBlocks += count;
        }

        void addArm(int count) {
            armBlocks += count;
        }

        int total() {
            return legBlocks + braceBlocks + armBlocks;
        }
    }

    private enum MemberKind {
        LEG,
        BRACE,
        ARM
    }
}
