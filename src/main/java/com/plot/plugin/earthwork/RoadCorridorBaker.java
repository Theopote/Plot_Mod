package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.BakedElevationGrid;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;

import java.util.List;

/**
 * 将道路设计纵断面烘焙为分区标高缓存，并切换为 {@link GradingZoneType#TERRAIN_FIT}。
 */
public final class RoadCorridorBaker {
    private RoadCorridorBaker() {
    }

    public static int bake(GradingZone zone, TerrainSnapshot terrain, RoadSurfaceLookup lookup) {
        if (zone == null || terrain == null || terrain.isEmpty() || lookup == null) {
            return 0;
        }
        String roadEdgeRef = RoadCorridorSurfaceResolver.resolveRoadEdgeRef(zone, zone.getDesignSurface());
        if (roadEdgeRef.isBlank()) {
            return 0;
        }

        BakedElevationGrid grid = new BakedElevationGrid();
        List<Vec2d> polygon = zone.getOuterPoints();
        int bakedCount = 0;
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (!EarthworkGeometryUtils.containsCanvasPoint(polygon, column.center())) {
                continue;
            }
            Integer targetY = lookup.sampleDesignY(roadEdgeRef, column.center());
            if (targetY == null) {
                continue;
            }
            grid.put(column.worldX(), column.worldZ(), targetY);
            bakedCount++;
        }
        if (bakedCount == 0) {
            return 0;
        }

        zone.getDesignSurface().setBakedElevationGrid(grid);
        zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BAKED_ROAD);
        zone.getDesignSurface().setKind(DesignSurfaceKind.BEST_FIT_PLANE);
        zone.setType(GradingZoneType.TERRAIN_FIT);
        return bakedCount;
    }
}
