package com.plot.plugin.road.tunnel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnoses whether lining cells above the road form a reliable 4-neighbor shell.
 */
class TunnelCrossSectionLiningConnectivityTest {

    private static final int ROAD_Y = 64;

    static Stream<Arguments> matrix() {
        List<Arguments> args = new ArrayList<>();
        int[] widths = {3, 5, 7, 11};
        int[] clears = {3, 5, 8, 12};
        int[] sides = {0, 1, 3};
        int[] linings = {1, 2, 3};
        for (TunnelShape shape : TunnelShape.values()) {
            for (int width : widths) {
                for (int clear : clears) {
                    for (int side : sides) {
                        for (int lining : linings) {
                            if (clear < TunnelStyle.MIN_CLEAR_HEIGHT || clear > TunnelStyle.MAX_CLEAR_HEIGHT) {
                                continue;
                            }
                            if (side < TunnelStyle.MIN_SIDE_CLEARANCE || side > TunnelStyle.MAX_SIDE_CLEARANCE) {
                                continue;
                            }
                            if (lining < TunnelStyle.MIN_LINING_THICKNESS
                                    || lining > TunnelStyle.MAX_LINING_THICKNESS) {
                                continue;
                            }
                            args.add(Arguments.of(shape, width, clear, side, lining));
                        }
                    }
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} w={1} h={2} side={3} lining={4}")
    @MethodSource("matrix")
    void liningAboveRoadIsFourNeighborConnected(
            TunnelShape shape, int width, int clear, int side, int lining) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, width, side, clear, lining, ROAD_Y, false);

        Set<Long> liningCells = new HashSet<>();
        Set<Long> airCells = new HashSet<>();
        int[] roadMinMax = {mask.roadMin(), mask.roadMax()};
        mask.forEach((lateral, y, kind) -> {
            if (y < ROAD_Y) {
                return;
            }
            if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                liningCells.add(pack(lateral, y));
            } else if (kind == TunnelCrossSectionMask.CellKind.AIR && y > ROAD_Y) {
                // Usable cavity above the pavement; roadY side niches may be equatorial orphans.
                airCells.add(pack(lateral, y));
            }
        });

        assumeTrue(!liningCells.isEmpty(), "shape produced no lining above road");
        assertFalse(hasIsolatedLining(liningCells, roadMinMax[0], roadMinMax[1]),
            () -> shape + " has lining cells without 4-neighbor lining/road contact");
        assertTrue(airIsConnected(airCells),
            () -> shape + " cavity air above road is disconnected");
        assertPavementHolePreserved(mask, width);
    }

    @ParameterizedTest(name = "{0} keeps invert below road")
    @MethodSource("invertShapes")
    void invertShapesKeepStructureBelowRoad(TunnelShape shape) {
        int clear = shape == TunnelShape.CIRCULAR ? 4 : 6;
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, 5, 1, clear, 1, ROAD_Y, false);
        boolean invert = false;
        for (int y = mask.minY(); y < ROAD_Y; y++) {
            TunnelCrossSectionMask.CellKind kind = mask.kindAt(0, y);
            if (kind == TunnelCrossSectionMask.CellKind.AIR
                    || kind == TunnelCrossSectionMask.CellKind.LINING) {
                invert = true;
                break;
            }
        }
        assertTrue(invert, shape + " must keep invert structure below roadY");
        assertEqualsOutsidePavement(mask);
    }

    static Stream<Arguments> invertShapes() {
        return Stream.of(
            Arguments.of(TunnelShape.CIRCULAR),
            Arguments.of(TunnelShape.ELLIPTICAL));
    }

    private static void assertPavementHolePreserved(TunnelCrossSectionMask mask, int width) {
        int roadMin = mask.roadMin();
        int roadMax = mask.roadMax();
        for (int lateral = roadMin; lateral <= roadMax; lateral++) {
            assertTrue(
                mask.kindAt(lateral, ROAD_Y) == TunnelCrossSectionMask.CellKind.OUTSIDE,
                "pavement cell must stay OUTSIDE at lateral=" + lateral);
        }
    }

    private static void assertEqualsOutsidePavement(TunnelCrossSectionMask mask) {
        assertTrue(mask.kindAt(0, ROAD_Y) == TunnelCrossSectionMask.CellKind.OUTSIDE);
    }

    private static boolean hasIsolatedLining(Set<Long> lining, int roadMin, int roadMax) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (long cell : lining) {
            int lateral = unpackLateral(cell);
            int y = unpackY(cell);
            int neighbors = 0;
            for (int[] d : dirs) {
                int nl = lateral + d[0];
                int ny = y + d[1];
                if (lining.contains(pack(nl, ny))) {
                    neighbors++;
                    continue;
                }
                // Road pavement closes the visible ring at roadY.
                if (ny == ROAD_Y && nl >= roadMin && nl <= roadMax) {
                    neighbors++;
                }
            }
            if (neighbors == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean airIsConnected(Set<Long> air) {
        if (air.isEmpty()) {
            return false;
        }
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        long start = air.iterator().next();
        queue.add(start);
        seen.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long cell = queue.removeFirst();
            int lateral = unpackLateral(cell);
            int y = unpackY(cell);
            for (int[] d : dirs) {
                long next = pack(lateral + d[0], y + d[1]);
                if (air.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen.size() == air.size();
    }

    private static long pack(int lateral, int y) {
        return (((long) lateral) << 32) ^ (y & 0xffffffffL);
    }

    private static int unpackLateral(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }
}
