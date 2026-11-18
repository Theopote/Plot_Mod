package com.plot.plugin.road.vertical;

/**
 * Optional horizontal reference lines for flat-road profile charts (current base Y vs suggested Y).
 * Derived analysis only; not persisted.
 */
public record FlatElevationProfileOverlay(Integer currentElevation, Integer suggestedElevation) {

    public static final FlatElevationProfileOverlay EMPTY = new FlatElevationProfileOverlay(null, null);

    public boolean isEmpty() {
        return !showCurrent() && !showSuggested();
    }

    /** Current flat baseline (uniform Y / flat base); always drawn when set. */
    public boolean showCurrent() {
        return currentElevation != null;
    }

    /** Optimizer recommendation, hidden once it matches the adopted base Y. */
    public boolean showSuggested() {
        return suggestedElevation != null
            && (currentElevation == null || Math.abs(suggestedElevation - currentElevation) >= 1);
    }

    public static FlatElevationProfileOverlay of(Integer currentElevation, Integer suggestedElevation) {
        if (currentElevation == null && suggestedElevation == null) {
            return EMPTY;
        }
        return new FlatElevationProfileOverlay(currentElevation, suggestedElevation);
    }
}
