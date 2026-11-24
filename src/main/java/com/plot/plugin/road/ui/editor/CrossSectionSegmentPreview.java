package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadMarkingPasses;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.lamp.RoadLampPlanner;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 横断面设计器下栏：纵深路段路面条带 + 标线 + 隔离带 + 桥墩示意 + 路灯竖杆（可旋转缩放）。
 */
final class CrossSectionSegmentPreview {
    /** 预览路段纵深（方块）；需足够展示路灯/桥墩间距。 */
    static final int SEGMENT_LENGTH_Z = 24;
    /** 3D 示意桥墩高度（路表以下格数）。 */
    static final int PREVIEW_PIER_HEIGHT = 4;
    /** 路灯示意杆高度（自路表向上）。 */
    private static final int LAMP_STUB_HEIGHT = 4;
    private static final String LAMP_STUB_BLOCK = "minecraft:iron_bars";

    private final RoadLampPreview orbit = new RoadLampPreview();
    private final float[] yaw = {30f};
    private final float[] zoom = {1f};
    private long cachedFingerprint = Long.MIN_VALUE;
    private List<RoadLampCompiler.Voxel> cachedScene = List.of();

    void invalidate() {
        cachedFingerprint = Long.MIN_VALUE;
        cachedScene = List.of();
    }

    void render(ResolvedCrossSection section, long fingerprint, float height) {
        render(section, null, fingerprint, height);
    }

    void render(
            ResolvedCrossSection section,
            ResolvedBridgeStructure bridge,
            long fingerprint,
            float height) {
        if (section == null || height < 40f) {
            return;
        }
        if (cachedScene.isEmpty() || cachedFingerprint != fingerprint) {
            cachedScene = buildSurfaceSegment(section, bridge, SEGMENT_LENGTH_Z);
            cachedFingerprint = fingerprint;
        }
        orbit.render(cachedScene, height, yaw, zoom);
    }

    static List<RoadLampCompiler.Voxel> buildSurfaceSegment(ResolvedCrossSection section, int lengthZ) {
        return buildSurfaceSegment(section, null, lengthZ);
    }

    static List<RoadLampCompiler.Voxel> buildSurfaceSegment(
            ResolvedCrossSection section,
            ResolvedBridgeStructure bridge,
            int lengthZ) {
        List<RoadLampCompiler.Voxel> scene = new ArrayList<>();
        if (section == null || lengthZ <= 0) {
            return scene;
        }
        int halfZ = Math.max(0, (lengthZ - 1) / 2);
        int z0 = -halfZ;
        int z1 = z0 + lengthZ - 1;
        // 横向扫描范围：行车道 + 外侧条带，略加余量。
        int half = Math.max(1, (int) Math.ceil(section.carriagewayWidth / 2.0)
            + section.shoulderWidth + section.bikeLaneWidth + section.sidewalkWidth + 2);
        half = Math.min(48, half);
        String carriageway = resolvePreviewBlock(
            section.carriagewayMaterial != null
                ? section.carriagewayMaterial.getPrimaryMaterial()
                : null,
            "minecraft:gray_concrete");
        for (int x = -half; x <= half; x++) {
            var band = section.surfaceBandAtLateral(x);
            if (band.isEmpty()) {
                continue;
            }
            var spec = band.get();
            String block = spec.materialId();
            if ((block == null || block.isBlank()) && spec.materialMix() != null) {
                block = spec.materialMix().getPrimaryMaterial();
            }
            block = resolvePreviewBlock(block, carriageway);
            for (int z = z0; z <= z1; z++) {
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, -1, z), block));
            }
        }
        // 标线嵌入路表（y=-1）；铁轨抬高一格。中央标线替换中央缝分隔线（黄/轨 vs 白虚线）。
        CenterLineStyle centerStyle = section.centerLineStyle != null
            ? section.centerLineStyle
            : CenterLineStyle.NONE;
        boolean rail = centerStyle == CenterLineStyle.RAIL;
        String centerMarking = resolvePreviewBlock(
            resolveCenterMarkingBlock(section, rail),
            ResolvedCrossSection.DEFAULT_MARKING_MATERIAL);
        String laneDivider = ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        for (RoadMarkingPasses.Pass pass : RoadMarkingPasses.fromCrossSection(section)) {
            int x = (int) Math.floor(pass.offset());
            boolean centerMarkingPass = pass.center();
            String block = centerMarkingPass ? centerMarking : laneDivider;
            int markingY = rail && centerMarkingPass ? 0 : -1;
            for (int z = z0; z <= z1; z++) {
                // 与世界生成一致：虚线为有-有-有-空（周期 4）。
                if (!pass.solid() && RoadMarkingPasses.isDashedGap(z - z0)) {
                    continue;
                }
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, markingY, z), block));
            }
        }
        if (section.includeMedian && section.medianWidth > 0 && section.medianHeight > 0
                && section.medianMaxLateral >= section.medianMinLateral) {
            String medianMat = resolvePreviewBlock(
                section.medianMaterial,
                com.plot.plugin.road.RoadParameterLimits.DEFAULT_MEDIAN_MATERIAL);
            for (int x = section.medianMinLateral; x <= section.medianMaxLateral; x++) {
                for (int y = 0; y < section.medianHeight; y++) {
                    for (int z = z0; z <= z1; z++) {
                        scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, y, z), medianMat));
                    }
                }
            }
        }
        appendPiers(scene, section, bridge, z0, z1);
        appendLampStubs(scene, section, z0, z1);
        return scene;
    }

    /**
     * 启用生成桥墩时，在路表下方画示意圆柱（高度 {@link #PREVIEW_PIER_HEIGHT}），
     * 沿纵深按桥墩间距排布；截面为直径×直径的近似圆盘，避免只沿横向铺成片状。
     */
    private static void appendPiers(
            List<RoadLampCompiler.Voxel> scene,
            ResolvedCrossSection section,
            ResolvedBridgeStructure bridge,
            int z0,
            int z1) {
        if (bridge == null || !bridge.generatePillars()) {
            return;
        }
        ResolvedBridgeStructure clamped = bridge.withCarriagewayWidth(section.carriagewayWidth);
        int diameter = Math.max(1, clamped.pierDiameterBlocks());
        int[] centers;
        if (clamped.pierStyle() == BridgePierStyle.DOUBLE) {
            centers = RoadDimensionUtils.doublePierCenters(clamped.pierOffsetBlocks(), diameter);
        } else {
            centers = new int[] {0};
        }
        String material = resolvePreviewBlock(
            clamped.pierMaterial(),
            ResolvedBridgeStructure.DEFAULT_PIER_MATERIAL);
        int step = Math.max(1, (int) Math.round(clamped.pierSpacingBlocks()));
        // 路表在 y=-1；桥墩顶贴路底 y=-2，向下共 PREVIEW_PIER_HEIGHT 格。
        int pierTopY = -2;
        int pierBottomY = pierTopY - PREVIEW_PIER_HEIGHT + 1;
        for (int z = 0; z >= z0; z -= step) {
            for (int centerX : centers) {
                addPierCylinder(scene, centerX, z, diameter, pierBottomY, pierTopY, material);
            }
        }
        for (int z = step; z <= z1; z += step) {
            for (int centerX : centers) {
                addPierCylinder(scene, centerX, z, diameter, pierBottomY, pierTopY, material);
            }
        }
    }

    /** 在桩位画圆柱体素：水平截面与世界建造共用 {@link RoadDimensionUtils#pierCylinderDiskOffsets}。 */
    private static void addPierCylinder(
            List<RoadLampCompiler.Voxel> scene,
            int centerLateral,
            int stationZ,
            int diameterBlocks,
            int y0,
            int y1,
            String material) {
        for (int[] cell : RoadDimensionUtils.pierCylinderDiskOffsets(diameterBlocks)) {
            int x = centerLateral + cell[0];
            int z = stationZ + cell[1];
            for (int y = y0; y <= y1; y++) {
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, y, z), material));
            }
        }
    }

    /** 中央标线材质：铁轨用轨块，其余用断面标线材质。 */
    private static String resolveCenterMarkingBlock(ResolvedCrossSection section, boolean rail) {
        if (rail) {
            return RoadMarkingGenerator.resolveRailBlockId(section.markingMaterial);
        }
        if (section.markingMaterial != null && !section.markingMaterial.isBlank()) {
            return section.markingMaterial;
        }
        return ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    }

    /** 预览体素 id：空白用 fallback，再走道路材质规范化，避免脏字符串进色表。 */
    private static String resolvePreviewBlock(String material, String fallback) {
        String raw = material != null && !material.isBlank() ? material : fallback;
        if (raw == null || raw.isBlank()) {
            return "minecraft:stone";
        }
        String resolved = RoadMaterialUtils.resolveBlockId(raw);
        return resolved != null && !resolved.isBlank() ? resolved : raw;
    }

    /** 有路灯间距时，两侧外侧条带上放细竖杆示意位置。 */
    private static void appendLampStubs(
            List<RoadLampCompiler.Voxel> scene,
            ResolvedCrossSection section,
            int z0,
            int z1) {
        Integer spacing = section.streetlightSpacing;
        if (spacing == null || spacing <= 0) {
            return;
        }
        int step = Math.max(1, spacing);
        for (int side : new int[] {1, -1}) {
            if (RoadLampPlanner.supportLayer(section, side, 0) == null) {
                continue;
            }
            int x = RoadLampPlanner.lateralOffset(section, side, 0);
            if (Math.abs(x) > 48) {
                continue;
            }
            int baseY = section.includeMedian && side == 0 ? section.medianHeight : 0;
            for (int z = 0; z >= z0; z -= step) {
                addStub(scene, x, baseY, z);
            }
            for (int z = step; z <= z1; z += step) {
                addStub(scene, x, baseY, z);
            }
        }
    }

    private static void addStub(List<RoadLampCompiler.Voxel> scene, int x, int baseY, int z) {
        for (int y = 0; y < LAMP_STUB_HEIGHT; y++) {
            scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, baseY + y, z), LAMP_STUB_BLOCK));
        }
    }
}
