package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.ManualElevationOrigin;

/**
 * Shared elevation-lock yield rules for first-pass node resolution and second-pass endpoints.
 * <p>
 * USER is hard. PROFILE_PROPAGATED / CROSSING / GRADE_SEPARATED (when not on the elevated GS
 * path) yield to a valid design consensus or first-pass network Y. AUTO terrain is soft only.
 */
public final class ElevationLockAuthority {

    private ElevationLockAuthority() {
    }

    public static boolean isHardUserLock(ManualElevationOrigin origin) {
        return origin == ManualElevationOrigin.USER;
    }

    /**
     * Derived locks that must not override a valid EDITED/FLAT design Y or first-pass network Y.
     */
    public static boolean yieldsToDesignOrNetwork(ManualElevationOrigin origin) {
        return origin == ManualElevationOrigin.PROFILE_PROPAGATED
            || origin == ManualElevationOrigin.CROSSING
            || origin == ManualElevationOrigin.GRADE_SEPARATED;
    }
}
