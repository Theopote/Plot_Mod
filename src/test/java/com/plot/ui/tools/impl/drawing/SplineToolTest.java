package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.impl.drawing.strategy.ThroughPointsSplineStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineToolTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;

    private AppState appState;
    private SplineTool tool;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        tool = new SplineTool(appState, null);
        tool.setCamera(new CanvasCamera());
    }

    @Test
    void updateConfigSwitchesGenerationMode() {
        tool.updateConfig(SplineTool.CONFIG_KEY_MODE, SplineTool.SplineMode.CONTROL_POLYGON.getId());

        assertEquals(
                SplineTool.SplineMode.CONTROL_POLYGON,
                tool.getSplineConfig().getCurrentMode());
    }

    @Test
    void closeSplineGeneratesClosedCurveWithThreeThroughPoints() throws Exception {
        tool.updateConfig(SplineTool.CONFIG_KEY_MODE, SplineTool.SplineMode.THROUGH_POINTS.getId());
        int before = appState.getShapes().size();
        injectControlPoints(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10));

        boolean handled = tool.onKeyTyped('c');

        assertTrue(handled);
        assertEquals(before + 1, appState.getShapes().size());
        BezierCurveShape committed = appState.getShapes().stream()
                .filter(BezierCurveShape.class::isInstance)
                .map(BezierCurveShape.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertTrue(committed.isClosed());
        assertEquals(3, committed.getSegmentCount());
        assertTrue(getControlPoints(tool).isEmpty());
    }

    @Test
    void closedCurveStrategyDoesNotExceedControlPairCount() {
        ThroughPointsSplineStrategy strategy = new ThroughPointsSplineStrategy();
        List<Vec2d> triangle = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(5, 10));

        BezierCurveShape closed = strategy.generateClosedCurve(
                triangle,
                tool.getSplineConfig());

        assertNotNull(closed);
        assertTrue(closed.isClosed());
        assertEquals(3, closed.getAnchorPoints().stream().distinct().count());
        BezierCurveShape open = strategy.generateCurve(triangle, tool.getSplineConfig());
        assertNotNull(open);
        // 开放曲线 n 个锚点对应 n-1 段；闭合曲线额外追加 1 段
        assertEquals(triangle.size() - 1, open.getSegmentCount());
        assertEquals(open.getSegmentCount() + 1, closed.getSegmentCount());
    }

    @Test
    void splineInteractionCompletesOpenCurveAfterMultiClick() {
        tool.updateConfig(SplineTool.CONFIG_KEY_MODE, SplineTool.SplineMode.THROUGH_POINTS.getId());
        int before = appState.getShapes().size();

        assertTrue(tool.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT));
        assertTrue(tool.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT));
        assertTrue(tool.onMouseDown(new Vec2d(10, 10), MOUSE_LEFT));
        assertTrue(tool.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT));

        assertEquals(before + 1, appState.getShapes().size());
        Shape lastShape = appState.getShapes().getLast();
        assertInstanceOf(BezierCurveShape.class, lastShape);
        BezierCurveShape curve = (BezierCurveShape) lastShape;
        assertFalse(curve.isClosed());
        assertEquals(2, curve.getSegmentCount());
    }

    @Test
    void closeKeyDoesNotCommitWhenControlPointsAreInsufficient() throws Exception {
        tool.updateConfig(SplineTool.CONFIG_KEY_MODE, SplineTool.SplineMode.THROUGH_POINTS.getId());
        int before = appState.getShapes().size();
        injectControlPoints(new Vec2d(0, 0), new Vec2d(10, 0));

        boolean handled = tool.onKeyTyped('c');

        assertFalse(handled);
        assertEquals(before, appState.getShapes().size());
        assertEquals(2, getControlPoints(tool).size());
    }

    @Test
    void modeSwitchPreservesExistingControlPoints() throws Exception {
        injectControlPoints(
                new Vec2d(0, 0),
                new Vec2d(5, 5),
                new Vec2d(10, 0));

        tool.updateConfig(SplineTool.CONFIG_KEY_MODE, SplineTool.SplineMode.CONTROL_POLYGON.getId());

        List<Vec2d> preserved = getControlPoints(tool);
        assertEquals(3, preserved.size());
        assertEquals(0.0, preserved.getFirst().x, 1e-6);
        assertEquals(10.0, preserved.getLast().x, 1e-6);
        assertEquals(SplineTool.SplineMode.CONTROL_POLYGON, tool.getSplineConfig().getCurrentMode());
    }

    private void injectControlPoints(Vec2d... points) throws Exception {
        Field field = SplineTool.class.getDeclaredField("controlPoints");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Vec2d> controlPoints = (List<Vec2d>) field.get(tool);
        controlPoints.clear();
        controlPoints.addAll(List.of(points));
    }

    @SuppressWarnings("unchecked")
    private static List<Vec2d> getControlPoints(SplineTool tool) throws Exception {
        Field field = SplineTool.class.getDeclaredField("controlPoints");
        field.setAccessible(true);
        return (List<Vec2d>) field.get(tool);
    }
}
