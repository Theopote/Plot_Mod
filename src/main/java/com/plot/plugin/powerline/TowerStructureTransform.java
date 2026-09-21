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
        return VoxelLineRasterizer.symmetricBlockCell(world[0], world[1], world[2]);
    }

    public TowerLocalPoint fromWorldBlock(BlockPos block) {
        return fromWorld(block.getX(), block.getY(), block.getZ());
    }

    public TowerLocalPoint fromWorld(double worldX, double worldY, double worldZ) {
        Vec2d plan = planFromWorldXZ(worldX, worldZ);
        Vec2d rel = new Vec2d(
            plan.x - frame.origin().x,
            plan.y - frame.origin().y);
        double lateral = rel.x * frame.right().x + rel.y * frame.right().y;
        double longitudinal = rel.x * frame.forward().x + rel.y * frame.forward().y;
        return TowerLocalPoint.of(lateral, worldY - frame.groundY(), longitudinal);
    }

    private Vec2d planFromWorldXZ(double worldX, double worldZ) {
        if (coordinates != null) {
            return coordinates.captureProjection().toCanvas(new Vec2d(worldX, worldZ));
        }
        return new Vec2d(worldX, worldZ);
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
