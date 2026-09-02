package com.plot.plugin.earthwork.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import net.minecraft.world.World;

import java.util.List;

/**
 * 从 World 或预置快照捕获场地/分区现状地形。
 */
public final class SiteTerrainCapture {

    private SiteTerrainCapture() {
    }

    public static TerrainSnapshot captureSite(
            ICoordinateService coordinateService,
            EarthworkSite site,
            World world,
            List<Vec2d> siteBoundary,
            TerrainSnapshot terrainSnapshot) {
        if (terrainSnapshot != null && !terrainSnapshot.isEmpty()) {
            return terrainSnapshot;
        }
        Polygon polygon = EarthworkGeometryUtils.toPolygon(siteBoundary);
        return TerrainSnapshot.capture(world, polygon, siteBoundary, coordinateService);
    }

    public static TerrainSnapshot captureRegion(
            ICoordinateService coordinateService,
            World world,
            List<Vec2d> outerPoints,
            TerrainSnapshot terrainSnapshot) {
        if (terrainSnapshot != null && !terrainSnapshot.isEmpty()) {
            return terrainSnapshot;
        }
        Polygon polygon = EarthworkGeometryUtils.toPolygon(outerPoints);
        return TerrainSnapshot.capture(world, polygon, outerPoints, coordinateService);
    }
}
