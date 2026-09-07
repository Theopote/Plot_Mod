package com.plot.plugin.powerline;

/** 塔体局部坐标点（相对杆塔中心 datum）。 */
public record TowerLocalPoint(
        double lateral,
        double vertical,
        double longitudinal) {

    public static TowerLocalPoint of(double lateral, double vertical, double longitudinal) {
        return new TowerLocalPoint(lateral, vertical, longitudinal);
    }
}
