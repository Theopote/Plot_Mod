package com.plot.core.command.commands;

import com.plot.core.state.AppState;
import com.plot.ui.panel.layer.LayerStructureSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerStructureEditCommandTest {

    @BeforeAll
    static void initializeLayerSystem() {
        // 生产环境由 PlotMod 启动流程初始化；独立单元测试需要显式建立该前置条件。
        AppState.getInstance().initializeLayerSystem();
    }

    @Test
    void sameStructureDetectsIdenticalSnapshots() {
        LayerStructureSnapshot first = LayerStructureSnapshot.capture();
        LayerStructureSnapshot second = LayerStructureSnapshot.capture();
        assertTrue(first.sameStructureAs(second));
    }

    @Test
    void commandUndoRedoDelegatesToSnapshots() {
        LayerStructureSnapshot before = LayerStructureSnapshot.capture();
        LayerStructureSnapshot after = LayerStructureSnapshot.capture();

        LayerStructureEditCommand command = new LayerStructureEditCommand(
                before,
                after,
                "history.plot.layer_structure.reorder");

        command.undo();
        command.redo();
        assertFalse(command.getDescription().isBlank());
    }
}
