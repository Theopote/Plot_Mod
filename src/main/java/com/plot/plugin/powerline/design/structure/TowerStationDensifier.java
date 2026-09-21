package com.plot.plugin.powerline.design.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 为塔腿和面内斜撑插入更密的竖向截面。
 * <p>
 * 腿按插值截面分段，斜撑按同一节间画短 X，避免一个 macro bay 拉出一条超长台阶。
 * 水平环仍只放在设计稿 station / 横担高度，不在加密截面加环。
 */
public final class TowerStationDensifier {
    /** 单段塔腿最大高度（格）；超过则插入插值 station。 */
    public static final double DEFAULT_MAX_LEG_BAY_HEIGHT = 6.0;
    private static final double HEIGHT_EPSILON = 1e-3;

    private TowerStationDensifier() {
    }

    public static List<TowerStation> densifyForLegs(List<TowerStation> macroStations) {
        return densifyForLegs(macroStations, DEFAULT_MAX_LEG_BAY_HEIGHT);
    }

    public static List<TowerStation> densifyForLegs(List<TowerStation> macroStations, double maxLegBayHeight) {
        if (macroStations == null || macroStations.size() < 2 || maxLegBayHeight <= HEIGHT_EPSILON) {
            return macroStations == null ? List.of() : List.copyOf(macroStations);
        }
        List<TowerStation> sorted = macroStations.stream()
            .sorted(Comparator.comparingDouble(TowerStation::getHeight))
            .toList();
        List<TowerStation> dense = new ArrayList<>(sorted.size() * 2);
        dense.add(sorted.get(0).copy());
        for (int i = 1; i < sorted.size(); i++) {
            appendInteriorStations(dense, sorted.get(i - 1), sorted.get(i), maxLegBayHeight, i);
            dense.add(sorted.get(i).copy());
        }
        return dense;
    }

    private static void appendInteriorStations(
            List<TowerStation> dense,
            TowerStation lower,
            TowerStation upper,
            double maxLegBayHeight,
            int macroBayIndex) {
        double span = upper.getHeight() - lower.getHeight();
        if (span <= maxLegBayHeight + HEIGHT_EPSILON) {
            return;
        }
        int segmentCount = (int) Math.ceil(span / maxLegBayHeight);
        for (int segment = 1; segment < segmentCount; segment++) {
            double t = segment / (double) segmentCount;
            double height = lower.getHeight() + span * t;
            if (height - lower.getHeight() < HEIGHT_EPSILON
                    || upper.getHeight() - height < HEIGHT_EPSILON) {
                continue;
            }
            dense.add(new TowerStation(
                interiorStationId(lower.getId(), upper.getId(), segment),
                height,
                lerp(lower.getHalfWidth(), upper.getHalfWidth(), t),
                lerp(lower.getHalfDepth(), upper.getHalfDepth(), t)));
        }
    }

    private static String interiorStationId(String lowerId, String upperId, int segment) {
        return "dense_" + lowerId + "_" + upperId + "_" + segment;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
