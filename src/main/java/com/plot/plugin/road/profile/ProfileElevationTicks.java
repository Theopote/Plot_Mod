package com.plot.plugin.road.profile;

import java.util.ArrayList;
import java.util.List;

/** 纵断面 Y 轴与 X 桩号刻度。 */
public final class ProfileElevationTicks {

    private ProfileElevationTicks() {
    }

    public static double niceElevationStep(double span) {
        if (span <= 10.0) {
            return 2.0;
        }
        if (span <= 25.0) {
            return 5.0;
        }
        if (span <= 50.0) {
            return 10.0;
        }
        return 20.0;
    }

    public static List<Double> elevationTicks(double minElevation, double maxElevation) {
        double step = niceElevationStep(maxElevation - minElevation);
        double start = Math.ceil(minElevation / step) * step;
        List<Double> ticks = new ArrayList<>();
        for (double value = start; value <= maxElevation + 1e-6; value += step) {
            ticks.add(value);
        }
        if (ticks.isEmpty()) {
            ticks.add(minElevation);
            ticks.add(maxElevation);
        }
        return ticks;
    }

    public static List<Double> stationTicks(double totalStation, int maxTicks) {
        if (totalStation <= 1e-6) {
            return List.of(0.0);
        }
        int count = Math.max(2, Math.min(maxTicks, 5));
        List<Double> ticks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ticks.add(totalStation * i / (count - 1));
        }
        return ticks;
    }

    public static double displayMargin(double minElevation, double maxElevation) {
        double span = maxElevation - minElevation;
        return Math.max(2.0, span * 0.10);
    }
}
