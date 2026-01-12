package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.SpiralShape;
import com.masterplanner.core.geometry.shapes.SpiralType;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.snap.SnapManager;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.tools.impl.drawing.config.SpiralConfigManager;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.masterplanner.ui.tools.impl.drawing.strategy.SpiralInteractionStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import com.masterplanner.ui.canvas.CanvasCamera;

import java.util.List;
import java.util.ArrayList;

/**
 * 螺旋线工具 - 状态中心版本
 * 
 * <p>作为"总指挥"和"状态中心"，负责：</p>
 * <ul>
 *   <li>持有所有核心状态（控制点、鼠标位置、预览图形）</li>
 *   <li>直接处理用户交互（点击、移动）</li>
 *   <li>协调所有组件（配置、工厂、渲染器）</li>
 *   <li>统一的状态管理和数据流</li>
 * </ul>
 * 
 * <p>支持六种螺旋线类型：</p>
 * <ul>
 *   <li>线性螺旋：等距离间隔的标准螺旋</li>
 *   <li>生长螺旋：指数增长的对数螺旋</li>
 *   <li>半圆螺旋：由半圆连接形成的螺旋</li>
 *   <li>费马螺旋：r = a*sqrt(θ) 的抛物螺旋</li>
 *   <li>斐波那契螺旋：基于黄金比例的螺旋</li>
 *   <li>多边形螺旋：沿多边形路径的螺旋</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 3.0 - 状态中心版本
 */
public class SpiralTool extends DrawingTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralTool.class);
    
    // ====== 组件管理 ======
    
    private final SpiralConfigManager configManager;
    private final SpiralPreviewRenderer previewRenderer;
    private final SpiralShapeFactory shapeFactory;
    
    // ====== 状态字段 - 唯一数据源 ======
    
    // 绘制状态
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private SpiralShape previewSpiral;
    
    // 状态消息映射
    private static final java.util.Map<SpiralType, java.util.List<String>> TYPE_STATUS_MESSAGES = java.util.Map.of(
        SpiralType.LINEAR, java.util.List.of(
            "点击确定螺旋中心",
            "点击确定螺旋起点",
            "点击确定螺距点",
            "移动鼠标调整最外圈，点击完成绘制"
        ),
        SpiralType.LOGARITHMIC, java.util.List.of(
            "点击确定螺旋中心",
            "点击确定起始半径（中心到第一点的距离）",
            "移动鼠标调整最外圈，滚轮调整生长因子，点击完成绘制"
        ),
        SpiralType.SEMICIRCLE, java.util.List.of(
            "点击确定螺旋中心",
            "点击确定起始半径（中心到第一点的距离）",
            "移动鼠标调整最外圈，滚轮调整扩张率，点击完成绘制"
        ),
        SpiralType.FERMAT, java.util.List.of(
            "点击确定螺旋中心（起点）",
            "移动鼠标调整最外圈，滚轮调整螺旋系数，点击完成绘制"
        ),
        SpiralType.FIBONACCI, java.util.List.of(
            "点击确定螺旋中心",
            "点击确定起始半径（中心到第一点的距离）",
            "移动鼠标调整最外圈，滚轮调整螺旋系数，点击完成绘制"
        ),
        SpiralType.POLYGON, java.util.List.of(
            "点击确定螺旋中心",
            "滚轮调整螺距，点击确定半径完成绘制"
        )
    );
    
    // ====== 构造函数 ======
    
    /**
     * 依赖注入构造函数（推荐方式）
     */
    public SpiralTool(AppState appState, SnapManager snapManager) {
        super("spiral", "螺旋线", Icons.SPIRAL_IDENTIFIER, 
              "绘制螺旋线，支持多种螺旋类型和参数", appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        // 初始化组件
        this.configManager = new SpiralConfigManager();
        this.previewRenderer = new SpiralPreviewRenderer(this, configManager);
        this.shapeFactory = new SpiralShapeFactory(configManager);
        
        initializeSpiralTool();
    }
    
    /**
     * 兼容性构造函数
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public SpiralTool() {
        super("spiral", "螺旋线", Icons.SPIRAL_IDENTIFIER, 
              "绘制螺旋线，支持多种螺旋类型和参数");
        
        // 初始化组件
        this.configManager = new SpiralConfigManager();
        this.previewRenderer = new SpiralPreviewRenderer(this, configManager);
        this.shapeFactory = new SpiralShapeFactory(configManager);
        
        initializeSpiralTool();
    }
    
    // ====== 初始化方法 ======
    
    /**
     * 初始化螺旋线工具
     */
    private void initializeSpiralTool() {
        // 初始化预览螺旋对象
        initializePreviewSpiral();
        
        // 订阅配置事件
        EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent && 
                getId().equals(toolConfigEvent.getToolId())) {
                configManager.handleToolConfig(toolConfigEvent);
            }
        });
        
        // 更新状态消息
        updateStatusMessage();
        
        LOGGER.debug("SpiralTool 初始化完成，类型: {}, 螺距: {}, 起始半径: {}", 
                    configManager.getCurrentType(), configManager.getSpacing(), configManager.getStartRadius());
    }
    
    /**
     * 初始化预览螺旋对象
     * 创建一次，后续通过更新属性来重用
     */
    private void initializePreviewSpiral() {
        if (previewSpiral == null) {
            previewSpiral = new SpiralShape(new Vec2d(0, 0), 10, 1, 10, SpiralType.LINEAR, false);
            previewSpiral.setStyle(getStyleHandler().getPreviewStyle());
            LOGGER.debug("SpiralTool: 预览螺旋对象初始化完成");
        }
    }
    
    // ====== 状态管理方法 ======
    
    /**
     * 更新状态消息
     */
    private void updateStatusMessage() {
        List<String> messages = TYPE_STATUS_MESSAGES.getOrDefault(configManager.getCurrentType(), List.of("点击确定螺旋中心", "点击确定半径和方向"));
        int index = Math.min(controlPoints.size(), messages.size() - 1);
        String message = messages.get(index);
        setStatusMessage(message);
        LOGGER.debug("螺旋线工具状态消息已更新: {}", message);
    }
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        currentMousePoint = null;
        previewSpiral = null;
        updateStatusMessage();
        LOGGER.debug("螺旋线工具状态已重置");
    }
    
    // ====== 交互处理 - 通过策略模式处理 ======
    
    /**
     * 处理鼠标点击事件
     */
    public void handleMouseClick(Vec2d pos, int button) {
        LOGGER.debug("SpiralTool.handleMouseClick: 鼠标点击，位置={}, 按键={}", pos, button);
        
        if (button != 0) { // 非左键
            if (button == 1) { // 右键取消
                resetDrawingState();
                setStatusMessage("绘制已取消");
                return;
            }
            return;
        }
        
        // 获取吸附后的世界坐标
        Vec2d snappedPoint = getSnapHandler().getSnappedWorldPoint(pos, getCamera());
        LOGGER.debug("SpiralTool.handleMouseClick: 吸附后坐标={}", snappedPoint);
        
        // 添加控制点
        controlPoints.add(snappedPoint);
        LOGGER.debug("SpiralTool.handleMouseClick: 添加控制点，当前数量={}", controlPoints.size());
        
        // 检查是否完成绘制
        if (isDrawingComplete()) {
            LOGGER.debug("SpiralTool.handleMouseClick: 绘制完成，提交图形");
            completeDrawing();
        } else {
            // 继续绘制
            updateStatusMessage();
            updatePreview();
        }
    }
    
    /**
     * 处理鼠标移动事件
     */
    public void handleMouseMove(Vec2d pos) {
        // 更新鼠标位置
        currentMousePoint = getSnapHandler().getSnappedWorldPoint(pos, getCamera());
        
        // 更新预览
        updatePreview();
        
        LOGGER.debug("SpiralTool.handleMouseMove: 鼠标移动到 {}", currentMousePoint);
    }
    
    /**
     * 检查绘制是否完成
     */
    public boolean isDrawingComplete() {
        SpiralType currentType = configManager.getCurrentType();
        int maxPoints;
        switch (currentType) {
            case LINEAR -> maxPoints = 4;
            case FIBONACCI, LOGARITHMIC, SEMICIRCLE -> maxPoints = 3; // 斐波那契、对数螺旋和半圆螺旋都使用3个点
            case FERMAT -> maxPoints = 2; // 费马螺旋使用2个点：中心点和最外圈点
            default -> maxPoints = 2;
        }
        return controlPoints.size() >= maxPoints;
    }
    
    /**
     * 完成绘制
     */
    protected void completeDrawing() {
        try {
            // 创建最终图形
            Shape finalShape = shapeFactory.createSpiralShape(controlPoints, getStyleHandler().getFinalStyle());
            if (finalShape != null) {
                // 提交图形
                commitShape(finalShape);
                LOGGER.debug("SpiralTool.completeDrawing: 图形提交成功");
            } else {
                LOGGER.error("SpiralTool.completeDrawing: 创建最终图形失败");
            }
        } catch (Exception e) {
            LOGGER.error("SpiralTool.completeDrawing: 完成绘制时发生错误", e);
        } finally {
            // 重置状态
            resetDrawingState();
        }
    }
    
    // ====== 基类重写方法 ======
    
    @Override
    public void onActivate() {
        LOGGER.debug("SpiralTool activated");
        resetDrawingState();
        updateStatusMessage();
    }
    
    @Override
    public void onDeactivate() {
        LOGGER.debug("SpiralTool deactivated");
        resetDrawingState();
        setStatusMessage("");
    }
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 使用形状工厂创建螺旋线图形
        return shapeFactory.createSpiralShape(controlPoints, getStyleHandler().getFinalStyle());
    }
    
    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 返回无状态策略，实际交互由SpiralTool直接处理
        return new SpiralInteractionStrategy();
    }
    
    // ====== 配置管理 ======
    
    /**
     * 处理键盘事件
     */
    @Override
    public boolean onKeyDown(int keyCode) {
        try {
            // 按ESC键取消绘制
            if (keyCode == java.awt.event.KeyEvent.VK_ESCAPE && !controlPoints.isEmpty()) {
                resetDrawingState();
                LOGGER.debug("SpiralTool: ESC键按下，取消绘制");
                return true;
            }
            
            return super.onKeyDown(keyCode);
        } catch (Exception e) {
            LOGGER.error("SpiralTool 处理键盘事件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 处理鼠标滚轮事件 - 调整斐波那契螺旋和对数螺旋的参数
     */
    @Override
    public boolean onMouseWheel(Vec2d pos, double delta) {
        try {
            LOGGER.debug("SpiralTool.onMouseWheel: 收到滚轮事件，类型={}, 控制点数={}, 鼠标点={}, delta={}", 
                configManager.getCurrentType(), controlPoints.size(), currentMousePoint, delta);
            
            // 检查是否是支持滚轮调整的螺旋类型
            if (configManager.getCurrentType() != SpiralType.FIBONACCI && 
                configManager.getCurrentType() != SpiralType.LOGARITHMIC &&
                configManager.getCurrentType() != SpiralType.SEMICIRCLE &&
                configManager.getCurrentType() != SpiralType.FERMAT &&
                configManager.getCurrentType() != SpiralType.POLYGON) {
                LOGGER.debug("SpiralTool.onMouseWheel: 当前不是支持滚轮调整的螺旋类型，跳过处理");
                return false;
            }
            
            // 费马螺旋和多边形螺旋只需要1个控制点就可以开始滚轮调整，其他类型需要至少2个点
            if (configManager.getCurrentType() == SpiralType.FERMAT || 
                configManager.getCurrentType() == SpiralType.POLYGON) {
                if (controlPoints.isEmpty()) {
                    LOGGER.debug("SpiralTool.onMouseWheel: 费马螺旋/多边形螺旋控制点数不足，需要至少1个点，当前有{}个", 0);
                    return false;
                }
            } else {
                if (controlPoints.size() < 2) {
                    LOGGER.debug("SpiralTool.onMouseWheel: 控制点数不足，需要至少2个点，当前有{}个", controlPoints.size());
                    return false;
                }
            }
            
            if (currentMousePoint == null) {
                LOGGER.debug("SpiralTool.onMouseWheel: 当前鼠标点为空");
                return false;
            }
            
            // 只有在支持滚轮调整的螺旋类型且处于绘制状态时才处理滚轮事件
            if ((configManager.getCurrentType() == SpiralType.FIBONACCI || 
                 configManager.getCurrentType() == SpiralType.LOGARITHMIC ||
                 configManager.getCurrentType() == SpiralType.SEMICIRCLE ||
                 configManager.getCurrentType() == SpiralType.FERMAT ||
                 configManager.getCurrentType() == SpiralType.POLYGON) &&
                    !controlPoints.isEmpty() && currentMousePoint != null) {
                
                if (configManager.getCurrentType() == SpiralType.FIBONACCI) {
                    // 斐波那契螺旋：调整spiralCoefficient
                    float currentSpiralCoefficient = configManager.getSpiralCoefficient();
                    float adjustment = (float) (delta * 0.1);
                    float newSpiralCoefficient = Math.max(0.1f, Math.min(5.0f, currentSpiralCoefficient + adjustment));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 调整螺旋系数 {} -> {}, 调整量={}", 
                        currentSpiralCoefficient, newSpiralCoefficient, adjustment);
                    
                    configManager.updateConfig("spiralCoefficient", String.valueOf(newSpiralCoefficient));
                    setStatusMessage(String.format("螺旋系数: %.2f (滚轮调整)", newSpiralCoefficient));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 处理完成，新螺旋系数={}", newSpiralCoefficient);
                } else if (configManager.getCurrentType() == SpiralType.LOGARITHMIC) {
                    // 对数螺旋：调整growthFactor
                    float currentGrowthFactor = configManager.getGrowthFactor();
                    float adjustment = (float) (delta * 0.1);
                    float newGrowthFactor = Math.max(0.1f, Math.min(10.0f, currentGrowthFactor + adjustment));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 调整生长因子 {} -> {}, 调整量={}", 
                        currentGrowthFactor, newGrowthFactor, adjustment);
                    
                    configManager.updateConfig("growthFactor", String.valueOf(newGrowthFactor));
                    setStatusMessage(String.format("生长因子: %.2f (滚轮调整)", newGrowthFactor));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 处理完成，新生长因子={}", newGrowthFactor);
                } else if (configManager.getCurrentType() == SpiralType.SEMICIRCLE) {
                    // 半圆螺旋：调整expansionRate
                    float currentExpansionRate = configManager.getExpansionRate();
                    float adjustment = (float) (delta * 0.1);
                    float newExpansionRate = Math.max(0.1f, Math.min(5.0f, currentExpansionRate + adjustment));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 调整扩张率 {} -> {}, 调整量={}", 
                        currentExpansionRate, newExpansionRate, adjustment);
                    
                    configManager.updateConfig("expansionRate", String.valueOf(newExpansionRate));
                    setStatusMessage(String.format("扩张率: %.2f (滚轮调整)", newExpansionRate));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 处理完成，新扩张率={}", newExpansionRate);
                } else if (configManager.getCurrentType() == SpiralType.FERMAT) {
                    // 费马螺旋：调整spiralCoefficient
                    float currentSpiralCoefficient = configManager.getSpiralCoefficient();
                    float adjustment = (float) (delta * 0.1);
                    float newSpiralCoefficient = Math.max(0.5f, Math.min(8.0f, currentSpiralCoefficient + adjustment));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 调整螺旋系数 {} -> {}, 调整量={}", 
                        currentSpiralCoefficient, newSpiralCoefficient, adjustment);
                    
                    configManager.updateConfig("spiralCoefficient", String.valueOf(newSpiralCoefficient));
                    setStatusMessage(String.format("螺旋系数: %.2f (滚轮调整)", newSpiralCoefficient));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 处理完成，新螺旋系数={}", newSpiralCoefficient);
                } else if (configManager.getCurrentType() == SpiralType.POLYGON) {
                    // 多边形螺旋：调整spacing（螺距）
                    float currentSpacing = configManager.getSpacing();
                    float adjustment = (float) (delta * 2.0); // 每次调整2个单位
                    float newSpacing = Math.max(10.0f, Math.min(200.0f, currentSpacing + adjustment));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 调整螺距 {} -> {}, 调整量={}", 
                        currentSpacing, newSpacing, adjustment);
                    
                    configManager.updateConfig("spacing", String.valueOf(newSpacing));
                    setStatusMessage(String.format("螺距: %.1f (滚轮调整)", newSpacing));
                    
                    LOGGER.debug("SpiralTool.onMouseWheel: 处理完成，新螺距={}", newSpacing);
                }
                
                // 更新预览
                updatePreview();
                return true;
            } else {
                LOGGER.debug("SpiralTool.onMouseWheel: 不满足处理条件，类型={}, 控制点数={}, 鼠标点={}", 
                    configManager.getCurrentType(), controlPoints.size(), currentMousePoint);
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.error("SpiralTool 处理鼠标滚轮事件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 更新工具配置
     */
    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("SpiralTool.updateConfig: 收到配置更新，key={}, value={}", key, value);
        try {
            boolean needsPreviewUpdate = configManager.updateConfig(key, value);
            
            LOGGER.debug("SpiralTool.updateConfig: 配置处理完成，needsPreviewUpdate={}", needsPreviewUpdate);
            
            if (needsPreviewUpdate && previewSpiral != null) {
                updatePreview();
                LOGGER.debug("SpiralTool.updateConfig: 已更新预览");
            }
        } catch (Exception e) {
            LOGGER.error("配置更新错误: key={}, value={}, error={}", key, value, e.getMessage());
        }
    }
    
    // ====== 预览更新方法 ======
    
    /**
     * 更新预览 - 统一入口
     */
    private void updatePreview() {
        LOGGER.debug("updatePreview: 开始更新预览，控制点数={}, 鼠标点={}, 类型={}", 
            controlPoints.size(), currentMousePoint, configManager.getCurrentType());
            
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            LOGGER.debug("updatePreview: 无控制点或鼠标点，清除预览");
            previewSpiral = null;
            return;
        }

        try {
            // 确保预览对象存在
            initializePreviewSpiral();
            
            // 使用工厂更新预览
            shapeFactory.updatePreviewShape(previewSpiral, controlPoints, currentMousePoint);
            
            // 更新预览样式以使用当前图层颜色
            previewSpiral.setStyle(getStyleHandler().getPreviewStyle());
            
            LOGGER.debug("updatePreview: 预览更新成功");
        } catch (Exception e) {
            LOGGER.error("updatePreview: 创建预览失败", e);
            previewSpiral = null;
        }
    }
    


    // ====== 预览渲染方法 ======
    
    @Override
    public void renderPreview(DrawContext context) {
        LOGGER.debug("renderPreview: 开始渲染预览，控制点数={}, 预览对象={}", 
            controlPoints.size(), previewSpiral != null ? "存在" : "null");
        previewRenderer.renderPreview(context, controlPoints, currentMousePoint);
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        LOGGER.debug("renderPreview(ImGui): 开始渲染预览，控制点数={}, 预览对象={}", 
            controlPoints.size(), previewSpiral != null ? "存在" : "null");
        previewRenderer.renderPreview(drawList, camera, controlPoints, currentMousePoint);
    }

    
    // ====== 公共Getter方法 - 为UI渲染器提供安全接口 ======
    
    /**
     * 获取当前螺旋类型
     * @return 当前螺旋类型
     */
    public SpiralType getCurrentType() {
        return configManager.getCurrentType();
    }
    
    /**
     * 获取螺距
     * @return 当前螺距值
     */
    public float getSpacing() {
        return configManager.getSpacing();
    }
    
    /**
     * 获取尖角样式状态
     * @return 是否启用尖角样式
     */
    public boolean isSharpEdged() {
        return configManager.isSharpEdged();
    }
    
    /**
     * 获取起始半径
     * @return 当前起始半径值
     */
    public float getStartRadius() {
        return configManager.getStartRadius();
    }

    /**
     * 获取扩张率
     * @return 当前扩张率值
     */
    public float getExpansionRate() {
        return configManager.getExpansionRate();
    }
    
    /**
     * 获取多边形边数
     * @return 当前边数值
     */
    public int getSides() {
        return configManager.getSides();
    }
    
    /**
     * 获取圈数
     * @return 当前圈数值
     */
    public float getTurns() {
        return configManager.getTurns();
    }
    
    /**
     * 获取螺旋系数
     * @return 当前螺旋系数值
     */
    public float getSpiralCoefficient() {
        return configManager.getSpiralCoefficient();
    }
    
    /**
     * 获取旋转方向
     * @return 是否顺时针旋转
     */
    public boolean isClockwise() {
        return configManager.isClockwise();
    }

    /**
     * 获取生长因子（对数螺旋参数）
     * @return 当前生长因子值
     */
    public float getGrowthFactor() {
        return configManager.getGrowthFactor();
    }

    /**
     * 获取预览螺旋对象
     * @return 预览螺旋对象
     */
    public SpiralShape getPreviewSpiral() {
        return previewSpiral;
    }
    
    /**
     * 获取控制点列表
     * @return 当前控制点列表
     */
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    /**
     * 获取当前鼠标位置
     * @return 当前鼠标位置
     */
    public Vec2d getCurrentMousePoint() {
        return currentMousePoint;
    }
} 