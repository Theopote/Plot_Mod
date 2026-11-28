package com.plot.plugin.road.station;

/** Refuse signal for lossless station migration helpers (propagates to edit fail-closed paths). */
public final class StationMigrationRefuseException extends RuntimeException {
    public StationMigrationRefuseException(String message) {
        super(message != null ? message : "station migration refused");
    }
}
