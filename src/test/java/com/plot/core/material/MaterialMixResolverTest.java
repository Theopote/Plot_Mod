package com.plot.core.material;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        BlockPos origin = new BlockPos(4, 8, 16);
        final int sampleCount = 1000;

        java.util.List<String> sequenceA = resolveRange(mix, origin, "edge-a", sampleCount);
        java.util.List<String> sequenceB = resolveRange(mix, origin, "edge-b", sampleCount);

        assertTrue(!sequenceA.equals(sequenceB),
            "different seeds should produce different selection sequences");

        int differences = 0;
        Map<String, Integer> accentCounts = new HashMap<>();
        accentCounts.put("edge-a", 0);
        accentCounts.put("edge-b", 0);
        for (int i = 0; i < sampleCount; i++) {
            String a = sequenceA.get(i);
            String b = sequenceB.get(i);
            if (!a.equals(b)) {
                differences++;
            }
            if ("accent".equals(a)) {
                accentCounts.merge("edge-a", 1, Integer::sum);
            }
            if ("accent".equals(b)) {
                accentCounts.merge("edge-b", 1, Integer::sum);
            }
        }

        // ~50% mismatch expected at ratio 0.5; require clear Hamming divergence, not a single cell.
        assertTrue(differences > 100,
            "expected significant sequence divergence, but only " + differences + " of " + sampleCount
                + " positions differed; accent counts=" + accentCounts);
    }

    private static java.util.List<String> resolveRange(
            MaterialMix mix, BlockPos origin, String seed, int count) {
        java.util.List<String> resolved = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = new BlockPos(origin.getX() + i, origin.getY(), origin.getZ());
            resolved.add(MaterialMixResolver.resolve(mix, pos, seed, material -> material));
        }
        return resolved;
    }
}
