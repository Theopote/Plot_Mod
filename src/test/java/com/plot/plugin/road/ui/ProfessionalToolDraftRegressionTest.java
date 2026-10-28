package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.*;
import com.plot.plugin.road.model.facility.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionalToolDraftRegressionTest {
    private final RoadSystemConfig config = new RoadSystemConfig("draft-regression");

    @Test
    void materialOnlySectionChangeIsDetected() {
        var a = new RoadCrossSection();
        var b = new RoadCrossSection();
        a.getCarriageway().setMaterial("minecraft:stone");
        b.getCarriageway().setMaterial("minecraft:deepslate");
        assertFalse(VariableCrossSectionEditor.stationsEqual(
            List.of(StationCrossSection.at(3, a)), List.of(StationCrossSection.at(3, b)), config));
    }

    @Test
    void shortRoadDefaultsStayInsideTheRoad() {
        var road = new Road("short");
        assertEquals(3, VariableCrossSectionEditor.StationDraft.defaultEntry(road, config, 3).station);
        assertEquals(3, StationFacilityEditor.FacilityRunDraft.defaultRun(3).endStation);
    }

    @Test
    void replacingWorkingRoadWithTheSameIdReloadsBothForms() throws Exception {
        var road = new Road("same");
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(StationCrossSection.at(2, new RoadCrossSection()))));
        road.setStationFacilities(new RoadStationFacilities(List.of(StationFacilityRun.of(2, 4.0,
            RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        var sections = new VariableCrossSectionEditor();
        var facilities = new StationFacilityEditor();
        invoke(sections, "syncDrafts", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        invoke(facilities, "syncDrafts", new Class<?>[]{Road.class}, road);
        var replacement = new Road(road.getId());
        replacement.setVariableCrossSections(new RoadVariableCrossSections(List.of(StationCrossSection.at(5, new RoadCrossSection()))));
        replacement.setStationFacilities(new RoadStationFacilities(List.of(StationFacilityRun.of(5, 7.0,
            RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        invoke(sections, "syncDrafts", new Class<?>[]{Road.class, RoadSystemConfig.class}, replacement, config);
        invoke(facilities, "syncDrafts", new Class<?>[]{Road.class}, replacement);
        assertEquals(5, ((VariableCrossSectionEditor.StationDraft) drafts(sections).getFirst()).station);
        assertEquals(5, ((StationFacilityEditor.FacilityRunDraft) drafts(facilities).getFirst()).startStation);
    }

    @Test
    void invalidFormBlocksSaveAndRestoreClearsIt() throws Exception {
        var manager = new com.plot.plugin.road.manager.RoadNetworkManager(config,
            new com.plot.plugin.road.manager.RoadProjectStatus());
        var road = manager.getNetwork().createRoad("r");
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(2, new RoadCrossSection()), StationCrossSection.at(6, new RoadCrossSection()))));
        var app = com.plot.core.context.ApplicationContext.getInstance();
        var host = new com.plot.core.context.PluginContext(app.getAppState(), app.getCommandService(), app.getEventBus(),
            app.getToolManager(), com.plot.test.world.IdentityCoordinateService.INSTANCE, null, null, null);
        var ctx = new RoadUiContext(manager, null, null, null, new com.plot.plugin.road.manager.RoadProjectStatus(), host);
        var window = new com.plot.plugin.road.ui.editor.VariableCrossSectionDesignerWindow();
        window.open(ctx, "r");
        var field = window.getClass().getDeclaredField("editor");
        field.setAccessible(true);
        var editor = (VariableCrossSectionEditor) field.get(window);
        invoke(editor, "syncDrafts", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        ((VariableCrossSectionEditor.StationDraft) drafts(editor).getFirst()).station = 6;
        long revision = manager.getNetworkRevision();
        assertTrue(window.isDirty());
        var confirm = window.getClass().getSuperclass().getDeclaredMethod("confirmEdits", RoadUiContext.class);
        confirm.setAccessible(true);
        assertEquals(false, confirm.invoke(window, ctx));
        assertTrue(window.isOpen());
        assertEquals(revision, manager.getNetworkRevision());
        invoke(window, "restoreSession", new Class<?>[]{});
        assertFalse(window.isDirty());
        assertEquals(2, road.getVariableCrossSections().stationCount());
    }

    @Test
    void openingFormsDoesNotRoundPreviouslySavedStationsOrHeights() throws Exception {
        var road = new Road("precise");
        var sections = new RoadVariableCrossSections(List.of(StationCrossSection.at(2.123456789, new RoadCrossSection())));
        var facilities = new RoadStationFacilities(List.of(new StationFacilityRun(2.123456789, 7.765432109,
            RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.LEFT, null, 1.23456789)));
        road.setVariableCrossSections(sections);
        road.setStationFacilities(facilities);
        sections = road.getVariableCrossSections();
        facilities = road.getStationFacilities();
        var sectionEditor = new VariableCrossSectionEditor();
        var facilityEditor = new StationFacilityEditor();
        invoke(sectionEditor, "syncDrafts", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        invoke(facilityEditor, "syncDrafts", new Class<?>[]{Road.class}, road);
        invoke(sectionEditor, "applyDraftsIfChanged", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        invoke(facilityEditor, "applyDraftsIfChanged", new Class<?>[]{Road.class}, road);
        assertSame(sections, road.getVariableCrossSections());
        assertSame(facilities, road.getStationFacilities());
    }

    @Test
    void movingStationPreservesInheritedAndUnexposedSectionParameters() {
        var section = new RoadCrossSection();
        section.getShoulder().setMaterial("minecraft:andesite");
        var draft = VariableCrossSectionEditor.StationDraft.from(StationCrossSection.at(2, section), config);
        draft.station = 4;
        var saved = VariableCrossSectionEditor.buildStations(List.of(draft), config).getFirst();
        assertEquals(4, saved.getStation());
        assertNull(saved.getCrossSection().getCarriageway().getWidth());
        assertEquals("minecraft:andesite", saved.getCrossSection().getShoulder().getMaterial());
    }

    @Test
    void invalidRowsDoNotSilentlyRemoveSavedEngineeringData() throws Exception {
        var road = new Road("invalid");
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(2, new RoadCrossSection()), StationCrossSection.at(6, new RoadCrossSection()))));
        road.setStationFacilities(new RoadStationFacilities(List.of(StationFacilityRun.of(2, 4.0,
            RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        var sections = new VariableCrossSectionEditor();
        var facilities = new StationFacilityEditor();
        invoke(sections, "syncDrafts", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        invoke(facilities, "syncDrafts", new Class<?>[]{Road.class}, road);
        ((VariableCrossSectionEditor.StationDraft) drafts(sections).getFirst()).station = 6;
        ((StationFacilityEditor.FacilityRunDraft) drafts(facilities).getFirst()).endStation = 1;
        invoke(sections, "applyDraftsIfChanged", new Class<?>[]{Road.class, RoadSystemConfig.class}, road, config);
        invoke(facilities, "applyDraftsIfChanged", new Class<?>[]{Road.class}, road);
        assertTrue(sections.hasInvalidDrafts());
        assertTrue(facilities.hasInvalidDrafts());
        assertEquals(2, road.getVariableCrossSections().stationCount());
        assertEquals(1, road.getStationFacilities().runCount());
        sections.reset();
        facilities.reset();
        assertFalse(sections.hasInvalidDrafts());
        assertFalse(facilities.hasInvalidDrafts());
    }

    private static List<?> drafts(Object editor) throws Exception {
        var field = editor.getClass().getDeclaredField("drafts");
        field.setAccessible(true);
        return (List<?>) field.get(editor);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, arguments);
    }
}
