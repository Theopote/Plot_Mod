package com.plot.plugin.pattern.image;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 系统文件选择器（在后台线程打开，避免阻塞渲染线程）。
 */
public final class PatternImageFilePicker {
    private PatternImageFilePicker() {
    }

    public static CompletableFuture<Optional<Path>> pickImageAsync(String title) {
        return CompletableFuture.supplyAsync(() -> pickImage(title));
    }

    public static Optional<Path> pickImage(String title) {
        FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp")
                || lower.endsWith(".webp");
        });
        dialog.setVisible(true);
        String fileName = dialog.getFile();
        String directory = dialog.getDirectory();
        if (fileName == null || directory == null) {
            return Optional.empty();
        }
        return Optional.of(new File(directory, fileName).toPath());
    }
}
