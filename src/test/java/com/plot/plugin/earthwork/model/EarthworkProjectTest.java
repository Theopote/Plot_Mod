package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 10),
            new Vec2d(0, 10)
        ));
        region.setName("North Pad");
        region.setAutoBalance(false);
        region.setManualTargetElevation(68);
        region.setMaterialProperties(new MaterialConversionModel(0.85f, 0.88f));
        region.setCutExposeMaterial("minecraft:sand");
        region.setFillMaterial("minecraft:grass_block");
        region.setPreviewGridSize(3);
        region.setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        region.setSlopeDirectionDegrees(90.0);
        region.setSlopePitchRatio(8);
        region.setSlopeAnchorCanvas(new Vec2d(6, 5));
        region.setSlopeAnchorElevation(70);
        region.setThreePointControl(0, new Vec2d(0, 0), 60);
        region.setThreePointControl(1, new Vec2d(12, 0), 64);
        region.setThreePointControl(2, new Vec2d(0, 10), 62);
        region.setFitSlopeBalanceCutFill(false);
        project.addRegion(region);

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingRegion restoredRegion = restored.getRegion(region.getId());
        assertNotNull(restoredRegion);
        assertEquals("North Pad", restoredRegion.getName());
        assertEquals(false, restoredRegion.isAutoBalance());
        assertEquals(68, restoredRegion.getManualTargetElevation());
        assertEquals(0.85f, restoredRegion.getMaterialProperties().reusableRatio(), 1e-6f);
        assertEquals(0.88f, restoredRegion.getMaterialProperties().cutToCompactedFillRatio(), 1e-6f);
        assertEquals("minecraft:sand", restoredRegion.getCutExposeMaterial());
        assertEquals("minecraft:grass_block", restoredRegion.getFillMaterial());
        assertEquals(3, restoredRegion.getPreviewGridSize());
        assertEquals(GradingSurfaceMode.SINGLE_SLOPE_PLANE, restoredRegion.getSurfaceMode());
        assertEquals(90.0, restoredRegion.getSlopeDirectionDegrees(), 1e-6);
        assertEquals(8, restoredRegion.getSlopePitchRatio());
        assertEquals(70, restoredRegion.getSlopeAnchorElevation());
        assertEquals(6.0, restoredRegion.getSlopeAnchorCanvas().x, 1e-6);
        assertEquals(5.0, restoredRegion.getSlopeAnchorCanvas().y, 1e-6);
        assertEquals(60, restoredRegion.getThreePointElevation(0));
        assertEquals(64, restoredRegion.getThreePointElevation(1));
        assertEquals(62, restoredRegion.getThreePointElevation(2));
        assertEquals(false, restoredRegion.isFitSlopeBalanceCutFill());
        assertEquals(4, restoredRegion.getOuterPoints().size());
        assertTrue(restoredRegion.computeArea() > 0.0);
    }

    @Test
    void legacyFillFactorMigratesToMaterialProperties() {
        String json = """
            {
              "regions": [{
                "id": "r1",
                "name": "Legacy",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 10, "y": 0},
                  {"x": 10, "y": 10}
                ],
                "fillFactor": 1.25
              }]
            }
            """;
        EarthworkProject project = EarthworkProject.fromJson(json);
        GradingRegion region = project.getRegion("r1");
        assertEquals(1.0f, region.getMaterialProperties().reusableRatio(), 1e-6f);
        assertEquals(0.8f, region.getMaterialProperties().cutToCompactedFillRatio(), 1e-6f);
    }

    @Test
    void legacyGridSizeMigratesToPreviewGridSize() {
        String json = """
            {
              "regions": [{
                "id": "r1",
                "name": "Legacy Grid",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 10, "y": 0},
                  {"x": 10, "y": 10}
                ],
                "gridSize": 7
              }]
            }
            """;
        EarthworkProject project = EarthworkProject.fromJson(json);
        assertEquals(7, project.getRegion("r1").getPreviewGridSize());
    }

    @Test
    void corruptJsonThrowsInsteadOfSilentEmptyProject() {
        assertThrows(IllegalArgumentException.class, () -> EarthworkProject.fromJson("{not-valid-json"));
    }

    @Test
    void loadFromCorruptFileThrowsIoException(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> EarthworkProject.loadFrom(file));
    }

    @Test
    void saveToIsAtomicAndRoundTrips(@TempDir Path dir) throws IOException {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ));
        region.setName("Pad");
        project.addRegion(region);

        Path file = dir.resolve("earthwork.json");
        project.saveTo(file);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("earthwork.json.tmp")));

        EarthworkProject loaded = EarthworkProject.loadFrom(file);
        assertEquals(1, loaded.getRegionCount());
        assertEquals("Pad", loaded.getRegion(region.getId()).getName());
    }

    @Test
    void v2JsonRoundTripPreservesSiteAndZones() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        site.setName("Main Site");
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 15),
            new Vec2d(0, 15)
        ));

        GradingZone zone = new GradingZone(List.of(
            new Vec2d(2, 2),
            new Vec2d(18, 2),
            new Vec2d(18, 12),
            new Vec2d(2, 12)
        ));
        zone.setName("Pad");
        zone.setType(GradingZoneType.SLOPED);
        zone.setPriority(80);
        zone.getRegion().setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        zone.getRegion().setSlopeDirectionDegrees(45.0);
        zone.getRegion().setMaterialProperties(new MaterialConversionModel(0.88f, 0.91f));
        site.addZone(zone);

        String json = project.toJson();
        assertTrue(json.contains("\"schemaVersion\": 3"));
        assertTrue(json.contains("\"sites\""));

        EarthworkProject restored = EarthworkProject.fromJson(json);
        assertEquals(EarthworkProject.SCHEMA_VERSION_CURRENT, restored.getSchemaVersion());
        assertEquals(1, restored.getSiteCount());
        EarthworkSite restoredSite = restored.getActiveSite();
        assertEquals("Main Site", restoredSite.getName());
        assertEquals(1, restoredSite.getZoneCount());
        GradingZone restoredZone = restoredSite.getZone(zone.getId());
        assertNotNull(restoredZone);
        assertEquals("Pad", restoredZone.getName());
        assertEquals(GradingZoneType.SLOPED, restoredZone.getType());
        assertEquals(80, restoredZone.getPriority());
        assertEquals(GradingSurfaceMode.SINGLE_SLOPE_PLANE, restoredZone.getRegion().getSurfaceMode());
        assertEquals(0.88f, restoredZone.getRegion().getMaterialProperties().reusableRatio(), 1e-6f);
    }

    @Test
    void jsonRoundTripPreservesVerticalAdjustmentPolicy() {
        EarthworkProject project = new EarthworkProject();
        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        zone.setType(GradingZoneType.FLAT);
        zone.setVerticalAdjustmentPolicy(new VerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.Mode.BOUNDED, -2, 2, 0.75f));
        project.getActiveSite().addZone(zone);

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingZone restoredZone = restored.getActiveSite().getZone(zone.getId());
        assertNotNull(restoredZone);
        assertTrue(restoredZone.hasExplicitVerticalAdjustmentPolicy());
        VerticalAdjustmentPolicy policy = restoredZone.getVerticalAdjustmentPolicy();
        assertEquals(VerticalAdjustmentPolicy.Mode.BOUNDED, policy.getMode());
        assertEquals(-2, policy.getMinOffset());
        assertEquals(2, policy.getMaxOffset());
        assertEquals(0.75f, policy.getWeight(), 1e-6f);
    }

    @Test
    void missingVerticalAdjustmentPolicyUsesTypeDefault() {
        String json = """
            {
              "schemaVersion": 3,
              "sites": [{
                "id": "site-1",
                "name": "Site",
                "gradingZones": [{
                  "id": "pad",
                  "name": "Pad",
                  "type": "BUILDING_PAD",
                  "outerPoints": [
                    {"x": 0, "y": 0},
                    {"x": 10, "y": 0},
                    {"x": 10, "y": 10},
                    {"x": 0, "y": 10}
                  ]
                }]
              }],
              "activeSiteId": "site-1"
            }
            """;
        EarthworkProject project = EarthworkProject.fromJson(json);
        GradingZone zone = project.getActiveSite().getZone("pad");
        assertNotNull(zone);
        assertFalse(zone.hasExplicitVerticalAdjustmentPolicy());
        assertEquals(VerticalAdjustmentPolicy.Mode.LOCKED, zone.getVerticalAdjustmentPolicy().getMode());
        assertTrue(zone.isElevationLocked());
    }

    @Test
    void v2JsonPreservesMatchExistingAndMultiPlaneFacets() {
        EarthworkProject project = new EarthworkProject();
        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(0, 12)
        ));
        zone.getDesignSurface().setKind(DesignSurfaceKind.MULTI_PLANE);
        DesignSurfaceFacet facet = new DesignSurfaceFacet("pad-a", List.of(
            new Vec2d(2, 2),
            new Vec2d(10, 2),
            new Vec2d(10, 10),
            new Vec2d(2, 10)
        ));
        facet.getPlane().setKind(DesignSurfaceKind.MATCH_EXISTING);
        facet.getPlane().setVerticalOffset(5);
        zone.getDesignSurface().setFacets(List.of(facet));
        zone.syncDesignSurfaceToRegion();
        project.getActiveSite().addZone(zone);

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingZone restoredZone = restored.getActiveSite().getZone(zone.getId());
        assertEquals(DesignSurfaceKind.MULTI_PLANE, restoredZone.getDesignSurface().getKind());
        assertEquals(1, restoredZone.getDesignSurface().getFacets().size());
        assertEquals(5, restoredZone.getDesignSurface().getFacets().getFirst().getPlane().getVerticalOffset());
        assertEquals(DesignSurfaceKind.MATCH_EXISTING,
            restoredZone.getDesignSurface().getFacets().getFirst().getPlane().getKind());
    }

    @Test
    void v1RegionsMigrateToSiteWithZones() {
        String json = """
            {
              "regions": [{
                "id": "r1",
                "name": "North",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 10, "y": 0},
                  {"x": 10, "y": 8}
                ],
                "surfaceMode": "THREE_POINT"
              }, {
                "id": "r2",
                "name": "South",
                "outerPoints": [
                  {"x": 0, "y": 10},
                  {"x": 10, "y": 10},
                  {"x": 10, "y": 18}
                ],
                "surfaceMode": "FLAT"
              }]
            }
            """;
        EarthworkProject project = EarthworkProject.fromJson(json);
        assertEquals(2, project.getRegionCount());
        EarthworkSite site = project.getActiveSite();
        assertEquals(2, site.getZoneCount());
        assertEquals(4, site.getSiteBoundary().size());
        assertEquals(GradingZoneType.SLOPED, site.getZone("r1").getType());
        assertEquals(GradingZoneType.FLAT, site.getZone("r2").getType());
        assertTrue(site.getSiteBoundaryArea() > 0.0);
    }

    @Test
    void jsonRoundTripPreservesRegionHoles() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone zone = new GradingZone("zone-donut", com.plot.core.geometry.RegionGeometry.of(
            List.of(
                new Vec2d(0, 0),
                new Vec2d(12, 0),
                new Vec2d(12, 12),
                new Vec2d(0, 12)),
            List.of(List.of(
                new Vec2d(4, 4),
                new Vec2d(8, 4),
                new Vec2d(8, 8),
                new Vec2d(4, 8)))));
        site.addZone(zone);

        ExclusionZone exclusion = new ExclusionZone("courtyard");
        exclusion.setGeometry(com.plot.core.geometry.RegionGeometry.of(
            List.of(
                new Vec2d(2, 2),
                new Vec2d(10, 2),
                new Vec2d(10, 10),
                new Vec2d(2, 10)),
            List.of(List.of(
                new Vec2d(5, 5),
                new Vec2d(7, 5),
                new Vec2d(7, 7),
                new Vec2d(5, 7)))));
        site.setExclusionZones(List.of(exclusion));

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingZone restoredZone = restored.getActiveSite().getZone("zone-donut");
        ExclusionZone restoredExclusion = restored.getActiveSite().getExclusionZones().getFirst();

        assertEquals(1, restoredZone.getHoles().size());
        assertEquals(4, restoredZone.getHoles().getFirst().size());
        assertEquals(128.0, restoredZone.computeArea(), 1e-6);
        assertEquals(1, restoredExclusion.getHoles().size());
        assertFalse(restoredExclusion.containsCanvasPoint(new Vec2d(6.5, 6.5)));
        assertTrue(restoredExclusion.containsCanvasPoint(new Vec2d(3.5, 3.5)));
    }
}
