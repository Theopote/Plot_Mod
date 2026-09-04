package com.plot.plugin.earthwork.design;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPadElevationServiceTest {

    @Test
    void resolvesManualPadElevationForLinkedBuilding() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = createSite();
        GradingZone pad = createPad("pad-1", "b1", 72);
        site.addZone(pad);
        project.addSite(site);

        Optional<Integer> elevation = BuildingPadElevationService.resolveEarthworkOwnedPadElevation(
            project, "b1", footprint(), null, null);

        assertTrue(elevation.isPresent());
        assertEquals(72, elevation.get());
    }

    @Test
    void skipsBuildingLinkedPadToAvoidCircularDependency() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = createSite();
        GradingZone pad = createPad("pad-1", "b1", 72);
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        site.addZone(pad);
        project.addSite(site);

        Optional<Integer> elevation = BuildingPadElevationService.resolveEarthworkOwnedPadElevation(
            project, "b1", footprint(), null, null);

        assertFalse(elevation.isPresent());
    }

    @Test
    void appliesSiteBalanceVerticalOffset() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = createSite();
        GradingZone pad = createPad("pad-1", "b1", 70);
        site.addZone(pad);
        site.setLastZoneVerticalOffsets(Map.of("pad-1", 3));
        project.addSite(site);

        Optional<Integer> elevation = BuildingPadElevationService.resolveEarthworkOwnedPadElevation(
            project, "b1", footprint(), null, null);

        assertTrue(elevation.isPresent());
        assertEquals(73, elevation.get());
    }

    @Test
    void samplesPreviewGridWhenExplicitElevationMissing() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = createSite();
        GradingZone pad = createPad("pad-1", "b1", null);
        pad.getDesignSurface().setManualTargetElevation(null);
        site.addZone(pad);
        project.addSite(site);

        DesignTerrainGrid grid = new DesignTerrainGrid();
        grid.put(5, 5, cell(5, 5, 68, "pad-1"));
        grid.put(6, 5, cell(6, 5, 68, "pad-1"));
        grid.put(5, 6, cell(5, 6, 70, "pad-1"));
        grid.finalizeStats();

        TestCoordinateService coordinates = new TestCoordinateService();
        Optional<Integer> elevation = BuildingPadElevationService.resolveEarthworkOwnedPadElevation(
            project, "b1", footprint(), grid, coordinates);

        assertTrue(elevation.isPresent());
        assertEquals(68, elevation.get());
    }

    @Test
    void prefersActiveSitePadMatch() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite inactive = createSite("site-a");
        inactive.addZone(createPad("pad-a", "b1", 60));
        EarthworkSite active = createSite("site-b");
        active.addZone(createPad("pad-b", "b1", 80));
        project.addSite(inactive);
        project.addSite(active);
        project.setActiveSiteId("site-b");

        Optional<Integer> elevation = BuildingPadElevationService.resolveEarthworkOwnedPadElevation(
            project, "b1", footprint(), null, null);

        assertTrue(elevation.isPresent());
        assertEquals(80, elevation.get());
    }

    private static EarthworkSite createSite() {
        return createSite("site-1");
    }

    private static EarthworkSite createSite(String id) {
        EarthworkSite site = new EarthworkSite(id);
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        return site;
    }

    private static GradingZone createPad(String id, String buildingRef, Integer elevation) {
        GradingZone pad = new GradingZone(id, footprint());
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.setBuildingFootprintRef(buildingRef);
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.MANUAL);
        if (elevation != null) {
            pad.getDesignSurface().setElevation(elevation);
        }
        return pad;
    }

    private static List<Vec2d> footprint() {
        return List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));
    }

    private static DesignTerrainCell cell(int x, int z, int targetY, String zoneId) {
        DesignTerrainCell cell = new DesignTerrainCell(x, z, new Vec2d(x + 0.5, z + 0.5), targetY - 2);
        cell.setTargetY(targetY);
        cell.setZoneId(zoneId);
        return cell;
    }

    /** 画布坐标与方块坐标 1:1 的测试用坐标服务。 */
    private static final class TestCoordinateService implements ICoordinateService {
        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            return canvasPos;
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return new WorldViewBounds(-512, 512, -512, 512);
        }
    }
}
