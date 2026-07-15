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

    /**
     * Maps (pos, seedKey) to a stable unit float in [0, 1).
     * Uses splitmix64-style avalanche mixing so nearby coords still decorate independently.
     */
    static double deterministicUnitFloat(BlockPos pos, String seedKey) {
        long seed = seedKey != null ? seedKey.hashCode() : 0L;
        long h = (long) pos.getX() * 0x9E3779B97F4A7C15L;
        h ^= (long) pos.getY() * 0xBF58476D1CE4E5B9L;
        h ^= (long) pos.getZ() * 0x94D049BB133111EBL;
        h ^= seed * 0x2545F4914F6CDD1DL;
        h = avalanche64(h);
        // High bits after multiplicative mixing are higher quality than low bits.
        return (h >>> 33) / (double) (1L << 31);
    }

    /** Stafford / SplitMix64 finalizer. */
    private static long avalanche64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
