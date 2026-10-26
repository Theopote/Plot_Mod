package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentChartSeedingTest {

    private final RoadSystemConfig config = new RoadSystemConfig("test");

    @Test
    void simplifiesDenseTerrainSamplesToSparseEditorPvis() {
        RoadNetwork network = straightRoad(500.0);
        Road road = network.getRoad("r1");
        RoadProfileChartData chart = denseChart(road.getId(), 500.0, 501);

        List<PointOfVerticalIntersection> pvis = VerticalAlignmentChartSeeding.buildEditorPvisFromChart(
            chart, network, road, config);

        assertTrue(pvis.size() <= 64, "editor seed should cap PVI count");
        assertTrue(pvis.size() < 100, "should be much sparser than raw samples");
        assertEquals(0.0, pvis.getFirst().getStation(), 1e-6);
        assertEquals(500.0, pvis.getLast().getStation(), 1e-6);
    }

    @Test
    void preservesIntersectionStationsWhenSimplifying() {
        RoadNetwork network = straightRoad(200.0);
        Road road = network.getRoad("r1");
        RoadProfileChartData chart = denseChartWithIntersection(road.getId(), 200.0, 100.0);

        List<PointOfVerticalIntersection> pvis = VerticalAlignmentChartSeeding.buildEditorPvisFromChart(
            chart, network, road, config);

        assertTrue(pvis.stream().anyMatch(p -> Math.abs(p.getStation() - 100.0) <= 1.0));
    }

    @Test
    void detectsOverdenseAutoSeed() {
        Road road = new Road("r1");
        road.setProfileSource(ProfileSource.AUTO);
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        for (int i = 0; i <= 80; i++) {
            pvis.add(PointOfVerticalIntersection.of(i * 5.0, 64.0 + i * 0.01));
        }
        road.setVerticalAlignment(new RoadVerticalAlignment(pvis));

        assertTrue(VerticalAlignmentChartSeeding.isOverdenseAutoSeed(road));
    }

    @Test
    void ignoresOverdenseEditedProfile() {
        Road road = new Road("r1");
        road.setProfileSource(ProfileSource.EDITED);
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        for (int i = 0; i <= 80; i++) {
            pvis.add(PointOfVerticalIntersection.of(i * 5.0, 64.0));
        }
        road.setVerticalAlignment(new RoadVerticalAlignment(pvis));

        assertFalse(VerticalAlignmentChartSeeding.isOverdenseAutoSeed(road));
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
        return network;
    }

    private static RoadProfileChartData denseChart(String roadId, double length, int sampleCount) {
        List<Double> stations = new ArrayList<>();
        List<Double> elevations = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            double station = length * i / (sampleCount - 1);
            double wave = Math.sin(station / 25.0) * 3.0;
            stations.add(station);
            elevations.add(64.0 + wave + station * 0.01);
        }
        return new RoadProfileChartData(
            roadId,
            length,
            stations,
            elevations,
            elevations,
            elevations,
            List.of(),
            List.of(),
            true,
            true);
    }

    private RoadProfileChartData denseChartWithIntersection(
            String roadId,
            double length,
            double intersectionStation) {
        List<Double> stations = new ArrayList<>();
        List<Double> elevations = new ArrayList<>();
        int sampleCount = 201;
        for (int i = 0; i < sampleCount; i++) {
            double station = length * i / (sampleCount - 1);
            stations.add(station);
            elevations.add(64.0 + Math.sin(station / 25.0) * 3.0);
        }
        RoadProfileIntersection intersection = new RoadProfileIntersection(
            "node-1",
            roadId,
            "other",
            "other",
            intersectionStation,
            intersectionStation,
            66.0,
            66.0,
            ResolvedCrossSection.fromConfig(config),
            false,
            false,
            0.0,
            false);
        return new RoadProfileChartData(
            roadId,
            length,
            stations,
            elevations,
            elevations,
            elevations,
            List.of(),
            List.of(intersection),
            true,
            true);
    }
}
