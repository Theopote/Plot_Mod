package com.plot.plugin.road.station;

/**
 * Result of VA/VCS/facility migration for split/merge/delete.
 * Distinguishes success (with optional HA refit) from refuse/failure.
 */
public record StationMigrationResult(
        boolean success,
        boolean requiresHaRefit,
        String diagnostic) {

    public static StationMigrationResult ok(boolean requiresHaRefit) {
        return new StationMigrationResult(true, requiresHaRefit, "");
    }

    public static StationMigrationResult rejected(String diagnostic) {
        return new StationMigrationResult(
            false, false, diagnostic != null ? diagnostic : "rejected");
    }

    public boolean isSuccess() {
        return success;
    }
}
