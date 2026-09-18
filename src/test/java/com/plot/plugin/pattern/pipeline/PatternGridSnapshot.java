package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSpace;

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
        return render(config, minX, maxX, minZ, maxZ, sampleOffset, null);
    }

    static String render(
            ProceduralPatternConfig config,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            double sampleOffset,
            PatternSpace space) {
        StringBuilder builder = new StringBuilder();
        Vec2d centroid = space != null ? space.regionCentroid() : new Vec2d(0, 0);
        String seedKey = space != null ? space.seedKey() : "snapshot";
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int index = space != null
                    ? ProceduralPatternMaterialResolver.resolveMaterialIndex(
                        config,
                        x + sampleOffset,
                        z + sampleOffset,
                        centroid,
                        seedKey,
                        space)
                    : ProceduralPatternMaterialResolver.resolveMaterialIndex(
                        config,
                        x + sampleOffset,
                        z + sampleOffset,
                        centroid,
                        seedKey);
                builder.append((char) ('0' + index));
            }
            if (z < maxZ) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }
}
