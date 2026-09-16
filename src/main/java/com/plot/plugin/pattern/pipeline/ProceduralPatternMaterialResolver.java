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
 * 程序化图案材质解析（Checkerboard / Stripes / Rings / Mosaic / Hexagonal / Diamond / Herringbone）。
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
        return switch (config.getType()) {
            case CHECKERBOARD -> resolveCheckerboard(config, patternX, patternZ, materials);
            case STRIPES -> resolveStripes(config, patternX, patternZ, materials);
            case CONCENTRIC_RINGS -> resolveConcentricRings(config, patternX, patternZ, regionCentroid, materials);
            case MOSAIC -> resolveMosaic(config, patternX, patternZ, materials, seedKey);
            case HEXAGONAL -> resolveHexagonal(config, patternX, patternZ, materials);
            case DIAMOND -> resolveDiamond(config, patternX, patternZ, materials);
            case HERRINGBONE -> resolveHerringbone(config, patternX, patternZ, materials, seedKey);
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
            double patternX,
            double patternZ,
            List<String> materials) {
        double tileSize = Math.max(1e-6, config.getTileSize());
        int parity = floorDiv(patternX, tileSize) + floorDiv(patternZ, tileSize);
        return positiveMod(parity, materials.size());
    }

    private static int resolveStripes(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            List<String> materials) {
        double radians = Math.toRadians(config.getAngleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rotatedX = patternX * cos + patternZ * sin;
        double tileSize = Math.max(1e-6, config.getTileSize());
        return positiveMod(floorDiv(rotatedX, tileSize), materials.size());
    }

    private static int resolveConcentricRings(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            Vec2d regionCentroid,
            List<String> materials) {
        Vec2d center = config.getCenterOverride();
        if (center == null) {
            center = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        }
        double dx = patternX - center.x;
        double dz = patternZ - center.y;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double tileSize = Math.max(1e-6, config.getTileSize());
        return positiveMod(floorDiv(distance, tileSize), materials.size());
    }

    private static int resolveMosaic(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            List<String> materials,
            String seedKey) {
        double tileSize = Math.max(0.5, config.getTileSize());
        int cellX = floorDiv(patternX, tileSize);
        int cellZ = floorDiv(patternZ, tileSize);
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
            double patternX,
            double patternZ,
            List<String> materials) {
        double tileSize = Math.max(1e-6, config.getTileSize());
        Vec2d offset = config.getOffset();
        double offsetX = offset != null ? offset.x : 0;
        double offsetZ = offset != null ? offset.y : 0;
        
        // 六边形网格计算
        double hexWidth = tileSize * Math.sqrt(3);
        double hexHeight = tileSize * 2;
        
        double x = patternX + offsetX;
        double z = patternZ + offsetZ;
        
        // 计算六边形网格坐标
        double col = x / hexWidth;
        double row = z / (hexHeight * 0.75);
        
        int colInt = (int) Math.floor(col);
        int rowInt = (int) Math.floor(row);
        
        // 判断是否在偏移行
        boolean isOffsetRow = (rowInt % 2) != 0;
        double effectiveCol = isOffsetRow ? col - 0.5 : col;
        
        // 计算六边形内部位置
        double colFrac = col - colInt;
        double rowFrac = row - rowInt;
        
        // 六边形内部判断
        double centerX = isOffsetRow ? 0.5 : 0;
        double dz = rowFrac - 0.5;
        double dx = colFrac - centerX;
        
        int hexIndex = rowInt * 2 + colInt;
        return positiveMod(hexIndex, materials.size());
    }

    private static int resolveDiamond(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            List<String> materials) {
        double tileSize = Math.max(1e-6, config.getTileSize());
        Vec2d offset = config.getOffset();
        double offsetX = offset != null ? offset.x : 0;
        double offsetZ = offset != null ? offset.y : 0;
        
        double radians = Math.toRadians(config.getAngleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        
        double x = patternX + offsetX;
        double z = patternZ + offsetZ;
        
        // 旋转坐标
        double rotatedX = x * cos + z * sin;
        double rotatedZ = -x * sin + z * cos;
        
        // 菱形网格计算
        double diamondSize = tileSize * config.getDensity();
        int cellX = floorDiv(rotatedX, diamondSize);
        int cellZ = floorDiv(rotatedZ, diamondSize);
        
        // 菱形判断 (曼哈顿距离)
        double localX = (rotatedX - cellX * diamondSize) / diamondSize;
        double localZ = (rotatedZ - cellZ * diamondSize) / diamondSize;
        
        boolean isDiamond = (Math.abs(localX - 0.5) + Math.abs(localZ - 0.5)) < 0.5;
        int baseIndex = cellX + cellZ;
        return positiveMod(baseIndex + (isDiamond ? 0 : 1), materials.size());
    }

    private static int resolveHerringbone(
            ProceduralPatternConfig config,
            double patternX,
            double patternZ,
            List<String> materials,
            String seedKey) {
        double tileSize = Math.max(1e-6, config.getTileSize());
        Vec2d offset = config.getOffset();
        double offsetX = offset != null ? offset.x : 0;
        double offsetZ = offset != null ? offset.y : 0;
        
        double radians = Math.toRadians(config.getAngleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        
        double x = patternX + offsetX;
        double z = patternZ + offsetZ;
        
        // 旋转坐标
        double rotatedX = x * cos + z * sin;
        double rotatedZ = -x * sin + z * cos;
        
        // 人字形网格计算
        double brickWidth = tileSize * config.getDensity();
        double brickHeight = tileSize * 0.5;
        
        int row = floorDiv(rotatedZ, brickHeight);
        boolean isOffsetRow = (row % 2) != 0;
        
        double localZ = rotatedZ - row * brickHeight;
        double effectiveX = isOffsetRow ? rotatedX - brickWidth * 0.5 : rotatedX;
        
        int col = floorDiv(effectiveX, brickWidth);
        double localX = effectiveX - col * brickWidth;
        
        // 人字形判断 (基于位置的倾斜)
        boolean isHerringbone = localX < localZ * (brickWidth / brickHeight);
        
        int baseIndex = row * 2 + col;
        int herringboneOffset = isHerringbone ? 1 : 0;
        
        // 使用种子进行轻微随机化
        BlockPos pos = new BlockPos(col, row, 0);
        double randomValue = MaterialMixResolver.unitRandomAt(pos, seedKey);
        int randomOffset = randomValue < 0.1 ? 1 : 0;
        
        return positiveMod(baseIndex + herringboneOffset + randomOffset, materials.size());
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
