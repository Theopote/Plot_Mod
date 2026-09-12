package com.plot.plugin.powerline.model;

/** 单塔落地结果：与世界方块登记簿一致。 */
public enum SingleTowerPlacementStatus {
    FULL,
    PARTIAL,
    FAILED;

    public static SingleTowerPlacementStatus fromCounts(int placedCount, int expectedCount) {
        if (placedCount <= 0) {
            return FAILED;
        }
        if (placedCount >= expectedCount) {
            return FULL;
        }
        return PARTIAL;
    }
}
