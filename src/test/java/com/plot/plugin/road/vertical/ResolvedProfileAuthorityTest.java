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

    @Test
    void canActivateEditedProfileWhenSignatureMatches() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        road.setProfileSource(ProfileSource.AUTO);

        assertTrue(ResolvedProfileAuthority.canActivateEditedProfile(network, road));
    }

    @Test
    void cannotActivateEditedProfileAfterGeometryChangeWithoutEditorConfirm() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        int staleSignature = road.getEditedGeometrySignature();

        RoadNode end = network.getNodes().values().stream()
            .filter(node -> node.getPosition().x > 0)
            .findFirst()
            .orElseThrow();
        end.setPosition(new Vec2d(80, 0));
        road.setProfileSource(ProfileSource.AUTO);

        assertFalse(ResolvedProfileAuthority.canActivateEditedProfile(network, road));
        assertFalse(EditedProfileGeometry.matches(network, road, staleSignature));

        road.setProfileSource(ProfileSource.EDITED);
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
    }

    /** VA-A01: full-coverage confirmed EDITED remains valid for generation. */
    @Test
    void fullCoverageEditedProfileIsValid() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        assertEquals(
            ResolvedProfileAuthority.EditedValidity.VALID,
            ResolvedProfileAuthority.evaluateEditedValidity(network, road));
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
    }

    /** VA-A05: network-wide uniform elevation resolves as FLAT authority. */
    @Test
    void networkUniformFlatResolvesAsFlatAuthority() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.AUTO);
        network.setElevationMode(com.plot.plugin.road.model.NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);
        Road road = network.getRoad("r1");

        assertEquals(
            ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, road).kind());
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
    }

    /** VA-A02: VA covering only part of the road is not a valid EDITED profile. */
    @Test
    void partialCoverageEditedProfileIsIllegalClaim() {
        RoadNetwork network = buildRoadWithAlignment(ProfileSource.EDITED);
        Road road = network.getRoad("r1");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(10.0, 70.0),
            PointOfVerticalIntersection.of(30.0, 74.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        assertEquals(
            ResolvedProfileAuthority.EditedValidity.INCOMPLETE_COVERAGE,
            ResolvedProfileAuthority.evaluateEditedValidity(network, road));
        assertTrue(ResolvedProfileAuthority.isIllegalEditedClaim(network, road));
        assertFalse(ResolvedProfileAuthority.invalidateEditedProfileIfStale(network, road));
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        assertFalse(ResolvedProfileAuthority.canActivateEditedProfile(network, road));
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
