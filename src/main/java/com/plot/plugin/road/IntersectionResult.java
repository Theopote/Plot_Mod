package com.plot.plugin.road;

/**
 * Result of iterative intersection detection and edge splitting.
 */
public enum IntersectionResult {
    /** All intersections were resolved within the pass limit. */
    COMPLETE,
    /** The pass limit was reached while work remained; topology may be incomplete. */
    INCOMPLETE
}
