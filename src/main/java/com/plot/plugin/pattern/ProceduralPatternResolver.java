package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 给定区域内的一个世界坐标点，按图案配置算出应使用 materials 列表里的第几个材质（下标）。
 */
public final class ProceduralPatternResolver {
    private ProceduralPatternResolver() {
    }

    public static int resolveMaterialIndex(
            ProceduralPatternConfig config,
            double worldX,
            double worldZ,
            Vec2d regionCentroid,
            String seedKey) {
        if (config == null) {
            return 0;
        }
        List<String> materials = config.getMaterials();
        if (materials.isEmpty()) {
            return 0;
        }
        return switch (config.getType()) {
            case CHECKERBOARD -> resolveCheckerboard(config, worldX, worldZ, materials);
            case STRIPES -> resolveStripes(config, worldX, worldZ, materials);
            case CONCENTRIC_RINGS -> resolveConcentricRings(config, worldX, worldZ, regionCentroid, materials);
            case MOSAIC -> resolveMosaic(config, worldX, worldZ, materials, seedKey);
        };
    }

    private static int resolveCheckerboard(
            ProceduralPatternConfig config,
            double worldX,
            double worldZ,
            List<String> materials) {
        double tileSize = Math.max(1e-6, config.getTileSize());
        int parity = (floorDiv(worldX, tileSize) + floorDiv(worldZ, tileSize)) & 1;
        return Math.min(parity, Math.min(1, materials.size() - 1));
    }

    private static int resolveStripes(
            ProceduralPatternConfig config,
            double worldX,
            double worldZ,
            List<String> materials) {
        double radians = Math.toRadians(config.getAngleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // 投影到条纹法线方向（θ=0 沿 X，θ=90° 沿 Z）
        double rotatedX = worldX * cos + worldZ * sin;
        double tileSize = Math.max(1e-6, config.getTileSize());
        int stripe = positiveMod(floorDiv(rotatedX, tileSize), materials.size());
        return stripe;
    }

    private static int resolveConcentricRings(
            ProceduralPatternConfig config,
            double worldX,
            double worldZ,
            Vec2d regionCentroid,
            List<String> materials) {
        Vec2d center = config.getCenterOverride();
        if (center == null) {
            center = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        }
        double dx = worldX - center.x;
        double dz = worldZ - center.y;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double tileSize = Math.max(1e-6, config.getTileSize());
        int ring = positiveMod(floorDiv(distance, tileSize), materials.size());
        return ring;
    }

    private static int resolveMosaic(
            ProceduralPatternConfig config,
            double worldX,
            double worldZ,
            List<String> materials,
            String seedKey) {
        MaterialMix mix = buildMosaicMix(config, materials);
        BlockPos pos = new BlockPos((int) Math.floor(worldX), 0, (int) Math.floor(worldZ));
        String resolved = MaterialMixResolver.resolve(mix, pos, seedKey, material -> material);
        for (int i = 0; i < materials.size(); i++) {
            if (materials.get(i).equals(resolved)) {
                return i;
            }
        }
        return 0;
    }

    static MaterialMix buildMosaicMix(ProceduralPatternConfig config, List<String> materials) {
        String primary = materials.getFirst();
        String accent = materials.size() > 1 ? materials.get(1) : primary;
        float accentRatio = (float) (1.0 - config.getMosaicPrimaryRatio());
        return new MaterialMix(primary, accent, accentRatio);
    }

    private static int floorDiv(double value, double divisor) {
        return (int) Math.floor(value / divisor);
    }

    private static int positiveMod(int value, int modulus) {
        if (modulus <= 0) {
            return 0;
        }
        int mod = value % modulus;
        return mod < 0 ? mod + modulus : mod;
    }
}
