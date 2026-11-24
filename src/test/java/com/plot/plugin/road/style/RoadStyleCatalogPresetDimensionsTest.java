package com.plot.plugin.road.style;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CarriagewayWidthRules;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStyleCatalogPresetDimensionsTest {

    private record DimExpectation(int lanes, int carriageway, int outerBand, int totalHard) {
    }

    private static final Map<String, DimExpectation> EXPECTED = Map.ofEntries(
        Map.entry("path", new DimExpectation(1, 3, 0, 3)),
        Map.entry("village_road", new DimExpectation(1, 5, 0, 5)),
        Map.entry("residential", new DimExpectation(2, 6, 1, 8)),
        Map.entry("city_street", new DimExpectation(2, 7, 1, 9)),
        Map.entry("avenue", new DimExpectation(2, 12, 2, 16)),
        Map.entry("boulevard", new DimExpectation(2, 9, 3, 15)),
        Map.entry("highway", new DimExpectation(4, 16, 2, 20)),
        Map.entry("mountain", new DimExpectation(2, 6, 1, 8)),
        Map.entry("dirt_road", new DimExpectation(1, 5, 1, 7)),
        Map.entry("medieval_road", new DimExpectation(1, 5, 1, 7)),
        Map.entry("cyberpunk_street", new DimExpectation(2, 9, 3, 15)),
        Map.entry("city_main", new DimExpectation(4, 16, 2, 20)),
        Map.entry("country_road", new DimExpectation(2, 7, 1, 9)),
        Map.entry("industrial", new DimExpectation(2, 8, 2, 12)),
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

    @Test
    void builtinMultiLaneWidthsAreSymmetric() {
        RoadSystemConfig defaults = new RoadSystemConfig("preset-sym");
        for (RoadStyle style : RoadStyleCatalog.defaultStyles()) {
            ResolvedCrossSection resolved = ResolvedCrossSection.resolve(style.toCrossSection(), defaults);
            if (resolved.laneCount < 2) {
                continue;
            }
            List<Integer> widths = resolved.laneWidths;
            assertEquals(resolved.laneCount, widths.size(), style.id + " lane width count");
            for (int i = 0; i < widths.size() / 2; i++) {
                assertEquals(
                    widths.get(i),
                    widths.get(widths.size() - 1 - i),
                    style.id + " lane " + i + " should mirror opposite lane");
            }
            int dividers = CarriagewayWidthRules.dividerBlocks(
                resolved.laneCount, resolved.centerLineStyle, resolved.laneDividers);
            assertEquals(
                0,
                (resolved.carriagewayWidth - dividers) % resolved.laneCount,
                style.id + " travel width should divide evenly across lanes");
        }
    }

    @Test
    void countryRoadDiffersFromDirtRoad() {
        RoadStyle country = RoadStyleCatalog.countryRoad();
        RoadStyle dirt = RoadStyleCatalog.dirtRoad();
        assertTrue(country.laneCount > dirt.laneCount || country.width != dirt.width);
        assertEquals("minecraft:gray_concrete", country.roadMaterial);
        assertEquals("minecraft:dirt", dirt.roadMaterial);
    }

    @Test
    void urbanPresetsBindProfessionalLampPlacement() {
        assertNotNull(RoadStyleCatalog.cityStreet().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.boulevard().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.cityMain().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.avenue().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.medievalRoad().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.cyberpunkStreet().toCrossSection().getStreetFurniture().getRoadLampPlacement());
        assertNotNull(RoadStyleCatalog.residential().toCrossSection().getStreetFurniture().getRoadLampPlacement());
    }
}
