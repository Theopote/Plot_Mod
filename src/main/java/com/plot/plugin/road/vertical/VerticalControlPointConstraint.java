package com.plot.plugin.road.vertical;

/** Persistent reason why an automatic profile operation must preserve a PVI. */
public enum VerticalControlPointConstraint {
    FREE,
    USER_LOCKED,
    JUNCTION_FIXED,
    ENDPOINT_FIXED;

    public boolean isAutomaticallyMovable() {
        return this == FREE;
    }
}
