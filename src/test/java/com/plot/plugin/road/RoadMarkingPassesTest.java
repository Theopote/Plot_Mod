package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadMarkingPassesTest {

    @Test
    void fromCrossSectionBuildsCenterAndLaneDividerPasses() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        section.getMarkings().setLaneDividers(true);
        section.getCarriageway().setLaneCount(3);
        section.getCarriageway().setWidth(9);

        ResolvedCrossSection resolved = section.resolve(config);
        var passes = RoadMarkingPasses.fromCrossSection(resolved);

        assertTrue(passes.size() >= 3);
        assertTrue(passes.stream().anyMatch(pass -> pass.solid() && pass.offset() < 0));
        assertTrue(passes.stream().anyMatch(pass -> pass.solid() && pass.offset() > 0));
        assertTrue(passes.stream().anyMatch(pass -> !pass.solid()));
    }

    @Test
    void hasAnyMarkingsDetectsCenterLineAndLaneDividers() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        RoadCrossSection none = new RoadCrossSection();
        none.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        none.getMarkings().setLaneDividers(false);
        assertFalse(RoadMarkingPasses.hasAnyMarkings(none.resolve(config)));

        RoadCrossSection dashed = new RoadCrossSection();
        dashed.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        assertTrue(RoadMarkingPasses.hasAnyMarkings(dashed.resolve(config)));

        RoadCrossSection dividers = new RoadCrossSection();
        dividers.getMarkings().setLaneDividers(true);
        dividers.getCarriageway().setLaneCount(2);
        assertTrue(RoadMarkingPasses.hasAnyMarkings(dividers.resolve(config)));
    }

    @Test
    void singleDashedProducesOneDashedPass() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);

        var passes = RoadMarkingPasses.fromCrossSection(section.resolve(config));
        assertEquals(1, passes.size());
        assertEquals(0.0, passes.getFirst().offset(), 1e-6);
        assertFalse(passes.getFirst().solid());
    }
}
