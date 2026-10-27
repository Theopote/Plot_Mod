package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.*;
import com.plot.plugin.road.model.section.*;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionalToolRegressionTest {
    private RoadNetworkManager manager;
    private RoadUiContext ctx;

    @BeforeEach
    void setup() {
        manager = new RoadNetworkManager(new RoadSystemConfig("professional"), new RoadProjectStatus());
        road("a", 10);
        road("b", 8);
        road("c", 12);
        var app = ApplicationContext.getInstance();
        var host = new PluginContext(app.getAppState(), app.getCommandService(), app.getEventBus(),
            app.getToolManager(), IdentityCoordinateService.INSTANCE, null, null, null);
        ctx = new RoadUiContext(manager, null, null, null, new RoadProjectStatus(), host);
    }

    @Test
    void copiedEngineeringDataDoesNotMakeUntouchedWindowsDirty() {
        var network = manager.getNetwork();
        var road = network.getRoad("a");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(new Vec2d(0, 0), 0,
            List.of(HorizontalAlignmentElement.tangent(10))));
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(3, new RoadCrossSection()))));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(2, 6.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        var baseline = network.snapshot();
        var working = baseline.snapshot();
        assertFalse(CenterlineEditorApplier.hasChanges(baseline, working, "a"));
        assertFalse(VariableCrossSectionEditorApplier.hasChanges(baseline, working, "a"));
        assertFalse(StationFacilityEditorApplier.hasChanges(baseline, working, "a"));
        working.getRoad("a").getHorizontalAlignment().setStartBearingRadians(0.5);
        assertTrue(CenterlineEditorApplier.hasChanges(baseline, working, "a"));
    }

    @Test
    void crossSectionSaveKeepsUntouchedParametersInherited() {
        var network = manager.getNetwork();
        var working = network.snapshot();
        working.getRoad("a").setWidth(14);
        CrossSectionEditorApplier.applyToLive(network, working, "a", manager.getConfig());
        var saved = network.getRoad("a");
        assertEquals(14, saved.getWidth());
        assertNull(saved.getIncludeShoulder());
        assertNull(saved.getIncludeSidewalk());
        assertNull(saved.getMaxSlope());
        assertNull(saved.getMaterial());
    }

    @Test
    void clearingAnOverrideIsAnEditEvenWhenTheResolvedWidthIsUnchanged() {
        var network = manager.getNetwork();
        network.getRoad("a").setWidth(manager.getConfig().getRoadWidth());
        var baseline = network.snapshot();
        var working = baseline.snapshot();
        working.getRoad("a").setWidth(null);
        assertTrue(CrossSectionEditorApplier.hasChanges(baseline, working, "a", manager.getConfig()));
    }

    @Test
    void batchSaveUsesCapturedRoadsAndUndoRestoresBoth() {
        manager.selectRoad("a", false);
        manager.selectRoad("b", true);
        var editor = new BatchCrossSectionDesignerWindow();
        editor.open(ctx, "a");
        var initial = manager.loadBatchEditDefaults();
        editor.replaceWorkingBatchDefaults(initial.withWidth(15));
        manager.selectRoad("a", false);
        manager.selectRoad("c", true);
        assertFalse(editor.matchesScope(Set.of("a", "c")));
        assertTrue(editor.confirmEdits(ctx));
        assertEquals(15, manager.getNetwork().getRoad("a").getWidth());
        assertEquals(15, manager.getNetwork().getRoad("b").getWidth());
        assertEquals(12, manager.getNetwork().getRoad("c").getWidth());
        assertNull(manager.getNetwork().getRoad("b").getMaterial());
        manager.undo();
        assertEquals(10, manager.getNetwork().getRoad("a").getWidth());
        assertEquals(8, manager.getNetwork().getRoad("b").getWidth());
    }

    @Test
    void batchRestoreResetsItsSeparateFormDraft() {
        manager.selectRoad("a", false);
        manager.selectRoad("b", true);
        var editor = new BatchCrossSectionDesignerWindow();
        editor.open(ctx, "a");
        editor.replaceWorkingBatchDefaults(manager.loadBatchEditDefaults().withWidth(16));
        assertTrue(editor.isDirty());
        editor.restoreSession();
        assertFalse(editor.isDirty());
    }

    @Test
    void diagnosisCacheDoesNotReuseLiveResultsForAnEditedWorkingCopy() {
        com.plot.plugin.road.repair.RoadRepairDiagnosisCache.invalidate();
        var network = manager.getNetwork();
        com.plot.plugin.road.repair.RoadRepairDiagnosisCache.diagnose(ctx, network, network.getRoad("a"));
        var working = network.snapshot();
        var road = working.getRoad("a");
        road.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0, 64),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(100, 64))));
        assertTrue(com.plot.plugin.road.repair.RoadRepairDiagnosisCache.diagnose(ctx, working, road)
            .contains(com.plot.plugin.road.repair.RoadRepairIssue.VERTICAL_PROFILE_MISMATCH));
        road.setVerticalAlignment(null);
        assertFalse(com.plot.plugin.road.repair.RoadRepairDiagnosisCache.diagnose(ctx, working, road)
            .contains(com.plot.plugin.road.repair.RoadRepairIssue.VERTICAL_PROFILE_MISMATCH));
    }

    @Test
    void diagnosticsRefreshesAndCanCloseWithoutAFalseConflict() {
        var editor = new DiagnosticsDesignerWindow();
        editor.open(ctx, "a");
        manager.mutateNetwork(() -> manager.getNetwork().getRoad("a").setWidth(14));
        editor.refreshCleanSession(ctx);
        assertEquals(14, editor.editorSession().workingNetwork().getRoad("a").getWidth());
        assertFalse(editor.isDirty());
        long revision = manager.getNetworkRevision();
        assertTrue(editor.confirmEdits(ctx));
        assertEquals(revision, manager.getNetworkRevision());
    }

    private Road road(String id, int width) {
        var network = manager.getNetwork();
        var road = network.createRoad(id);
        road.setWidth(width);
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), id);
        return road;
    }
}
