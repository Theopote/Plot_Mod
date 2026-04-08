package com.plot.ui.dialog;

import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.infrastructure.event.file.FileImportedEvent;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * 用于导入矢量图形文件到Plot中
 */
public class ImportFileDialog {
    private static final Logger LOGGER = LogManager.getLogger("ImportFileDialog");
    private static ImportFileDialog SHARED_INSTANCE;

    // === 常量定义 ===
    private static final String DIALOG_TITLE = "导入文件";
    private static final String[] SUPPORTED_EXTENSIONS = {".svg", ".dxf", ".dwg", ".ai", ".pdf"};  // 支持的文件扩展名
    private static final int MAX_VISIBLE_FILES = 10;  // 文件列表最大显示行数

    // === 状态字段 ===
    private boolean isVisible = false;          // 对话框是否可见
    private final ImString filePath;            // 文件路径输入缓冲区
    private String lastBrowsedPath = System.getProperty("user.home");  // 上次浏览的路径
    private List<File> currentFiles = new ArrayList<>();  // 当前目录下的文件列表
    private File selectedFile = null;           // 当前选中的文件对象
    private final ImString fileFilterInput;     // 文件过滤输入

    // === 依赖项 ===
    private final AppState appState;            // 应用程序状态
    private final EventBus eventBus;            // 事件总线
    private final Consumer<String> showWarningDialog;  // 警告对话框回调

    public static synchronized ImportFileDialog getSharedInstance() {
        if (SHARED_INSTANCE == null) {
            SHARED_INSTANCE = new ImportFileDialog(
                    AppState.getInstance(),
                    EventBus.getInstance(),
                    ImportFileDialog::publishWarningMessage
            );
        }
        return SHARED_INSTANCE;
    }

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
        
        // 重置选中的文件
        selectedFile = null;
    }

    /**
     * 隐藏对话框并重置状态
     */
    private void hide() {
        isVisible = false;
        selectedFile = null;
    }

    private static void publishWarningMessage(String message) {
        EventBus.getInstance().publish(new Events.WarningEvent("ImportFileDialog", message));
    }

    private void showWarning(String message) {
        if (showWarningDialog != null) {
            showWarningDialog.accept(message);
        } else {
            publishWarningMessage(message);
        }
    }

    /**
     * 导入选中的文件
     */
    private void importFile() {
        importFile(selectedFile);
    }

    private void importFile(File fileToImport) {
        if (fileToImport == null) {
            showWarning("请先选择要导入的文件");
            return;
        }

        if (fileToImport.isDirectory()) {
            showWarning("请选择要导入的文件，而不是目录");
            return;
        }

        if (!fileToImport.exists()) {
            showWarning("选择的文件不存在");
            return;
        }

        if (!isSupportedFile(fileToImport)) {
            showWarning("不支持的文件格式，请选择以下格式之一: " +
                    String.join(", ", SUPPORTED_EXTENSIONS));
            return;
        }

        try {
            String fullPathStr = fileToImport.getAbsolutePath();
            eventBus.publish(new FileImportedEvent(fullPathStr));

            LOGGER.info("成功导入文件: {}", fullPathStr);

            hide();
            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            showWarning("导入文件失败: " + e.getMessage());
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
                    
                    selectedFile = null;
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
                selectedFile = null;
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
                selectedFile = null;
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

        float totalWidth = DialogStyleManager.DialogWidth.WIDE.value;

        // 固定宽度，交由内容驱动高度，避免系统缩放或字体变大时底部按钮被裁剪
        ImGui.setNextWindowSize(totalWidth, 0.0f, ImGuiCond.Always);

        // 只在第一次显示时设置窗口位置（水平居中）
        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(
                ImGui.getMainViewport().getCenter().x,
                ImGui.getMainViewport().getCenter().y,
                ImGuiCond.Appearing,
                0.5f,
                0.5f
            );
        }

        // 设置窗口标志
        int windowFlags = ImGuiWindowFlags.NoResize |
                         ImGuiWindowFlags.NoSavedSettings |
                         ImGuiWindowFlags.NoScrollbar |
                         ImGuiWindowFlags.AlwaysAutoResize;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                if (DialogStyleManager.renderTopRightCloseButton("import_file")) {
                    hide();
                    ImGui.closeCurrentPopup();
                    ImGui.endPopup();
                    return;
                }

                // 计算布局尺寸
                float contentWidth = DialogStyleManager.getContentWidth();

                // 过滤文件列表
                String filterText = fileFilterInput.get().toLowerCase();
                List<File> filteredFiles = currentFiles.stream()
                    .filter(file -> filterText.isEmpty() ||
                                   file.getName().toLowerCase().contains(filterText))
                    .collect(Collectors.toList());

                // 文件列表采用动态高度：按行高和可见行数计算，并保底显示最少几行
                float rowHeight = ImGui.getTextLineHeightWithSpacing();
                int visibleRows = Math.max(4, Math.min(MAX_VISIBLE_FILES, filteredFiles.size()));
                float fileListHeight = rowHeight * visibleRows + DialogStyleManager.ITEM_SPACING * 2.0f;
                
                DialogLayoutHelper.beginSection("文件浏览");
                DialogLayoutHelper.helpText("支持 .svg、.dxf、.dwg、.ai、.pdf，双击文件可直接导入。");
                DialogLayoutHelper.subsectionGap();

                // === 路径栏 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("位置：");
                ImGui.sameLine();

                float upButtonWidth = 30.0f;
                DialogLayoutHelper.reserveTrailingButton(upButtonWidth);
                if (ImGui.inputText("##import_file_path", filePath, ImGuiInputTextFlags.ReadOnly)) {
                    updateFileList(filePath.get());
                }

                if (DialogLayoutHelper.trailingButton("↑", upButtonWidth)) {
                    navigateUp();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("上一级目录");
                }

                DialogLayoutHelper.subsectionGap();

                // === 过滤栏 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("过滤：");
                ImGui.sameLine();

                ImGui.setNextItemWidth(-1.0f);
                if (ImGui.inputText("##file_filter", fileFilterInput)) {
                    selectedFile = null;
                }

                DialogLayoutHelper.subsectionGap();

                // === 文件列表 ===
                ImGui.beginChild("##file_list", contentWidth, fileListHeight, true);
                
                // 渲染文件列表
                for (int i = 0; i < filteredFiles.size(); i++) {
                    File file = filteredFiles.get(i);
                    String displayName = file.isDirectory() ? 
                                        "[目录] " + file.getName() : 
                                        file.getName();
                    
                    boolean isSelected = selectedFile != null && selectedFile.equals(file);
                    if (ImGui.selectable(displayName, isSelected, ImGuiSelectableFlags.AllowDoubleClick)) {
                        selectedFile = file;
                        if (file.isDirectory()) {
                            if (ImGui.isMouseDoubleClicked(0)) {
                                navigateToDirectory(file);
                            }
                        } else if (ImGui.isMouseDoubleClicked(0)) {
                            importFile(file);
                        }
                    }
                }
                
                ImGui.endChild();

                DialogLayoutHelper.endSection();
                DialogLayoutHelper.beginFooter();
                DialogLayoutHelper.FooterResult action =
                        DialogLayoutHelper.footerConfirmCancelRight("取消", "导入", contentWidth);

                if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                    importFile();
                }

                if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
                    hide();
                    ImGui.closeCurrentPopup();
                }
                
                ImGui.endPopup();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }
}