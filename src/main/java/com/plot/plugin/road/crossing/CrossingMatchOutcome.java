package com.plot.plugin.road.crossing;

/**
 * Result of matching a newly detected crossing against the existing registry.
 */
public enum CrossingMatchOutcome {
    /** Unique inheritance target found. */
    MATCHED,
    /** No plausible candidate — treat as new detection or truly removed existing. */
    NO_MATCH,
    /** Multiple plausible candidates — must not silently delete or swap design. */
    AMBIGUOUS
}
