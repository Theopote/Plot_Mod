package com.plot.plugin.pattern.image;

import com.plot.plugin.pattern.model.ImagePatternConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/**
 * 图案插件图片持久化（复制到插件数据目录）。
 */
public final class PatternImageStore {
    private PatternImageStore() {
    }

    public static Path imagesDir(Path pluginDataDir) {
        return pluginDataDir.resolve("images");
    }

    public static Path resolveImagePath(Path pluginDataDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute()) {
            return normalized;
        }
        return pluginDataDir.resolve(normalized).normalize();
    }

    public static ImportedImage importImage(Path pluginDataDir, String footprintId, Path sourceFile)
            throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Source image not found");
        }
        Files.createDirectories(imagesDir(pluginDataDir));
        String extension = extensionOf(sourceFile);
        String relativePath = "images/" + footprintId + extension;
        Path target = pluginDataDir.resolve(relativePath);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);

        ImagePatternRaster raster = ImagePatternRaster.load(target);
        return new ImportedImage(relativePath, raster.width(), raster.height());
    }

    public static Optional<ImagePatternRaster> loadRaster(Path pluginDataDir, ImagePatternConfig config) {
        if (config == null || !config.hasImage()) {
            return Optional.empty();
        }
        Path file = resolveImagePath(pluginDataDir, config.getImagePath());
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ImagePatternRaster.load(file));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public static void deleteImage(Path pluginDataDir, String relativePath) {
        Path file = resolveImagePath(pluginDataDir, relativePath);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) {
            return ".png";
        }
        String ext = name.substring(dot);
        return switch (ext) {
            case ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp" -> ext;
            default -> ".png";
        };
    }

    public record ImportedImage(String relativePath, int width, int height) {
        public void applyTo(ImagePatternConfig config) {
            config.setImagePath(relativePath);
            config.setImageWidth(width);
            config.setImageHeight(height);
        }
    }
}
