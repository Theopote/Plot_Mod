package com.plot.plugin.pattern.image;

import com.plot.plugin.pattern.model.ImagePatternConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * 图案插件图片持久化（复制到插件数据目录）。
 * <p>
 * Footprint 图片使用不可变资产路径 {@code images/{footprintId}/{assetId}.ext}，
 * 导入时只新增文件、不覆盖或删除旧资产，以便项目 Undo/Redo 能恢复历史引用。
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
        if (!isSafeAssetId(footprintId)) {
            throw new IOException("Invalid footprint image id");
        }

        String extension = PatternImageFormats.requireSupportedExtension(sourceFile);
        String assetId = UUID.randomUUID().toString().replace("-", "");
        String relativePath = "images/" + footprintId + "/" + assetId + extension;

        Path imagesBase = imagesDir(pluginDataDir).toAbsolutePath().normalize();
        Files.createDirectories(imagesBase.resolve(footprintId));
        Path target = resolveImagePath(pluginDataDir, relativePath);
        if (target == null || !target.startsWith(imagesBase)) {
            throw new IOException("Invalid footprint image path");
        }
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        try {
            ImagePatternRaster raster = ImagePatternRaster.load(target);
            return new ImportedImage(relativePath, raster.width(), raster.height());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best effort cleanup for failed import transaction
            }
            throw e;
        }
    }

    /**
     * @deprecated 旧资产不再删除；请使用 {@link #importImage(Path, String, Path)}。
     */
    @Deprecated
    public static ImportedImage importReplacing(
            Path pluginDataDir,
            String footprintId,
            Path sourceFile,
            String previousRelativePath) throws IOException {
        return importImage(pluginDataDir, footprintId, sourceFile);
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

    /**
     * 物理删除图片资产。Footprint 删除与图片替换不应调用此方法，以免破坏 Undo/Redo。
     */
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
