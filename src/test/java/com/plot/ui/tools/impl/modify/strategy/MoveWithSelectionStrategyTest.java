package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.MoveHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.MoveWithSelectionStrategy.StrategyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveWithSelectionStrategyTest {

    private static final int ESC_KEY = 27;

    private AppState appState;
    private MoveWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new MoveWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsToSelectionMode() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void completeMoveCreatesModifyCommand() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeMove",
                new Class<?>[] {Vec2d.class, IModifyStrategy.ModifyToolContext.class},
                new Vec2d(5, 0),
                context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertTrue(strategy.getModifyCommand() != null);
        // TODO: 断言 MoveCommand 平移向量 (5,0)
    }

    @Test
    void completeMoveIgnoresTinyDisplacement() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeMove",
                new Class<?>[] {Vec2d.class, IModifyStrategy.ModifyToolContext.class},
                new Vec2d(0.05, 0),
                context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertNull(strategy.getModifyCommand());
    }

    // TODO: 右键 SELECTION→MOVE 模式切换
    // TODO: Shift 正交约束预览
    // TODO: Ctrl 复制移动 → CopyMoveCommand

    private void injectActiveMoveState(Shape shape, Vec2d basePoint) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.MoveWithSelectionStrategy$StrategyMode",
                        "MOVE"));
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "hasBasePoint", true);
        ModifyStrategyReflections.setField(strategy, "moveStartPoint", basePoint);
        ModifyStrategyReflections.setField(strategy, "moveCurrentPoint", basePoint);
        ModifyStrategyReflections.setField(strategy, "moveHandler", new MoveHandler(appState));
        ModifyStrategyReflections.setField(strategy, "moveParameters",
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());

        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }
}
