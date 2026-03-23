package com.plot.ui.dialog;

import imgui.ImGui;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 对话框工具类
 * 提供对话框相关的通用功能
 */
public class DialogUtils {
    private static final Logger LOGGER = LogManager.getLogger("DialogUtils");
    
    /**
     * 检查对话框位置并在必要时重新定位
     * 确保对话框不会被拖出屏幕可视范围
     * 
     * @param dialogWidth 对话框宽度
     * @param dialogHeight 对话框高度
     * @param loggerName 日志记录器名称，用于记录重新定位信息
     */
    public static void checkAndRepositionDialog(float dialogWidth, float dialogHeight, String loggerName) {
        // 获取当前窗口位置
        float posX = ImGui.getWindowPos().x;
        float posY = ImGui.getWindowPos().y;
        
        // 获取主视口（屏幕）的尺寸和位置
        float viewportMinX = ImGui.getMainViewport().getPos().x;
        float viewportMinY = ImGui.getMainViewport().getPos().y;
        float viewportMaxX = viewportMinX + ImGui.getMainViewport().getSize().x;
        float viewportMaxY = viewportMinY + ImGui.getMainViewport().getSize().y;
        
        // 计算对话框边界
        float dialogRight = posX + dialogWidth;
        float dialogBottom = posY + dialogHeight;
        
        boolean needRepositioning = false;
        float newPosX = posX;
        float newPosY = posY;
        
        // 检查对话框是否完全在视口外
        if (dialogRight < viewportMinX || dialogBottom < viewportMinY || 
            posX > viewportMaxX || posY > viewportMaxY) {
            // 对话框完全在视口外，重新定位到视口中心
            newPosX = viewportMinX + (viewportMaxX - viewportMinX - dialogWidth) / 2;
            newPosY = viewportMinY + (viewportMaxY - viewportMinY - dialogHeight) / 2;
            needRepositioning = true;
            LOGGER.debug("[{}] 对话框完全在视口外，重新定位到中心", loggerName);
        } else {
            // 对话框部分在视口内，仅调整超出部分
            
            // 检查左边界
            if (posX < viewportMinX) {
                newPosX = viewportMinX;
                needRepositioning = true;
            }
            
            // 检查上边界
            if (posY < viewportMinY) {
                newPosY = viewportMinY;
                needRepositioning = true;
            }
            
            // 检查右边界
            if (dialogRight > viewportMaxX) {
                // 如果对话框宽度大于视口宽度，则将左边缘对齐到视口左边缘
                if (dialogWidth > ImGui.getMainViewport().getSize().x) {
                    newPosX = viewportMinX;
                } else {
                    // 否则确保右边缘不超出视口
                    newPosX = viewportMaxX - dialogWidth;
                }
                needRepositioning = true;
            }
            
            // 检查下边界
            if (dialogBottom > viewportMaxY) {
                // 如果对话框高度大于视口高度，则将上边缘对齐到视口上边缘
                if (dialogHeight > ImGui.getMainViewport().getSize().y) {
                    newPosY = viewportMinY;
                } else {
                    // 否则确保下边缘不超出视口
                    newPosY = viewportMaxY - dialogHeight;
                }
                needRepositioning = true;
            }
        }
        
        // 如果需要重新定位，则设置新位置
        if (needRepositioning) {
            // 使用SetWindowPos而不是直接修改ImGui.getWindowPos()
            ImGui.setWindowPos(newPosX, newPosY);
            LOGGER.debug("[{}] 对话框重新定位: [{}, {}] -> [{}, {}]", 
                    loggerName, posX, posY, newPosX, newPosY);
        }
    }
    
    /**
     * 检查对话框位置并在必要时重新定位
     * 确保对话框不会被拖出屏幕可视范围
     * 
     * @param dialogWidth 对话框宽度
     * @param dialogHeight 对话框高度
     */
    public static void checkAndRepositionDialog(float dialogWidth, float dialogHeight) {
        checkAndRepositionDialog(dialogWidth, dialogHeight, "Dialog");
    }
    
    /**
     * 禁用对话框的移动功能
     * 通过在每一帧重新设置对话框的位置来实现
     * 
     * @param posX 对话框的X坐标
     * @param posY 对话框的Y坐标
     */
    public static void lockDialogPosition(float posX, float posY) {
        // 获取当前窗口位置
        float currentPosX = ImGui.getWindowPos().x;
        float currentPosY = ImGui.getWindowPos().y;
        
        // 如果位置发生变化，则重置回指定位置
        if (currentPosX != posX || currentPosY != posY) {
            ImGui.setWindowPos(posX, posY);
        }
    }
} 