package com.plot.plugin.road.pipeline.corridor;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Validates that tunnel air seeds within an open volume are mutually reachable.
 */
public final class TunnelJunctionAirConnectivityValidator {
    private TunnelJunctionAirConnectivityValidator() {
    }

    public record Result(int disconnectedComponentCount, int reachableSeedCount, int totalSeedCount) {
        public static final Result NONE = new Result(0, 0, 0);

        public boolean allSeedsConnected() {
            return totalSeedCount <= 1 || disconnectedComponentCount == 0;
        }
    }

    public static Result validate(Set<BlockPos> openVolume, List<BlockPos> seeds) {
        if (openVolume == null || openVolume.isEmpty() || seeds == null || seeds.isEmpty()) {
            return Result.NONE;
        }
        List<BlockPos> validSeeds = seeds.stream().filter(openVolume::contains).toList();
        if (validSeeds.isEmpty()) {
            return new Result(seeds.size(), 0, seeds.size());
        }
        Set<BlockPos> visited = new HashSet<>();
        int components = 0;
        int reachableSeeds = 0;
        for (BlockPos seed : validSeeds) {
            if (visited.contains(seed)) {
                continue;
            }
            components++;
            Set<BlockPos> component = floodFill(openVolume, seed);
            visited.addAll(component);
            for (BlockPos other : validSeeds) {
                if (component.contains(other)) {
                    reachableSeeds++;
                }
            }
        }
        int disconnected = Math.max(0, components - 1);
        return new Result(disconnected, reachableSeeds, validSeeds.size());
    }

    private static Set<BlockPos> floodFill(Set<BlockPos> openVolume, BlockPos start) {
        Set<BlockPos> component = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);
        int[][] neighbors = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int[] offset : neighbors) {
                BlockPos next = current.add(offset[0], offset[1], offset[2]);
                if (openVolume.contains(next) && component.add(next)) {
                    queue.add(next);
                }
            }
        }
        return component;
    }
}
