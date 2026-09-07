package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.TowerLocalPoint;

/** 塔体角点与局部几何辅助。 */
public final class TowerStructureGeometry {
    public static final int CORNER_COUNT = 4;

    private TowerStructureGeometry() {
    }

    public static TowerLocalPoint cornerPoint(TowerStation station, int cornerIndex) {
        double lateral = cornerLateral(cornerIndex, station.getHalfWidth());
        double longitudinal = cornerLongitudinal(cornerIndex, station.getHalfDepth());
        return TowerLocalPoint.of(lateral, station.getHeight(), longitudinal);
    }

    public static double cornerLateral(int cornerIndex, double halfWidth) {
        return (cornerIndex == 0 || cornerIndex == 3) ? -halfWidth : halfWidth;
    }

    public static double cornerLongitudinal(int cornerIndex, double halfDepth) {
        return (cornerIndex == 0 || cornerIndex == 1) ? -halfDepth : halfDepth;
    }

    /** 前面（负 forward）两角：0,1；后面：2,3；右侧：1,2；左侧：0,3。 */
    public static int[] frontCorners() {
        return new int[] {0, 1};
    }

    public static int[] backCorners() {
        return new int[] {2, 3};
    }

    public static int[] rightCorners() {
        return new int[] {1, 2};
    }

    public static int[] leftCorners() {
        return new int[] {0, 3};
    }
}
