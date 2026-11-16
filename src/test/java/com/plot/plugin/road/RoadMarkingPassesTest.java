package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        section.getCarriageway().setLaneCount(4);
        section.getCarriageway().setDefaultLaneWidth(3);

        ResolvedCrossSection resolved = section.resolve(config);
        var passes = RoadMarkingPasses.fromCrossSection(resolved);

        assertTrue(passes.size() >= 3);
        assertTrue(passes.stream().anyMatch(pass -> pass.solid() && pass.offset() < 0));
        assertTrue(passes.stream().anyMatch(pass -> pass.solid() && pass.offset() > 0));
        assertTrue(passes.stream().anyMatch(pass -> !pass.solid()));
    }

    @Test
    void twoLaneCenterLineAtZeroIsNotSkipped() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);

        ResolvedCrossSection resolved = section.resolve(config);
        assertEquals(List.of(0.0), resolved.laneDividerOffsets);
        var passes = RoadMarkingPasses.fromCrossSection(resolved);
        assertTrue(passes.stream().anyMatch(pass -> Math.abs(pass.offset()) < 1e-6));
    }

    @Test
    void medianKeepsCenterMarkingPasses() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(1);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.SINGLE_DASHED, true, 0);

        ResolvedCrossSection resolved = section.resolve(config);
        assertTrue(resolved.includeMedian);
        var passes = RoadMarkingPasses.fromCrossSection(resolved);
        assertTrue(
            passes.stream().anyMatch(pass -> Math.abs(pass.offset()) < 1e-6),
            "median must not suppress center pavement marking");
    }

    @Test
    void singleLaneHasNoMarkingPasses() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(1);
        section.getCarriageway().setDefaultLaneWidth(2);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.SINGLE_DASHED, true, 0);

        ResolvedCrossSection resolved = section.resolve(config);
        assertEquals(2, resolved.carriagewayWidth);
        assertEquals(CenterLineStyle.NONE, resolved.centerLineStyle);
        assertTrue(RoadMarkingPasses.fromCrossSection(resolved).isEmpty());
        assertFalse(RoadMarkingPasses.hasAnyMarkings(resolved));
    }

    @Test
    void hasAnyMarkingsDetectsCenterLineAndLaneDividers() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        RoadCrossSection none = new RoadCrossSection();
        none.getCarriageway().setLaneCount(2);
        none.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        none.getMarkings().setLaneDividers(false);
        assertFalse(RoadMarkingPasses.hasAnyMarkings(none.resolve(config)));

        RoadCrossSection dashed = new RoadCrossSection();
        dashed.getCarriageway().setLaneCount(2);
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
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);

        var passes = RoadMarkingPasses.fromCrossSection(section.resolve(config));
        assertEquals(1, passes.size());
        assertEquals(0.0, passes.getFirst().offset(), 1e-6);
        assertFalse(passes.getFirst().solid());
    }

    @Test
    void railProducesSolidCenterPass() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.RAIL);
        section.getMarkings().setLaneDividers(false);

        ResolvedCrossSection resolved = section.resolve(config);
        assertEquals(CenterLineStyle.RAIL, resolved.centerLineStyle);
        var passes = RoadMarkingPasses.fromCrossSection(resolved);
        assertEquals(1, passes.size());
        assertEquals(0.0, passes.getFirst().offset(), 1e-6);
        assertTrue(passes.getFirst().solid());
        assertTrue(RoadMarkingPasses.hasAnyMarkings(resolved));
    }

    @Test
    void railSleeperSpacingIsEvenSamples() {
        assertEquals(2, com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.RAIL_SLEEPER_EVERY_SAMPLES);
        assertEquals(1, com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.RAIL_SLEEPER_HALF_WIDTH);
        assertEquals(
            "minecraft:oak_planks",
            com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.RAIL_SLEEPER_MATERIAL);
        assertTrue(2 % com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.RAIL_SLEEPER_EVERY_SAMPLES == 0);
        assertFalse(1 % com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.RAIL_SLEEPER_EVERY_SAMPLES == 0);
    }

    @Test
    void resolveRailBlockIdRejectsThemeMarkingPalette() {
        assertEquals(
            "minecraft:rail",
            com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.resolveRailBlockId(
                "minecraft:white_concrete"));
        assertEquals(
            "minecraft:powered_rail",
            com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.resolveRailBlockId(
                "minecraft:powered_rail"));
        assertEquals(
            "minecraft:rail",
            com.plot.plugin.road.pipeline.marking.RoadMarkingGenerator.resolveRailBlockId(null));
    }
}
