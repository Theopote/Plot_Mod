package com.plot.plugin.powerline.ui.tower;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import com.plot.plugin.powerline.ui.PowerLineUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerDesignerSessionTest {

    @Test
    void switchingToLegacyClearsLineParametricOverride() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");

        fixture.session.syncParametricConfigToSelectedLine(draft);
        assertTrue(fixture.line.hasParametricTowerConfig());

        fixture.session.syncStructureMode(draft, false);
        assertFalse(draft.hasTowerStructure());
        assertFalse(fixture.line.hasParametricTowerConfig());
    }

    @Test
    void discardRestoresFootprintBaselineAfterParametricEdit() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        assertTrue(draft.hasTowerStructure());
        fixture.line.setParametricTowerConfig(draft.getGeneratorConfig().copy());
        double baselineHeight = fixture.line.getParametricTowerConfig().parameters().height();

        fixture.session.beginSession(draft);
        fixture.session.applyParametricChange(draft, source -> withHeight(source, 55.0));
        double editedHeight = draft.getGeneratorConfig().parameters().height();
        assertNotEquals(baselineHeight, editedHeight, 0.01);
        assertEquals(editedHeight, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);

        fixture.session.endSession(false, draft);
        assertEquals(baselineHeight, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void afterDraftRestoredSyncsFootprintFromDraft() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.line.setParametricTowerConfig(draft.getGeneratorConfig().copy());

        fixture.session.beginSession(draft);
        fixture.session.applyParametricChange(draft, source -> withHeight(source, 60.0));

        PoleDesign restored = draft.copy();
        restored.setGeneratorConfig(restored.getGeneratorConfig().withParameters(
            withHeight(restored.getGeneratorConfig().parameters(), 50.0)));
        TowerParametricEditor.recompile(restored, null);

        fixture.session.afterDraftRestored(restored);
        assertEquals(50.0, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void liveParametricEditDoesNotSyncFootprintUntilCommitted() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.line.setParametricTowerConfig(draft.getGeneratorConfig().copy());
        double baselineHeight = fixture.line.getParametricTowerConfig().parameters().height();

        fixture.session.beginSession(draft);
        fixture.session.applyParametricChange(draft, source -> withHeight(source, 40.0), false);
        double editedHeight = draft.getGeneratorConfig().parameters().height();
        assertNotEquals(baselineHeight, editedHeight, 0.01);
        assertEquals(baselineHeight, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);

        fixture.session.syncParametricConfigToSelectedLine(draft);
        assertEquals(editedHeight, fixture.line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void canSaveDraftAllowsValidParametricDesign() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.session.beginSession(draft);
        fixture.session.refreshConstraints(draft);
        assertTrue(fixture.session.canSaveDraft(draft));
    }

    @Test
    void canSaveDraftBlocksInvalidManualTower() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.session.beginSession(draft);
        TowerParametricEditor.convertToManual(draft);
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 12, 2, 2));
        draft.setTowerStructure(structure);
        assertFalse(fixture.session.canSaveDraft(draft));
        assertTrue(fixture.session.structureValidationIssues(draft).stream()
            .anyMatch(issue -> issue.messageKey().contains("min_stations")));
    }

    @Test
    void canSaveDraftAllowsValidManualTower() {
        SessionFixture fixture = newSessionWithLine("custom-tower");
        PoleDesign draft = parametricDraft("custom-tower");
        fixture.session.beginSession(draft);
        TowerParametricEditor.convertToManual(draft);
        assertTrue(fixture.session.canSaveDraft(draft));
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
