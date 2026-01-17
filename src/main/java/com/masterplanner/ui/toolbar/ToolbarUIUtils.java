package com.masterplanner.ui.toolbar;

import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiKey;
import imgui.type.ImFloat;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具栏UI工具类
 * 提供统一的按钮和滑块渲染方法，减少代码重复
 */
public class ToolbarUIUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolbarUIUtils.class);
    
    /**
     * 渲染工具栏按钮（统一样式版本）
     * @param icon 图标
     * @param tooltip 提示文本
     * @param isDisabled 是否禁用
     * @param isSelected 是否选中
     * @return 是否被点击
     */
    public static boolean renderToolbarButton(Identifier icon, String tooltip, boolean isDisabled, boolean isSelected) {
        if (isDisabled) {
            ImGui.beginDisabled();
        }
        
        // 统一处理样式
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.getInstance().getCurrentTheme().buttonBorder);

        boolean clicked = false;
        try {
            clicked = UIUtils.imageButton(icon, tooltip, UILayout.Toolbar.BUTTON_SIZE, isSelected);
        } catch (Exception e) {
            LOGGER.error("Error rendering toolbar button: {}", tooltip, e);
        } finally {
            ImGui.popStyleColor();
            ImGui.popStyleVar();
            
            if (isDisabled) {
                ImGui.endDisabled();
            }
        }
        
        return clicked;
    }
    
    /**
     * 渲染工具栏按钮（简化版本）
     * @param icon 图标
     * @param tooltip 提示文本
     * @return 是否被点击
     */
    public static boolean renderToolbarButton(Identifier icon, String tooltip) {
        return renderToolbarButton(icon, tooltip, false, false);
    }
    
    /**
     * 渲染标准滑块
     * @param label 标签文本
     * @param maxLabelWidth 最大标签宽度
     * @param sliderWidth 滑块宽度
     * @param value 滑块值
     * @param min 最小值
     * @param max 最大值
     * @param format 格式化字符串
     * @param isDisabled 是否禁用
     * @param onChange 值改变回调
     * @param onRightClick 右键点击回调
     * @return 值是否改变
     */
    public static boolean renderSlider(String label, float maxLabelWidth, float sliderWidth, 
                                     float[] value, float min, float max, String format, 
                                     boolean isDisabled, Runnable onChange, Runnable onRightClick) {
        // 对齐标签
        ImGui.dummy((maxLabelWidth - ImGui.calcTextSize(label).x), 0);
        ImGui.sameLine(0, 0);
        ImGui.text(label);
        ImGui.sameLine();
        
        // 设置滑块宽度
        ImGui.pushItemWidth(sliderWidth);
        
        boolean changed = false;
        try {
            if (isDisabled) {
                ImGui.beginDisabled();
            }
            
            // 生成唯一的滑动条ID，包含更多上下文信息避免冲突
            String sanitizedLabel = label.replaceAll("[^a-zA-Z0-9]", "_");
            String sliderID = "##slider_" + sanitizedLabel + "_" + System.identityHashCode(value);
            
            if (ImGui.sliderFloat(sliderID, value, min, max, format)) {
                changed = true;
                if (onChange != null) {
                    onChange.run();
                }
            }
            
            if (isDisabled) {
                ImGui.endDisabled();
            }
            
            // 处理右键点击
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1) && onRightClick != null) {
                onRightClick.run();
            }
            
        } finally {
            ImGui.popItemWidth();
        }
        
        return changed;
    }
    
    /**
     * 渲染简化的滑块（无右键功能）
     */
    public static boolean renderSlider(String label, float maxLabelWidth, float sliderWidth, 
                                     float[] value, float min, float max, String format, 
                                     boolean isDisabled, Runnable onChange) {
        return renderSlider(label, maxLabelWidth, sliderWidth, value, min, max, format, isDisabled, onChange, null);
    }
    
    /**
     * 渲染带输入弹窗的滑块
     */
    public static boolean renderSliderWithInput(String label, float maxLabelWidth, float sliderWidth, 
                                               float[] value, float min, float max, String format, 
                                               boolean isDisabled, Runnable onChange, 
                                               String popupTitle, String inputLabel) {
        // 为弹窗生成唯一ID，避免冲突
        String uniquePopupTitle = popupTitle + "_" + System.identityHashCode(value);
        
        boolean changed = renderSlider(label, maxLabelWidth, sliderWidth, value, min, max, format, 
                                     isDisabled, onChange, () -> ImGui.openPopup(uniquePopupTitle));
        
        // 渲染输入弹窗
        renderInputPopup(uniquePopupTitle, inputLabel, value[0], min, max, newValue -> {
            value[0] = newValue;
            if (onChange != null) {
                onChange.run();
            }
        });
        
        return changed;
    }
    
    /**
     * 渲染分两行的滑动条（标题在上，滑动条在下）
     * @param label 标签文本
     * @param sliderWidth 滑动条宽度
     * @param value 滑动条值
     * @param min 最小值
     * @param max 最大值
     * @param format 格式化字符串
     * @param isDisabled 是否禁用
     * @param onChange 值改变回调
     * @param onRightClick 右键点击回调
     * @return 值是否改变
     */
    public static boolean renderSliderTwoRows(String label, float sliderWidth, 
                                             float[] value, float min, float max, String format, 
                                             boolean isDisabled, Runnable onChange, Runnable onRightClick) {
        // 第一行：显示标签
        ImGui.text(label);
        
        // 换行：移动到下一行
        // ImGui.text() 已经将光标移动到文本底部，加上间距即可
        // 但为了确保标题和滑动条之间的间距更紧凑，使用 ITEM_SPACING 的一半
        float lineSpacing = UILayout.Toolbar.ITEM_SPACING * 0.5f;
        ImGui.setCursorPosY(ImGui.getCursorPosY() + lineSpacing);
        
        // 第二行：显示滑动条
        // 注意：滑动条的样式应该在调用此方法之前通过 setupSliderStyles() 设置
        ImGui.pushItemWidth(sliderWidth);
        
        boolean changed = false;
        try {
            if (isDisabled) {
                ImGui.beginDisabled();
            }
            
            // 生成唯一的滑动条ID
            String sanitizedLabel = label.replaceAll("[^a-zA-Z0-9]", "_");
            String sliderID = "##slider_" + sanitizedLabel + "_" + System.identityHashCode(value);
            
            if (ImGui.sliderFloat(sliderID, value, min, max, format)) {
                changed = true;
                if (onChange != null) {
                    onChange.run();
                }
            }
            
            if (isDisabled) {
                ImGui.endDisabled();
            }
            
            // 处理右键点击
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1) && onRightClick != null) {
                onRightClick.run();
            }
            
        } finally {
            ImGui.popItemWidth();
        }
        
        return changed;
    }
    
    /**
     * 渲染分两行的滑动条（带输入弹窗）
     */
    public static boolean renderSliderTwoRowsWithInput(String label, float sliderWidth, 
                                                      float[] value, float min, float max, String format, 
                                                      boolean isDisabled, Runnable onChange, 
                                                      String popupTitle, String inputLabel) {
        // 为弹窗生成唯一ID，避免冲突
        String uniquePopupTitle = popupTitle + "_" + System.identityHashCode(value);
        
        boolean changed = renderSliderTwoRows(label, sliderWidth, value, min, max, format, 
                                            isDisabled, onChange, () -> ImGui.openPopup(uniquePopupTitle));
        
        // 渲染输入弹窗
        renderInputPopup(uniquePopupTitle, inputLabel, value[0], min, max, newValue -> {
            value[0] = newValue;
            if (onChange != null) {
                onChange.run();
            }
        });
        
        return changed;
    }
    
    /**
     * 渲染数值输入弹窗
     */
    public static void renderInputPopup(String popupName, String label, float currentValue, 
                                      float minValue, float maxValue, FloatConsumer onValueChanged) {
        if (ImGui.beginPopup(popupName)) {
            ImGui.text(label);
            
            ImFloat value = new ImFloat(currentValue);
            if (ImGui.inputFloat("##value", value)) {
                value.set(Math.max(minValue, Math.min(maxValue, value.get())));
            }
            
            ImGui.spacing();
            if (ImGui.button("确定") || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                onValueChanged.accept(value.get());
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 执行代码块时自动管理工具栏样式
     */
    public static void withToolbarStyles(Runnable block) {
        // 注意：这个方法只是为了API兼容性，实际的样式设置在ControlPanel中
        try {
            block.run();
        } catch (Exception e) {
            LOGGER.error("Error in withToolbarStyles", e);
        }
    }
    
    /**
     * 渲染垂直分隔线
     * @param separatorX 分隔线X坐标（相对于窗口）
     */
    public static void renderVerticalSeparator(float separatorX) {
        float windowX = ImGui.getWindowPos().x;
        float windowY = ImGui.getWindowPos().y;
        
        // 转换为绝对屏幕坐标
        float absoluteX = windowX + separatorX;
        float startY = windowY + UILayout.Toolbar.BUTTON_PADDING;
        float endY = startY + UILayout.Toolbar.BUTTON_SIZE;

        int separatorColor = ThemeManager.getInstance().getCurrentTheme().separatorColor;
        ImGui.getWindowDrawList().addLine(
                absoluteX,
                startY,
                absoluteX,
                endY,
                separatorColor,
                UILayout.Toolbar.SEPARATOR_WIDTH
        );
    }
    
    /**
     * 函数式接口用于处理浮点值变化
     */
    @FunctionalInterface
    public interface FloatConsumer {
        void accept(float value);
    }
}