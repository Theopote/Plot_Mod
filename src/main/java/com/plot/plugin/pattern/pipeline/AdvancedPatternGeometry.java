package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSpace;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

/** 扩展程序化图案：工字砌、编织纹、碎拼、放射纹、风车格、回字边、鱼鳞。 */
final class AdvancedPatternGeometry {
    private static final double RUNNING_BOND_ASPECT = 0.5;

    private AdvancedPatternGeometry() {
    }

    static int runningBond(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double width = PatternCoordinateTransform.effectiveTileSize(config);
        double height = width * RUNNING_BOND_ASPECT;
        int row = PatternGridMath.floorDiv(z, height);
        double offsetX = (row & 1) != 0 ? width * 0.5 : 0.0;
        int col = PatternGridMath.floorDiv(x - offsetX, width);
        return PatternGridMath.positiveMod(row + col, materials.size());
    }

    static int crosshatch(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double tile = PatternCoordinateTransform.effectiveTileSize(config);
        int gridX = PatternGridMath.floorDiv(x, tile);
        int gridZ = PatternGridMath.floorDiv(z, tile);
        return PatternGridMath.positiveMod(gridX + gridZ, materials.size());
    }

    static int scatter(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials,
            String seedKey) {
        double tile = Math.max(0.5, PatternCoordinateTransform.effectiveTileSize(config));
        int cellX = PatternGridMath.floorDiv(x, tile);
        int cellZ = PatternGridMath.floorDiv(z, tile);
        int hash = Objects.hash(cellX, cellZ, seedKey, "scatter");
        double jitter = 0.6 + (Math.floorMod(hash, 100) / 100.0) * 0.8;
        int subX = PatternGridMath.floorDiv(x, tile * jitter);
        int subZ = PatternGridMath.floorDiv(z, tile * jitter);
        BlockPos pos = new BlockPos(subX, 0, subZ);
        double value = MaterialMixResolver.unitRandomAt(pos, seedKey + ":scatter");
        int index = (int) Math.floor(value * materials.size());
        if (index >= materials.size()) {
            index = materials.size() - 1;
        }
        return Math.max(0, index);
    }

    static int radial(
            ProceduralPatternConfig config,
            double x,
            double z,
            Vec2d regionCentroid,
            List<String> materials) {
        Vec2d center = resolveCenter(config, regionCentroid);
        double dx = x - center.x;
        double dz = z - center.y;
        double angle = Math.atan2(dz, dx);
        if (angle < 0) {
            angle += Math.PI * 2.0;
        }
        double sectorDegrees = Math.max(10.0, config.getTileSize() * 15.0);
        int sector = PatternGridMath.floorDiv(Math.toDegrees(angle), sectorDegrees);
        return PatternGridMath.positiveMod(sector, materials.size());
    }

    static int windmill(
            ProceduralPatternConfig config,
            double x,
            double z,
            Vec2d regionCentroid,
            List<String> materials) {
        Vec2d center = resolveCenter(config, regionCentroid);
        double lx = x - center.x;
        double lz = z - center.y;
        int sector = lx >= 0
            ? (lz >= 0 ? 0 : 3)
            : (lz >= 0 ? 1 : 2);
        double tile = PatternCoordinateTransform.effectiveTileSize(config);
        int stripe = (sector == 0 || sector == 2)
            ? PatternGridMath.floorDiv(lz, tile)
            : PatternGridMath.floorDiv(lx, tile);
        return PatternGridMath.positiveMod(sector + stripe, materials.size());
    }

    static int frame(
            ProceduralPatternConfig config,
            double x,
            double z,
            PatternSpace space,
            List<String> materials) {
        double tile = PatternCoordinateTransform.effectiveTileSize(config);
        double distToEdge = space == null
            ? 0.0
            : PatternPolygonBoundaryDistance.distanceToPolygonBoundary(
                x,
                z,
                space.outerRing(),
                space.holes(),
                space.regionBounds());
        int ring = PatternGridMath.floorDiv(distToEdge, tile);
        return PatternGridMath.positiveMod(ring, materials.size());
    }

    static int fishScale(
            ProceduralPatternConfig config,
            double x,
            double z,
            List<String> materials) {
        double radius = PatternCoordinateTransform.effectiveTileSize(config);
        double rowHeight = radius * 0.5;
        int row = PatternGridMath.floorDiv(z, rowHeight);
        double offsetX = (row & 1) != 0 ? radius * 0.5 : 0.0;
        int col = PatternGridMath.floorDiv(x - offsetX, radius);
        double centerX = offsetX + col * radius + radius * 0.5;
        double centerZ = row * rowHeight;
        double dx = x - centerX;
        double dz = z - centerZ;
        double distSq = dx * dx + dz * dz;
        boolean inScale = distSq <= radius * radius * 0.22 && dz <= radius * 0.35;
        return PatternGridMath.positiveMod(col + row + (inScale ? 0 : 1), materials.size());
    }

    private static Vec2d resolveCenter(ProceduralPatternConfig config, Vec2d regionCentroid) {
        Vec2d center = config.getCenterOverride();
        if (center == null) {
            center = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        }
        Vec2d offset = config.getOffset();
        if (offset != null) {
            center = new Vec2d(center.x + offset.x, center.y + offset.y);
        }
        return center;
    }
}
