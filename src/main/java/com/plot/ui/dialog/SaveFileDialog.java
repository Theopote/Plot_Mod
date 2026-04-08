package com.plot.ui.dialog;

import com.plot.core.model.Project;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * 保存文件对话框
 * 用于保存Plot文件
 */
public class SaveFileDialog {
    private static final Logger LOGGER = LogManager.getLogger("SaveFileDialog");
    private static SaveFileDialog SHARED_INSTANCE;

    // === 常量定义 ===
    private static final String DIALOG_TITLE = "保存文件";
    private static final String DEFAULT_FILE_EXTENSION = ".mp";  // 默认文件扩展名

    private record FolderSelectionResult(String path) {
    }

    // === 状态字段 ===
    private boolean isVisible = false;          // 对话框是否可见
    private final ImString fileName;            // 文件名输入缓冲区
    private final ImString filePath;            // 文件路径输入缓冲区
    private String lastBrowsedPath = System.getProperty("user.home");  // 上次浏览的路径
    private volatile FolderSelectionResult pendingFolderSelection;
    private volatile boolean folderDialogInProgress = false;

    // === 依赖项 ===
    private final AppState appState;            // 应用程序状态
    private final EventBus eventBus;            // 事件总线
    private final Consumer<String> showWarningDialog;  // 警告对话框回调

    public static synchronized SaveFileDialog getSharedInstance() {
        if (SHARED_INSTANCE == null) {
            SHARED_INSTANCE = new SaveFileDialog(
                    AppState.getInstance(),
                    EventBus.getInstance(),
                    SaveFileDialog::publishWarningMessage
            );
        }
        return SHARED_INSTANCE;
    }

    public SaveFileDialog(
            AppState appState,
            EventBus eventBus,
            Consumer<String> showWarningDialog) {
        this.appState = appState;
        this.eventBus = eventBus;
        this.showWarningDialog = showWarningDialog;
        this.fileName = new ImString(256);
        this.filePath = new ImString(1024);
        
        // 设置默认文件路径为用户文档目录
        String documentsPath = System.getProperty("user.home") + File.separator + "Documents";
        if (Files.exists(Paths.get(documentsPath))) {
            filePath.set(documentsPath);
            lastBrowsedPath = documentsPath;
        } else {
            filePath.set(System.getProperty("user.home"));
        }
    }

    /**
     * 显示对话框
     */
    public void show() {
        isVisible = true;
        ImGui.openPopup(DIALOG_TITLE);

        // 获取当前项目
        Project currentProject = appState.getCurrentProject();
        
        if (currentProject != null) {
            // 获取当前项目路径
            String currentProjectPath = currentProject.getFilePath();
            
            if (currentProjectPath != null && !currentProjectPath.isEmpty()) {
                // 如果已有项目路径，使用它
                Path path = Paths.get(currentProjectPath);
                if (Files.exists(path.getParent())) {
                    filePath.set(path.getParent().toString());
                    fileName.set(path.getFileName().toString());
                    lastBrowsedPath = path.getParent().toString();
                }
            } else if (fileName.get().isEmpty()) {
                // 设置默认文件名
                fileName.set("未命名" + DEFAULT_FILE_EXTENSION);
            }
        } else {
            // 如果没有当前项目，设置默认文件名
            if (fileName.get().isEmpty()) {
                fileName.set("未命名" + DEFAULT_FILE_EXTENSION);
            }
        }
    }

    /**
     * 隐藏对话框并重置状态
     */
    private void hide() {
        isVisible = false;
    }

    private static void publishWarningMessage(String message) {
        EventBus.getInstance().publish(new Events.WarningEvent("SaveFileDialog", message));
    }

    private void showWarning(String message) {
        if (showWarningDialog != null) {
            showWarningDialog.accept(message);
        } else {
            publishWarningMessage(message);
        }
    }

    private void consumePendingFolderSelection() {
        FolderSelectionResult result = pendingFolderSelection;
        if (result == null) {
            return;
        }
        pendingFolderSelection = null;

        String selectedPath = result.path();
        LOGGER.info("文件夹选择对话框返回结果: {}", selectedPath);
        if (selectedPath != null && !selectedPath.isEmpty()) {
            filePath.set(selectedPath);
            lastBrowsedPath = selectedPath;
            LOGGER.info("成功设置选择的文件夹路径: {}", selectedPath);
        } else {
            LOGGER.info("用户取消了文件夹选择或未选择任何文件夹");
        }
    }

    /**
     * 保存文件
     */
    private void saveFile() {
        String name = fileName.get().trim();
        String path = filePath.get().trim();

        if (name.isEmpty()) {
            showWarning("文件名不能为空");
            return;
        }

        // 确保文件名有正确的扩展名
        if (!name.toLowerCase().endsWith(DEFAULT_FILE_EXTENSION)) {
            name += DEFAULT_FILE_EXTENSION;
        }

        // 检查路径是否存在
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            showWarning("指定的路径不存在");
            return;
        }

        // 构建完整的文件路径
        Path fullPath = dirPath.resolve(name);
        
        // 检查文件是否已存在，如果存在则询问是否覆盖
        if (Files.exists(fullPath)) {
            // 这里简化处理，直接覆盖
            LOGGER.info("文件已存在，将覆盖: {}", fullPath);
        }

        try {
            // 保存文件
            String fullPathStr = fullPath.toString();
            
            // 获取当前项目
            Project currentProject = appState.getCurrentProject();
            
            if (currentProject != null) {
                // 更新项目中的文件路径
                currentProject.setFilePath(fullPathStr);
                
                // 发布项目保存事件
                eventBus.publish(new ProjectSavedEvent(currentProject.getId(), fullPathStr));
                
                LOGGER.info("成功保存文件: {}", fullPathStr);
            } else {
                LOGGER.error("保存失败：当前没有活动项目");
                showWarning("保存失败：当前没有活动项目");
                return;
            }
            
            hide();
            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            showWarning("保存文件失败: " + e.getMessage());
            LOGGER.error("保存文件失败", e);
        }
    }

    /**
     * 打开文件浏览对话框
     */
    private void browseFolder() {
        LOGGER.info("开始打开文件夹选择对话框，初始路径: {}", lastBrowsedPath);
        try {
            if (folderDialogInProgress) {
                LOGGER.debug("文件夹选择对话框已在进行中，忽略重复打开请求");
                return;
            }

            folderDialogInProgress = true;
            FileDialogUtil.showFolderDialogAsync(lastBrowsedPath, selectedPath -> {
                pendingFolderSelection = new FolderSelectionResult(selectedPath);
                folderDialogInProgress = false;
            });
        } catch (Exception e) {
            folderDialogInProgress = false;
            LOGGER.error("打开文件夹选择对话框时发生错误", e);
            showWarning("打开文件夹选择对话框失败: " + e.getMessage());
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
        consumePendingFolderSelection();
        if (!isVisible) return;

        float totalWidth = DialogStyleManager.DialogWidth.STANDARD.value;

        // 不再设置固定高度，让窗口自动调整
        ImGui.setNextWindowSize(totalWidth, 0, ImGuiCond.Always);

        // 只在第一次显示时设置窗口位置（水平居中）
        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(
                ImGui.getMainViewport().getCenter().x - totalWidth/2,
                ImGui.getMainViewport().getCenter().y - 100,  // 垂直位置稍微上移
                ImGuiCond.Appearing
            );
        }

        // 设置窗口标志，添加 AlwaysAutoResize 以自动调整高度
        int windowFlags = ImGuiWindowFlags.NoResize |
                         ImGuiWindowFlags.NoSavedSettings |
                         ImGuiWindowFlags.NoScrollbar |
                         ImGuiWindowFlags.AlwaysAutoResize;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                if (DialogStyleManager.renderTopRightCloseButton("save_file")) {
                    hide();
                    ImGui.closeCurrentPopup();
                    ImGui.endPopup();
                    return;
                }

                // 计算布局尺寸
                float contentWidth = DialogStyleManager.getContentWidth();

                DialogLayoutHelper.beginSection("保存参数");
                DialogLayoutHelper.helpText("文件将保存为 .mp 项目文件，可通过右侧按钮快速选择目录。");
                if (DialogLayoutHelper.beginForm("##save_file_form")) {
                    DialogLayoutHelper.formRowLabel("文件名");
                    if (ImGui.isWindowAppearing()) {
                        ImGui.setKeyboardFocusHere();
                    }
                    ImGui.inputText("##save_file_name", fileName,
                        ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

                    DialogLayoutHelper.formRowLabel("保存位置");
                    float browseButtonWidth = 60.0f;
                    DialogLayoutHelper.reserveTrailingButton(browseButtonWidth);
                    ImGui.inputText("##save_file_path", filePath,
                        ImGuiInputTextFlags.ReadOnly);

                    if (DialogLayoutHelper.trailingButton("浏览...", browseButtonWidth)) {
                        browseFolder();
                    }

                    DialogLayoutHelper.endForm();
                }
                DialogLayoutHelper.endSection();
                DialogLayoutHelper.beginFooter();
                DialogLayoutHelper.FooterResult action =
                        DialogLayoutHelper.footerConfirmCancelRight("取消", "保存", contentWidth);

                if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                    saveFile();
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