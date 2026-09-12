package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;
import com.plot.plugin.powerline.ui.PowerLinePreviewKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PowerLine Stabilization 验收测试：Preview → Edit → Build → Undo 闭环。
 */
class PowerLineStabilizationTest {

    @Test
    void previewForLineAIsInvalidWhenLineBIsSelected() {
        PowerLineFootprint lineA = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineFootprint lineB = new PowerLineFootprint(List.of(new Vec2d(0, 5), new Vec2d(10, 5)));
        PowerLineDesignProject designs = new PowerLineDesignProject();

        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(lineA, designs);

        assertTrue(previewKey.matches(lineA, designs));
        assertFalse(previewKey.matches(lineB, designs));
    }

    @Test
    void previewInvalidAfterGenerationParameterChange() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);

        line.setSagRatio(0.25);
        assertFalse(previewKey.matches(line, designs));
    }

    @Test
    void previewInvalidAfterDesignProjectChange() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);

        designs.addDesign(PoleDesignCatalog.simpleWoodPole().copy());
        assertFalse(previewKey.matches(line, designs));
    }

    @Test
    void poleHeightChangeDoesNotInvalidatePreviewWhenDesignSelected() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);

        line.setPoleHeight(99.0);
        assertTrue(previewKey.matches(line, designs));
    }

    @Test
    void openBezierSelectionIsAdoptable() {
        List<com.plot.core.model.Shape> selection = List.of(sampleBezier());
        PowerLinePathSelectionAnalysis analysis = PowerLinePathSelectionAnalysis.analyze(selection);

        assertTrue(analysis.hasCanvasSelection());
        assertTrue(analysis.canAdopt());
        assertEquals(1, analysis.adoptable().size());
    }

    @Test
    void undoRestoresUiEditBeforeMutation() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setMaxPoleSpacing(20.0);
        project.addLine(line);

        history.push(project);
        line.setMaxPoleSpacing(40.0);

        PowerLineFootprint restored = history.undo(project).getLines().get(line.getId());
        assertEquals(20.0, restored.getMaxPoleSpacing(), 1e-6);
    }

    @Test
    void undoRestoresPoleDesignSelection() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        project.addLine(line);

        history.push(project);
        line.setPoleDesignId(PoleDesignCatalog.LATTICE_STEEL_TOWER_ID);

        assertNull(history.undo(project).getLines().get(line.getId()).getPoleDesignId());
    }

    @Test
    void wireSampleCountUsesSegmentPlusOneForFractionalSpan() {
        assertEquals(22, PowerLineWireRasterizer.computeWireSampleCount(20.1, 1));
        assertEquals(21, PowerLineWireRasterizer.computeWireSampleCount(20.0, 1));
    }

    @Test
    void twentyBlockSpanHasNoWireGaps() {
        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(
            WireTestSupport.horizontalLine(20.0));
        WireTestSupport.assertHorizontalWireCoversX(result, 64 + 10, 0, 20);
    }

    @Test
    void twentyPointOneBlockSpanHasNoWireGaps() {
        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(
            WireTestSupport.horizontalLine(20.1));
        WireTestSupport.assertHorizontalWireCoversX(result, 64 + 10, 0, 20);
    }

    @Test
    void fortyFiveDegreeSpanHasNoWireGaps() {
        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(
            WireTestSupport.diagonalLine45(20.0));
        WireTestSupport.assertWireAlongPlanLine(
            result,
            64 + 10,
            new Vec2d(0, 0),
            new Vec2d(20, 20));
    }

    private static BezierCurveShape sampleBezier() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});
        return new BezierCurveShape(anchors, controls, false);
    }
}
