package com.plot.plugin.earthwork.voxel;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RetainingWallProtectionTest {
    @Test void wallWritesRespectUnloadedExcludedAndNoTouchColumns() {
        for (int mode = 0; mode < 3; mode++) {
            var grid = new DesignTerrainGrid();
            var protectedCell = cell(0, mode != 0);
            protectedCell.setExcluded(mode == 1);
            protectedCell.setNoTouch(mode == 2);
            grid.put(0, 0, protectedCell);
            grid.put(1, 0, cell(1, true));
            var result = generate(new EarthworkSite("site"), grid, IdentityCoordinateService.INSTANCE);
            assertFalse(result.placementRecords.isEmpty());
            assertTrue(result.placementRecords.keySet().stream().noneMatch(pos -> pos.getX() == 0));
            assertNull(ZoneBoundaryRetainingEdgeAdapter.resolveWallBounds(new Vec2d(.5,.5), grid, null));
        }
    }

    @Test void explicitExclusionProtectsWallsEvenWithoutGrid() {
        var site = new EarthworkSite("site");
        site.addExclusionZone(new ExclusionZone("protected", List.of(
            new Vec2d(0,0), new Vec2d(1,0), new Vec2d(1,1), new Vec2d(0,1))));
        var result = generate(site, null, IdentityCoordinateService.INSTANCE);
        assertFalse(result.placementRecords.isEmpty());
        assertTrue(result.placementRecords.keySet().stream().noneMatch(pos -> pos.getX() == 0));
    }

    @Test void distantKnownTerrainCannotSupplyWallHeights() {
        var grid = new DesignTerrainGrid();
        grid.put(100, 0, cell(100, true));
        assertNull(ZoneBoundaryRetainingEdgeAdapter.resolveWallBounds(new Vec2d(.5,.5), grid, null));
        assertTrue(generate(new EarthworkSite("site"), grid, IdentityCoordinateService.INSTANCE)
            .placementRecords.isEmpty());
    }

    @Test void scaledProjectionUsesWorldGridRatherThanCanvasIndices() {
        var coordinates = SnapshotCoordinateService.uniformScale(4.0);
        var grid = new DesignTerrainGrid();
        for (int x = 2; x <= 6; x++) {
            var cell = new DesignTerrainCell(x, 2, new Vec2d((x + .5)/4, .625), 70, true);
            cell.setTargetY(64);
            grid.put(x, 2, cell);
        }
        var result = generate(new EarthworkSite("site"), grid, coordinates);
        assertFalse(result.placementRecords.isEmpty());
        assertTrue(result.placementRecords.keySet().stream().allMatch(pos -> pos.getZ() == 2));
        assertArrayEquals(new int[] {64, 70}, ZoneBoundaryRetainingEdgeAdapter.resolveWallBounds(
            new Vec2d(.5, .5), grid, null,
            EarthworkCanvasScale.capture(coordinates, List.of())));
    }

    private DesignTerrainCell cell(int x, boolean loaded) {
        var cell = new DesignTerrainCell(x, 0, new Vec2d(x + .5, .5), 70, loaded);
        cell.setTargetY(64);
        cell.setZoneId("pad");
        return cell;
    }

    private EarthworkGenerationResult generate(EarthworkSite site, DesignTerrainGrid grid,
                                                ICoordinateService coordinates) {
        var edge = new RetainingEdge("wall");
        edge.setPolyline(List.of(new Vec2d(.5,.5), new Vec2d(1.5,.5)));
        edge.setBottomElevation(64);
        edge.setTopElevation(70);
        var result = new EarthworkGenerationResult();
        RetainingWallGenerator.generateWall(edge, site, null, coordinates, result,
            new HashMap<>(), grid, EarthworkCanvasScale.capture(coordinates, edge.getPolyline()));
        return result;
    }
}
