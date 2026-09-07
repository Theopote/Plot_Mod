package com.plot.plugin.road.golden;

import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Semantic Acceptance：手写正确性断言（不可由 Snapshot 生成 expected）。
 */
public final class RoadSemanticAcceptanceAssertions {
    private RoadSemanticAcceptanceAssertions() {
    }

    public static void assertUniversal(String caseId, RoadGoldenHarness.Run run) {
        RoadGoldenMetrics metrics = run.metrics();
        assertTrue(metrics.surfaceBlocks() > 0, caseId + " must produce road surface");
        assertTrue(metrics.placementRecords() > 0, caseId + " must produce placement records");
        assertFalse(run.preview().aggregate().roadBlocks.isEmpty(), caseId + " roadBlocks non-empty");
    }

    public static void assertStraightRoad(RoadGoldenHarness.Run run) {
        assertTrue(hasContinuousSurfaceAtElevation(run.preview().aggregate(), 64),
            "R01 surface should be continuous at design elevation");
        assertTrue(run.metrics().cutVolume() >= 0);
        assertTrue(run.metrics().fillVolume() >= 0);
    }

    public static void assertJunctionPresent(RoadGoldenHarness.Run run, int minDegree) {
        assertTrue(run.metrics().junctionBlocks() > 0, "junction must produce blocks");
        long junctionNodes = run.network().getNodes().values().stream()
            .filter(n -> n.getDegree() >= minDegree)
            .count();
        assertTrue(junctionNodes > 0, "network must contain junction node");
    }

    public static void assertBridgeSemantics(RoadGoldenHarness.Run run) {
        assertTrue(run.metrics().bridgeCount() > 0, "bridge scenario must classify bridge");
        assertTrue(run.metrics().bridgeBlocks() > 0, "bridge must produce deck/support blocks");
        assertTrue(run.metrics().fillVolume() == 0,
            "bridge must not fill valley as ordinary roadbed");
    }

    public static void assertTunnelSemantics(RoadGoldenHarness.Run run) {
        assertTrue(run.metrics().tunnelCount() > 0 || run.metrics().tunnelBlocks() > 0,
            "tunnel scenario must produce tunnel structure");
    }

    public static void assertGradeSeparated(RoadGoldenHarness.Run run) {
        assertTrue(run.metrics().surfaceBlocks() > 0);
        assertTrue(run.network().getNodes().values().stream()
            .anyMatch(n -> n.isGradeSeparated()),
            "grade separation must be configured");
    }

    public static void assertClosedLoop(RoadGoldenHarness.Run run) {
        assertTrue(run.network().getRoads().values().stream()
            .anyMatch(r -> r.getTopologyMode() == com.plot.plugin.road.model.RoadTopologyMode.LOOP),
            "closed shape should be LOOP topology");
        assertTrue(run.metrics().surfaceBlocks() > 0);
    }

    private static boolean hasContinuousSurfaceAtElevation(RoadGenerationResult result, int elevation) {
        Set<Long> columns = new HashSet<>();
        for (BlockPos pos : result.roadBlocks) {
            if (pos.getY() != elevation) {
                continue;
            }
            columns.add(pack(pos.getX(), pos.getZ()));
        }
        if (columns.size() <= 1) {
            return !columns.isEmpty();
        }
        long seed = columns.iterator().next();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = unpackX(current);
            int z = unpackZ(current);
            for (int[] delta : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long neighbor = pack(x + delta[0], z + delta[1]);
                if (columns.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited.size() == columns.size();
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
