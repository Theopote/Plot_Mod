package com.masterplanner.ui.dialog;

import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.file.FileImportedEvent;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.masterplanner.infrastructure.event.EventBus;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 导入文件对话框
 * 用于导入矢量图形文件到MasterPlanner中
 */
public class ImportFileDialog {
    private static final Logger LOGGER = LogManager.getLogger("ImportFileDialog");

    // === 常量定义 ===
    private static final float SPACING = 4.0f;  // 控件之间的间距
    private static final String DIALOG_TITLE = "导入文件";
    private static final String[] SUPPORTED_EXTENSIONS = {".svg", ".dxf", ".dwg", ".ai", ".pdf"};  // 支持的文件扩展名
    private static final int MAX_VISIBLE_FILES = 10;  // 文件列表最大显示行数

    // === 状态字段 ===
    private boolean isVisible = false;          // 对话框是否可见
    private final ImString filePath;            // 文件路径输入缓冲区
    private String lastBrowsedPath = System.getProperty("user.home");  // 上次浏览的路径
    private List<File> currentFiles = new ArrayList<>();  // 当前目录下的文件列表
    private int selectedFileIndex = -1;         // 当前选中的文件索引
    private final ImString fileFilterInput;     // 文件过滤输入

    // === 依赖项 ===
    private final AppState appState;            // 应用程序状态
    private final EventBus eventBus;            // 事件总线
    private final Consumer<String> showWarningDialog;  // 警告对话框回调

    public ImportFileDialog(
            AppState appState,
            EventBus eventBus,
            Consumer<String> showWarningDialog) {
        this.appState = appState;
        this.eventBus = eventBus;
        this.showWarningDialog = showWarningDialog;
        this.filePath = new ImString(1024);
        this.fileFilterInput = new ImString(256);
        
        // 设置默认文件路径为用户文档目录
        String documentsPath = System.getProperty("user.home") + File.separator + "Documents";
        if (Files.exists(Paths.get(documentsPath))) {
            filePath.set(documentsPath);
            lastBrowsedPath = documentsPath;
            updateFileList(documentsPath);
        } else {
            filePath.set(System.getProperty("user.home"));
            updateFileList(System.getProperty("user.home"));
        }
    }

    /**
     * 显示对话框
     */
    public void show() {
        isVisible = true;
        ImGui.openPopup(DIALOG_TITLE);
        
        // 重新加载当前目录的文件列表
        updateFileList(filePath.get());
        
        // 清空文件过滤输入
        fileFilterInput.set("");
        
        // 重置选中的文件索引
        selectedFileIndex = -1;
    }

    /**
     * 隐藏对话框并重置状态
     */
    private void hide() {
        isVisible = false;
        selectedFileIndex = -1;
    }

    /**
     * 导入选中的文件
     */
    private void importFile() {
        if (selectedFileIndex < 0 || selectedFileIndex >= currentFiles.size()) {
            showWarningDialog.accept("请先选择要导入的文件");
            return;
        }
        
        File selectedFile = currentFiles.get(selectedFileIndex);
        
        // 检查文件是否存在
        if (!selectedFile.exists()) {
            showWarningDialog.accept("选择的文件不存在");
            return;
        }
        
        // 检查文件是否是支持的格式
        boolean isSupported = false;
        String fileName = selectedFile.getName().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                isSupported = true;
                break;
            }
        }
        
        if (!isSupported) {
            showWarningDialog.accept("不支持的文件格式，请选择以下格式之一: " + 
                                    String.join(", ", SUPPORTED_EXTENSIONS));
            return;
        }

        try {
            // 发布文件导入事件
            String fullPathStr = selectedFile.getAbsolutePath();
            eventBus.publish(new FileImportedEvent(fullPathStr));
            
            LOGGER.info("成功导入文件: {}", fullPathStr);
            
            hide();
            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            showWarningDialog.accept("导入文件失败: " + e.getMessage());
            LOGGER.error("导入文件失败", e);
        }
    }

    /**
     * 更新文件列表
     */
    private void updateFileList(String directoryPath) {
        try {
            File directory = new File(directoryPath);
            if (directory.exists() && directory.isDirectory()) {
                // 获取目录下的所有文件和子目录
                File[] files = directory.listFiles();
                if (files != null) {
                    // 过滤并排序文件列表
                    currentFiles = Arrays.stream(files)
                        .filter(file -> file.isDirectory() || isSupportedFile(file))
                        .sorted((f1, f2) -> {
                            // 目录排在前面
                            if (f1.isDirectory() && !f2.isDirectory()) return -1;
                            if (!f1.isDirectory() && f2.isDirectory()) return 1;
                            // 按名称排序
                            return f1.getName().compareToIgnoreCase(f2.getName());
                        })
                        .collect(Collectors.toList());
                    
                    LOGGER.info("已加载目录 {} 中的 {} 个文件/文件夹", directoryPath, currentFiles.size());
                } else {
                    currentFiles.clear();
                    LOGGER.warn("无法列出目录 {} 中的文件", directoryPath);
                }
            } else {
                currentFiles.clear();
                LOGGER.warn("目录 {} 不存在或不是一个目录", directoryPath);
            }
        } catch (Exception e) {
            currentFiles.clear();
            LOGGER.error("更新文件列表时发生错误", e);
        }
    }

    /**
     * 检查文件是否是支持的格式
     */
    private boolean isSupportedFile(File file) {
        if (file.isDirectory()) return true;
        
        String fileName = file.getName().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 导航到上一级目录
     */
    private void navigateUp() {
        try {
            File currentDir = new File(filePath.get());
            File parentDir = currentDir.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                filePath.set(parentDir.getAbsolutePath());
                lastBrowsedPath = parentDir.getAbsolutePath();
                updateFileList(parentDir.getAbsolutePath());
                selectedFileIndex = -1;
            }
        } catch (Exception e) {
            LOGGER.error("导航到上一级目录时发生错误", e);
        }
    }

    /**
     * 导航到选中的目录
     */
    private void navigateToDirectory(File directory) {
        try {
            if (directory.exists() && directory.isDirectory()) {
                filePath.set(directory.getAbsolutePath());
                lastBrowsedPath = directory.getAbsolutePath();
                updateFileList(directory.getAbsolutePath());
                selectedFileIndex = -1;
            }
        } catch (Exception e) {
            LOGGER.error("导航到目录时发生错误", e);
        }
    }

    /**
     * 检查对话框是否可见
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * 渲染对话框
     */
    public void render() {
        if (!isVisible) return;

        float totalWidth = 500.0f;
        float totalHeight = 400.0f;
        
        // 设置窗口大小
        ImGui.setNextWindowSize(totalWidth, totalHeight, ImGuiCond.Always);

        // 只在第一次显示时设置窗口位置（水平居中）
        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(
                ImGui.getMainViewport().getCenter().x - totalWidth/2,
                ImGui.getMainViewport().getCenter().y - totalHeight/2,  // 垂直居中
                ImGuiCond.Appearing
            );
        }

        // 设置窗口标志
        int windowFlags = ImGuiWindowFlags.NoResize |
                         ImGuiWindowFlags.NoSavedSettings |
                         ImGuiWindowFlags.NoScrollbar;

        // 设置控件样式
        var currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置所有控件的基础样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.panelControlRounding);
        
        // 设置所有控件的颜色
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);          // 边框颜色
        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);    // 按钮背景色
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);   // 按钮悬停色
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);     // 按钮激活色
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);    // 控件背景色
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered); // 控件悬停色
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);   // 控件激活色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonActive);          // 选中项背景色
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);  // 选中项悬停色
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);    // 选中项激活色

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                // 计算布局尺寸
                float contentWidth = totalWidth - 16;           // 内容区域总宽度
                float contentHeight = totalHeight - 16;         // 内容区域总高度
                float pathBarHeight = 30.0f;                    // 路径栏高度
                float filterBarHeight = 30.0f;                  // 过滤栏高度
                float buttonBarHeight = 40.0f;                  // 按钮栏高度
                float fileListHeight = contentHeight - pathBarHeight - filterBarHeight - buttonBarHeight - 20.0f; // 文件列表高度
                
                // === 路径栏 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("位置：");
                ImGui.sameLine();
                
                // 路径输入框和上级按钮在同一行
                float upButtonWidth = 30.0f;
                float pathInputWidth = contentWidth - ImGui.getItemRectSize().x - upButtonWidth - SPACING * 3;
                
                ImGui.setNextItemWidth(pathInputWidth);
                if (ImGui.inputText("##import_file_path", filePath, ImGuiInputTextFlags.ReadOnly)) {
                    // 路径变更时更新文件列表
                    updateFileList(filePath.get());
                }
                
                ImGui.sameLine();
                if (ImGui.button("↑", upButtonWidth, 0)) {
                    navigateUp();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("上一级目录");
                }
                
                ImGui.spacing();
                
                // === 过滤栏 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("过滤：");
                ImGui.sameLine();
                
                float filterInputWidth = contentWidth - ImGui.getItemRectSize().x - SPACING * 2;
                ImGui.setNextItemWidth(filterInputWidth);
                if (ImGui.inputText("##file_filter", fileFilterInput)) {
                    // 过滤条件变更时重置选中项
                    selectedFileIndex = -1;
                }
                
                ImGui.spacing();
                
                // === 文件列表 ===
                ImGui.beginChild("##file_list", contentWidth, fileListHeight, true);
                
                // 过滤文件列表
                String filterText = fileFilterInput.get().toLowerCase();
                List<File> filteredFiles = currentFiles.stream()
                    .filter(file -> filterText.isEmpty() || 
                                   file.getName().toLowerCase().contains(filterText))
                    .toList();
                
                // 渲染文件列表
                for (int i = 0; i < filteredFiles.size(); i++) {
                    File file = filteredFiles.get(i);
                    String displayName = file.isDirectory() ? 
                                        "[目录] " + file.getName() : 
                                        file.getName();
                    
                    boolean isSelected = i == selectedFileIndex;
                    if (ImGui.selectable(displayName, isSelected, ImGuiSelectableFlags.AllowDoubleClick)) {
                        if (file.isDirectory()) {
                            if (ImGui.isMouseDoubleClicked(0)) {
                                // 双击目录时导航到该目录
                                navigateToDirectory(file);
                            } else {
                                // 单击选中目录
                                selectedFileIndex = i;
                            }
                        } else {
                            // 选中文件
                            selectedFileIndex = i;
                            
                            if (ImGui.isMouseDoubleClicked(0)) {
                                // 双击文件时导入
                                importFile();
                            }
                        }
                    }
                }
                
                ImGui.endChild();
                
                ImGui.spacing();
                
                // === 按钮区域 ===
                float buttonWidth = 100.0f;
                float buttonSpacing = 10.0f;
                
                // 右对齐按钮
                ImGui.setCursorPosX(contentWidth - 2 * buttonWidth - buttonSpacing);
                
                if (ImGui.button("导入", buttonWidth, 0) || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                    importFile();
                }
                
                ImGui.sameLine(0, buttonSpacing);
                
                if (ImGui.button("取消", buttonWidth, 0) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                    hide();
                    ImGui.closeCurrentPopup();
                }
                
                ImGui.endPopup();
            }
        } finally {
            // 恢复样式
            ImGui.popStyleVar(3);  // 弹出之前推入的3个样式变量
            ImGui.popStyleColor(10);  // 弹出之前推入的10个样式颜色
        }
    }
}