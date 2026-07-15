package com.plot.core.material;

import net.minecraft.util.math.BlockPos;

import java.util.function.Function;

/**
 * 按方块坐标确定性解析混合材质，同一位置永远得到相同结果。
 */
public final class MaterialMixResolver {
    @FunctionalInterface
    public interface BlockIdResolver extends Function<String, String> {
    }

    private MaterialMixResolver() {
    }

    public static String resolve(MaterialMix mix, BlockPos pos, String seedKey) {
        return resolve(mix, pos, seedKey, material -> material);
    }

    public static String resolve(
            MaterialMix mix,
            BlockPos pos,
            String seedKey,
            BlockIdResolver blockIdResolver) {
        if (mix == null) {
            return blockIdResolver.apply(null);
        }
        if (!mix.hasAccent()) {
            return blockIdResolver.apply(mix.getPrimaryMaterial());
        }

        double value = deterministicUnitFloat(pos, seedKey);
        String chosen = value < mix.getAccentRatio()
            ? mix.getAccentMaterial()
            : mix.getPrimaryMaterial();
        return blockIdResolver.apply(chosen);
    }

    static double deterministicUnitFloat(BlockPos pos, String seedKey) {
        long hash = pos.getX();
        hash = hash * 31L + pos.getY();
        hash = hash * 31L + pos.getZ();
        hash = hash * 31L + (seedKey != null ? seedKey.hashCode() : 0);
        return (hash & 0x7FFFFFFFL) / (double) 0x80000000L;
    }
}
