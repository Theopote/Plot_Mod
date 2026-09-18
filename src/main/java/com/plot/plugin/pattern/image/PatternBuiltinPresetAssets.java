package com.plot.plugin.pattern.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 从 classpath 安装内置图片预设资产到 {@code preset-assets/builtin/}。
 */
public final class PatternBuiltinPresetAssets {
    private static final String RESOURCE_PREFIX = "assets/plot/pattern/builtin/";
    private static final String PRESET_BUILTIN_DIR = "preset-assets/builtin/";
    private static final List<String> BUILTIN_SLUGS = List.of("zebra_crossing");

    private PatternBuiltinPresetAssets() {
    }

    public static void installAll(Path pluginDataDir) throws IOException {
        if (pluginDataDir == null) {
            return;
        }
        for (String slug : BUILTIN_SLUGS) {
            install(pluginDataDir, slug);
        }
    }

    public static String relativePath(String slug) {
        return PRESET_BUILTIN_DIR + slug + ".png";
    }

    public static int[] readDimensions(Path pluginDataDir, String slug) throws IOException {
        Path file = PatternImageStore.resolveImagePath(pluginDataDir, relativePath(slug));
        if (file == null || !Files.exists(file)) {
            throw new IOException("Builtin preset asset missing: " + slug);
        }
        ImagePatternRaster raster = ImagePatternRaster.load(file);
        return new int[] {raster.width(), raster.height()};
    }

    private static void install(Path pluginDataDir, String slug) throws IOException {
        String resource = RESOURCE_PREFIX + slug + ".png";
        try (InputStream input = PatternBuiltinPresetAssets.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing builtin pattern asset: " + resource);
            }
            Path targetDir = pluginDataDir.resolve(PRESET_BUILTIN_DIR);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(slug + ".png");
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
