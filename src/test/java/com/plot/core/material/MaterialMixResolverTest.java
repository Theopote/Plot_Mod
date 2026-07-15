package com.plot.core.material;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialMixResolverTest {

    @Test
    void resolveIsDeterministicForSameInput() {
        MaterialMix mix = new MaterialMix("minecraft:stone", "minecraft:gravel", 0.3f);
        BlockPos pos = new BlockPos(12, 64, -7);

        String first = MaterialMixResolver.resolve(mix, pos, "edge-a", material -> material);
        String second = MaterialMixResolver.resolve(mix, pos, "edge-a", material -> material);

        assertEquals(first, second);
    }

    @Test
    void resolveWithoutAccentAlwaysReturnsPrimary() {
        MaterialMix mix = MaterialMix.single("minecraft:stone");
        BlockPos pos = new BlockPos(1, 2, 3);

        assertEquals("minecraft:stone", MaterialMixResolver.resolve(mix, pos, "edge-a", material -> material));
        assertEquals("minecraft:stone", MaterialMixResolver.resolve(mix, pos, "edge-b", material -> material));
    }

    @Test
    void accentRatioIsApproximatelyRespected() {
        MaterialMix mix = new MaterialMix("primary", "accent", 0.2f);
        int accentCount = 0;
        int total = 10_000;
        for (int x = 0; x < total; x++) {
            String resolved = MaterialMixResolver.resolve(
                mix, new BlockPos(x, 0, 0), "seed", material -> material);
            if ("accent".equals(resolved)) {
                accentCount++;
            }
        }

        double ratio = accentCount / (double) total;
        assertTrue(ratio > 0.15 && ratio < 0.25, "accent ratio was " + ratio);
    }

    @Test
    void differentSeedKeysChangeSelectionDistribution() {
        MaterialMix mix = new MaterialMix("primary", "accent", 0.5f);
        BlockPos pos = new BlockPos(4, 8, 16);
        Map<String, Integer> countsBySeed = new HashMap<>();

        for (String seed : java.util.List.of("edge-a", "edge-b", "edge-c")) {
            int accentCount = 0;
            for (int i = 0; i < 200; i++) {
                String resolved = MaterialMixResolver.resolve(
                    mix, new BlockPos(pos.getX() + i, pos.getY(), pos.getZ()), seed, material -> material);
                if ("accent".equals(resolved)) {
                    accentCount++;
                }
            }
            countsBySeed.put(seed, accentCount);
        }

        assertTrue(countsBySeed.values().stream().distinct().count() > 1,
            "different seeds should not all produce identical accent counts");
        assertNotEquals(
            MaterialMixResolver.resolve(mix, pos, "edge-a", material -> material),
            MaterialMixResolver.resolve(mix, pos, "edge-b", material -> material));
    }
}
