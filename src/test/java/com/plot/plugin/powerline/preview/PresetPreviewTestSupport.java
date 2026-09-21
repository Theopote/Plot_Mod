package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** PL-PRESET-S6：预览几何/签名断言辅助。 */
final class PresetPreviewTestSupport {
    private PresetPreviewTestSupport() {
    }

    /**
     * 正立面镜像：仅检查纵向中心切片（z=0），避免平面斜撑在 z≠0 时的体素离散误差。
     */
    /**
     * 仅检查杆塔分层（柱/横担/斜撑）材质在正立面 z=0 的镜像对称；
     * 不含 {@link com.plot.plugin.powerline.placement.PoleIdentityFeaturePlacer} 侧挂设备。
     */
    static void assertStructuralFrontMirrorSymmetric(PoleVoxelPreviewModel model, PoleDesign design) {
        if (model == null || model.isEmpty()) {
            throw new AssertionError("empty voxel model cannot be checked for symmetry");
        }
        Set<String> structuralBlocks = structuralBlockIds(design);
        Set<String> occupied = new HashSet<>();
        for (PreviewVoxel voxel : model.voxels()) {
            if (voxel.z() != 0 || !structuralBlocks.contains(baseBlockId(voxel.blockId()))) {
                continue;
            }
            occupied.add(key(voxel.x(), voxel.y(), baseBlockId(voxel.blockId())));
        }
        if (occupied.isEmpty()) {
            throw new AssertionError("no structural z=0 voxels to evaluate front symmetry");
        }
        for (PreviewVoxel voxel : model.voxels()) {
            if (voxel.z() != 0 || voxel.x() == 0) {
                continue;
            }
            String blockId = baseBlockId(voxel.blockId());
            if (!structuralBlocks.contains(blockId)) {
                continue;
            }
            String mirrorKey = key(-voxel.x(), voxel.y(), blockId);
            if (!occupied.contains(mirrorKey)) {
                throw new AssertionError(
                    "missing structural front mirror voxel at x="
                        + (-voxel.x())
                        + " y="
                        + voxel.y()
                        + " block="
                        + blockId);
            }
        }
    }

    static void assertFrontMirrorSymmetric(PoleVoxelPreviewModel model) {
        if (model == null || model.isEmpty()) {
            throw new AssertionError("empty voxel model cannot be checked for symmetry");
        }
        Set<String> occupied = new HashSet<>();
        for (PreviewVoxel voxel : model.voxels()) {
            if (voxel.z() != 0) {
                continue;
            }
            occupied.add(key(voxel.x(), voxel.y(), voxel.blockId()));
        }
        if (occupied.isEmpty()) {
            throw new AssertionError("no z=0 voxels to evaluate front symmetry");
        }
        for (PreviewVoxel voxel : model.voxels()) {
            if (voxel.z() != 0 || voxel.x() == 0) {
                continue;
            }
            String mirrorKey = key(-voxel.x(), voxel.y(), voxel.blockId());
            if (!occupied.contains(mirrorKey)) {
                throw new AssertionError(
                    "missing front mirror voxel at x="
                        + (-voxel.x())
                        + " y="
                        + voxel.y()
                        + " block="
                        + voxel.blockId());
            }
        }
    }

    static DesignSignature signature(PoleDesign design) {
        if (design == null) {
            return new DesignSignature(0, 0, 0, 0.0, 0.0, null);
        }
        if (design.hasTowerStructure()) {
            double maxReach = design.getTowerStructure().getArms().stream()
                .mapToDouble(TowerArm::getLateralReach)
                .max()
                .orElse(0.0);
            String profileId = design.isParametricMode() && design.getGeneratorConfig() != null
                ? design.getGeneratorConfig().profileId()
                : null;
            return new DesignSignature(
                design.getTowerStructure().getArms().size(),
                countEnabledAttachments(design),
                design.getTowerStructure().getDecorations().size(),
                design.getTowerStructure().maxHeight(),
                maxReach,
                profileId);
        }
        return new DesignSignature(
            0,
            countEnabledAttachments(design),
            0,
            design.totalHeight(),
            0.0,
            design.isParametricMode() && design.getGeneratorConfig() != null
                ? design.getGeneratorConfig().profileId()
                : null);
    }

    static void assertSignaturesClose(String label, DesignSignature expected, DesignSignature actual) {
        if (!expected.matches(actual)) {
            throw new AssertionError(label + " signature mismatch: expected " + expected + " but was " + actual);
        }
    }

    private static int countEnabledAttachments(PoleDesign design) {
        int count = 0;
        for (var attachment : design.getAttachments()) {
            if (attachment != null && attachment.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> structuralBlockIds(PoleDesign design) {
        Set<String> ids = new LinkedHashSet<>();
        if (design == null) {
            return ids;
        }
        for (PoleLayer layer : design.getLayers()) {
            addMaterialId(ids, layer.getMaterial());
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                addMaterialId(ids, layer.resolveCrossarmBraceMaterial());
            }
        }
        return ids;
    }

    private static void addMaterialId(Set<String> ids, MaterialMix material) {
        if (material != null && material.getPrimaryMaterial() != null && !material.getPrimaryMaterial().isBlank()) {
            ids.add(material.getPrimaryMaterial());
        }
    }

    private static String baseBlockId(String blockId) {
        int brace = blockId.indexOf('[');
        return brace >= 0 ? blockId.substring(0, brace) : blockId;
    }

    private static String key(int x, int y, String blockId) {
        return x + "|" + y + "|" + blockId;
    }

    record DesignSignature(
            int armCount,
            int attachmentCount,
            int decorationCount,
            double height,
            double maxArmReach,
            String profileId) {
        boolean matches(DesignSignature other) {
            if (other == null) {
                return false;
            }
            return armCount == other.armCount
                && attachmentCount == other.attachmentCount
                && decorationCount == other.decorationCount
                && Math.abs(height - other.height) < 0.75
                && Math.abs(maxArmReach - other.maxArmReach) < 0.75
                && (profileId == null ? other.profileId == null : profileId.equals(other.profileId));
        }

        @Override
        public String toString() {
            return "arms=" + armCount
                + ", attachments=" + attachmentCount
                + ", decorations=" + decorationCount
                + ", height=" + height
                + ", maxReach=" + maxArmReach
                + ", profile=" + profileId;
        }
    }
}
