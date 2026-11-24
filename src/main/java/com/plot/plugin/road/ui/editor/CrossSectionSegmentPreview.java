package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadMarkingPasses;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.lamp.RoadLampPlanner;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 横断面设计器下栏：纵深路段路面条带 + 标线 + 隔离带 + 路灯竖杆示意（可旋转缩放）。
 */
final class CrossSectionSegmentPreview {
    /** 预览路段纵深（方块）；需足够展示路灯间距。 */
    static final int SEGMENT_LENGTH_Z = 24;
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
        if (section == null || height < 40f) {
            return;
        }
        if (cachedScene.isEmpty() || cachedFingerprint != fingerprint) {
            cachedScene = buildSurfaceSegment(section, SEGMENT_LENGTH_Z);
            cachedFingerprint = fingerprint;
        }
        orbit.render(cachedScene, height, yaw, zoom);
    }

    static List<RoadLampCompiler.Voxel> buildSurfaceSegment(ResolvedCrossSection section, int lengthZ) {
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
        String carriageway = section.carriagewayMaterial != null
            ? section.carriagewayMaterial.getPrimaryMaterial()
            : "minecraft:gray_concrete";
        for (int x = -half; x <= half; x++) {
            var band = section.surfaceBandAtLateral(x);
            if (band.isEmpty()) {
                continue;
            }
            String block = band.get().materialId();
            if (block == null || block.isBlank()) {
                block = carriageway;
            }
            for (int z = z0; z <= z1; z++) {
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, -1, z), block));
            }
        }
        // 标线嵌入路表（y=-1）；铁轨抬高一格。车道分隔线（白虚线）与中央标线（材质/实虚）分开画，避免混淆。
        CenterLineStyle centerStyle = section.centerLineStyle != null
            ? section.centerLineStyle
            : CenterLineStyle.NONE;
        boolean rail = centerStyle == CenterLineStyle.RAIL;
        String centerMarking = resolveCenterMarkingBlock(section, rail);
        String laneDivider = ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        for (RoadMarkingPasses.Pass pass : RoadMarkingPasses.fromCrossSection(section)) {
            int x = (int) Math.floor(pass.offset());
            boolean centerMarkingPass = Math.abs(pass.offset()) <= 1.0 + 1e-6
                && centerStyle != CenterLineStyle.NONE;
            String block = centerMarkingPass ? centerMarking : laneDivider;
            int markingY = rail && centerMarkingPass ? 0 : -1;
            for (int z = z0; z <= z1; z++) {
                // 与世界生成一致：非实线沿纵深隔格跳过，呈虚线。
                if (!pass.solid() && ((z - z0) & 1) != 0) {
                    continue;
                }
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, markingY, z), block));
            }
        }
        if (section.includeMedian && section.medianWidth > 0 && section.medianHeight > 0) {
            int min = RoadDimensionUtils.minLateralOffset(section.medianWidth);
            int max = RoadDimensionUtils.maxLateralOffset(section.medianWidth);
            String medianMat = section.medianMaterial != null && !section.medianMaterial.isBlank()
                ? section.medianMaterial
                : com.plot.plugin.road.RoadParameterLimits.DEFAULT_MEDIAN_MATERIAL;
            for (int x = min; x <= max; x++) {
                for (int y = 0; y < section.medianHeight; y++) {
                    for (int z = z0; z <= z1; z++) {
                        scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, y, z), medianMat));
                    }
                }
            }
        }
        appendLampStubs(scene, section, z0, z1);
        return scene;
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
