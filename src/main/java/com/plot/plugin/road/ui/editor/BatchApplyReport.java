package com.plot.plugin.road.ui.editor;

/** 批量桩号数据写回摘要（用于确认后状态提示）。 */
public record BatchApplyReport(
        int appliedTargets,
        int skippedTargets,
        int droppedItems,
        int clampedItems) {
    public static BatchApplyReport empty() {
        return new BatchApplyReport(0, 0, 0, 0);
    }

    public boolean hasWarnings() {
        return skippedTargets > 0 || droppedItems > 0 || clampedItems > 0;
    }

    public boolean isLossy() {
        return hasWarnings();
    }
}
