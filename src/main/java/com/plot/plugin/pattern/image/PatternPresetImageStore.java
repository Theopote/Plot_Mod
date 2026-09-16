package com.plot.plugin.pattern.image;

import com.plot.plugin.pattern.model.ImagePatternConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 预设专用图片资产（与 footprint 的 images/ 目录隔离）。
 */
public final class PatternPresetImageStore {
    private static final String PRESET_ASSETS_DIR = "preset-assets";

    private PatternPresetImageStore() {
    }

    public static Path presetAssetsDir(Path pluginDataDir) {
        return pluginDataDir.resolve(PRESET_ASSETS_DIR);
    }

    public static String copyFootprintImageToPresetAsset(
            Path pluginDataDir,
            String presetId,
            ImagePatternConfig source)
            throws IOException {
        if (pluginDataDir == null || presetId == null || presetId.isBlank() || source == null || !source.hasImage()) {
            throw new IOException("Invalid preset image copy request");
        }
        Path sourceFile = PatternImageStore.resolveImagePath(pluginDataDir, source.getImagePath());
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Source image not found: " + source.getImagePath());
        }
        Files.createDirectories(presetAssetsDir(pluginDataDir));
        String extension = extensionOf(sourceFile);
        String relativePath = PRESET_ASSETS_DIR + "/" + presetId + extension;
        Path target = pluginDataDir.resolve(relativePath);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return relativePath;
    }

    public static void deletePresetAsset(Path pluginDataDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || pluginDataDir == null) {
            return;
        }
        if (!relativePath.startsWith(PRESET_ASSETS_DIR + "/")) {
            return;
        }
        Path file = PatternImageStore.resolveImagePath(pluginDataDir, relativePath);
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
}
