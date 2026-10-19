package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.RotateWithSelectionStrategy.StrategyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RotateWithSelectionStrategyTest {

    private static final int ESC_KEY = 27;

    private AppState appState;
    private RotateWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new RotateWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        appState.addShape(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0)));
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsStrategy() {
        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    // TODO: 注入 ROTATE 模式 + 圆心/参考点，验证 RotateCommand 角度
    // TODO: Shift 15° 步进约束
}
