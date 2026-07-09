package com.plot.api.state;

import com.plot.api.graphics.IShapeStyle;
import com.plot.api.model.ILayer;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.Canvas;
import java.util.concurrent.ScheduledFuture;

/**
 * 应用状态管理接口
 * 定义应用级状态管理的核心功能，降低与具体实现类的耦合
 * 
 * 主要职责：
 * - 画布管理
 * - 图层管理  
 * - 样式管理
 * - 任务调度
 */
public interface IAppState {
    
    /**
     * 获取当前画布实例
     * @return 画布对象，可能为null
     */
    Canvas getCanvas();
    
    /**
     * 获取当前活动图层
     * @return 活动图层，可能为null
     */
    ILayer getActiveLayer();
    
    /**
     * 获取当前图形样式
     * @return 当前样式，可能为null
     */
    IShapeStyle getCurrentShapeStyle();
    
    /**
     * 调度延迟任务
     * @param task 要执行的任务
     * @param delayMs 延迟时间（毫秒）
     * @return 任务的Future对象，可用于取消任务
     */
    ScheduledFuture<?> scheduleDelayedTask(Runnable task, long delayMs);
    
    /**
     * 检查应用状态是否有效
     * @return 是否有效
     */
    boolean isValid();
    
    /**
     * 获取应用状态的版本号（用于缓存失效检测）
     * @return 版本号
     */
    long getStateVersion();

    /**
     * 将图形添加到当前活动图层
     * @param shape 要添加的图形
     */
    void addShape(Shape shape);
} 