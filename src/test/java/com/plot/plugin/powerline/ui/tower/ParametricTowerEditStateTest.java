package com.plot.plugin.powerline.ui.tower;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametricTowerEditStateTest {

    @Test
    void constraintErrorKeepsRequestedParametersAndLastValidStructure() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.line.setParametricTowerConfig(draft.getGeneratorConfig().copy());
        double baselineHeight = draft.getGeneratorConfig().parameters().height();
        double baselineTop = draft.getTowerStructure().maxHeight();

        fixture.session.beginSession(draft);
        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);
        fixture.session.applyParametricChange(
            draft,
            envelope,
            source -> withHeight(source, 52.0));

        assertTrue(fixture.session.lastConstraintResult().hasErrors());
        assertEquals(52.0, draft.getGeneratorConfig().parameters().height(), 0.01);
        assertEquals(baselineTop, draft.getTowerStructure().maxHeight(), 0.01);
        assertEquals(baselineHeight, fixture.session.lastValidParameters().height(), 0.01);
        assertEquals(baselineHeight, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void successfulEditUpdatesLastValidState() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.session.beginSession(draft);

        fixture.session.applyParametricChange(draft, source -> withHeight(source, 44.0));
        double editedHeight = draft.getGeneratorConfig().parameters().height();
        assertNotEquals(36.0, editedHeight, 0.01);
        assertEquals(editedHeight, fixture.session.lastValidParameters().height(), 0.01);
        assertEquals(editedHeight, fixture.session.requestedParameters().height(), 0.01);
    }

    private static SessionFixture newSessionWithLine(String designId) {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId(designId);
        PowerLineProject project = new PowerLineProject();
        project.addLine(line);
        state.setProject(project);
        state.setDesignProject(new PowerLineDesignProject());
        state.getSelection().select(line.getId(), false);
        state.setPoleDesignerEditingId(designId);
        return new SessionFixture(line, new TowerDesignerSession(uiContext(state)));
    }

    private static PoleDesign parametricDraft(String designId) {
        PoleDesign draft = new PoleDesign(designId, "Custom");
        TowerParametricEditor.enableParametricClassic(draft, TowerParameterSet.classicDefaults());
        return draft;
    }

    private static TowerParameterSet withHeight(TowerParameterSet source, double height) {
        return new TowerParameterSet(
            height,
            source.baseWidth(),
            source.armSpan(),
            source.depthScale(),
            source.waistRatio(),
            source.armLevelScales(),
            source.density());
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

    private record SessionFixture(PowerLineFootprint line, TowerDesignerSession session) {
    }
}
