package com.plot.ui.dialog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 文件对话框工具类
 * 提供打开文件、保存文件、选择文件夹等功能
 */
public class FileDialogUtil {
    private static final Logger LOGGER = LogManager.getLogger("FileDialogUtil");

    private static void configureChooser(JFileChooser fileChooser, String initialPath,
                                         String fileExtension, String description, boolean allowFileSelection) {
        if (fileExtension != null && !fileExtension.isEmpty()) {
            fileChooser.setFileFilter(new FileNameExtensionFilter(description, fileExtension));
        }

        if (initialPath == null || initialPath.isEmpty()) {
            return;
        }

        File initialFile = new File(initialPath);
        if (!initialFile.exists()) {
            return;
        }

        if (initialFile.isDirectory()) {
            fileChooser.setCurrentDirectory(initialFile);
        } else if (allowFileSelection) {
            File parent = initialFile.getParentFile();
            if (parent != null && parent.exists()) {
                fileChooser.setCurrentDirectory(parent);
            }
            fileChooser.setSelectedFile(initialFile);
        }
    }

    private static void runChooserAsync(String dialogTitle,
                                        Consumer<JFileChooser> configurator,
                                        Function<JFileChooser, Integer> dialogOpener,
                                        Function<File, String> resultMapper,
                                        Consumer<String> callback) {
        LOGGER.info("异步打开文件对话框: {}", dialogTitle);

        SwingUtilities.invokeLater(() -> {
            String result = null;
            try {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle(dialogTitle);
                if (configurator != null) {
                    configurator.accept(fileChooser);
                }

                int returnValue = dialogOpener.apply(fileChooser);
                if (returnValue == JFileChooser.APPROVE_OPTION && fileChooser.getSelectedFile() != null) {
                    File selectedFile = fileChooser.getSelectedFile();
                    result = resultMapper != null
                            ? resultMapper.apply(selectedFile)
                            : selectedFile.getAbsolutePath();
                }
            } catch (Exception e) {
                LOGGER.error("显示文件对话框时发生错误: {}", dialogTitle, e);
            }

            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    private static String waitForAsyncResult(Consumer<Consumer<String>> starter, String timeoutMessage) {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        starter.accept(path -> {
            result.set(path);
            latch.countDown();
        });

        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                LOGGER.warn(timeoutMessage);
                return null;
            }
        } catch (InterruptedException e) {
            LOGGER.error("等待文件对话框结果时被中断", e);
            Thread.currentThread().interrupt();
            return null;
        }

        return result.get();
    }

    public static void showFolderDialogAsync(String initialDirectory, Consumer<String> callback) {
        runChooserAsync(
                "选择文件夹",
                fileChooser -> {
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    configureChooser(fileChooser, initialDirectory, null, null, false);
                },
                fileChooser -> fileChooser.showOpenDialog(null),
                File::getAbsolutePath,
                callback
        );
    }

    public static void showOpenFileDialogAsync(String initialDirectory, String fileExtension,
                                               String description, Consumer<String> callback) {
        runChooserAsync(
                "打开文件",
                fileChooser -> configureChooser(fileChooser, initialDirectory, fileExtension, description, true),
                fileChooser -> fileChooser.showOpenDialog(null),
                File::getAbsolutePath,
                callback
        );
    }

    public static void showSaveFileDialogAsync(String initialDirectory, String defaultFileName,
                                               String fileExtension, String description,
                                               Consumer<String> callback) {
        runChooserAsync(
                "保存文件",
                fileChooser -> {
                    configureChooser(fileChooser, initialDirectory, fileExtension, description, false);
                    if (defaultFileName != null && !defaultFileName.isEmpty()) {
                        File baseDir = fileChooser.getCurrentDirectory();
                        fileChooser.setSelectedFile(baseDir != null
                                ? new File(baseDir, defaultFileName)
                                : new File(defaultFileName));
                    }
                },
                fileChooser -> fileChooser.showSaveDialog(null),
                selectedFile -> {
                    String filePath = selectedFile.getAbsolutePath();
                    if (fileExtension != null && !fileExtension.isEmpty()
                            && !filePath.toLowerCase().endsWith("." + fileExtension.toLowerCase())) {
                        filePath += "." + fileExtension;
                    }
                    return filePath;
                },
                callback
        );
    }
}