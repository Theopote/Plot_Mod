package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGradeSeparationAlternative;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileIntersectionWarningResolverTest {

    @Test
    void flagsSteepLockedGradeSeparation() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setGradeSeparated(true);
        junction.setElevatedRoadId("road-a");

        RoadGradeSeparationAlternative steep = new RoadGradeSeparationAlternative(
            "road-a", "road-b", 18f, 12f, true, 1030f);
        RoadGradeSeparationAlternative better = new RoadGradeSeparationAlternative(
            "road-b", "road-a", 6f, 2f, false, 7f);
        RoadGradeSeparationEvaluation evaluation = new RoadGradeSeparationEvaluation(
            "road-a", "road-b", steep, better, true);

        assertTrue(RoadProfileIntersectionWarningResolver.steepGradeWarning(junction, evaluation));
    }

    @Test
    void enrichesIntersectionMarkersWithWarning() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setGradeSeparated(true);
        junction.setElevatedRoadId("road-a");

        RoadProfileIntersection base = sampleIntersection(junction.getId(), false);
        RoadGradeSeparationAlternative steep = new RoadGradeSeparationAlternative(
            "road-a", "road-b", 18f, 12f, true, 1030f);
        RoadGradeSeparationAlternative better = new RoadGradeSeparationAlternative(
            "road-b", "road-a", 6f, 2f, false, 7f);
        RoadGradeSeparationEvaluation evaluation = new RoadGradeSeparationEvaluation(
            "road-a", "road-b", steep, better, true);

        List<RoadProfileIntersection> enriched = RoadProfileIntersectionWarningResolver.withSteepGradeWarnings(
            List.of(base),
            network,
            nodeId -> evaluation);

        assertTrue(enriched.getFirst().steepGradeWarning());
    }

    @Test
    void skipsWarningForAtGradeCrossing() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));

        assertFalse(RoadProfileIntersectionWarningResolver.steepGradeWarning(junction, null));
    }

    private static RoadProfileIntersection sampleIntersection(String nodeId, boolean warning) {
        return new RoadProfileIntersection(
            nodeId,
            "road-a",
            "road-b",
            "Road B",
            40.0,
            40.0,
            72.0,
            68.0,
            ResolvedCrossSection.fromConfig(new RoadSystemConfig("test")),
            true,
            true,
            4.0,
            warning);
    }
}
