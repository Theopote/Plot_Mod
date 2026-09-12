package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 认领参考路径并生成杆塔折线骨架。 */
public final class PowerLinePathLayout {

    private PowerLinePathLayout() {
    }

    public static PowerLineFootprint adopt(Shape shape, ICoordinateService coordinates) {
        Objects.requireNonNull(shape, "shape");
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        PowerLineSourcePath sourcePath = PowerLinePathAdapters.from(shape);
        PowerLineSourceDescriptor descriptor = PowerLineSourceDescriptor.capture(shape);
        double length = sourcePath.worldLength(coords);
        Vec2d start = sourcePath.pointAtStation(0.0, coords);
        Vec2d end = sourcePath.isClosed()
            ? sourcePath.pointAtStation(length / 3.0, coords)
            : length > 1e-9
                ? sourcePath.pointAtStation(length, coords)
                : start.add(new Vec2d(1, 0));
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(start.copy(), end.copy()));
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(footprint, sourcePath, coords);
        commitLayout(footprint, descriptor, sourcePath, sites);
        return footprint;
    }

    public static List<PowerPoleSite> layoutAndSync(
            PowerLineFootprint footprint,
            ICoordinateService coordinates) {
        PowerLineSourcePath sourcePath = footprint.resolveSourcePath();
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(footprint, sourcePath, coordinates);
        validateClosedLoop(sourcePath, sites);
        syncTowerPolyline(footprint, sites);
        return sites;
    }

    public static void commitLayout(
            PowerLineFootprint footprint,
            PowerLineSourceDescriptor descriptor,
            PowerLineSourcePath sourcePath,
            List<PowerPoleSite> sites) {
        validateClosedLoop(sourcePath, sites);
        footprint.setSourceDescriptor(descriptor);
        syncTowerPolyline(footprint, sites);
    }

    static void validateClosedLoop(PowerLineSourcePath sourcePath, List<PowerPoleSite> sites) {
        if (sourcePath == null || !sourcePath.isClosed()) {
            return;
        }
        if (sites.size() < 3) {
            throw new ClosedLoopLayoutException("Closed path requires at least 3 towers");
        }
        List<Vec2d> towers = new ArrayList<>(sites.size());
        for (PowerPoleSite site : sites) {
            towers.add(site.getPlanPosition());
        }
        if (!ClosedPathGeometry.isValidTowerLoop(towers)) {
            throw new ClosedLoopLayoutException("Closed path towers are collinear or degenerate");
        }
    }

    private static void syncTowerPolyline(PowerLineFootprint footprint, List<PowerPoleSite> sites) {
        if (footprint == null || sites.size() < 2) {
            throw new ClosedLoopLayoutException("Unable to derive tower polyline from source path");
        }
        List<Vec2d> towerPolyline = new ArrayList<>(sites.size());
        for (PowerPoleSite site : sites) {
            towerPolyline.add(site.getPlanPosition().copy());
        }
        footprint.setPathPoints(towerPolyline);
    }
}
