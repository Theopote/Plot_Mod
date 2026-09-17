package com.plot.plugin.pattern.image;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统文件选择器。必须在 Minecraft 客户端主线程调用，避免后台线程打开 AWT 对话框失败。
 */
public final class PatternImageFilePicker {
    private PatternImageFilePicker() {
    }

    public static Optional<Path> pickImage(String title) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                return showChooser(title);
            }
            AtomicReference<Optional<Path>> result = new AtomicReference<>(Optional.empty());
            SwingUtilities.invokeAndWait(() -> result.set(showChooser(title)));
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ignored) {
            return showFileDialog(title);
        }
    }

    private static Optional<Path> showChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Image",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "bmp",
            "webp"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        File file = chooser.getSelectedFile();
        return file != null ? Optional.of(file.toPath()) : Optional.empty();
    }

    private static Optional<Path> showFileDialog(String title) {
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
