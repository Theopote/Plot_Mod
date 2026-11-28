package com.plot.plugin.road.pipeline.profile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First-pass network node elevations plus hard design conflicts that must block apply.
 * Conflicts are never encoded as absent map entries alone.
 */
public record NetworkNodeElevationResult(
        Map<String, Integer> heights,
        Set<String> conflictNodeIds,
        Map<String, List<Integer>> conflictSamples) {

    public NetworkNodeElevationResult {
        heights = heights != null ? Map.copyOf(heights) : Map.of();
        conflictNodeIds = conflictNodeIds != null ? Set.copyOf(conflictNodeIds) : Set.of();
        conflictSamples = conflictSamples != null ? Map.copyOf(conflictSamples) : Map.of();
    }

    public static NetworkNodeElevationResult empty() {
        return new NetworkNodeElevationResult(Map.of(), Set.of(), Map.of());
    }

    public boolean hasHardConflicts() {
        return !conflictNodeIds.isEmpty();
    }
}
