package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.TowerLocalPoint;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmPlacement;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import com.plot.plugin.powerline.placement.VoxelSink;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 塔体结构局部体素预览（与 {@link com.plot.plugin.powerline.TowerStructureGenerator} 同形，写入 {@link VoxelSink}）。 */
public final class TowerStructurePreviewVoxelPlacer {
    private TowerStructurePreviewVoxelPlacer() {
    }

    public static void placePreview(
            TowerStructureDesign structure,
            VoxelSink sink,
            String materialSeedKey) {
        if (structure == null || sink == null) {
            return;
        }
        String seed = materialSeedKey != null ? materialSeedKey : "powerline_preview";
        List<TowerStation> stations = structure.sortedStations();
        if (stations.size() < 2) {
            return;
        }

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

            generateLegs(lower, upper, structure, sink, seed);
            generateFaceBracing(
                lower,
                upper,
                bay.getFrontBackBracing(),
                TowerStructureGeometry.frontCorners(),
                structure,
                sink,
                seed);
            generateFaceBracing(
                lower,
                upper,
                bay.getSideBracing(),
                TowerStructureGeometry.rightCorners(),
                structure,
                sink,
                seed);
            if (bay.isHorizontalRing()) {
                generateHorizontalRing(upper, structure, sink, seed);
            }
            if (bay.isPlanDiagonalBracing()) {
                generatePlanDiagonalBracing(upper, structure, sink, seed);
            }
        }

        for (TowerArm arm : structure.getArms()) {
            generateArm(arm, structure, sink, seed);
        }
    }

    private static void generateLegs(
            TowerStation lower,
            TowerStation upper,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
        MaterialMix material = structure.getPrimaryMaterial();
        int thickness = structure.getLegProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            placeMember(
                TowerStructureGeometry.cornerPoint(lower, corner),
                TowerStructureGeometry.cornerPoint(upper, corner),
                material,
                thickness,
                sink,
                seed);
        }
    }

    private static void generateFaceBracing(
            TowerStation lower,
            TowerStation upper,
            BracingPattern pattern,
            int[] corners,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
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
            placeBrace(aLower, bUpper, structure, sink, seed);
            placeBrace(bLower, aUpper, structure, sink, seed);
        } else if (pattern == BracingPattern.K) {
            TowerLocalPoint centerUpper = midpoint(aUpper, bUpper);
            placeBrace(aLower, centerUpper, structure, sink, seed);
            placeBrace(bLower, centerUpper, structure, sink, seed);
        } else if (pattern == BracingPattern.SINGLE_DIAGONAL) {
            placeBrace(aLower, bUpper, structure, sink, seed);
        } else if (pattern == BracingPattern.V) {
            TowerLocalPoint centerLower = midpoint(aLower, bLower);
            placeBrace(aUpper, centerLower, structure, sink, seed);
            placeBrace(bUpper, centerLower, structure, sink, seed);
        }
    }

    private static void generateHorizontalRing(
            TowerStation station,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
            int next = (corner + 1) % TowerStructureGeometry.CORNER_COUNT;
            placeMember(
                TowerStructureGeometry.cornerPoint(station, corner),
                TowerStructureGeometry.cornerPoint(station, next),
                material,
                thickness,
                sink,
                seed);
        }
    }

    private static void generatePlanDiagonalBracing(
            TowerStation station,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
        MaterialMix material = structure.getBraceMaterial();
        int thickness = structure.getBraceProfile().getThickness();
        placeMember(
            TowerStructureGeometry.cornerPoint(station, 0),
            TowerStructureGeometry.cornerPoint(station, 2),
            material,
            thickness,
            sink,
            seed);
        placeMember(
            TowerStructureGeometry.cornerPoint(station, 1),
            TowerStructureGeometry.cornerPoint(station, 3),
            material,
            thickness,
            sink,
            seed);
    }

    private static void generateArm(
            TowerArm arm,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
        MaterialMix chordMaterial = arm.getMaterial() != null ? arm.getMaterial() : structure.getPrimaryMaterial();
        MaterialMix braceMaterial = arm.getMaterial() != null ? arm.getMaterial() : structure.getBraceMaterial();
        TowerArmPlacement.placeArm(
            arm,
            chordMaterial,
            braceMaterial,
            (lateralStart, lateralEnd, height, longHalf, material) ->
                placeArmChord(lateralStart, lateralEnd, height, longHalf, material, sink, seed),
            (start, end, material) ->
                placeMember(start, end, material, 1, sink, seed));
    }

    private static void placeArmChord(
            double lateralStart,
            double lateralEnd,
            double height,
            double longHalf,
            MaterialMix material,
            VoxelSink sink,
            String seed) {
        if (longHalf <= 0) {
            placeMember(
                TowerLocalPoint.of(lateralStart, height, 0),
                TowerLocalPoint.of(lateralEnd, height, 0),
                material,
                1,
                sink,
                seed);
            return;
        }
        placeMember(
            TowerLocalPoint.of(lateralStart, height, -longHalf),
            TowerLocalPoint.of(lateralEnd, height, -longHalf),
            material,
            1,
            sink,
            seed);
        placeMember(
            TowerLocalPoint.of(lateralStart, height, longHalf),
            TowerLocalPoint.of(lateralEnd, height, longHalf),
            material,
            1,
            sink,
            seed);
    }

    private static void placeBrace(
            TowerLocalPoint start,
            TowerLocalPoint end,
            TowerStructureDesign structure,
            VoxelSink sink,
            String seed) {
        placeMember(
            start,
            end,
            structure.getBraceMaterial(),
            structure.getBraceProfile().getThickness(),
            sink,
            seed);
    }

    private static void placeMember(
            TowerLocalPoint start,
            TowerLocalPoint end,
            MaterialMix material,
            int thickness,
            VoxelSink sink,
            String seed) {
        Set<BlockPos> blocks = new LinkedHashSet<>(VoxelLineRasterizer.rasterizeLine3D(
            start.lateral(),
            start.vertical(),
            start.longitudinal(),
            end.lateral(),
            end.vertical(),
            end.longitudinal()));

        if (thickness >= 2) {
            expandThickness(blocks);
        }

        for (BlockPos pos : blocks) {
            String blockId = MaterialMixResolver.resolve(material, pos, seed);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), blockId);
        }
    }

    private static void expandThickness(Set<BlockPos> blocks) {
        Set<BlockPos> expanded = new LinkedHashSet<>(blocks);
        for (BlockPos pos : blocks) {
            expanded.add(pos.east());
            expanded.add(pos.south());
            expanded.add(pos.east().south());
        }
        blocks.clear();
        blocks.addAll(expanded);
    }

    private static TowerLocalPoint midpoint(TowerLocalPoint a, TowerLocalPoint b) {
        return TowerLocalPoint.of(
            (a.lateral() + b.lateral()) / 2.0,
            (a.vertical() + b.vertical()) / 2.0,
            (a.longitudinal() + b.longitudinal()) / 2.0);
    }
}
