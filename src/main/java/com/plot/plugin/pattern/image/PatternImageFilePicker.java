package com.plot.plugin.pattern.image;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 原生文件选择器（LWJGL tinyfiledialogs），适用于 Minecraft headless 环境。
 */
public final class PatternImageFilePicker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PatternImageFilePicker");
    private static final String[] FILTER_PATTERNS = {
        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"
    };

    private PatternImageFilePicker() {
    }

    public static Optional<Path> pickImage(String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(FILTER_PATTERNS.length);
            for (String pattern : FILTER_PATTERNS) {
                filters.put(stack.UTF8(pattern));
            }
            filters.flip();

            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                title,
                null,
                filters,
                "Image files",
                false);
            if (selected == null || selected.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Path.of(selected));
        } catch (Exception e) {
            LOGGER.warn("打开图片文件选择器失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
