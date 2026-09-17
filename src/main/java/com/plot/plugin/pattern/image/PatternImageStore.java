package com.plot.plugin.pattern.image;

import com.plot.plugin.pattern.model.ImagePatternConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
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
        if (relativePath == null || relativePath.isBlank() || pluginDataDir == null) {
            return null;
        }
        Path base = pluginDataDir.toAbsolutePath().normalize();
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute()) {
            return null;
        }
        Path resolved = base.resolve(normalized).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    public static ImportedImage importImage(Path pluginDataDir, String footprintId, Path sourceFile)
            throws IOException {
        if (pluginDataDir == null || sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Source image not found");
        }
        Files.createDirectories(imagesDir(pluginDataDir));
        String extension = PatternImageFormats.requireSupportedExtension(sourceFile);
        String relativePath = "images/" + footprintId + extension;
        Path target = resolveImagePath(pluginDataDir, relativePath);
        if (target == null || !isSafeAssetId(footprintId)) {
            throw new IOException("Invalid footprint image id");
        }
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);

        ImagePatternRaster raster = ImagePatternRaster.load(target);
        return new ImportedImage(relativePath, raster.width(), raster.height());
    }

    /**
     * 导入新图片；新文件写入成功后再删除旧关联文件（路径不同时）。
     */
    public static ImportedImage importReplacing(
            Path pluginDataDir,
            String footprintId,
            Path sourceFile,
            String previousRelativePath) throws IOException {
        ImportedImage imported = importImage(pluginDataDir, footprintId, sourceFile);
        if (previousRelativePath != null
                && !previousRelativePath.isBlank()
                && !Objects.equals(previousRelativePath, imported.relativePath())) {
            deleteImage(pluginDataDir, previousRelativePath);
        }
        return imported;
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

    static boolean isSafeAssetId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]+");
    }

    public record ImportedImage(String relativePath, int width, int height) {
        public void applyTo(ImagePatternConfig config) {
            config.setImagePath(relativePath);
            config.setImageWidth(width);
            config.setImageHeight(height);
        }
    }
}
