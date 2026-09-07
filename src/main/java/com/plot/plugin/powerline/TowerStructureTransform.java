package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import net.minecraft.util.math.BlockPos;

/** 将塔体局部坐标转换为世界坐标（复用 PoleFrame）。 */
public final class TowerStructureTransform {
    private final PoleFrame frame;
    private final ICoordinateService coordinates;

    public TowerStructureTransform(PoleFrame frame, ICoordinateService coordinates) {
        this.frame = frame;
        this.coordinates = coordinates;
    }

    public double[] toWorld(TowerLocalPoint point) {
        return toWorld(point.lateral(), point.vertical(), point.longitudinal());
    }

    public double[] toWorld(double lateral, double vertical, double longitudinal) {
        Vec2d planPoint = frame.toPlanPoint(lateral, longitudinal);
        double[] xz = planToWorldXz(planPoint);
        return new double[] {xz[0], frame.groundY() + vertical, xz[1]};
    }

    public BlockPos toBlock(TowerLocalPoint point) {
        double[] world = toWorld(point);
        return new BlockPos(
            (int) Math.floor(world[0]),
            (int) Math.floor(world[1]),
            (int) Math.floor(world[2]));
    }

    private double[] planToWorldXz(Vec2d planPoint) {
        if (planPoint == null) {
            return new double[] {0.0, 0.0};
        }
        if (coordinates != null) {
            Vec2d worldPos = coordinates.canvasToMinecraftWorld(planPoint);
            if (worldPos != null) {
                return new double[] {worldPos.x, worldPos.y};
            }
        }
        return new double[] {planPoint.x, planPoint.y};
    }
}
