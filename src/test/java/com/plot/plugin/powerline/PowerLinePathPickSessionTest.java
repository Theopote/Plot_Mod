package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathPickSessionTest {

    private PowerLinePathPickSession session;

    @BeforeEach
    void setUp() {
        session = new PowerLinePathPickSession();
        session.begin();
    }

    @Test
    void hintKeyForEmptySelectionUsesActiveKey() {
        assertEquals(
            "status.plot.powerline.pick_path_active",
            session.hintKeyForCurrentSelection(List.of()));
    }

    @Test
    void hintKeyUsesAccumulatedCountWhenPresent() {
        LineShape path = line(0, 0, 10, 0);
        session.mergeSelectionChange(List.of(), List.of(path), false);

        assertEquals(
            "status.plot.powerline.pick_path_right_click",
            session.hintKeyForCurrentSelection(List.of()));
    }

    @Test
    void normalClickUnionsAddedPathsWithoutRemovingExistingAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA), false);
        session.mergeSelectionChange(List.of(pathA), List.of(pathB), false);

        assertEquals(List.of(pathA.getId(), pathB.getId()), session.accumulatedPathIds());
    }

    @Test
    void ctrlClickAddsOnlyDeltaWithoutTogglingExistingAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA), false);
        session.mergeSelectionChange(List.of(pathA), List.of(pathA, pathB), true);

        assertEquals(List.of(pathA.getId(), pathB.getId()), session.accumulatedPathIds());
    }

    @Test
    void normalClickOnBlankClearsAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA, pathB), false);
        session.mergeSelectionChange(List.of(pathA, pathB), List.of(), false);

        assertTrue(session.accumulatedPathIds().isEmpty());
    }

    @Test
    void ctrlClickRemovesDeselectedPathFromAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA), false);
        session.mergeSelectionChange(List.of(pathA), List.of(), true);

        assertTrue(session.accumulatedPathIds().isEmpty());
    }

    @Test
    void ctrlDeselectOnePathKeepsOthersInAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA), false);
        session.mergeSelectionChange(List.of(pathA), List.of(pathA, pathB), false);
        session.mergeSelectionChange(List.of(pathA, pathB), List.of(pathB), true);

        assertEquals(List.of(pathB.getId()), session.accumulatedPathIds());
    }

    @Test
    void boxSelectThenNormalClickUnionsNewPathWithoutDroppingBoxSelection() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);
        LineShape pathC = line(40, 0, 50, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA, pathB), false);
        session.mergeSelectionChange(List.of(pathA, pathB), List.of(pathC), false);

        assertEquals(List.of(pathA.getId(), pathB.getId(), pathC.getId()), session.accumulatedPathIds());
    }

    @Test
    void ctrlClickTogglesNewlyAddedPathOffWhenNotYetAccumulated() {
        LineShape pathA = line(0, 0, 10, 0);
        LineShape pathB = line(20, 0, 30, 0);

        session.mergeSelectionChange(List.of(), List.of(pathA), false);
        session.mergeSelectionChange(List.of(pathA), List.of(pathA, pathB), true);
        session.mergeSelectionChange(List.of(pathA, pathB), List.of(pathA), true);

        assertEquals(List.of(pathA.getId()), session.accumulatedPathIds());
    }

    private static LineShape line(double x1, double y1, double x2, double y2) {
        return new LineShape(new Vec2d(x1, y1), new Vec2d(x2, y2));
    }
}
