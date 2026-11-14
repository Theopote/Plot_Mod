package com.plot.plugin.road.pipeline.corridor;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JunctionElevationFieldTest {
    @Test
    void continuousRelaxationRemovesQuantizationLockedBumpWithoutMovingBoundary() {
        var heights = new LinkedHashMap<ColumnKey, Integer>();
        var center = new ColumnKey(0, 0);
        heights.put(center, 65);
        heights.put(new ColumnKey(-1, 0), 64);
        heights.put(new ColumnKey(1, 0), 64);
        heights.put(new ColumnKey(0, -1), 64);
        heights.put(new ColumnKey(0, 1), 64);
        JunctionElevationField.relaxContinuousFillet(heights, Set.of(center));
        heights.values().forEach(y -> assertEquals(64, y));
    }
}
