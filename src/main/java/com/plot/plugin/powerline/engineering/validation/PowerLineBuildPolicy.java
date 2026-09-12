package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 落地前工程检查阻断策略。 */
public final class PowerLineBuildPolicy {
    private PowerLineBuildPolicy() {
    }

    public static boolean hasBlockingIssues(
            PowerLineFootprint line,
            PowerLineValidationReport engineering,
            PowerLineValidationReport terrain) {
        if (line == null) {
            return false;
        }
        if (line.isLineChecksEnabled() && engineering != null && engineering.errorCount() > 0) {
            return true;
        }
        return line.isTerrainAvoidanceEnabled() && terrain != null && terrain.errorCount() > 0;
    }

    public static int blockingErrorCount(
            PowerLineFootprint line,
            PowerLineValidationReport engineering,
            PowerLineValidationReport terrain) {
        if (line == null) {
            return 0;
        }
        int count = 0;
        if (line.isLineChecksEnabled() && engineering != null) {
            count += engineering.errorCount();
        }
        if (line.isTerrainAvoidanceEnabled() && terrain != null) {
            count += terrain.errorCount();
        }
        return count;
    }
}
