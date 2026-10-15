package com.plot.plugin.road.pipeline.corridor;

import java.util.HashSet;
import java.util.Set;

/**
 * Authoritative set of world columns that received a junction surface stamp.
 * Clearance and diagnostics derive from the same stamped columns.
 */
public final class JunctionSurfaceMask {
    private final Set<ColumnKey> expectedColumns = new HashSet<>();
    private final Set<ColumnKey> stampedColumns = new HashSet<>();

    public void expectColumn(ColumnKey key) {
        if (key != null) {
            expectedColumns.add(key);
        }
    }

    public void markStamped(ColumnKey key) {
        if (key != null) {
            stampedColumns.add(key);
        }
    }

    public boolean isStamped(ColumnKey key) {
        return key != null && stampedColumns.contains(key);
    }

    public int expectedColumnCount() {
        return expectedColumns.size();
    }

    public int stampedColumnCount() {
        return stampedColumns.size();
    }

    public int missingColumnCount() {
        int missing = 0;
        for (ColumnKey key : expectedColumns) {
            if (!stampedColumns.contains(key)) {
                missing++;
            }
        }
        return missing;
    }

    public Set<ColumnKey> expectedColumns() {
        return Set.copyOf(expectedColumns);
    }

    public Set<ColumnKey> stampedColumns() {
        return Set.copyOf(stampedColumns);
    }
}
