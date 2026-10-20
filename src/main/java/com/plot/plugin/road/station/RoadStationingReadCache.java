package com.plot.plugin.road.station;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Session-owned memoization for a generation snapshot whose topology, centerlines
 * and loop seam have already been prepared. Never activate for an editable network.
 * Activation is thread-local and bounded by a generation tick; terrain and profiles
 * are not cached. The owning preview session is used by one thread at a time.
 */
public final class RoadStationingReadCache {
    private record Key(String roadId, boolean loopSeam) { }
    private static final ThreadLocal<RoadStationingReadCache> ACTIVE = new ThreadLocal<>();
    private final RoadNetwork network;
    private final Map<Key, List<OrientedRoadSegment>> segments = new HashMap<>();
    private final Map<String, Boolean> stationable = new HashMap<>();

    public RoadStationingReadCache(RoadNetwork frozenNetwork) {
        network = frozenNetwork;
    }

    static RoadStationingReadCache activeFor(RoadNetwork network) {
        var cache = ACTIVE.get();
        return cache != null && cache.network == network ? cache : null;
    }

    /** A preview tick has already materialized this snapshot's horizontal geometry. */
    public static boolean isActiveFor(RoadNetwork network) {
        return activeFor(network) != null;
    }

    List<OrientedRoadSegment> segments(String roadId, boolean loopSeam,
            Supplier<List<OrientedRoadSegment>> build) {
        var key = new Key(roadId, loopSeam);
        var value = segments.get(key);
        if (value == null) {
            // Building a LOOP traversal can request its base traversal recursively.
            // Avoid computeIfAbsent: the nested request legitimately adds another key.
            value = List.copyOf(build.get());
            segments.put(key, value);
        }
        return value;
    }

    boolean stationable(String roadId, BooleanSupplier evaluate) {
        var value = stationable.get(roadId);
        if (value == null) {
            value = evaluate.getAsBoolean();
            stationable.put(roadId, value);
        }
        return value;
    }

    public Scope activate() {
        var scope = new Scope(ACTIVE.get());
        ACTIVE.set(this);
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final RoadStationingReadCache previous;
        private boolean closed;

        private Scope(RoadStationingReadCache previous) { this.previous = previous; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
