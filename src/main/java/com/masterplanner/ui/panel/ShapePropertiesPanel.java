package com.masterplanner.ui.panel;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.component.UIComponent;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import imgui.type.ImFloat;
import net.minecraft.util.Identifier;
import com.masterplanner.MasterPlannerMod;
import org.lwjgl.opengl.GL11;
import com.masterplanner.ui.utils.TextureManager;


public class ShapePropertiesPanel implements UIComponent {

    // 通用状态
    private final ImString nameBuffer;
    private final ImFloat opacity;
    private final ImFloat posX;
    private final ImFloat posY;
    private final ImFloat scaleX;
    private final ImFloat scaleY;
    private final ImFloat rotation;

    // 在类的成员变量中添加锁定状态
    private boolean scaleRatioLocked = true;  // 默认锁定状态

    // 在类的开头添加成员变量
    private static int TEXTURE_SCALE_LOCK;
    private static int TEXTURE_SCALE_UNLOCK;
    private boolean texturesInitialized = false;
    private boolean initialized = false;

    public ShapePropertiesPanel() {
        // 初始化通用状态
        this.nameBuffer = new ImString(64);
        this.opacity = new ImFloat(100.0f);
        this.posX = new ImFloat(0.0f);
        this.posY = new ImFloat(0.0f);
        this.scaleX = new ImFloat(1.0f);
        this.scaleY = new ImFloat(1.0f);
        this.rotation = new ImFloat(0.0f);

        // 在构造函数中初始化纹理
        try {
            MasterPlannerMod.LOGGER.info("正在初始化ShapePropertiesPanel纹理...");
            initializeTextures();
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ShapePropertiesPanel纹理初始化失败: {}", e.getMessage());
        }
    }

    @Override
    public void render() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置所有控件的基础样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.panelControlRounding);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
        
        // 设置所有控件的颜色
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);          // 边框颜色
        ImGui.pushStyleColor(ImGuiCol.ChildBg, currentTheme.panelBackground); // 子窗口背景色
        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);    // 按钮背景色
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);   // 按钮悬停色
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);     // 按钮激活色
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);    // 控件背景色
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered); // 控件悬停色
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);   // 控件激活色
        ImGui.pushStyleColor(ImGuiCol.PopupBg, currentTheme.panelBackground);      // 弹出窗口背景色
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);          // 下拉列表选项背景
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);  // 下拉列表选项悬停
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);    // 下拉列表选项激活

        try {
            // 计算参数部分的内容高度
            float itemHeight = ImGui.getFrameHeight();  // 单个控件的高度
            float itemSpacing = ImGui.getStyle().getItemSpacingY();  // 控件之间的间距
            float padding = 8.0f;  // 内边距
            
            // 计算总高度：5个控件 + 4个间距 + 上下内边距
            float contentHeight = itemHeight * 5 + itemSpacing * 4 + padding * 2;

            // 开始参数部分的子窗口
            ImGui.beginChild("##parameters_frame", ImGui.getContentRegionAvailX(), contentHeight, true);

            // 设置内边距
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padding, padding);

            // 设置标签宽度和控件宽度
            float labelWidth = 60.0f;  // 标签固定宽度
            float availableWidth = ImGui.getWindowWidth() - padding * 2;  // 获取子窗口宽度并减去左右内边距
            float contentWidth = availableWidth - labelWidth;  // 控件宽度

            // 图元名称
            ImGui.alignTextToFramePadding();
            ImGui.text("图元名称：");
            ImGui.sameLine(labelWidth + padding);
            ImGui.setNextItemWidth(contentWidth);
            ImGui.inputText("##name", nameBuffer);

            // 透明度
            ImGui.alignTextToFramePadding();
            ImGui.text("透明度：");
            ImGui.sameLine(labelWidth + padding);
            ImGui.setNextItemWidth(contentWidth);
            float[] tempOpacity = {opacity.get()};
            if (ImGui.sliderFloat("##opacity", tempOpacity, 0.0f, 100.0f, "%.0f%%")) {
                opacity.set(tempOpacity[0]);
            }

            // 位置
            ImGui.alignTextToFramePadding();
            ImGui.text("位置：");
            ImGui.sameLine(labelWidth + padding);
            float halfWidth = (contentWidth - ImGui.getStyle().getItemSpacingX()) / 2;
            ImGui.setNextItemWidth(halfWidth);
            float[] x = {posX.get()};
            if (ImGui.dragFloat("##pos_x", x, 0.1f, -10000.0f, 10000.0f, "X: %.1f")) {
                posX.set(x[0]);
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(halfWidth);
            float[] y = {posY.get()};
            if (ImGui.dragFloat("##pos_y", y, 0.1f, -10000.0f, 10000.0f, "Y: %.1f")) {
                posY.set(y[0]);
            }

            // 缩放
            renderScaleControls(labelWidth, padding, contentWidth);

            // 旋转
            ImGui.alignTextToFramePadding();
            ImGui.text("旋转：");
            ImGui.sameLine(labelWidth + padding);
            ImGui.setNextItemWidth(contentWidth);
            float[] rot = {rotation.get()};
            if (ImGui.dragFloat("##rotation", rot, 1.0f, -360.0f, 360.0f, "%.1f°")) {
                if (rot[0] > 360.0f) rot[0] = rot[0] - 360.0f;
                if (rot[0] < -360.0f) rot[0] = rot[0] + 360.0f;
                rotation.set(rot[0]);
            }

            ImGui.popStyleVar();  // 弹出 WindowPadding
            ImGui.endChild();
        } finally {
            ImGui.popStyleColor(12);
            ImGui.popStyleVar(4);
        }
    }

    private void renderScaleControls(float labelWidth, float padding, float contentWidth) {
        ImGui.alignTextToFramePadding();
        ImGui.text("缩放：");
        ImGui.sameLine(labelWidth + padding);

        float lockButtonSize = ImGui.getFrameHeight();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float scaleControlWidth = (contentWidth - lockButtonSize - spacing * 2) / 2;

        // X缩放控件
        ImGui.setNextItemWidth(scaleControlWidth);
        float[] scaleX = {this.scaleX.get()};
        if (ImGui.dragFloat("##scale_x", scaleX, 0.01f, 0.01f, 100.0f, "X: %.2f")) {
            if (scaleX[0] < 0.01f) scaleX[0] = 0.01f;
            this.scaleX.set(scaleX[0]);
            if (scaleRatioLocked) {
                this.scaleY.set(scaleX[0]);
            }
        }

        // 锁定按钮
        ImGui.sameLine(0, spacing);
        if (texturesInitialized && TEXTURE_SCALE_LOCK > 0 && TEXTURE_SCALE_UNLOCK > 0) {
            // 使用图标
            int lockTexture = scaleRatioLocked ? TEXTURE_SCALE_LOCK : TEXTURE_SCALE_UNLOCK;
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
            ImGui.pushID("scale_lock_button");
            if (ImGui.imageButton(lockTexture, lockButtonSize, lockButtonSize)) {
                scaleRatioLocked = !scaleRatioLocked;
            }
            ImGui.popID();
            ImGui.popStyleVar();
        } else {
            // 使用文本按钮作为备用
            if (ImGui.button(scaleRatioLocked ? "🔒" : "🔓", lockButtonSize, lockButtonSize)) {
                scaleRatioLocked = !scaleRatioLocked;
            }
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(scaleRatioLocked ? "解锁比例" : "锁定比例");
        }

        // Y缩放控件
        ImGui.sameLine(0, spacing);
        ImGui.setNextItemWidth(scaleControlWidth);
        float[] scaleY = {this.scaleY.get()};
        if (ImGui.dragFloat("##scale_y", scaleY, 0.01f, 0.01f, 100.0f, "Y: %.2f")) {
            if (scaleY[0] < 0.01f) scaleY[0] = 0.01f;
            this.scaleY.set(scaleY[0]);
            if (scaleRatioLocked) {
                this.scaleX.set(scaleY[0]);
            }
        }
    }

    @Override
    public void close() throws Exception {
        try {
            MasterPlannerMod.LOGGER.debug("正在关闭ShapePropertiesPanel...");
            // 释放纹理资源
            if (TEXTURE_SCALE_LOCK > 0) GL11.glDeleteTextures(TEXTURE_SCALE_LOCK);
            if (TEXTURE_SCALE_UNLOCK > 0) GL11.glDeleteTextures(TEXTURE_SCALE_UNLOCK);
            
            nameBuffer.clear();
            MasterPlannerMod.LOGGER.debug("ShapePropertiesPanel关闭完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ShapePropertiesPanel关闭失败: " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void init() {
        if (initialized) {
            MasterPlannerMod.LOGGER.debug("ShapePropertiesPanel已经初始化，跳过初始化流程");
            return;
        }

        try {
            MasterPlannerMod.LOGGER.info("正在初始化ShapePropertiesPanel...");
            
            // 如果纹理还未初始化，再次尝试
            if (!texturesInitialized) {
                MasterPlannerMod.LOGGER.debug("重新尝试加载ShapePropertiesPanel纹理...");
                initializeTextures();
            }

            initialized = true;
            MasterPlannerMod.LOGGER.info("ShapePropertiesPanel初始化完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ShapePropertiesPanel初始化失败: {}", e.getMessage());
            initialized = false;
        }
    }

    private void initializeTextures() {
        if (!texturesInitialized) {
            try {
                TextureManager textureManager = TextureManager.getInstance();
                
                // 使用与 LayerPanel 完全相同的路径
                TEXTURE_SCALE_LOCK = textureManager.loadTexture(
                    Identifier.of("masterplanner", "textures/gui/layer/lock.png"));
                TEXTURE_SCALE_UNLOCK = textureManager.loadTexture(
                    Identifier.of("masterplanner", "textures/gui/layer/unlock.png"));
                
                // 验证纹理加载结果
                if (TEXTURE_SCALE_LOCK <= 0 || TEXTURE_SCALE_UNLOCK <= 0) {
                    MasterPlannerMod.LOGGER.error("ShapePropertiesPanel纹理ID无效: LOCK={}, UNLOCK={}", 
                        TEXTURE_SCALE_LOCK, TEXTURE_SCALE_UNLOCK);
                    texturesInitialized = false;
                    return;
                }
                
                texturesInitialized = true;
                MasterPlannerMod.LOGGER.info("ShapePropertiesPanel纹理加载完成: LOCK={}, UNLOCK={}", 
                    TEXTURE_SCALE_LOCK, TEXTURE_SCALE_UNLOCK);
                
            } catch (Exception e) {
                MasterPlannerMod.LOGGER.error("ShapePropertiesPanel纹理加载失败: {}", e.getMessage());
                MasterPlannerMod.LOGGER.debug("错误堆栈:", e);
                texturesInitialized = false;
            }
        }
    }

    // Getters and setters for the properties
    public String getName() {
        return nameBuffer.get();
    }

    public void setName(String name) {
        nameBuffer.set(name);
    }

    public float getOpacity() {
        return opacity.get();
    }

    public void setOpacity(float value) {
        opacity.set(value);
    }

    public float getPosX() {
        return posX.get();
    }

    public void setPosX(float value) {
        posX.set(value);
    }

    public float getPosY() {
        return posY.get();
    }

    public void setPosY(float value) {
        posY.set(value);
    }

    public float getScaleX() {
        return scaleX.get();
    }

    public void setScaleX(float value) {
        scaleX.set(value);
    }

    public float getScaleY() {
        return scaleY.get();
    }

    public void setScaleY(float value) {
        scaleY.set(value);
    }

    public float getRotation() {
        return rotation.get();
    }

    public void setRotation(float value) {
        rotation.set(value);
    }
} 