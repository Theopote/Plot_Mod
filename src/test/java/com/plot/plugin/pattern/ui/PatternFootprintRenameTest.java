package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternFootprintRenameTest {

    @Test
    void footprintRenamePreservesLongUtf8Name() {
        PatternPluginState state = new PatternPluginState();
        PatternFootprint footprint = new PatternFootprint("footprint-a", List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)));
        PatternProject project = new PatternProject();
        project.addFootprint(footprint);
        state.setProject(project);

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
        PatternUiContext ctx = new PatternUiContext(host, state, new Object());
        String expected = "铺装轮廓名称测试".repeat(12);

        ctx.beginFootprintNameRename(footprint);
        state.getFootprintNameBuffer().set(expected);
        ctx.commitFootprintNameRename(footprint);

        assertEquals(expected, footprint.getName());
    }
}