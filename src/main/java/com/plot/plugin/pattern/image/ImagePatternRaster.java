package com.plot.plugin.pattern.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 已加载图片像素栅格。
 */
public final class ImagePatternRaster {
    private final int width;
    private final int height;
    private final int[] pixels;

    private ImagePatternRaster(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public static ImagePatternRaster load(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            throw new IOException("Image file not found: " + file);
        }
        try (InputStream input = Files.newInputStream(file)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unsupported image format: " + file.getFileName());
            }
            return fromBufferedImage(image);
        }
    }

    public static ImagePatternRaster fromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        return new ImagePatternRaster(width, height, pixels);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int sampleArgb(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return pixels[y * width + x];
    }

    public boolean isTransparent(int argb, int alphaThreshold) {
        int alpha = (argb >>> 24) & 0xFF;
        return alpha < alphaThreshold;
    }
}
