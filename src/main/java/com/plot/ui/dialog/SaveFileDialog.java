package com.plot.ui.dialog;

import com.plot.core.state.AppState;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.plot.infrastructure.event.EventBus;
import com.plot.core.model.Project;

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

    // === 常量定义 ===
    private static final float SPACING = 4.0f;  // 控件之间的间距
    private static final String DIALOG_TITLE = "保存文件";
    private static final String DEFAULT_FILE_EXTENSION = ".mp";  // 默认文件扩展名

    // === 状态字段 ===
    private boolean isVisible = false;          // 对话框是否可见
    private final ImString fileName;            // 文件名输入缓冲区
    private final ImString filePath;            // 文件路径输入缓冲区
    private String lastBrowsedPath = System.getProperty("user.home");  // 上次浏览的路径

    // === 依赖项 ===
    private final AppState appState;            // 应用程序状态
    private final EventBus eventBus;            // 事件总线
    private final Consumer<String> showWarningDialog;  // 警告对话框回调

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

    /**
     * 保存文件
     */
    private void saveFile() {
        String name = fileName.get().trim();
        String path = filePath.get().trim();

        if (name.isEmpty()) {
            showWarningDialog.accept("文件名不能为空");
            return;
        }

        // 确保文件名有正确的扩展名
        if (!name.toLowerCase().endsWith(DEFAULT_FILE_EXTENSION)) {
            name += DEFAULT_FILE_EXTENSION;
        }

        // 检查路径是否存在
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            showWarningDialog.accept("指定的路径不存在");
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
                showWarningDialog.accept("保存失败：当前没有活动项目");
                return;
            }
            
            hide();
            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            showWarningDialog.accept("保存文件失败: " + e.getMessage());
            LOGGER.error("保存文件失败", e);
        }
    }

    /**
     * 打开文件浏览对话框
     */
    private void browseFolder() {
        LOGGER.info("开始打开文件夹选择对话框，初始路径: {}", lastBrowsedPath);
        try {
            // 使用FileDialogUtil打开文件夹选择对话框
            String selectedPath = FileDialogUtil.showFolderDialog(lastBrowsedPath);
            LOGGER.info("文件夹选择对话框返回结果: {}", selectedPath);
            
            if (selectedPath != null && !selectedPath.isEmpty()) {
                filePath.set(selectedPath);
                lastBrowsedPath = selectedPath;
                LOGGER.info("成功设置选择的文件夹路径: {}", selectedPath);
            } else {
                LOGGER.info("用户取消了文件夹选择或未选择任何文件夹");
            }
        } catch (Exception e) {
            LOGGER.error("打开文件夹选择对话框时发生错误", e);
            showWarningDialog.accept("打开文件夹选择对话框失败: " + e.getMessage());
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

        float totalWidth = 300.0f;
        
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

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                // 计算布局尺寸
                float contentWidth = totalWidth - 16;           // 内容区域总宽度
                float labelWidth = 60.0f;                       // 标签宽度
                float controlWidth = contentWidth - labelWidth; // 控件宽度
                float itemSpacing = 8.0f;                       // 控件之间的间距
    
                // === 文件名输入 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("文件名：");
                ImGui.sameLine(labelWidth);                     // 对齐到标签宽度位置
                ImGui.setNextItemWidth(controlWidth);           // 设置输入框宽度
                if (ImGui.isWindowAppearing()) {
                    ImGui.setKeyboardFocusHere();               // 自动聚焦到文件名输入框
                }
                ImGui.inputText("##save_file_name", fileName,    // 文件名输入框
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
    
                ImGui.spacing();  // 添加间距
    
                // === 文件路径输入 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("保存位置：");
                ImGui.sameLine(labelWidth);
                
                // 计算取消按钮的右侧边界位置
                float cancelButtonRightEdge = labelWidth + controlWidth;
                
                // 计算浏览按钮的起始位置，使其右侧边界与取消按钮的右侧边界对齐
                float browseButtonWidth = 60.0f;
                float browseButtonX = cancelButtonRightEdge - browseButtonWidth;
                
                // 计算路径输入框的宽度
                float pathInputWidth = browseButtonX - labelWidth - SPACING;
                
                ImGui.setNextItemWidth(pathInputWidth);
                ImGui.inputText("##save_file_path", filePath,    // 路径输入框
                    ImGuiInputTextFlags.ReadOnly);              // 设为只读，通过浏览按钮选择
                
                ImGui.sameLine();
                ImGui.setCursorPosX(browseButtonX);
                if (ImGui.button("浏览...", browseButtonWidth, 0)) {
                    browseFolder();
                }
    
                ImGui.spacing();  // 添加间距
                ImGui.separator();  // 分隔线
                ImGui.spacing();  // 添加间距
    
                // === 按钮区域 ===
                float buttonSpacing = 8.0f;                     // 按钮之间的间距
                float buttonWidth = (controlWidth - buttonSpacing) / 2;  // 按钮宽度
    
                ImGui.setCursorPosX(labelWidth);               // 对齐到标签宽度位置
                if (ImGui.button("保存", buttonWidth, 0) ||     // 保存按钮
                    ImGui.isKeyPressed(ImGuiKey.Enter)) {      // 或按回车键
                    saveFile();
                }
    
                ImGui.sameLine(labelWidth + buttonWidth + buttonSpacing);
                if (ImGui.button("取消", buttonWidth, 0) ||     // 取消按钮
                    ImGui.isKeyPressed(ImGuiKey.Escape)) {     // 或按ESC键
                    hide();
                    ImGui.closeCurrentPopup();
                }
    
                ImGui.endPopup();
            }
        } finally {
            // 恢复样式
            ImGui.popStyleVar(3);  // 弹出之前推入的3个样式变量
            ImGui.popStyleColor(7);  // 弹出之前推入的7个样式颜色
        }
    }
}