package com.plot.plugin.building.roofstress;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.roofstress.RoofStressCaseFactory.RoofStressCase;
import com.plot.plugin.building.roofstress.RoofStressCaseFactory.SymmetryKind;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Roof Stress Suite 断言：continuity / rise / symmetry / no isolated voxel / no hole。
 */
public final class RoofStressAssertions {
    private RoofStressAssertions() {
    }

    public static void assertCase(RoofStressCase stressCase) {
        RoofStressField field = RoofStressField.sample(stressCase.polygon(), stressCase.pitch());
        assertSkeletonOk(stressCase, field);
        assertRiseBounds(stressCase, field);
        assertNoIsolatedVoxel(stressCase, field);
        assertNoHole(stressCase, field);
        assertRoofCellContinuity(stressCase, field);
        assertSymmetry(stressCase, field);
    }

    private static void assertSkeletonOk(RoofStressCase stressCase, RoofStressField field) {
        assertTrue(field.skeleton().success(),
            stressCase.id() + " skeleton must succeed");
        assertTrue(field.cellCount() > 0,
            stressCase.id() + " must have interior roof cells");
    }

    private static void assertRiseBounds(RoofStressCase stressCase, RoofStressField field) {
        double maxTime = field.skeleton().maxSkeletalTime();
        int pitch = field.pitch();
        if (maxTime < pitch) {
            assertEquals(0, field.maxRise(),
                stressCase.id() + " narrow footprint should be flat (maxRise=0)");
            assertEquals(0, field.minRise(),
                stressCase.id() + " narrow footprint minRise");
            return;
        }
        assertTrue(field.maxRise() >= 1,
            stressCase.id() + " maxRise expected >= 1, got " + field.maxRise()
                + " (maxTime=" + maxTime + ")");
        assertEquals(0, field.minRise(),
            stressCase.id() + " eave cells should yield minRise=0, got " + field.minRise());
        assertTrue(field.maxRise() <= (int) Math.floor(maxTime / pitch) + 1,
            stressCase.id() + " maxRise exceeds distance-field bound");
    }

    /**
     * rise&gt;0 区域 4-连通（从任一最大 rise 格可达全部正 rise 格）。
     */
    private static void assertRoofCellContinuity(RoofStressCase stressCase, RoofStressField field) {
        if (field.positiveCellCount() <= 1) {
            return;
        }
        Long seed = null;
        int max = field.maxRise();
        for (Map.Entry<Long, Integer> e : field.riseByKey().entrySet()) {
            if (e.getValue() == max) {
                seed = e.getKey();
                break;
            }
        }
        if (seed == null) {
            fail(stressCase.id() + " missing max-rise seed");
            return;
        }

        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            long key = queue.removeFirst();
            Vec2d cell = field.cellByKey().get(key);
            if (cell == null) {
                continue;
            }
            for (Vec2d n : RoofStressField.orthogonalNeighbors(cell)) {
                long nk = RoofStressField.cellKey(n);
                Integer nr = field.riseByKey().get(nk);
                if (nr == null || nr <= 0) {
                    continue;
                }
                if (visited.add(nk)) {
                    queue.add(nk);
                }
            }
        }
        assertEquals(field.positiveCellCount(), visited.size(),
            stressCase.id() + " roof cells with rise>0 are not 4-connected "
                + "(visited=" + visited.size() + " positive=" + field.positiveCellCount() + ")");
    }

    private static void assertNoIsolatedVoxel(RoofStressCase stressCase, RoofStressField field) {
        for (Map.Entry<Long, Integer> entry : field.riseByKey().entrySet()) {
            int rise = entry.getValue();
            if (rise <= 0) {
                continue;
            }
            Vec2d cell = field.cellByKey().get(entry.getKey());
            int neighborMax = -1;
            int known = 0;
            for (Vec2d n : RoofStressField.orthogonalNeighbors(cell)) {
                Integer nr = field.riseByKey().get(RoofStressField.cellKey(n));
                if (nr == null) {
                    continue;
                }
                known++;
                neighborMax = Math.max(neighborMax, nr);
            }
            if (known > 0 && rise > neighborMax + 1) {
                fail(stressCase.id() + " isolated voxel at " + cell
                    + " rise=" + rise + " neighborMax=" + neighborMax);
            }
        }
    }

    private static void assertNoHole(RoofStressCase stressCase, RoofStressField field) {
        for (Map.Entry<Long, Integer> entry : field.riseByKey().entrySet()) {
            if (entry.getValue() != 0) {
                continue;
            }
            Vec2d cell = field.cellByKey().get(entry.getKey());
            int high = 0;
            int known = 0;
            for (Vec2d n : RoofStressField.orthogonalNeighbors(cell)) {
                Integer nr = field.riseByKey().get(RoofStressField.cellKey(n));
                if (nr == null) {
                    continue;
                }
                known++;
                if (nr > 0) {
                    high++;
                }
            }
            if (known == 4 && high == 4) {
                fail(stressCase.id() + " roof hole at " + cell);
            }
        }
    }

    private static void assertSymmetry(RoofStressCase stressCase, RoofStressField field) {
        SymmetryKind kind = stressCase.symmetry();
        if (kind == SymmetryKind.NONE) {
            return;
        }
        if (kind == SymmetryKind.MATCH_REFERENCE) {
            assertMatchesReference(stressCase, field);
            return;
        }
        if (field.maxRise() == 0) {
            return;
        }

        Vec2d c = centroid(stressCase.polygon());
        int mismatches = 0;
        int compared = 0;
        for (Map.Entry<Long, Vec2d> entry : field.cellByKey().entrySet()) {
            Vec2d cell = entry.getValue();
            int rise = field.riseByKey().get(entry.getKey());
            Vec2d mirrored = switch (kind) {
                case BILATERAL_X -> new Vec2d(2 * c.x - cell.x, cell.y);
                case BILATERAL_XY -> new Vec2d(2 * c.x - cell.x, 2 * c.y - cell.y);
                case ROTATIONAL_180 -> new Vec2d(2 * c.x - cell.x, 2 * c.y - cell.y);
                default -> cell;
            };
            if (kind == SymmetryKind.BILATERAL_XY) {
                mismatches += comparePair(field, cell, rise, new Vec2d(2 * c.x - cell.x, cell.y));
                mismatches += comparePair(field, cell, rise, new Vec2d(cell.x, 2 * c.y - cell.y));
                compared += 2;
            } else if (kind == SymmetryKind.BILATERAL_X) {
                mismatches += comparePair(field, cell, rise, mirrored);
                compared++;
            } else {
                mismatches += comparePair(field, cell, rise, mirrored);
                compared++;
            }
        }
        assertTrue(compared > 0, stressCase.id() + " symmetry compared no cells");
        // 允许少量栅格边界误差
        double ratio = mismatches / (double) compared;
        assertTrue(ratio <= 0.08,
            stressCase.id() + " symmetry mismatch ratio=" + ratio
                + " (" + mismatches + "/" + compared + ")");
    }

    private static int comparePair(RoofStressField field, Vec2d cell, int rise, Vec2d other) {
        Integer otherRise = field.riseAt(other);
        if (otherRise == null) {
            // 镜像点不在 footprint 内：跳过（凹形对称轴外）
            return 0;
        }
        return Math.abs(rise - otherRise) > 1 ? 1 : 0;
    }

    private static void assertMatchesReference(RoofStressCase stressCase, RoofStressField field) {
        RoofStressField reference = RoofStressField.sample(
            stressCase.referencePolygon(), stressCase.pitch());
        assertEquals(reference.maxRise(), field.maxRise(),
            stressCase.id() + " maxRise vs reference");
        assertEquals(reference.minRise(), field.minRise(),
            stressCase.id() + " minRise vs reference");
        assertEquals(reference.positiveCellCount(), field.positiveCellCount(),
            stressCase.id() + " positiveCellCount vs reference");

        Vec2d cCase = centroid(stressCase.polygon());
        Vec2d cRef = centroid(stressCase.referencePolygon());
        double dx = cCase.x - cRef.x;
        double dy = cCase.y - cRef.y;

        int mismatches = 0;
        int compared = 0;
        for (Map.Entry<Long, Vec2d> entry : reference.cellByKey().entrySet()) {
            Vec2d refCell = entry.getValue();
            int refRise = reference.riseByKey().get(entry.getKey());
            Vec2d mapped = new Vec2d(refCell.x + dx, refCell.y + dy);
            Integer caseRise = field.riseAt(mapped);
            if (caseRise == null) {
                mismatches++;
                compared++;
                continue;
            }
            if (Math.abs(caseRise - refRise) > 0) {
                mismatches++;
            }
            compared++;
        }
        assertTrue(compared > 0, stressCase.id() + " reference compare empty");
        assertTrue(mismatches / (double) compared <= 0.02,
            stressCase.id() + " field mismatch vs reference ratio="
                + (mismatches / (double) compared));
    }

    private static Vec2d centroid(java.util.List<Vec2d> polygon) {
        double sx = 0;
        double sy = 0;
        for (Vec2d p : polygon) {
            sx += p.x;
            sy += p.y;
        }
        return new Vec2d(sx / polygon.size(), sy / polygon.size());
    }
}
