package com.plot.core.command.commands;

import com.plot.core.state.AppState;
import com.plot.ui.panel.layer.LayerStructureSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void commandExecuteAppliesAfterSnapshot() {
        var layerManager = AppState.getInstance().getLayerManager();
        if (layerManager.getLayerCount() == 0) {
            layerManager.createLayer("base layer");
        }
        if (layerManager.getLayerCount() == 1) {
            layerManager.createLayer("second layer");
        }

        LayerStructureSnapshot before = LayerStructureSnapshot.capture();
        int countBeforeAdd = layerManager.getLayerCount();

        var created = layerManager.createLayer("CommandService test layer");
        assertTrue(created.isSuccess());
        LayerStructureSnapshot after = LayerStructureSnapshot.capture();

        layerManager.removeLayer(created.getLayer());
        assertEquals(countBeforeAdd, layerManager.getLayerCount());

        LayerStructureEditCommand command = new LayerStructureEditCommand(
                before,
                after,
                "history.plot.layer_structure.create");

        command.execute();
        assertEquals(countBeforeAdd + 1, layerManager.getLayerCount());
    }
}
