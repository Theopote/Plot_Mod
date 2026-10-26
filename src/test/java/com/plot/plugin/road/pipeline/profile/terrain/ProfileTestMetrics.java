package com.plot.plugin.road.pipeline.profile.terrain;

import java.util.List;

/** Measurements shared by the ordinary terrain-profile regression tests. */
final class ProfileTestMetrics {
    private ProfileTestMetrics() { }

    static int countGradeSignReversals(double[] elevations, List<Double> distances) {
        int previous = 0;
        int reversals = 0;
        for (int i = 0; i < distances.size(); i++) {
            double rise = GradeLimitedProfileSolver.gradeAtSegment(elevations[i], elevations[i + 1], distances.get(i));
            int sign = Math.abs(rise) < 1e-9 ? 0 : (rise > 0.0 ? 1 : -1);
            if (sign == 0) continue;
            if (previous != 0 && previous != sign) reversals++;
            previous = sign;
        }
        return reversals;
    }
}
