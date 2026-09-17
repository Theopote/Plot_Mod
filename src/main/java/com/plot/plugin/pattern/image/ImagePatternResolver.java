package com.plot.plugin.pattern.image;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.ImagePatternConfig;

/**
 * 将区域格点映射到图片像素并匹配方块。
 */
public final class ImagePatternResolver {
    private ImagePatternResolver() {
    }

    /**
     * @return 方块 id；透明像素返回 {@code null}（跳过该格）
     */
    public static String resolveBlockId(
            ImagePatternConfig config,
            ImagePatternRaster raster,
            BlockColorMatcher matcher,
            Vec2d cellCenter,
            PolygonRegionUtils.RectBounds bounds) {
        if (config == null || raster == null || matcher == null || cellCenter == null || bounds == null) {
            return null;
        }
        if (!config.hasImage()) {
            return null;
        }

        SampleCoord coord = mapToPixel(config, cellCenter, bounds, raster.width(), raster.height());
        if (coord == null) {
            return null;
        }
        int argb = raster.sampleArgb(coord.x, coord.y);
        if (raster.isTransparent(argb, config.getAlphaThreshold())) {
            return null;
        }
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return matcher.nearestBlock(red, green, blue);
    }

    private static SampleCoord mapToPixel(
            ImagePatternConfig config,
            Vec2d cellCenter,
            PolygonRegionUtils.RectBounds bounds,
            int imageWidth,
            int imageHeight) {
        double spanX = Math.max(1e-6, bounds.width());
        double spanZ = Math.max(1e-6, bounds.depth());
        double localX = cellCenter.x - bounds.minX();
        double localZ = cellCenter.y - bounds.minZ();

        return switch (config.getFitMode()) {
            case STRETCH -> mapStretch(localX, localZ, spanX, spanZ, imageWidth, imageHeight);
            case CONTAIN -> mapContain(localX, localZ, spanX, spanZ, imageWidth, imageHeight);
            case TILE -> mapTile(localX, localZ, config.getTileScale(), imageWidth, imageHeight);
        };
    }

    private static SampleCoord mapStretch(
            double localX,
            double localZ,
            double spanX,
            double spanZ,
            int imageWidth,
            int imageHeight) {
        double u = clamp01(localX / spanX);
        double v = clamp01(localZ / spanZ);
        int pixelX = clamp((int) Math.floor(u * imageWidth), 0, imageWidth - 1);
        int pixelY = clamp((int) Math.floor(v * imageHeight), 0, imageHeight - 1);
        return new SampleCoord(pixelX, pixelY);
    }

    private static SampleCoord mapContain(
            double localX,
            double localZ,
            double spanX,
            double spanZ,
            int imageWidth,
            int imageHeight) {
        double scale = Math.min(spanX / imageWidth, spanZ / imageHeight);
        if (scale <= 0.0) {
            return null;
        }
        double drawnWidth = imageWidth * scale;
        double drawnHeight = imageHeight * scale;
        double offsetX = (spanX - drawnWidth) * 0.5;
        double offsetZ = (spanZ - drawnHeight) * 0.5;
        double imageX = (localX - offsetX) / scale;
        double imageY = (localZ - offsetZ) / scale;
        if (imageX < 0.0 || imageY < 0.0 || imageX >= imageWidth || imageY >= imageHeight) {
            return null;
        }
        int pixelX = clamp((int) Math.floor(imageX), 0, imageWidth - 1);
        int pixelY = clamp((int) Math.floor(imageY), 0, imageHeight - 1);
        return new SampleCoord(pixelX, pixelY);
    }

    private static SampleCoord mapTile(
            double localX,
            double localZ,
            double tileScale,
            int imageWidth,
            int imageHeight) {
        double scale = Math.max(0.25, tileScale);
        int pixelX = positiveMod((int) Math.floor(localX / scale), imageWidth);
        int pixelY = positiveMod((int) Math.floor(localZ / scale), imageHeight);
        return new SampleCoord(pixelX, pixelY);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int positiveMod(int value, int modulus) {
        if (modulus <= 0) {
            return 0;
        }
        int mod = value % modulus;
        return mod < 0 ? mod + modulus : mod;
    }

    private record SampleCoord(int x, int y) {
    }
}
