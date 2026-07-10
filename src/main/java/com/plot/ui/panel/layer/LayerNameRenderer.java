package com.plot.ui.panel.layer;

import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.utils.PlotI18n;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 图层名称渲染器
 * 负责渲染和处理图层名称的显示和编辑
 */
public class LayerNameRenderer {
    private static final Logger LOGGER = LogManager.getLogger(LayerNameRenderer.class);
    
    // === 常量定义 ===
    private static final float BORDER_THICKNESS = 1.0f;  // 边框粗细
    private static final float ACTIVE_MARKER_WIDTH = 3.0f;  // 活动标记宽度
    private static final float TEXT_PADDING_X = 4.0f;  // 文本内边距

    // === 状态字段 ===
    private final Map<String, Boolean> editingStates = new HashMap<>();
    private final ImString nameBuffer = new ImString(1024); // 进一步增加缓冲区大小，确保足够容纳中文字符
    private String currentEditingLayerId = null;
    private boolean setFocus = false;
    private String editingLayerId = null;
    /** 是否处于编辑状态 */
    private boolean isEditing = false;
    
    // 双击检测相关
    private long lastClickTime = 0;
    private String lastClickedLayerId = null;
    private static final long DOUBLE_CLICK_THRESHOLD = 500; // 500ms内的两次点击视为双击

    // === 依赖项 ===
    private final LayerManager layerManager;
    private final Consumer<String> showWarningDialog;

    public LayerNameRenderer(
            LayerManager layerManager,
            Consumer<String> showWarningDialog) { // 添加主题管理器参数
        this.layerManager = layerManager;
        this.showWarningDialog = showWarningDialog;
        // 添加主题管理器依赖
    }

    /**
     * 渲染图层名称，支持双击编辑
     * @param layer 要渲染的图层
     * @param width 宽度
     * @param height 高度
     * @param isActive 是否为活动图层
     * @param isSelected 是否被选中
     */
    public void render(Layer layer, float width, float height, boolean isActive, boolean isSelected) {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        if (layer == null) {
            LOGGER.warn("尝试渲染空图层名称");
            return;
        }
        
        // 验证尺寸参数，防止ImGui断言失败
        if (width <= 0.0f || height <= 0.0f) {
            LOGGER.warn("图层名称渲染尺寸无效: width={}, height={}, 图层: {}", width, height, layer.getName());
            return;
        }
        
        // 保存原始光标位置，以便在渲染完成后恢复
        float originalX = ImGui.getCursorPosX();
        float originalY = ImGui.getCursorPosY();
        
        // 获取当前位置

        // 计算边框位置
        float scrollY = ImGui.getScrollY();
        float windowX = ImGui.getWindowPosX();
        float windowY = ImGui.getWindowPosY();
        
        // 创建一个不可见的按钮来占据整个区域，确保布局一致性
        ImGui.setCursorPos(originalX, originalY);
        ImGui.invisibleButton("##layername_area_" + layer.getId(), width, height);
        boolean isHovered = ImGui.isItemHovered();
        
        // 绘制边框
        ImGui.getWindowDrawList().addRect(
                originalX + windowX,
                originalY + windowY - scrollY,
                originalX + windowX + width,
                originalY + windowY - scrollY + height,
                ImGui.getColorU32(ImGuiCol.Border),
                0.0f,
                0,
                BORDER_THICKNESS
        );
        
        // 如果是活动图层，绘制活动标记
        if (isActive) {
            ImGui.getWindowDrawList().addRectFilled(
                    originalX + windowX,
                    originalY + windowY - scrollY,
                    originalX + windowX + ACTIVE_MARKER_WIDTH,
                    originalY + windowY - scrollY + height,
                    theme.accent
            );
        }
        
        // 设置文本颜色
        int textColor;
        if (layer.isLocked()) {
            textColor = ImGui.getColorU32(ImGuiCol.TextDisabled); // 禁用文本颜色
        } else if (isSelected) {
            textColor = theme.text; // 选中时仍保持主题文本可读性
        } else {
            textColor = ImGui.getColorU32(ImGuiCol.Text); // 普通文本颜色
        }
        
        // 计算文本位置（垂直居中）
        float textHeight = ImGui.getTextLineHeight();
        float textY = originalY + (height - textHeight) * 0.5f; // 垂直居中
        float textX = originalX + TEXT_PADDING_X + (isActive ? ACTIVE_MARKER_WIDTH : 0);
        
        // 计算文本区域的绝对坐标
        float textScreenX = textX + windowX;
        float textScreenY = textY + windowY - scrollY;
        
        // 计算可用于文本的宽度
        float availableTextWidth = width - TEXT_PADDING_X * 2 - (isActive ? ACTIVE_MARKER_WIDTH : 0);
        
        // 如果当前图层正在编辑中
        if (isEditing && layer.getId().equals(currentEditingLayerId)) {
            // 使用完全自定义的方法处理编辑模式，避免使用ImGui的输入框
            handleCustomEditing(layer, textScreenX, textScreenY, availableTextWidth, textHeight);
        } else {
            // 显示图层名称
            String displayName = PlotI18n.layerDisplayName(layer.getName());
            
            // 如果名称过长，截断并添加省略号
            float textWidth = ImGui.calcTextSize(displayName).x;
            if (textWidth > availableTextWidth) {
                displayName = truncateWithEllipsis(displayName, availableTextWidth);
            }
            
            // 绘制文本
            ImGui.getWindowDrawList().addText(
                textScreenX,
                textScreenY,
                textColor,
                displayName
            );
            
            // 改进的双击检测逻辑 - 添加详细日志
            if (isHovered && !layer.isLocked()) {
                // 检测单击
                if (ImGui.isMouseClicked(0)) {
                    long currentTime = System.currentTimeMillis();
                    String layerId = layer.getId();
                    
                    LOGGER.info("检测到单击事件 - 图层: '{}', 时间: {}", layer.getName(), currentTime);
                    
                    // 检查是否为双击
                    if (lastClickedLayerId != null && lastClickedLayerId.equals(layerId) && 
                        (currentTime - lastClickTime) <= DOUBLE_CLICK_THRESHOLD) {
                        LOGGER.info("检测到双击事件，开始编辑图层: '{}', 时间间隔: {}ms", 
                                   layer.getName(), currentTime - lastClickTime);
                        startEditing(layer);
                        // 重置点击状态，避免连续触发
                        lastClickTime = 0;
                        lastClickedLayerId = null;
                    } else {
                        // 记录单击信息
                        lastClickTime = currentTime;
                        lastClickedLayerId = layerId;
                        LOGGER.debug("记录单击信息 - 图层: '{}', 时间: {}", layer.getName(), currentTime);
                    }
                }
                
                // 同时保留原有的ImGui双击检测作为备用
                if (ImGui.isMouseDoubleClicked(0)) {
                    LOGGER.info("检测到ImGui双击事件，开始编辑图层: '{}'", layer.getName());
                    startEditing(layer);
                }
            }
        }
        
        // 恢复原始光标位置，确保不影响后续元素的布局
        ImGui.setCursorPos(originalX + width, originalY);
    }
    
    /**
     * 处理自定义编辑模式
     * 使用可见的输入框，确保有闪烁的光标和文本编辑框
     */
    private void handleCustomEditing(Layer layer, float x, float y, float width, float height) {
        LOGGER.info("进入编辑模式 - 图层: '{}', 位置: ({}, {}), 尺寸: ({}, {})", 
                   layer.getName(), x, y, width, height);
        
        // 保存当前光标位置
        float originalX = ImGui.getCursorPosX();
        float originalY = ImGui.getCursorPosY();
        
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        // 根据当前主题设置编辑框背景色
        int editBgColor = theme.buttonHovered;
            
        // 根据当前主题设置编辑框边框色
        int borderColor = theme.accent;
        
        // 绘制编辑框背景
        ImGui.getWindowDrawList().addRectFilled(
            x - 2, 
            y - 2, 
            x + width + 2, 
            y + height + 2, 
            editBgColor
        );
        
        // 绘制编辑框边框
        ImGui.getWindowDrawList().addRect(
            x - 2, 
            y - 2, 
            x + width + 2, 
            y + height + 2, 
            borderColor,
            3.0f, // 圆角
            0,
            1.5f // 更粗的边框
        );
        
        // 确保编辑缓冲区包含当前图层名称
        if (!nameBuffer.get().equals(layer.getName())) {
            nameBuffer.set(layer.getName());
            LOGGER.debug("同步编辑缓冲区内容为: '{}'", layer.getName());
        }
        
        // 创建一个不可见的按钮来占据原始空间，保持布局一致性
        ImGui.setCursorPos(originalX, originalY);
        if (width > 0.0f && height > 0.0f) {
            ImGui.invisibleButton("##layout_keeper_" + layer.getId(), width, height);
        }

        // 设置样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0, 0, 0, 0); // 透明背景
        
        // 根据主题设置文本颜色
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        
        // 设置文本选中背景色
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 
            theme.buttonSelected
        );
        
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0); // 移除内边距
        
        // 计算输入框位置 - 确保与原文本位置一致

        // 创建一个临时的子窗口来容纳输入框
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
        ImGui.setNextWindowBgAlpha(0.0f); // 完全透明背景
        
        // 使用子窗口确保输入框位置正确
        if (ImGui.beginChild("##edit_child_" + layer.getId(), width, height, false,
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar)) {

            // 设置输入框宽度
            ImGui.setNextItemWidth(width - 4); // 留出一些边距

            // 确保输入框有焦点
            if (setFocus) {
                ImGui.setKeyboardFocusHere();
                setFocus = false;
            }

            // 渲染输入框 - 添加支持中文输入的标志
            boolean enterPressed = ImGui.inputText(
                    "##edit_" + layer.getId(),
                    nameBuffer,
                    ImGuiInputTextFlags.EnterReturnsTrue |
                            ImGuiInputTextFlags.AutoSelectAll |
                            ImGuiInputTextFlags.CharsNoBlank // 防止输入空白字符
            );

            // 检查是否完成编辑
            boolean finished = false;
            boolean canceled = false;

            // 按Enter完成编辑
            if (enterPressed || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                finished = true;
                LOGGER.debug("按下Enter键，完成编辑");
            }

            // 按Escape取消编辑
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                canceled = true;
                LOGGER.debug("按下Escape键，取消编辑");
            }

            // 如果点击了其他地方，完成编辑
            if (ImGui.isMouseClicked(0) && !ImGui.isItemHovered()) {
                finished = true;
                LOGGER.debug("点击其他地方，完成编辑");
            }

            // 处理编辑完成或取消
            if (finished) {
                LOGGER.debug("应用名称更改: '{}' -> '{}'", layer.getName(), nameBuffer.get());
                applyNameChange(layer);
            } else if (canceled) {
                LOGGER.debug("取消编辑");
                cancelEditing(layer.getId());
            }

            ImGui.endChild();
        }

        // 恢复样式
        ImGui.popStyleVar();
        ImGui.popStyleColor(3);
        
        // 恢复原始光标位置
        ImGui.setCursorPos(originalX, originalY);
    }

    /**
     * 开始编辑图层名称
     * @param layer 要编辑的图层
     */
    public void startEditing(Layer layer) {
        if (layer == null) {
            LOGGER.warn("尝试编辑空图层");
            return;
        }
        
        LOGGER.info("startEditing被调用 - 图层: '{}' (ID: {})", layer.getName(), layer.getId());
        
        // 如果图层被锁定，不允许编辑
        if (layer.isLocked()) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.locked_no_rename"));
            LOGGER.info("图层 '{}' 已锁定，无法编辑", layer.getName());
            return;
        }
        
        // 如果已经在编辑其他图层，先取消
        if (isEditing && currentEditingLayerId != null && !currentEditingLayerId.equals(layer.getId())) {
            LOGGER.info("取消正在编辑的图层: {}", currentEditingLayerId);
            cancelEditing(currentEditingLayerId);
        }
        
        // 设置编辑状态
        editingLayerId = layer.getId();
        currentEditingLayerId = layer.getId();
        editingStates.put(layer.getId(), true);
        isEditing = true; // 设置全局编辑状态标志
        
        // 完全清空名称缓冲区，确保没有残留数据
        nameBuffer.clear();
        
        // 重新设置图层名称到缓冲区
        nameBuffer.set(layer.getName());
        
        // 设置焦点标志
        setFocus = true;
        
        LOGGER.info("编辑状态设置完成 - isEditing: {}, currentEditingLayerId: '{}', nameBuffer: '{}'", 
                   isEditing, currentEditingLayerId, nameBuffer.get());
    }

    /**
     * 应用名称更改
     * @param layer 要更新名称的图层
     */
    private void applyNameChange(Layer layer) {
        String newName = nameBuffer.get().trim();
        
        // 检查名称是否为空
        if (newName.isEmpty()) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_empty"));
            nameBuffer.set(layer.getName());
            return;
        }
        
        // 检查名称长度（中文字符按2个字符计算）
        int nameLength = calculateDisplayLength(newName);
        if (nameLength > 50) { // 限制显示长度
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_too_long"));
            nameBuffer.set(layer.getName());
            return;
        }
        
        // 检查名称是否已存在（排除当前图层）
        if (!newName.equals(layer.getName()) && layerManager.isNameExists(newName)) {
            showWarningDialog.accept(PlotI18n.tr("layer.plot.name_exists"));
            nameBuffer.set(layer.getName());
            return;
        }
        
        // 更新图层名称 - 通过LayerManager更新确保正确的事件处理
        if (!newName.equals(layer.getName())) {
            String oldName = layer.getName();
            layerManager.updateLayerProperty(layer, "name", newName);
            LOGGER.info("更新图层名称: '{}' -> '{}'", oldName, newName);
        }
        
        // 结束编辑状态
        cancelEditing(layer.getId());
    }
    
    /**
     * 计算字符串的显示长度（中文字符按2个字符计算）
     */
    private int calculateDisplayLength(String text) {
        if (text == null) return 0;
        
        int length = 0;
        for (char c : text.toCharArray()) {
            if (c > 127) { // 非ASCII字符（包括中文）
                length += 2;
            } else {
                length += 1;
            }
        }
        return length;
    }

    /**
     * 取消编辑
     * @param layerId 图层ID
     */
    private void cancelEditing(String layerId) {
        editingStates.put(layerId, false);
        editingLayerId = null;
        currentEditingLayerId = null;
        isEditing = false; // 重置全局编辑状态标志
        setFocus = false; // 重置焦点标志
        nameBuffer.clear(); // 清空缓冲区
        LOGGER.debug("取消编辑图层: {}", layerId);
    }

    /**
     * 截断文本并添加省略号
     * @param text 原始文本
     * @param maxWidth 最大宽度
     * @return 截断后的文本
     */
    private String truncateWithEllipsis(String text, float maxWidth) {
        if (ImGui.calcTextSize(text).x <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int length = text.length();

        while (length > 0) {
            String truncated = text.substring(0, length) + ellipsis;
            if (ImGui.calcTextSize(truncated).x <= maxWidth) {
                return truncated;
            }
            length--;
        }

        return ellipsis;
    }
}