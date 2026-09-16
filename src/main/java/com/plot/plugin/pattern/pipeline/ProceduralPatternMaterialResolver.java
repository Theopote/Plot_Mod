package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSpace;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * 程序化图案材质解析。
 */
public final class ProceduralPatternMaterialResolver implements PatternMaterialResolver {
    private final ProceduralPatternConfig config;

    public ProceduralPatternMaterialResolver(ProceduralPatternConfig config) {
        this.config = Objects.requireNonNull(config, "config").copy();
    }

    @Override
    public String resolveMaterial(PatternSpace space, PatternSample sample) {
        if (sample == null || space == null) {
            return null;
        }
        List<String> materials = config.getMaterials();
        if (materials.isEmpty()) {
            return null;
        }
        int index = resolveMaterialIndex(
            config,
            sample.x(),
            sample.z(),
            space.regionCentroid(),
            space.seedKey());
        return materials.get(Math.min(index, materials.size() - 1));
    }

    public static int resolveMaterialIndex(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            Vec2d regionCentroid,
            String seedKey) {
        if (config == null) {
            return 0;
        }
        List<String> materials = config.getMaterials();
        if (materials.isEmpty()) {
            return 0;
        }
        PatternCoordinateTransform.Point point =
            PatternCoordinateTransform.transform(config, patternX, patternZ);
        return switch (config.getType()) {
            case CHECKERBOARD -> resolveCheckerboard(config, point.x(), point.z(), materials);
            case STRIPES -> resolveStripes(config, point.x(), point.z(), materials);
            case CONCENTRIC_RINGS -> resolveConcentricRings(config, point.x(), point.z(), regionCentroid, materials);
            case MOSAIC -> resolveMosaic(config, point.x(), point.z(), materials, seedKey);
            case HEXAGONAL -> resolveHexagonal(config, point.x(), point.z(), materials);
            case DIAMOND -> resolveDiamond(config, point.x(), point.z(), materials);
            case HERRINGBONE -> resolveHerringbone(config, point.x(), point.z(), materials);
        };
    }

    public static MaterialMix buildMosaicMix(ProceduralPatternConfig config, List<String> materials) {
        String primary = materials.getFirst();
        String accent = materials.size() > 1 ? materials.get(1) : primary;
        float accentRatio = (float) (1.0 - config.getMosaicPrimaryRatio());
        return new MaterialMix(primary, accent, accentRatio);
    }

    private static int resolveCheckerboard(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double tileSize = PatternCoordinateTransform.effectiveTileSize(config);
        int parity = floorDiv(x, tileSize) + floorDiv(z, tileSize);
        return positiveMod(parity, materials.size());
    }

    private static int resolveStripes(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double radians = Math.toRadians(config.getAngleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rotatedX = x * cos + z * sin;
        double tileSize = PatternCoordinateTransform.effectiveTileSize(config);
        return positiveMod(floorDiv(rotatedX, tileSize), materials.size());
    }

    private static int resolveConcentricRings(
            ProceduralPatternConfig config,
            double x,
            double z,
            Vec2d regionCentroid,
            List<String> materials) {
        Vec2d center = config.getCenterOverride();
        if (center == null) {
            center = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        }
        double dx = x - center.x;
        double dz = z - center.y;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double tileSize = PatternCoordinateTransform.effectiveTileSize(config);
        return positiveMod(floorDiv(distance, tileSize), materials.size());
    }

    private static int resolveMosaic(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials,
            String seedKey) {
        double tileSize = Math.max(0.5, PatternCoordinateTransform.effectiveTileSize(config));
        int cellX = floorDiv(x, tileSize);
        int cellZ = floorDiv(z, tileSize);
        BlockPos pos = new BlockPos(cellX, 0, cellZ);

        if (materials.size() <= 2) {
            MaterialMix mix = buildMosaicMix(config, materials);
            String resolved = MaterialMixResolver.resolve(mix, pos, seedKey, material -> material);
            return indexOfMaterial(materials, resolved);
        }

        double value = MaterialMixResolver.unitRandomAt(pos, seedKey);
        double primaryRatio = config.getMosaicPrimaryRatio();
        double accentShare = (1.0 - primaryRatio) / (materials.size() - 1);
        double cumulative = 0.0;
        for (int i = 0; i < materials.size(); i++) {
            double weight = i == 0 ? primaryRatio : accentShare;
            cumulative += weight;
            if (value < cumulative) {
                return i;
            }
        }
        return materials.size() - 1;
    }

    private static int resolveHexagonal(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double hexSize = PatternCoordinateTransform.effectiveTileSize(config);
        double hexWidth = hexSize * Math.sqrt(3);
        double hexHeight = hexSize * 2.0;

        double col = x / hexWidth;
        double row = z / (hexHeight * 0.75);
        int colInt = (int) Math.floor(col);
        int rowInt = (int) Math.floor(row);
        boolean offsetRow = (rowInt & 1) != 0;
        double centerX = offsetRow ? 0.5 : 0.0;
        double localX = col - colInt - centerX;
        double localZ = row - rowInt - 0.5;

        if (!isInsideFlatTopHex(localX, localZ, hexSize, hexWidth, hexHeight)) {
            return positiveMod(colInt + rowInt + 1, materials.size());
        }
        int hexIndex = rowInt * 2 + colInt;
        return positiveMod(hexIndex, materials.size());
    }

    private static boolean isInsideFlatTopHex(
            double localX,
            double localZ,
            double hexSize,
            double hexWidth,
            double hexHeight) {
        double halfWidth = hexWidth * 0.5;
        double halfHeight = hexHeight * 0.5;
        double ax = Math.abs(localX) * halfWidth;
        double az = Math.abs(localZ) * halfHeight;
        return ax + az * (halfWidth / halfHeight) <= halfWidth * 0.5;
    }

    private static int resolveDiamond(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double diamondSize = PatternCoordinateTransform.effectiveTileSize(config);
        int cellX = floorDiv(x, diamondSize);
        int cellZ = floorDiv(z, diamondSize);
        double localX = (x - cellX * diamondSize) / diamondSize;
        double localZ = (z - cellZ * diamondSize) / diamondSize;
        boolean isDiamond = (Math.abs(localX - 0.5) + Math.abs(localZ - 0.5)) < 0.5;
        int baseIndex = cellX + cellZ;
        return positiveMod(baseIndex + (isDiamond ? 0 : 1), materials.size());
    }

    private static int resolveHerringbone(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double brickWidth = PatternCoordinateTransform.effectiveTileSize(config);
        double brickHeight = brickWidth * 0.5;
        int row = floorDiv(z, brickHeight);
        boolean offsetRow = (row & 1) != 0;
        double effectiveX = offsetRow ? x - brickWidth * 0.5 : x;
        int col = floorDiv(effectiveX, brickWidth);
        double localX = effectiveX - col * brickWidth;
        double localZ = z - row * brickHeight;
        boolean herringbone = localX < localZ * (brickWidth / brickHeight);
        int baseIndex = row * 2 + col;
        return positiveMod(baseIndex + (herringbone ? 1 : 0), materials.size());
    }

    private static int indexOfMaterial(List<String> materials, String resolved) {
        for (int i = 0; i < materials.size(); i++) {
            if (materials.get(i).equals(resolved)) {
                return i;
            }
        }
        return 0;
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
