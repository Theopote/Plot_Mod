package com.plot.plugin.road.crossing;

import com.plot.plugin.road.IntersectionResult;

/** Crossing 注册表 reconcile 变更摘要。 */
public record CrossingReconcileResult(
        IntersectionResult result,
        int added,
        int removed,
        int updated) {

    public boolean changed() {
        return added > 0 || removed > 0 || updated > 0;
    }
}
