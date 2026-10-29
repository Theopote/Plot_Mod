package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoadStyleCatalogPresetDimensionsTest {

    private record DimExpectation(int lanes, int carriageway, int outerBand, int totalHard) {
    }

    private static final Map<String, DimExpectation> EXPECTED = Map.ofEntries(
        Map.entry("path", new DimExpectation(1, 3, 0, 3)),
        Map.entry("village_road", new DimExpectation(1, 5, 0, 5)),
        Map.entry("residential", new DimExpectation(2, 6, 1, 8)),
        Map.entry("city_street", new DimExpectation(2, 7, 1, 9)),
        Map.entry("avenue", new DimExpectation(2, 11, 2, 15)),
        Map.entry("boulevard", new DimExpectation(2, 8, 2, 12)),
        Map.entry("highway", new DimExpectation(2, 11, 2, 15)),
        Map.entry("railway", new DimExpectation(1, 5, 1, 7)),
        Map.entry("mountain", new DimExpectation(2, 6, 1, 8)),
        Map.entry("dirt_road", new DimExpectation(1, 5, 1, 7)),
        Map.entry("medieval_road", new DimExpectation(1, 5, 1, 7)),
        Map.entry("cyberpunk_street", new DimExpectation(2, 8, 3, 14)),
        Map.entry("city_main", new DimExpectation(2, 10, 2, 14)),
        Map.entry("country_road", new DimExpectation(1, 5, 1, 7)),
        Map.entry("industrial", new DimExpectation(2, 7, 2, 11)),
        Map.entry("park", new DimExpectation(1, 4, 1, 6))
    );

    @Test
    void builtinStylesMatchGoldenDimensions() {
        RoadSystemConfig defaults = new RoadSystemConfig("preset-dims");
        for (RoadStyle style : RoadStyleCatalog.defaultStyles()) {
            DimExpectation expected = EXPECTED.get(style.id);
            assertNotNull(expected, "missing golden expectation for " + style.id);
            ResolvedCrossSection resolved = ResolvedCrossSection.resolve(style.toCrossSection(), defaults);
            assertEquals(expected.lanes(), resolved.laneCount, style.id + " lanes");
            assertEquals(expected.carriageway(), resolved.carriagewayWidth, style.id + " carriageway");
            assertEquals(expected.outerBand(), resolved.outerBandBlockCount(), style.id + " outerBand");
            assertEquals(
                expected.totalHard(),
                resolved.totalHardSurfaceWidthBlocks(),
                style.id + " totalHard");
        }
    }
}
