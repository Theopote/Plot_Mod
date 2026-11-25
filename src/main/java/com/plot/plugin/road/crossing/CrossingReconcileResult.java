package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionResult;

/** Crossing 注册表 reconcile 变更摘要。 */
public record CrossingReconcileResult(
        IntersectionResult result,
        int added,
        int removed,
        int updated,
        int ambiguous) {

    public CrossingReconcileResult(
            IntersectionResult result,
            int added,
            int removed,
            int updated) {
        this(result, added, removed, updated, 0);
    }

    public boolean changed() {
        return added > 0 || removed > 0 || updated > 0;
    }

    public boolean hasAmbiguity() {
        return ambiguous > 0;
    }

    public boolean isPending() {
        return hasAmbiguity() || result == IntersectionResult.INCOMPLETE;
    }
}
