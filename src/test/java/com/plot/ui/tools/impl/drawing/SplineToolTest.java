package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.drawing.strategy.ThroughPointsSplineStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineToolTest {

    private AppState appState;
    private SplineTool tool;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        tool = new SplineTool(appState, null);
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
        injectControlPoints(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10));

        boolean handled = tool.onKeyTyped('c');

        assertTrue(handled);
        // TODO: 断言 commit 后 AppState 新增 BezierCurveShape 且闭合
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
        // TODO: 断言 controls.size() == anchors.size() - 1（闭合段不越界）
    }

    // TODO: SplineInteractionStrategy 多点点击完成开放曲线
    // TODO: 控制点不足时 'c' 键不提交
    // TODO: 模式切换后保留/清空 controlPoints 行为

    private void injectControlPoints(Vec2d... points) throws Exception {
        Field field = SplineTool.class.getDeclaredField("controlPoints");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Vec2d> controlPoints = (List<Vec2d>) field.get(tool);
        controlPoints.clear();
        controlPoints.addAll(List.of(points));
    }
}
