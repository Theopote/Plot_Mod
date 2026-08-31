package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.StationCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  private static VariableCrossSectionEditor.StationDraft draftAt(
      float station, int width, RoadSystemConfig config) {
    VariableCrossSectionEditor.StationDraft draft = new VariableCrossSectionEditor.StationDraft();
    draft.station = station;
    draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(sectionWithWidth(width), config);
    return draft;
  }

  private static RoadCrossSection sectionWithWidth(int width) {
    RoadCrossSection section = new RoadCrossSection();
    section.getCarriageway().setWidth(width);
    return section;
  }

  private static int widthOf(RoadCrossSection section) {
    Integer width = section.getCarriageway().getWidth();
    return width != null ? width : 0;
  }
}
