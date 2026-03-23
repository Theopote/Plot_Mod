package com.plot.ui.dialog;

import com.plot.core.state.AppState;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * 新建文件对话框
 * 用于创建新的Plot文件
 */
public class NewFileDialog {
    private static final Logger LOGGER = LogManager.getLogger("NewFileDialog");

    // === 常量定义 ===
    private static final float SPACING = 4.0f;  // 控件之间的间距
    private static final String DIALOG_TITLE = "新建文件";
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

    public NewFileDialog(
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

        if (fileName.get().isEmpty()) {
            // 设置默认文件名
            fileName.set("未命名" + DEFAULT_FILE_EXTENSION);
        }
    }

    /**
     * 隐藏对话框并重置状态
     */
    private void hide() {
        isVisible = false;
        fileName.clear();
    }

    /**
     * 创建新文件
     */
    private void createNewFile() {
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
        
        // 检查文件是否已存在
        if (Files.exists(fullPath)) {
            showWarningDialog.accept("文件已存在，请使用其他文件名");
            return;
        }

        try {
            // 清除当前应用状态
            appState.clear();
            
            // 创建新文件（这里只是准备好路径，实际文件会在保存时创建）
            String fullPathStr = fullPath.toString();
            
            // 发布项目加载事件（虽然是新建文件，但使用相同的事件处理机制）
            eventBus.publish(new ProjectLoadedEvent(fullPathStr));
            
            LOGGER.info("成功创建新文件: {}", fullPathStr);
            
            hide();
            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            showWarningDialog.accept("创建文件失败: " + e.getMessage());
            LOGGER.error("创建文件失败", e);
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

        // 设置固定的对话框尺寸
        float totalWidth = 300.0f;  // 与SaveFileDialog保持一致
        
        // 不再设置固定高度，让窗口自动调整
        ImGui.setNextWindowSize(totalWidth, 0, ImGuiCond.Always);

        // 计算对话框居中位置
        float centerPosX = ImGui.getMainViewport().getCenter().x - totalWidth/2;
        float centerPosY = ImGui.getMainViewport().getCenter().y - 100;  // 垂直位置稍微上移
        
        // 只在第一次显示时设置窗口位置（水平居中）
        if (ImGui.isPopupOpen(DIALOG_TITLE)) {
            ImGui.setNextWindowPos(centerPosX, centerPosY, ImGuiCond.Appearing);
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
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonActive);          // 选中项背景色
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);  // 选中项悬停色
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);    // 选中项激活色

        try {
            if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
                // 检查对话框是否超出屏幕边界，如果是则重新定位
                DialogUtils.checkAndRepositionDialog(totalWidth, 0, "NewFileDialog");
                
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
                ImGui.inputText("##new_file_name", fileName,    // 文件名输入框
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

                ImGui.spacing();  // 添加间距

                // === 文件路径输入 ===
                ImGui.alignTextToFramePadding();
                ImGui.text("保存位置：");
                ImGui.sameLine(labelWidth);

                // 路径输入框和浏览按钮在同一行
                float browseButtonWidth = 60.0f;

                // 计算取消按钮的右侧边界位置
                float cancelButtonRightEdge = labelWidth + controlWidth;

                // 计算浏览按钮的起始位置，使其右侧边界与取消按钮的右侧边界对齐
                float browseButtonX = cancelButtonRightEdge - browseButtonWidth;

                // 计算路径输入框的宽度
                float pathInputWidth = browseButtonX - labelWidth - SPACING;

                ImGui.setNextItemWidth(pathInputWidth);
                ImGui.inputText("##new_file_path", filePath,    // 路径输入框
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
                if (ImGui.button("确定", buttonWidth, 0) ||     // 确定按钮
                    ImGui.isKeyPressed(ImGuiKey.Enter)) {      // 或按回车键
                    createNewFile();
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
            ImGui.popStyleColor(10);  // 弹出之前推入的10个样式颜色
        }
    }
}