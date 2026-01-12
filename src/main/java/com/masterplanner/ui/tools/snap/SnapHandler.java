package com.masterplanner.ui.tools.snap;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.drawing.helper.ISnapHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 吸附处理器实现
 * 从原始DrawingTool中提取的吸附功能
 */
public class SnapHandler implements ISnapHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapHandler.class);
    
    private final IAppState appState;
    private final ISnapManager snapManager;
    private final String toolId;
    private boolean snapEnabled = true;
    
    // 简化的缓存
    private List<Shape> snapTargetShapes;
    private long lastUpdateTime = 0;
    private static final long CACHE_TIMEOUT = 2000; // 2秒
    
    public SnapHandler(IAppState appState, ISnapManager snapManager, String toolId) {
        this.appState = Objects.requireNonNull(appState, "AppState不能为空");
        this.snapManager = snapManager;
        this.toolId = toolId != null ? toolId : "未知工具";
        LOGGER.debug("SnapHandler [{}] 初始化完成", this.toolId);
    }
    
    @Override
    public Vec2d getSnappedWorldPoint(Vec2d screenPoint, CanvasCamera camera) {
        if (!snapEnabled || screenPoint == null || camera == null) {
            return convertScreenToWorldSafe(screenPoint, camera);
        }
        
        try {
            Vec2d worldPoint = camera.screenToWorld(screenPoint);
            
            if (snapManager == null) {
                return worldPoint;
            }
            
            List<Shape> snapTargets = getCachedSnapTargets();
            Vec2d snappedPoint = snapManager.snapPoint(worldPoint, null, snapTargets);
            
            LOGGER.trace("SnapHandler [{}] 吸附处理: ({}, {}) -> ({}, {})", 
                        toolId, worldPoint.x, worldPoint.y, snappedPoint.x, snappedPoint.y);
            
            return snappedPoint;
            
        } catch (Exception e) {
            LOGGER.error("SnapHandler [{}] 吸附处理失败: {}", toolId, e.getMessage(), e);
            return convertScreenToWorldSafe(screenPoint, camera);
        }
    }
    
    @Override
    public void clearCache() {
        snapTargetShapes = null;
        lastUpdateTime = 0;
        LOGGER.debug("SnapHandler [{}] 缓存已清除", toolId);
    }
    
    @Override
    public boolean isSnapEnabled() {
        return snapEnabled;
    }
    
    @Override
    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (!enabled) {
            clearCache();
        }
        LOGGER.debug("SnapHandler [{}] 吸附功能{}", toolId, enabled ? "启用" : "禁用");
    }
    
    private Vec2d convertScreenToWorldSafe(Vec2d screenPoint, CanvasCamera camera) {
        try {
            if (camera != null && screenPoint != null) {
                return camera.screenToWorld(screenPoint);
            }
        } catch (Exception e) {
            LOGGER.debug("SnapHandler [{}] 坐标转换失败: {}", toolId, e.getMessage());
        }
        return screenPoint != null ? screenPoint : new Vec2d(0, 0);
    }
    
    private List<Shape> getCachedSnapTargets() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // 检查缓存是否需要更新
            if (snapTargetShapes == null || 
                (currentTime - lastUpdateTime) > CACHE_TIMEOUT) {
                
                updateCache(currentTime);
            }
            
            return snapTargetShapes != null ? snapTargetShapes : new ArrayList<>();
            
        } catch (Exception e) {
            LOGGER.error("SnapHandler [{}] 获取吸附目标失败: {}", toolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    private void updateCache(long currentTime) {
        try {
            snapTargetShapes = new ArrayList<>();
            
            // 尝试从AppState获取所有图形（支持跨图层吸附）
            if (appState instanceof com.masterplanner.core.state.AppState) {
                List<Shape> allShapes = ((com.masterplanner.core.state.AppState) appState).getShapes();
                if (allShapes != null) {
                    snapTargetShapes.addAll(allShapes);
                }
            } else {
                // 兼容性处理：使用反射获取图形
                try {
                    java.lang.reflect.Method getShapesMethod = appState.getClass().getMethod("getShapes");
                    Object result = getShapesMethod.invoke(appState);
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Shape> shapes = (List<Shape>) result;
                        snapTargetShapes.addAll(shapes);
                    }
                } catch (Exception reflectionEx) {
                    LOGGER.debug("SnapHandler [{}] 无法通过反射获取图形列表，回退到当前图层", toolId);
                    // 回退到只获取当前图层的图形
                    ILayer currentLayer = appState.getActiveLayer();
                    if (currentLayer != null) {
                        List<Shape> layerShapes = currentLayer.getShapes();
                        if (layerShapes != null) {
                            snapTargetShapes.addAll(layerShapes);
                        }
                    }
                }
            }
            
            lastUpdateTime = currentTime;
            
            LOGGER.debug("SnapHandler [{}] 缓存已更新: 图形数量={}", 
                        toolId, snapTargetShapes.size());
                        
        } catch (Exception e) {
            LOGGER.error("SnapHandler [{}] 更新缓存失败: {}", toolId, e.getMessage(), e);
            snapTargetShapes = new ArrayList<>();
        }
    }
} 