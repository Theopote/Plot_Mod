package com.plot.plugin.pattern.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** 图案插件支持的图片扩展名（与 {@link PatternImageFilePicker} 过滤器一致）。 */
public final class PatternImageFormats {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");

    private PatternImageFormats() {
    }

    public static String requireSupportedExtension(Path file) throws IOException {
        if (file == null || file.getFileName() == null) {
            throw new IOException("Unsupported image format: missing file name");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) {
            throw new IOException("Unsupported image format: missing extension");
        }
        String extension = name.substring(dot);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IOException("Unsupported image format: " + extension);
        }
        return extension;
    }
}
