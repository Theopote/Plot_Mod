package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;

/**
 * Pattern Space 通用变换：先偏移，再旋转（条纹类型在 resolver 内单独使用角度）。
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
        if (config == null || config.getType() == ProceduralPatternConfig.PatternType.STRIPES) {
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

    public static double effectiveTileSize(ProceduralPatternConfig config) {
        if (config == null) {
            return 1.0;
        }
        double tileSize = Math.max(1e-6, config.getTileSize());
        double density = Math.max(0.1, config.getDensity());
        return switch (config.getType()) {
            case HEXAGONAL -> tileSize / density;
            case DIAMOND, HERRINGBONE -> tileSize * density;
            default -> tileSize;
        };
    }
}
