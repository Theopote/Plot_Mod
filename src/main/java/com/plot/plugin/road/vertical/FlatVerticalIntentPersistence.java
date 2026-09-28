package com.plot.plugin.road.vertical;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON DTO conversion for {@link FlatVerticalIntent}. */
public final class FlatVerticalIntentPersistence {

    public static final class FlatVerticalIntentData {
        public double baseElevation;
        public Map<String, Double> intersectionOverrides = new LinkedHashMap<>();
    }

    private FlatVerticalIntentPersistence() {
    }

    public static FlatVerticalIntentData toData(FlatVerticalIntent intent) {
        if (intent == null) {
            return null;
        }
        FlatVerticalIntentData data = new FlatVerticalIntentData();
        data.baseElevation = intent.getBaseElevation();
        data.intersectionOverrides.putAll(intent.getIntersectionOverrides());
        return data;
    }

    public static FlatVerticalIntent fromData(FlatVerticalIntentData data) {
        if (data == null || !Double.isFinite(data.baseElevation)) {
            return null;
        }
        return new FlatVerticalIntent(data.baseElevation, data.intersectionOverrides);
    }
}
