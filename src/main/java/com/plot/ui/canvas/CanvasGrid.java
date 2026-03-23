package com.plot.ui.canvas;

import com.plot.ui.grid.GridSettings;
import imgui.ImDrawList;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.ui.component.UIComponent;
import com.plot.api.model.IDirty;

/**
 * 画布网格
 */
public class CanvasGrid implements UIComponent, AutoCloseable, IDirty {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/CanvasGrid");
    
    private boolean visible = true;
    private GridSettings gridSettings;

    private boolean dirty = false;
    private final CanvasCore core;

    /**
     * 构造函数
     * @param core 画布核心对象
     */
    public CanvasGrid(CanvasCore core) {
        this.core = core;
        this.gridSettings = new GridSettings(); // 使用默认设置
    }
    
    @Override
    public void render() {
        // 基础渲染实现，实际渲染在 render(ImDrawList, CanvasCamera) 中完成
        LOGGER.trace("基础渲染方法被调用");
    }
    
    /**
     * 使用指定的绘图列表和相机渲染网格
     * @param drawList ImGui绘图列表
     * @param camera 画布相机
     */
    public void render(ImDrawList drawList, CanvasCamera camera) {
        LOGGER.debug("CanvasGrid.render被调用，可见性={}, drawList={}", visible, drawList != null ? "非空" : "空");
        
        if (!visible) {
            LOGGER.debug("网格不可见，跳过渲染");
            return;
        }
        
        if (drawList == null) {
            LOGGER.error("drawList为空，无法渲染网格");
            return;
        }
        
        try {
            // 使用 CanvasRenderer 同步过来的画布屏幕区域（不依赖 ImGui 当前窗口）
            float windowX = core != null ? core.getCanvasScreenX() : 0.0f;
            float windowY = core != null ? core.getCanvasScreenY() : 0.0f;
            float windowWidth = core != null ? core.getCanvasScreenW() : ImGui.getIO().getDisplaySizeX();
            float windowHeight = core != null ? core.getCanvasScreenH() : ImGui.getIO().getDisplaySizeY();
            
            LOGGER.debug("渲染窗口: 位置=({}, {}), 尺寸=({}, {})", 
                windowX, windowY, windowWidth, windowHeight);
            
            // 使用 gridSettings 的设置
            int gridColor = gridSettings.getColorWithOpacity();
            float gridSize = gridSettings.getGridSize();
            float thickness = gridSettings.getLineWidth();
            
            LOGGER.debug("网格设置: 大小={}, 颜色={}, 线宽={}", 
                gridSize, String.format("#%08X", gridColor), thickness);
            
            // 检查相机缩放和网格大小
            if (camera == null) {
                LOGGER.error("相机对象为空，无法计算缩放后的网格大小");
                return;
            }
            
            // 计算网格线位置
            float zoom = camera.getZoom();
            float scaledGridSize = gridSize * zoom; // 缩放网格大小，zoom已经是0.1到10.0的实际值，不需要除以100
            
            LOGGER.debug("相机缩放: {}倍, 缩放后的网格大小: {}", zoom, scaledGridSize);
            
            // 安全检查：确保我们有有效的网格大小
            if (scaledGridSize <= 0) {
                LOGGER.error("缩放后的网格大小无效: {}, 跳过渲染", scaledGridSize);
                return;
            }
            
            // 计算需要绘制的网格线范围
            // 确保起始点在窗口左上角
            float startX = windowX - (windowX % scaledGridSize);
            float startY = windowY - (windowY % scaledGridSize);
            // 确保结束点超出窗口右下角
            float endX = windowX + windowWidth + scaledGridSize;
            float endY = windowY + windowHeight + scaledGridSize;
            
            LOGGER.debug("网格线范围: X=[{}, {}], Y=[{}, {}]", startX, endX, startY, endY);
            
            // 设置裁剪区域为整个窗口
            drawList.pushClipRect(windowX, windowY, 
                                windowX + windowWidth, 
                                windowY + windowHeight);
            
            // 计算垂直线和水平线的数量
            int verticalLines = (int)((endX - startX) / scaledGridSize) + 1;
            int horizontalLines = (int)((endY - startY) / scaledGridSize) + 1;
            
            LOGGER.debug("准备绘制网格线: 垂直线={}, 水平线={}", verticalLines, horizontalLines);
            
            // 绘制垂直线
            int vertLinesDrawn = 0;
            for (float x = startX; x <= endX; x += scaledGridSize) {
                drawList.addLine(x, startY, x, endY, gridColor, thickness);
                vertLinesDrawn++;
            }
            
            // 绘制水平线
            int horizLinesDrawn = 0;
            for (float y = startY; y <= endY; y += scaledGridSize) {
                drawList.addLine(startX, y, endX, y, gridColor, thickness);
                horizLinesDrawn++;
            }
            
            drawList.popClipRect();
            
            LOGGER.debug("网格线绘制完成: 实际绘制垂直线={}, 水平线={}", vertLinesDrawn, horizLinesDrawn);
            
        } catch (Exception e) {
            LOGGER.error("渲染网格失败", e);
        }
    }
    
    /**
     * 设置网格设置
     * @param settings 网格设置
     */
    public void setGridSettings(GridSettings settings) {
        if (settings != null) {
            LOGGER.debug("更新网格设置: {} -> {}", this.gridSettings, settings);
            this.gridSettings = settings;
            markDirty();
        } else {
            LOGGER.warn("尝试设置空的网格设置，已忽略");
        }
    }
    
    /**
     * 设置网格可见性
     * @param visible 是否可见
     */
    public void setVisible(boolean visible) {
        LOGGER.debug("设置网格可见性: {} -> {}", this.visible, visible);
        if (this.visible != visible) {
            this.visible = visible;
            LOGGER.debug("网格可见性已更改，标记为需要重绘");
            markDirty();
        } else {
            LOGGER.debug("网格可见性未变化，仍为: {}", this.visible);
        }
    }
    
    /**
     * 获取网格可见性
     * @return 是否可见
     */
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void init() {
        try {
            LOGGER.debug("初始化CanvasGrid...");
            // 目前无需特殊初始化，因为所有初始化工作都在构造函数中完成了
            LOGGER.debug("CanvasGrid初始化成功");
        } catch (Exception e) {
            LOGGER.error("初始化CanvasGrid失败", e);
            throw new RuntimeException("初始化CanvasGrid失败", e);
        }
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("释放CanvasGrid资源...");
        // 清理资源
    }

    /**
     * 根据条件渲染
     * @param renderGrid 是否渲染网格
     */
    public void render(boolean renderGrid) {
        if (!renderGrid) {
            // 如果指定不渲染网格，直接返回
        }
        // ... 渲染其他内容 ...
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void clearDirty() {
        dirty = false;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }
} 