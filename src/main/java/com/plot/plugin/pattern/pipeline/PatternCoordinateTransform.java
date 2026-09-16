package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;

/**
 * Pattern Space 通用变换：先偏移，再旋转。
 * <p>
 * 条纹在 resolver 内单独旋转；同心环偏移作用在圆心；马赛克仅平移、不旋转。
 */
public final class PatternCoordinateTransform {
    private PatternCoordinateTransform() {
    }

    public record Point(double x, double z) {
    }

    public static Point transform(ProceduralPatternConfig config, double patternX, double patternZ) {
        Vec2d offset = config != null ? config.getOffset() : null;
        double x = patternX + (offset != null ? offset.x : 0.0);
        double z = patternZ + (offset != null ? offset.y : 0.0);
        if (config == null || skipsRotation(config.getType())) {
            return new Point(x, z);
        }
        double radians = Math.toRadians(config.getAngleDegrees());
        if (Math.abs(radians) < 1e-9) {
            return new Point(x, z);
        }
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Point(x * cos + z * sin, -x * sin + z * cos);
    }

    public static boolean skipsRotation(ProceduralPatternConfig.PatternType type) {
        return type == ProceduralPatternConfig.PatternType.STRIPES
            || type == ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS
            || type == ProceduralPatternConfig.PatternType.MOSAIC;
    }

    public static double effectiveTileSize(ProceduralPatternConfig config) {
        if (config == null) {
            return 1.0;
        }
        double tileSize = Math.max(1e-6, config.getTileSize());
        double density = Math.max(0.1, config.getDensity());
        return switch (config.getType()) {
            case HEXAGONAL, HERRINGBONE, DIAMOND -> tileSize / density;
            default -> tileSize;
        };
    }
}
