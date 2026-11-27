package com.plot.plugin.road.station;

/** Internal refuse signal for lossless station migration helpers. */
final class StationMigrationRefuseException extends RuntimeException {
    StationMigrationRefuseException(String message) {
        super(message != null ? message : "station migration refused");
    }
}
