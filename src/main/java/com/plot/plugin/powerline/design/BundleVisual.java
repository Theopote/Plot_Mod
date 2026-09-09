package com.plot.plugin.powerline.design;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单条导线 span 的体素截面形状（纯视觉，不影响挂点匹配或工程校验）。
 */
public enum BundleVisual {
    /** 单根导线（默认）。 */
    SINGLE,
    /** 沿跨距法向平面的双股截面。 */
    TWIN,
    /** 沿跨距法向平面的 2×2 截面。 */
    QUAD;

    public Set<BlockPos> expand(BlockPos center, double tangentX, double tangentY, double tangentZ) {
        if (center == null || this == SINGLE) {
            return center == null ? Set.of() : Set.of(center);
        }
        int[] horizontalPerp = horizontalPerpendicular(tangentX, tangentZ);
        int px = horizontalPerp[0];
        int pz = horizontalPerp[1];
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        blocks.add(center);
        if (this == TWIN) {
            blocks.add(center.add(px, 0, pz));
            return blocks;
        }
        blocks.add(center.add(px, 0, pz));
        blocks.add(center.add(0, 1, 0));
        blocks.add(center.add(px, 1, pz));
        return blocks;
    }

    public Set<BlockPos> expandAll(
            Iterable<BlockPos> centers,
            double tangentX,
            double tangentY,
            double tangentZ) {
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        if (centers == null) {
            return blocks;
        }
        for (BlockPos center : centers) {
            blocks.addAll(expand(center, tangentX, tangentY, tangentZ));
        }
        return blocks;
    }

    public static BundleVisual forSubconductorCount(int bundleCount) {
        if (bundleCount >= 4) {
            return QUAD;
        }
        if (bundleCount >= 2) {
            return TWIN;
        }
        return SINGLE;
    }

    public static BundleVisual parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SINGLE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SINGLE;
        }
    }

    /**
     * 水平跨距 (dx, dz) 的法向，取整到单格轴对齐方向。
     */
    static int[] horizontalPerpendicular(double tangentX, double tangentZ) {
        double dx = tangentX;
        double dz = tangentZ;
        double horizontalLen = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLen < 1e-6) {
            return new int[] {0, 1};
        }
        double perpX = -dz / horizontalLen;
        double perpZ = dx / horizontalLen;
        if (Math.abs(perpX) >= Math.abs(perpZ)) {
            return new int[] {perpX >= 0.0 ? 1 : -1, 0};
        }
        return new int[] {0, perpZ >= 0.0 ? 1 : -1};
    }
}
