package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableCrossSectionEditorTest {

  @Test
  void buildStationsSkipsDuplicateStations() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    VariableCrossSectionEditor.StationDraft first = draftAt(40f, 10, config);
    VariableCrossSectionEditor.StationDraft duplicate = draftAt(40f, 12, config);

    List<StationCrossSection> stations =
        VariableCrossSectionEditor.buildStations(List.of(first, duplicate), config);
    assertEquals(1, stations.size());
    assertEquals(10, widthOf(stations.getFirst().getCrossSection()));
  }

  @Test
  void buildStationsSortsByStation() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    VariableCrossSectionEditor.StationDraft late = draftAt(80f, 14, config);
    VariableCrossSectionEditor.StationDraft early = draftAt(20f, 8, config);

    List<StationCrossSection> stations =
        VariableCrossSectionEditor.buildStations(List.of(late, early), config);
    assertEquals(2, stations.size());
    assertEquals(20.0, stations.get(0).getStation());
    assertEquals(80.0, stations.get(1).getStation());
  }

  @Test
  void fromCrossSectionRoundTripPreservesWidth() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    RoadCrossSection section = sectionWithWidth(11);

    CrossSectionDraft draft = CrossSectionDraft.fromCrossSection(section, config);
    RoadCrossSection restored = draft.toCrossSection();
    assertEquals(11, widthOf(restored));
  }

  @Test
  void stationsEqualComparesStationAndCrossSection() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    StationCrossSection left = StationCrossSection.at(30.0, sectionWithWidth(10));
    StationCrossSection right = StationCrossSection.at(30.0, sectionWithWidth(10));
    assertTrue(VariableCrossSectionEditor.stationsEqual(List.of(left), List.of(right), config));
  }

  @Test
  void defaultEntryAvoidsOccupiedStations() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    var road = new com.plot.plugin.road.model.Road("r");
    VariableCrossSectionEditor.StationDraft first =
        VariableCrossSectionEditor.StationDraft.defaultEntry(road, config, 100.0);
    VariableCrossSectionEditor.StationDraft second =
        VariableCrossSectionEditor.StationDraft.defaultEntry(
            road, config, 100.0, List.of(first), first.station);
    assertTrue(Math.abs(second.station - first.station) > 1e-4f);
    VariableCrossSectionEditor.StationDraft third =
        VariableCrossSectionEditor.StationDraft.defaultEntry(
            road, config, 100.0, List.of(first, second), first.station);
    assertTrue(Math.abs(third.station - first.station) > 1e-4f);
    assertTrue(Math.abs(third.station - second.station) > 1e-4f);
  }

  @Test
  void defaultEntryCopiesTemplateCrossSection() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    var road = new com.plot.plugin.road.model.Road("r");
    VariableCrossSectionEditor.StationDraft template = draftAt(20f, 14, config);
    VariableCrossSectionEditor.StationDraft added =
        VariableCrossSectionEditor.StationDraft.defaultEntry(
            road, config, 100.0, List.of(template), 50f, template);
    assertEquals(14, added.crossSectionDraft.width());
    assertTrue(Math.abs(added.station - template.station) > 1e-4f);
  }

  @Test
  void smoothPlayheadPreviewAveragesWidthBetweenStations() {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    var road = new com.plot.plugin.road.model.Road("r");
    road.setWidth(6);
    VariableCrossSectionEditor.StationDraft narrow = draftAt(0f, 6, config);
    VariableCrossSectionEditor.StationDraft wide = draftAt(100f, 16, config);
    List<StationCrossSection> stations =
        VariableCrossSectionEditor.buildStations(List.of(narrow, wide), config);
    ResolvedCrossSection mid = VariableCrossSectionResolver.resolve(
        road.getCrossSection(),
        stations,
        VariableCrossSectionTransitionMode.SMOOTH,
        50.0,
        config);
    assertEquals(11, mid.carriagewayWidth);
  }

  @Test
  void applyDraftsIfChangedSkipsWhenNotDirty() throws Exception {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    var road = new com.plot.plugin.road.model.Road("r");
    road.setVariableCrossSections(new com.plot.plugin.road.model.section.RoadVariableCrossSections(List.of(
        StationCrossSection.at(10.0, sectionWithWidth(8)),
        StationCrossSection.at(40.0, sectionWithWidth(10)))));
    var original = road.getVariableCrossSections();
    VariableCrossSectionEditor editor = new VariableCrossSectionEditor();
    invoke(editor, "syncDrafts", new Class<?>[]{
        com.plot.plugin.road.model.Road.class, RoadSystemConfig.class}, road, config);
    draftsOf(editor).getFirst().station = 15f;

    invoke(editor, "applyDraftsIfChanged", new Class<?>[]{
        com.plot.plugin.road.model.Road.class, RoadSystemConfig.class}, road, config);

    assertSame(original, road.getVariableCrossSections());
    assertEquals(10.0, road.getVariableCrossSections().sortedStations().getFirst().getStation());
    assertFalse(editor.isDraftsDirty());
  }

  @Test
  void applyDraftsIfChangedWritesWhenDirty() throws Exception {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    var road = new com.plot.plugin.road.model.Road("r");
    road.setVariableCrossSections(new com.plot.plugin.road.model.section.RoadVariableCrossSections(List.of(
        StationCrossSection.at(10.0, sectionWithWidth(8)),
        StationCrossSection.at(40.0, sectionWithWidth(10)))));
    VariableCrossSectionEditor editor = new VariableCrossSectionEditor();
    invoke(editor, "syncDrafts", new Class<?>[]{
        com.plot.plugin.road.model.Road.class, RoadSystemConfig.class}, road, config);
    draftsOf(editor).getFirst().station = 15f;
    editor.markDraftsDirty();

    invoke(editor, "applyDraftsIfChanged", new Class<?>[]{
        com.plot.plugin.road.model.Road.class, RoadSystemConfig.class}, road, config);

    assertEquals(15.0, road.getVariableCrossSections().sortedStations().getFirst().getStation());
    assertFalse(editor.isDraftsDirty());
  }

  @Test
  void sortDraftsKeepingSelectionReordersAndKeepsIdentity() throws Exception {
    RoadSystemConfig config = new RoadSystemConfig("road_system");
    VariableCrossSectionEditor editor = new VariableCrossSectionEditor();
    List<VariableCrossSectionEditor.StationDraft> drafts = draftsOf(editor);
    VariableCrossSectionEditor.StationDraft late = draftAt(120f, 14, config);
    VariableCrossSectionEditor.StationDraft mid = draftAt(50f, 10, config);
    VariableCrossSectionEditor.StationDraft early = draftAt(20f, 8, config);
    drafts.add(late);
    drafts.add(mid);
    drafts.add(early);
    Field selectedIndex = VariableCrossSectionEditor.class.getDeclaredField("selectedIndex");
    selectedIndex.setAccessible(true);
    selectedIndex.setInt(editor, 1); // mid @ 50m

    editor.sortDraftsKeepingSelection();

    assertEquals(20f, drafts.get(0).station, 1e-4f);
    assertEquals(50f, drafts.get(1).station, 1e-4f);
    assertEquals(120f, drafts.get(2).station, 1e-4f);
    assertEquals(1, selectedIndex.getInt(editor));
    assertSame(mid, drafts.get(1));
  }

  @SuppressWarnings("unchecked")
  private static List<VariableCrossSectionEditor.StationDraft> draftsOf(
      VariableCrossSectionEditor editor) throws Exception {
    Field field = VariableCrossSectionEditor.class.getDeclaredField("drafts");
    field.setAccessible(true);
    return (List<VariableCrossSectionEditor.StationDraft>) field.get(editor);
  }

  private static void invoke(Object target, String name, Class<?>[] types, Object... arguments)
      throws Exception {
    var method = target.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    method.invoke(target, arguments);
  }

  private static VariableCrossSectionEditor.StationDraft draftAt(
      float station, int width, RoadSystemConfig config) {
    VariableCrossSectionEditor.StationDraft draft = new VariableCrossSectionEditor.StationDraft();
    draft.station = station;
    draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(sectionWithWidth(width), config);
    return draft;
  }

  private static RoadCrossSection sectionWithWidth(int width) {
    RoadCrossSection section = new RoadCrossSection();
    section.getMarkings().setLaneDividers(false);
    section.getMarkings().setCenterLineStyle(
        com.plot.plugin.road.model.section.CenterLineStyle.NONE);
    int w = Math.max(1, width);
    int lanes = Math.min(4, Math.max(1, (w + 5) / 6));
    int base = w / lanes;
    int rem = w % lanes;
    while (base > 6 && lanes < 4) {
      lanes++;
      base = w / lanes;
      rem = w % lanes;
    }
    section.getCarriageway().syncLaneCount(lanes);
    for (int i = 0; i < lanes; i++) {
      int lw = Math.min(6, Math.max(1, base + (i < rem ? 1 : 0)));
      section.getCarriageway().setLaneWidthAt(i, lw);
    }
    section.getCarriageway().syncDerivedWidth(
        com.plot.plugin.road.model.section.CenterLineStyle.NONE, false, 0);
    return section;
  }

  private static int widthOf(RoadCrossSection section) {
    Integer width = section.getCarriageway().getWidth();
    return width != null ? width : 0;
  }
}
