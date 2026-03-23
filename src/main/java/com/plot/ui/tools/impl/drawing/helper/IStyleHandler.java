package com.plot.ui.tools.impl.drawing.helper;

import com.plot.core.model.Shape;
import com.plot.core.graphics.style.ShapeStyle;

/**
 * 样式处理器接口
 * 负责处理绘图过程中的样式管理
 * 
 * @author Plot Team
 * @version 2.0 - 添加样式获取方法
 */
public interface IStyleHandler {
    
    /**
     * 为预览图形应用样式
     * @param shape 要应用样式的图形
     */
    void applyPreviewStyle(Shape shape);
    
    /**
     * 为最终图形应用样式
     * @param shape 要应用样式的图形
     */
    void applyFinalStyle(Shape shape);
    
    /**
     * 获取预览样式对象
     * 推荐使用此方法替代DrawingTool.getCurrentStyle()
     * @return 预览样式，带缓存优化
     */
    ShapeStyle getPreviewStyle();
    
    /**
     * 获取最终样式对象
     * @return 最终样式，带缓存优化
     */
    ShapeStyle getFinalStyle();
    
    /**
     * 清理样式缓存
     */
    void invalidateCache();
} 