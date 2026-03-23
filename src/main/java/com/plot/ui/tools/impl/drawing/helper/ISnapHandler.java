package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.canvas.CanvasCamera;

/**
 * 吸附处理器接口
 * 负责处理绘图过程中的吸附功能
 * 
 * @author Plot Team
 * @version 1.0
 */
public interface ISnapHandler {
    
    /**
     * 获取经过吸附处理的世界坐标点
     * @param screenPoint 屏幕坐标点
     * @param camera 相机引用
     * @return 经过吸附处理的世界坐标点
     */
    Vec2d getSnappedWorldPoint(Vec2d screenPoint, CanvasCamera camera);
    
    /**
     * 清理吸附缓存
     */
    void clearCache();
    
    /**
     * 检查是否启用吸附功能
     * @return 是否启用吸附
     */
    boolean isSnapEnabled();
    
    /**
     * 设置吸附功能开关
     * @param enabled 是否启用
     */
    void setSnapEnabled(boolean enabled);
} 