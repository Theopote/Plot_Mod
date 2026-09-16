package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;

/**
 * 将图案材质索引渲染为 ASCII 网格，用于快照回归测试。
 * X 向右递增，Z 向下递增（每行一个 Z 层）。
 */
final class PatternGridSnapshot {
    private PatternGridSnapshot() {
    }

    static String render(
            ProceduralPatternConfig config,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            double sampleOffset) {
        StringBuilder builder = new StringBuilder();
        Vec2d centroid = new Vec2d(0, 0);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int index = ProceduralPatternMaterialResolver.resolveMaterialIndex(
                    config,
                    x + sampleOffset,
                    z + sampleOffset,
                    centroid,
                    "snapshot");
                builder.append((char) ('0' + index));
            }
            if (z < maxZ) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }
}
