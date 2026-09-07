package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class WireTestSupport {

    private WireTestSupport() {
    }

    static Set<BlockPos> wireBlocksAtY(PowerLineGenerationResult result, int wireY) {
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY) {
                blocks.add(record.pos);
            }
        }
        return blocks;
    }

    static void assertHorizontalWireCoversX(
            PowerLineGenerationResult result,
            int wireY,
            int xStart,
            int xEnd) {
        Set<Integer> xs = new HashSet<>();
        for (BlockPos block : wireBlocksAtY(result, wireY)) {
            xs.add(block.getX());
        }
        for (int x = xStart; x <= xEnd; x++) {
            assertTrue(xs.contains(x), "missing wire block at x=" + x + ", y=" + wireY);
        }
    }

    static void assertWireAlongPlanLine(
            PowerLineGenerationResult result,
            int wireY,
            Vec2d startPlan,
            Vec2d endPlan) {
        double spanLength = startPlan.distance(endPlan);
        int sampleCount = PowerLineWireRasterizer.computeWireSampleCount(spanLength, 1);
        int segmentCount = sampleCount - 1;

        double[] worldX = new double[sampleCount];
        double[] worldZ = new double[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / segmentCount;
            Vec2d planPoint = startPlan.lerp(endPlan, t);
            worldX[i] = planPoint.x;
            worldZ[i] = planPoint.y;
        }

        Set<BlockPos> wireBlocks = wireBlocksAtY(result, wireY);
        for (int i = 0; i < segmentCount; i++) {
            for (BlockPos expected : PowerLineWireRasterizer.rasterizeLine3D(
                    worldX[i],
                    wireY,
                    worldZ[i],
                    worldX[i + 1],
                    wireY,
                    worldZ[i + 1])) {
                assertTrue(
                    wireBlocks.contains(expected),
                    "missing wire block at " + expected + " along plan segment");
            }
        }
    }

    static PowerLineFootprint horizontalLine(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, 0)));
        line.setPoleHeight(10.0);
        line.setMaxPoleSpacing(100.0);
        line.setSagRatio(0.0);
        return line;
    }

    static PowerLineFootprint diagonalLine45(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, length)));
        line.setPoleHeight(10.0);
        line.setMaxPoleSpacing(100.0);
        line.setSagRatio(0.0);
        return line;
    }
}
