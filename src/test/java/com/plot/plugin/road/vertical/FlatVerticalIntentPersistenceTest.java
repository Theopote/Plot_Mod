package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlatVerticalIntentPersistenceTest {

    @Test
    void flatIntentRoundTripsWithIntersectionOverrides() {
        FlatVerticalIntent original = new FlatVerticalIntent(70.0, Map.of(
            "node-a", 74.0,
            "node-b", 68.0));

        FlatVerticalIntentPersistence.FlatVerticalIntentData data =
            FlatVerticalIntentPersistence.toData(original);
        FlatVerticalIntent restored = FlatVerticalIntentPersistence.fromData(data);

        assertNotNull(restored);
        assertEquals(70.0, restored.getBaseElevation(), 1e-6);
        assertEquals(74.0, restored.getIntersectionOverride("node-a"), 1e-6);
        assertEquals(68.0, restored.getIntersectionOverride("node-b"), 1e-6);
    }

    @Test
    void nullIntentRoundTripsAsNull() {
        assertNull(FlatVerticalIntentPersistence.toData(null));
        assertNull(FlatVerticalIntentPersistence.fromData(null));
    }
}
