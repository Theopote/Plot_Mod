package com.masterplanner.ui.dialog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件对话框工具类
 * 提供打开文件、保存文件、选择文件夹等功能
 */
public class FileDialogUtil {
    private static final Logger LOGGER = LogManager.getLogger("FileDialogUtil");

    /**
     * 显示文件夹选择对话框
     * @param initialDirectory 初始目录
     * @return 选择的文件夹路径，如果取消则返回null
     */
    public static String showFolderDialog(String initialDirectory) {
        LOGGER.info("调用showFolderDialog，初始目录: {}", initialDirectory);
        
        // 使用AtomicReference来存储结果，因为我们需要在lambda表达式中修改它
        AtomicReference<String> result = new AtomicReference<>();
        
        // 创建一个锁对象，用于同步
        Object lock = new Object();
        
        // 标记操作是否完成
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        
        // 在Swing线程中执行文件选择对话框
        SwingUtilities.invokeLater(() -> {
            try {
                LOGGER.info("在Swing线程中创建文件选择对话框");
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("选择文件夹");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                
                // 设置初始目录
                if (initialDirectory != null && !initialDirectory.isEmpty()) {
                    File initialDir = new File(initialDirectory);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        fileChooser.setCurrentDirectory(initialDir);
                        LOGGER.info("设置初始目录: {}", initialDir.getAbsolutePath());
                    }
                }
                
                // 显示对话框
                LOGGER.info("显示文件夹选择对话框");
                int returnValue = fileChooser.showOpenDialog(null);
                
                synchronized (lock) {
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        result.set(selectedFile.getAbsolutePath());
                        LOGGER.info("用户选择了文件夹: {}", selectedFile.getAbsolutePath());
                    } else {
                        result.set(null);
                        LOGGER.info("用户取消了文件夹选择");
                    }
                    
                    // 标记操作完成
                    done.set(true);
                    
                    // 通知等待的线程
                    lock.notifyAll();
                }
            } catch (Exception e) {
                LOGGER.error("显示文件夹选择对话框时发生错误", e);
                
                synchronized (lock) {
                    result.set(null);
                    done.set(true);
                    lock.notifyAll();
                }
            }
        });
        
        // 等待对话框操作完成
        synchronized (lock) {
            try {
                // 最多等待10秒
                long startTime = System.currentTimeMillis();
                while (!done.get() && System.currentTimeMillis() - startTime < 10000) {
                    lock.wait(100);
                }
                
                if (!done.get()) {
                    LOGGER.warn("文件夹选择对话框操作超时");
                    return null;
                }
            } catch (InterruptedException e) {
                LOGGER.error("等待文件夹选择结果时被中断", e);
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        LOGGER.info("showFolderDialog返回结果: {}", result.get());
        return result.get();
    }

    /**
     * 显示打开文件对话框
     * @param initialDirectory 初始目录
     * @param fileExtension 文件扩展名（不包含点，例如"mp"）
     * @param description 文件类型描述
     * @return 选择的文件路径，如果取消则返回null
     */
    public static String showOpenFileDialog(String initialDirectory, String fileExtension, String description) {
        LOGGER.info("调用showOpenFileDialog，初始目录: {}", initialDirectory);
        
        // 使用AtomicReference来存储结果
        AtomicReference<String> result = new AtomicReference<>();
        
        // 创建一个锁对象，用于同步
        Object lock = new Object();
        
        // 标记操作是否完成
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        
        // 在Swing线程中执行文件选择对话框
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("打开文件");
                
                // 设置文件过滤器
                if (fileExtension != null && !fileExtension.isEmpty()) {
                    FileNameExtensionFilter filter = new FileNameExtensionFilter(
                        description, fileExtension
                    );
                    fileChooser.setFileFilter(filter);
                }
                
                // 设置初始目录
                if (initialDirectory != null && !initialDirectory.isEmpty()) {
                    File initialDir = new File(initialDirectory);
                    if (initialDir.exists()) {
                        if (initialDir.isDirectory()) {
                            fileChooser.setCurrentDirectory(initialDir);
                        } else {
                            fileChooser.setCurrentDirectory(initialDir.getParentFile());
                            fileChooser.setSelectedFile(initialDir);
                        }
                    }
                }
                
                // 显示对话框
                int returnValue = fileChooser.showOpenDialog(null);
                
                synchronized (lock) {
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        result.set(selectedFile.getAbsolutePath());
                    } else {
                        result.set(null);
                    }
                    
                    // 标记操作完成
                    done.set(true);
                    
                    // 通知等待的线程
                    lock.notifyAll();
                }
            } catch (Exception e) {
                LOGGER.error("显示打开文件对话框时发生错误", e);
                
                synchronized (lock) {
                    result.set(null);
                    done.set(true);
                    lock.notifyAll();
                }
            }
        });
        
        // 等待对话框操作完成
        synchronized (lock) {
            try {
                // 最多等待10秒
                long startTime = System.currentTimeMillis();
                while (!done.get() && System.currentTimeMillis() - startTime < 10000) {
                    lock.wait(100);
                }
                
                if (!done.get()) {
                    LOGGER.warn("打开文件对话框操作超时");
                    return null;
                }
            } catch (InterruptedException e) {
                LOGGER.error("等待打开文件结果时被中断", e);
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        return result.get();
    }

    /**
     * 显示保存文件对话框
     * @param initialDirectory 初始目录
     * @param defaultFileName 默认文件名
     * @param fileExtension 文件扩展名（不包含点，例如"mp"）
     * @param description 文件类型描述
     * @return 选择的文件路径，如果取消则返回null
     */
    public static String showSaveFileDialog(String initialDirectory, String defaultFileName, 
                                           String fileExtension, String description) {
        LOGGER.info("调用showSaveFileDialog，初始目录: {}", initialDirectory);
        
        // 使用AtomicReference来存储结果
        AtomicReference<String> result = new AtomicReference<>();
        
        // 创建一个锁对象，用于同步
        Object lock = new Object();
        
        // 标记操作是否完成
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        
        // 在Swing线程中执行文件选择对话框
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("保存文件");
                
                // 设置文件过滤器
                if (fileExtension != null && !fileExtension.isEmpty()) {
                    FileNameExtensionFilter filter = new FileNameExtensionFilter(
                        description, fileExtension
                    );
                    fileChooser.setFileFilter(filter);
                }
                
                // 设置初始目录和文件名
                if (initialDirectory != null && !initialDirectory.isEmpty()) {
                    File initialDir = new File(initialDirectory);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        fileChooser.setCurrentDirectory(initialDir);
                    }
                }
                
                if (defaultFileName != null && !defaultFileName.isEmpty()) {
                    fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), defaultFileName));
                }
                
                // 显示对话框
                int returnValue = fileChooser.showSaveDialog(null);
                
                synchronized (lock) {
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        String filePath = selectedFile.getAbsolutePath();
                        
                        // 确保文件有正确的扩展名
                        if (fileExtension != null && !fileExtension.isEmpty() && 
                            !filePath.toLowerCase().endsWith("." + fileExtension.toLowerCase())) {
                            filePath += "." + fileExtension;
                        }
                        
                        result.set(filePath);
                    } else {
                        result.set(null);
                    }
                    
                    // 标记操作完成
                    done.set(true);
                    
                    // 通知等待的线程
                    lock.notifyAll();
                }
            } catch (Exception e) {
                LOGGER.error("显示保存文件对话框时发生错误", e);
                
                synchronized (lock) {
                    result.set(null);
                    done.set(true);
                    lock.notifyAll();
                }
            }
        });
        
        // 等待对话框操作完成
        synchronized (lock) {
            try {
                // 最多等待10秒
                long startTime = System.currentTimeMillis();
                while (!done.get() && System.currentTimeMillis() - startTime < 10000) {
                    lock.wait(100);
                }
                
                if (!done.get()) {
                    LOGGER.warn("保存文件对话框操作超时");
                    return null;
                }
            } catch (InterruptedException e) {
                LOGGER.error("等待保存文件结果时被中断", e);
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        return result.get();
    }
}