package com.masterplanner.ui.panel.tool.renderer.helpers;

import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

import java.util.function.Consumer;

/**
 * 滑块渲染辅助类
 * 
 * <p>提供通用的滑块控件渲染功能，减少重复代码。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public final class SliderRenderHelper {
    
    private SliderRenderHelper() {
        // 工具类，禁止实例化
    }
    
    /**
     * 滑块操作接口
     */
    @FunctionalInterface
    public interface SliderAction {
        /**
         * 执行滑块操作
         * @param label 标签
         * @return 是否发生了变更
         */
        boolean execute(String label);
    }
    
    /**
     * 渲染通用滑块控件
     * 
     * @param label 标签文本
     * @param theme 主题配置
     * @param sliderAction 滑块操作
     * @param onChanged 值变更回调（可为null）
     */
    public static void renderSlider(String label, 
                                  UITheme.ThemeColors theme, 
                                  SliderAction sliderAction,
                                  Consumer<String> onChanged) {
        
        // 开始滑块行
        beginSliderRow(label, theme);
        
        try {
            // 执行滑块操作
            ImGui.pushItemWidth(-1);
            boolean changed = sliderAction.execute("##" + label);
            ImGui.popItemWidth();
            
            // 如果值发生变更且有回调，则调用回调
            if (changed && onChanged != null) {
                onChanged.accept(label);
            }
        } finally {
            // 结束滑块行
            endSliderRow();
        }
    }
    
    /**
     * 渲染浮点数滑块
     * 
     * @param label 标签
     * @param configKey 配置键
     * @param value 值数组
     * @param min 最小值
     * @param max 最大值
     * @param format 格式字符串
     * @param theme 主题
     * @param onChanged 变更回调
     */
    public static void renderFloatSlider(String label,
                                       String configKey,
                                       float[] value,
                                       float min,
                                       float max,
                                       String format,
                                       UITheme.ThemeColors theme,
                                       Consumer<String> onChanged) {
        renderSlider(label, theme, 
            (l) -> ImGui.sliderFloat(l, value, min, max, format),
            (l) -> {
                if (onChanged != null) {
                    onChanged.accept(configKey);
                }
            });
    }
    
    /**
     * 渲染整数滑块
     * 
     * @param label 标签
     * @param configKey 配置键
     * @param value 值数组
     * @param min 最小值
     * @param max 最大值
     * @param format 格式字符串
     * @param theme 主题
     * @param onChanged 变更回调
     */
    public static void renderIntSlider(String label,
                                     String configKey,
                                     int[] value,
                                     int min,
                                     int max,
                                     String format,
                                     UITheme.ThemeColors theme,
                                     Consumer<String> onChanged) {
        renderSlider(label, theme,
            (l) -> ImGui.sliderInt(l, value, min, max, format),
            (l) -> {
                if (onChanged != null) {
                    onChanged.accept(configKey);
                }
            });
    }
    
    /**
     * 开始滑块行渲染
     */
    private static void beginSliderRow(String label, UITheme.ThemeColors theme) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        
        // 应用控件样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.controlBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.frameBorder);
        
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, theme.grabRounding);
        
        ImGui.tableNextColumn();
    }
    
    /**
     * 结束滑块行渲染
     */
    private static void endSliderRow() {
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(6);
    }
}