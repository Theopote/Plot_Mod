package com.plot.plugin.powerline.ui.tower;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import com.plot.plugin.powerline.ui.PowerLineUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerDesignerSessionTest {

    @Test
    void switchingToLegacyClearsLineParametricOverride() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId("custom-tower");
        PowerLineProject project = new PowerLineProject();
        project.addLine(line);
        state.setProject(project);
        state.setDesignProject(new PowerLineDesignProject());
        state.getSelection().select(line.getId(), false);
        state.setPoleDesignerEditingId("custom-tower");

        PowerLineUiContext ctx = uiContext(state);
        PoleDesign draft = new PoleDesign("custom-tower", "Custom");
        TowerParametricEditor.enableParametricClassic(draft, TowerParameterSet.classicDefaults());

        TowerDesignerSession session = new TowerDesignerSession(ctx);
        session.syncParametricConfigToSelectedLine(draft);
        assertTrue(line.hasParametricTowerConfig());

        session.syncStructureMode(draft, false);
        assertFalse(draft.hasTowerStructure());
        assertFalse(line.hasParametricTowerConfig());
    }

    private static PowerLineUiContext uiContext(PowerLinePluginState state) {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        return new PowerLineUiContext(host, state, new Object());
    }
}
