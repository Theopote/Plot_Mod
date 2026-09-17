package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import net.minecraft.util.math.BlockPos;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternGeometryUtilsTest {

    @Test
    void detectsEdgeOverlapWithoutCentroidInside() {
        PatternProject project = new PatternProject();
        project.addFootprint(new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4))));

        List<Vec2d> overlapping = List.of(
            new Vec2d(3, 0),
            new Vec2d(7, 0),
            new Vec2d(7, 4),
            new Vec2d(3, 4));

        assertTrue(PatternGeometryUtils.overlapsExistingFootprint(overlapping, project));
    }

    @Test
    void ignoresSeparatedRegions() {
        PatternProject project = new PatternProject();
        project.addFootprint(new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4))));

        List<Vec2d> separated = List.of(
            new Vec2d(10, 0),
            new Vec2d(14, 0),
            new Vec2d(14, 4),
            new Vec2d(10, 4));

        assertFalse(PatternGeometryUtils.overlapsExistingFootprint(separated, project));
    }

    @Test
    void acceptsHoleInsideOuter() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10));
        List<Vec2d> hole = List.of(
            new Vec2d(3, 3),
            new Vec2d(7, 3),
            new Vec2d(7, 7),
            new Vec2d(3, 7));

        assertEquals(
            PatternGeometryUtils.HoleAddIssue.NONE,
            PatternGeometryUtils.validateHoleForFootprint(outer, List.of(), hole));
    }

    @Test
    void rejectsHoleOutsideOuter() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4));
        List<Vec2d> hole = List.of(
            new Vec2d(2, 2),
            new Vec2d(6, 2),
            new Vec2d(6, 3),
            new Vec2d(2, 3));

        assertEquals(
            PatternGeometryUtils.HoleAddIssue.OUTSIDE_OUTER,
            PatternGeometryUtils.validateHoleForFootprint(outer, List.of(), hole));
    }

    @Test
    void rejectsHoleCoveringMostOfOuter() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4));
        List<Vec2d> hole = List.of(
            new Vec2d(0.05, 0.05),
            new Vec2d(3.95, 0.05),
            new Vec2d(3.95, 3.95),
            new Vec2d(0.05, 3.95));

        assertEquals(
            PatternGeometryUtils.HoleAddIssue.TOO_LARGE,
            PatternGeometryUtils.validateHoleForFootprint(outer, List.of(), hole));
    }

    @Test
    void groupsInnerRingAsHoleWhenAdoptingTogether() {
        Shape outer = new RectangleShape(new Vec2d(0, 0), 10, 10, 0);
        Shape inner = new RectangleShape(new Vec2d(3, 3), 4, 4, 0);

        List<PatternGeometryUtils.AdoptedRegionGroup> groups =
            PatternGeometryUtils.groupAdoptableRegionsWithHoles(List.of(outer, inner));

        assertEquals(1, groups.size());
        assertEquals(1, groups.getFirst().holes().size());
    }

    @Test
    void keepsSeparateRegionsWhenNotNested() {
        Shape left = new RectangleShape(new Vec2d(0, 0), 4, 4, 0);
        Shape right = new RectangleShape(new Vec2d(10, 0), 4, 4, 0);

        List<PatternGeometryUtils.AdoptedRegionGroup> groups =
            PatternGeometryUtils.groupAdoptableRegionsWithHoles(List.of(left, right));

        assertEquals(2, groups.size());
        assertTrue(groups.stream().allMatch(group -> group.holes().isEmpty()));
    }

    @Test
    void assignsMultipleHolesToSameOuter() {
        Shape outer = new RectangleShape(new Vec2d(0, 0), 20, 20, 0);
        Shape holeA = new RectangleShape(new Vec2d(2, 2), 4, 4, 0);
        Shape holeB = new RectangleShape(new Vec2d(12, 12), 4, 4, 0);

        List<PatternGeometryUtils.AdoptedRegionGroup> groups =
            PatternGeometryUtils.groupAdoptableRegionsWithHoles(List.of(outer, holeA, holeB));

        assertEquals(1, groups.size());
        assertEquals(2, groups.getFirst().holes().size());
    }

    @Test
    void convertsNegativeCellCentersToTheContainingMinecraftBlock() {
        BlockPos pos = PatternGeometryUtils.canvasToBlockXZ(new Vec2d(-0.5, -1.5), null);
        assertEquals(-1, pos.getX());
        assertEquals(-2, pos.getZ());
    }
}
