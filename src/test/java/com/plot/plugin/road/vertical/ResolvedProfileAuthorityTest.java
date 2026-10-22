package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedProfileAuthorityTest {

    @Test
    void autoSourceStripsDesignProfileOnGenerationSnapshot() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.AUTO);
        int stripped = VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);
        assertEquals(1, stripped);
        assertFalse(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, network.getRoad("r1")));
    }

    @Test
    void editedSourceKeepsDesignProfileOnGenerationSnapshot() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        int stripped = VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);
        assertEquals(0, stripped);
        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));
    }

    @Test
    void geometryChangeInvalidatesEditedSource() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));

        RoadNode end = network.getNodes().values().stream()
            .filter(node -> node.getPosition().x > 0)
            .findFirst()
            .orElseThrow();
        end.setPosition(new Vec2d(80, 0));

        assertTrue(ResolvedProfileAuthority.invalidateEditedProfileIfStale(network, road));
        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
        assertTrue(ResolvedProfileAuthority.hasStoredEditedCurve(road));
    }

    @Test
    void autoWithSavedCurveDoesNotUseAlignmentForGeneration() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.AUTO);
        assertTrue(ResolvedProfileAuthority.hasStoredEditedCurve(network.getRoad("r1")));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(
            network, network.getRoad("r1")));
    }

    private static RoadNetwork buildRoadWithAlignment(ProfileSource source) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(source);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 74.0))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
        if (source == ProfileSource.EDITED) {
            road.setEditedGeometrySignature(EditedProfileGeometry.signature(network, road));
        }
        return network;
    }
}
