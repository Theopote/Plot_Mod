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
    void accentRatioIsApproximatelyRespectedOnRoadFootprint() {
        MaterialMix mix = new MaterialMix("primary", "accent", 0.2f);
        String[] seeds = {
            "edge-0", "edge-1", "edge-42", "road-12345", "road--999", "seg-777888"
        };

        for (String seed : seeds) {
            int accentCount = 0;
            int total = 0;
            // Typical carriageway strip: ~200 long, ~9 wide, fixed Y.
            for (int x = 0; x <= 200; x++) {
                for (int z = -4; z <= 4; z++) {
                    total++;
                    String resolved = MaterialMixResolver.resolve(
                        mix, new BlockPos(x, 64, z), seed, material -> material);
                    if ("accent".equals(resolved)) {
                        accentCount++;
                    }
                }
            }

            double ratio = accentCount / (double) total;
            assertTrue(ratio > 0.15 && ratio < 0.25,
                "accent ratio for seed '" + seed + "' was " + ratio);
        }
    }

    @Test
    void accentBlocksAreScatteredNotBinarizedOnShortRoad() {
        MaterialMix mix = new MaterialMix("primary", "accent", 0.2f);
        int accentCount = 0;
        int total = 0;
        int runs = 0;
        boolean previousAccent = false;
        boolean sawRun = false;

        for (int x = 0; x <= 200; x++) {
            for (int z = -4; z <= 4; z++) {
                total++;
                boolean accent = "accent".equals(MaterialMixResolver.resolve(
                    mix, new BlockPos(x, 64, z), "edge-a", material -> material));
                if (accent) {
                    accentCount++;
                }
                if (accent != previousAccent && total > 1) {
                    runs++;
                    sawRun = true;
                }
                previousAccent = accent;
            }
        }

        double ratio = accentCount / (double) total;
        assertTrue(ratio > 0.15 && ratio < 0.25, "accent ratio was " + ratio);
        // All-or-nothing would yield ~1 run; sparse speckles need many transitions.
        assertTrue(sawRun && runs > 50,
            "expected scattered accents, but only saw " + runs + " runs");
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
