package com.plot.plugin.road.pipeline.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Thread-local holder for the most recent profile solve diagnostics. */
public final class ProfileSolveDiagnosticsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileSolveDiagnosticsCollector.class);
    private static final ThreadLocal<ProfileSolveDiagnostics> LAST = new ThreadLocal<>();

    private ProfileSolveDiagnosticsCollector() {
    }

    public static void record(ProfileSolveDiagnostics diagnostics) {
        if (diagnostics == null) {
            return;
        }
        LAST.set(diagnostics);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "profile solve roadId={} requestedSource={} resolvedAuthority={} "
                    + "editedDetached={} retainedManualLocks={} retainedCrossingLocks={} "
                    + "clearedPropagatedLocks={} "
                    + "constraintsOk={}",
                diagnostics.roadId(),
                diagnostics.requestedSource(),
                diagnostics.resolvedAuthority(),
                diagnostics.editedAlignmentDetached(),
                diagnostics.retainedManualNodeLocks(),
                diagnostics.retainedCrossingLocks(),
                diagnostics.clearedPropagatedNodeLocks(),
                diagnostics.finalConstraintsSatisfied());
        }
    }

    public static Optional<ProfileSolveDiagnostics> last() {
        return Optional.ofNullable(LAST.get());
    }

    public static void clear() {
        LAST.remove();
    }
}
