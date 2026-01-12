package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.SpiralShape;
import com.masterplanner.core.geometry.shapes.SpiralType;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.config.SpiralConfigManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 螺旋线图形工厂 - 唯一创建入口
 * 
 * <p>职责：</p>
 * <ul>
 *   <li>创建和更新螺旋线图形（包括预览和最终图形）</li>
 *   <li>封装所有复杂的参数计算逻辑</li>
 *   <li>提供统一的图形创建接口</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.0 - 统一创建入口
 */
public class SpiralShapeFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralShapeFactory.class);
    private final SpiralConfigManager configManager;

    public SpiralShapeFactory(SpiralConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 从控制点创建螺旋线图形
     * 线性螺旋4点参数：中心、起点、螺距点、最外圈点
     * 斐波那契螺旋3点参数：中心、起始点、最外圈点
     * 其它类型2点参数：中心、半径点
     */
    public Shape createSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        SpiralType type = configManager.getCurrentType();
        if (type == SpiralType.LINEAR && controlPoints.size() >= 4) {
            return createLinearSpiralShape(controlPoints, style);
        } else if (type == SpiralType.FIBONACCI && controlPoints.size() >= 3) {
            return createFibonacciSpiralShape(controlPoints, style);
        } else if (type == SpiralType.LOGARITHMIC && controlPoints.size() >= 3) {
            return createLogarithmicSpiralShape(controlPoints, style);
        } else if (type == SpiralType.SEMICIRCLE && controlPoints.size() >= 3) {
            return createSemicircleSpiralShape(controlPoints, style);
        } else if (type == SpiralType.FERMAT && controlPoints.size() >= 2) {
            return createFermatSpiralShape(controlPoints, style);
        } else if (type == SpiralType.POLYGON && controlPoints.size() >= 2) {
            return createPolygonSpiralShape(controlPoints, style);
        } else if (controlPoints.size() >= 2) {
            return createNonLinearSpiralShape(controlPoints, style);
        }
        return null;
    }
    
    /**
     * 更新预览螺旋对象
     * 根据控制点和鼠标位置更新预览图形的参数
     * 
     * @param preview 要更新的预览对象
     * @param controlPoints 控制点列表
     * @param mousePoint 当前鼠标位置
     */
    public void updatePreviewShape(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        if (preview == null || controlPoints.isEmpty() || mousePoint == null) {
            LOGGER.debug("updatePreviewShape: 参数无效，跳过更新");
            return;
        }
        
        try {
            SpiralType type = configManager.getCurrentType();
            if (type == SpiralType.LINEAR) {
                updateLinearSpiralPreview(preview, controlPoints, mousePoint);
            } else if (type == SpiralType.FIBONACCI) {
                updateFibonacciSpiralPreview(preview, controlPoints, mousePoint);
            } else if (type == SpiralType.LOGARITHMIC) {
                updateLogarithmicSpiralPreview(preview, controlPoints, mousePoint);
            } else if (type == SpiralType.SEMICIRCLE) {
                updateSemicircleSpiralPreview(preview, controlPoints, mousePoint);
            } else if (type == SpiralType.FERMAT) {
                updateFermatSpiralPreview(preview, controlPoints, mousePoint);
            } else if (type == SpiralType.POLYGON) {
                updatePolygonSpiralPreview(preview, controlPoints, mousePoint);
            } else {
                updateNonLinearSpiralPreview(preview, controlPoints, mousePoint);
            }
            
            LOGGER.debug("updatePreviewShape: 预览更新成功，类型={}", type);
        } catch (Exception e) {
            LOGGER.error("updatePreviewShape: 预览更新失败", e);
        }
    }

    /**
     * 创建线性螺旋图形
     * 参数：中心点、起点、螺距点、最外圈点
     */
    private SpiralShape createLinearSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d startPoint = controlPoints.get(1);
        Vec2d pitchPoint = controlPoints.get(2);
        Vec2d maxRadiusPoint = controlPoints.get(3);

        float startRadiusVal = (float) Math.max(0.01, center.distance(startPoint));
        float maxRadiusVal = (float) Math.max(startRadiusVal + 0.01, center.distance(maxRadiusPoint));
        
        // 计算螺距：使用第二个点和第三个点之间的距离
        float pitchVal = (float) Math.max(2.0, startPoint.distance(pitchPoint));
        
        // 计算圈数：基于最大半径和起始半径的差值除以螺距
        float turnsVal = Math.max(0.1f, (maxRadiusVal - startRadiusVal) / pitchVal);

        try {
            SpiralShape spiral = new SpiralShape(center, startRadiusVal, turnsVal, pitchVal, SpiralType.LINEAR, configManager.isSharpEdged());
            spiral.setStartRadius(startRadiusVal);
            if (style != null) {
                spiral.setStyle(style);
            }
            LOGGER.debug("创建线性螺旋：起始半径={}, 螺距={}, 最大半径={}, 圈数={}",
                startRadiusVal, pitchVal, maxRadiusVal, turnsVal);
            return spiral;
        } catch (Exception e) {
            LOGGER.error("创建线性螺旋失败：起始半径={}, 螺距={}, 最大半径={}, 圈数={}, 错误={}",
                startRadiusVal, pitchVal, maxRadiusVal, turnsVal, e.getMessage());
            return null;
        }
    }

    /**
     * 创建非线性螺旋图形
     */
    private SpiralShape createNonLinearSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d radiusPoint = controlPoints.get(1);

        // 计算起始半径和最大半径
        double startRadius;
        double maxRadius;
        
        if (configManager.getCurrentType() == SpiralType.LOGARITHMIC || configManager.getCurrentType() == SpiralType.SEMICIRCLE) {
            // 对数螺旋和半圆螺旋：使用第一个点和第二个点之间的距离作为起始半径
            startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
            // 对于2点绘制，最大半径设为起始半径的2倍作为默认值
            maxRadius = startRadius * 2.0;
            LOGGER.debug("createNonLinearSpiralShape: {}螺旋使用控制点距离作为起始半径 = {}, 最大半径 = {}", 
                configManager.getCurrentType(), startRadius, maxRadius);
        } else {
            // 其他类型：使用配置的起始半径，最大半径为控制点距离
            startRadius = Math.max(0.01, configManager.getStartRadius());
            maxRadius = center.distance(radiusPoint);
        }

        // 计算圈数
        double calculatedTurns = calculateTurnsForRadius(maxRadius, startRadius, configManager.getCurrentType());

        // 创建螺旋
        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), configManager.getCurrentType(), configManager.isSharpEdged());
            spiral.setStartRadius(startRadius);
            configureSpiral(spiral);

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("createNonLinearSpiralShape: 创建螺旋成功，类型={}, 最大半径={}, 起始半径={}, 圈数={}",
                configManager.getCurrentType(), maxRadius, startRadius, calculatedTurns);
            return spiral;

        } catch (Exception e) {
            LOGGER.error("createNonLinearSpiralShape: 创建螺旋失败：中心={}, 最大半径={}, 起始半径={}, 圈数={}, 类型={}, 错误={}",
                center, maxRadius, startRadius, calculatedTurns, configManager.getCurrentType(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建斐波那契螺旋图形
     * 参数：中心点、起始点、最外圈点
     */
    private SpiralShape createFibonacciSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d startRadiusPoint = controlPoints.get(1);
        Vec2d maxRadiusPoint = controlPoints.get(2);

        double startRadius = Math.max(0.01, center.distance(startRadiusPoint));
        double maxRadius = Math.max(startRadius + 0.01, center.distance(maxRadiusPoint));
        
        // 斐波那契螺旋的生长是固定的，我们只需根据起始和最大半径计算出需要绘制的圈数
        double calculatedTurns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.FIBONACCI);

        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), SpiralType.FIBONACCI, configManager.isSharpEdged());
            spiral.setStartRadius(startRadius);
            configureSpiral(spiral); // 应用通用配置，如旋转方向

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("创建斐波那契螺旋: 起始半径={}, 最大半径={}, 圈数={}",
                startRadius, maxRadius, calculatedTurns);
            return spiral;

        } catch (Exception e) {
            LOGGER.error("创建斐波那契螺旋失败: 起始半径={}, 最大半径={}, 圈数={}, 错误={}",
                startRadius, maxRadius, calculatedTurns, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建对数螺旋图形
     * 参数：中心点、起始点、最外圈点
     */
    private SpiralShape createLogarithmicSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d startRadiusPoint = controlPoints.get(1);
        Vec2d maxRadiusPoint = controlPoints.get(2);

        double startRadius = Math.max(0.01, center.distance(startRadiusPoint));
        double maxRadius = Math.max(startRadius + 0.01, center.distance(maxRadiusPoint));
        
        // 对数螺旋的生长由growthFactor控制，我们根据起始和最大半径计算出需要绘制的圈数
        double calculatedTurns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.LOGARITHMIC);

        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), SpiralType.LOGARITHMIC, configManager.isSharpEdged());
            spiral.setStartRadius(startRadius);
            configureSpiral(spiral); // 应用通用配置，如旋转方向

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("创建对数螺旋: 起始半径={}, 最大半径={}, 圈数={}, 生长因子={}",
                startRadius, maxRadius, calculatedTurns, configManager.getGrowthFactor());
            return spiral;

        } catch (Exception e) {
            LOGGER.error("创建对数螺旋失败: 起始半径={}, 最大半径={}, 圈数={}, 错误={}",
                startRadius, maxRadius, calculatedTurns, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建半圆螺旋图形
     * 参数：中心点、起始点、最外圈点
     */
    private SpiralShape createSemicircleSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d startRadiusPoint = controlPoints.get(1);
        Vec2d maxRadiusPoint = controlPoints.get(2);

        double startRadius = Math.max(0.01, center.distance(startRadiusPoint));
        double maxRadius = Math.max(startRadius + 0.01, center.distance(maxRadiusPoint));
        
        // 半圆螺旋的生长由expansionRate控制，我们根据起始和最大半径计算出需要绘制的圈数
        double calculatedTurns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.SEMICIRCLE);

        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), SpiralType.SEMICIRCLE, configManager.isSharpEdged());
            spiral.setStartRadius(startRadius);
            configureSpiral(spiral); // 应用通用配置，如旋转方向

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("创建半圆螺旋: 起始半径={}, 最大半径={}, 圈数={}, 扩张率={}",
                startRadius, maxRadius, calculatedTurns, configManager.getExpansionRate());
            return spiral;

        } catch (Exception e) {
            LOGGER.error("创建半圆螺旋失败: 起始半径={}, 最大半径={}, 圈数={}, 错误={}",
                startRadius, maxRadius, calculatedTurns, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 创建费马螺旋图形 (新增方法)
     * 参数：中心点（起点）、最外圈点
     */
    private SpiralShape createFermatSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d maxRadiusPoint = controlPoints.get(1);

        double maxRadius = Math.max(0.01, center.distance(maxRadiusPoint));
        
        // 费马螺旋的圈数根据最大半径和螺旋系数计算
        double calculatedTurns = calculateTurnsForRadius(maxRadius, 0.01, SpiralType.FERMAT);

        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), SpiralType.FERMAT, configManager.isSharpEdged());
            spiral.setStartRadius(0.01); // 费马螺旋从中心开始
            configureSpiral(spiral); // 应用通用配置，如旋转方向

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("创建费马螺旋: 最大半径={}, 圈数={}, 螺旋系数={}",
                maxRadius, calculatedTurns, configManager.getSpiralCoefficient());
            return spiral;

        } catch (Exception e) {
            LOGGER.error("创建费马螺旋失败: 最大半径={}, 圈数={}, 错误={}",
                maxRadius, calculatedTurns, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建多边形螺旋图形
     * 参数：中心点、半径点
     */
    private SpiralShape createPolygonSpiralShape(List<Vec2d> controlPoints, ShapeStyle style) {
        Vec2d center = controlPoints.get(0);
        Vec2d radiusPoint = controlPoints.get(1);

        // 多边形螺旋使用配置的起始半径和spacing
        double startRadius = Math.max(0.01, configManager.getStartRadius());
        double maxRadius = Math.max(startRadius + 0.01, center.distance(radiusPoint));
        
        // 计算圈数：基于最大半径和起始半径的差值除以spacing
        double calculatedTurns = Math.max(0.1, (maxRadius - startRadius) / configManager.getSpacing());

        try {
            SpiralShape spiral = new SpiralShape(center, maxRadius, calculatedTurns, configManager.getSpacing(), SpiralType.POLYGON, configManager.isSharpEdged());
            spiral.setStartRadius(startRadius);
            configureSpiral(spiral); // 应用通用配置，如边数、旋转方向等

            if (style != null) {
                spiral.setStyle(style);
            }

            LOGGER.debug("创建多边形螺旋: 起始半径={}, 最大半径={}, 螺距={}, 圈数={}, 边数={}",
                startRadius, maxRadius, configManager.getSpacing(), calculatedTurns, configManager.getSides());
            return spiral;

        } catch (Exception e) {
            LOGGER.error("创建多边形螺旋失败: 起始半径={}, 最大半径={}, 螺距={}, 圈数={}, 错误={}",
                startRadius, maxRadius, configManager.getSpacing(), calculatedTurns, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 更新费马螺旋预览 (新增方法)
     */
    private void updateFermatSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        double maxRadius;
        double turns;

        if (controlPoints.size() == 1) {// 步骤1：已定义中心，鼠标位置定义最外圈半径
            maxRadius = Math.max(0.01, center.distance(mousePoint));
            turns = calculateTurnsForRadius(maxRadius, 0.01, SpiralType.FERMAT);

            LOGGER.debug("updateFermatSpiralPreview: 步骤1 - 最大半径={}, 圈数={}", maxRadius, turns);
        } else {
            LOGGER.debug("updateFermatSpiralPreview: 未知步骤，不更新预览");
            return;
        }
        
        // 使用通用参数更新方法来设置预览对象
        updateSpiralParameters(preview, center, maxRadius, turns, configManager.getSpacing(), SpiralType.FERMAT, 0.01);
    }
    
    /**
     * 更新线性螺旋预览
     */
    private void updateLinearSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        
        switch (controlPoints.size()) {
            case 1 -> {
                // 步骤1：定义螺旋起点
                double startRadius = Math.max(0.01, center.distance(mousePoint));
                double pitch = Math.max(2.0, configManager.getSpacing());
                double turns = 1.0; // 默认1圈用于预览
                
                updateSpiralParameters(preview, center, startRadius, turns, pitch);
                LOGGER.debug("updateLinearSpiralPreview: 步骤1 - 起始半径={}, 螺距={}, 圈数={}", startRadius, pitch, turns);
            }
            
            case 2 -> {
                // 步骤2：定义螺距
                double startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
                double pitch = Math.max(2.0, controlPoints.get(1).distance(mousePoint));
                double turns = 2.0; // 默认2圈用于预览
                
                updateSpiralParameters(preview, center, startRadius, turns, pitch);
                LOGGER.debug("updateLinearSpiralPreview: 步骤2 - 起始半径={}, 螺距={}, 圈数={}", startRadius, pitch, turns);
            }
            
            case 3 -> {
                // 步骤3：定义最外圈
                double startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
                double pitch = Math.max(2.0, controlPoints.get(1).distance(controlPoints.get(2)));
                double maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
                double turns = Math.max(0.1, (maxRadius - startRadius) / pitch);
                
                updateSpiralParameters(preview, center, startRadius, turns, pitch);
                LOGGER.debug("updateLinearSpiralPreview: 步骤3 - 起始半径={}, 螺距={}, 最大半径={}, 圈数={}", 
                    startRadius, pitch, maxRadius, turns);
            }
            
            default -> LOGGER.debug("updateLinearSpiralPreview: 未知步骤，清除预览");
        }
    }
    
    /**
     * 更新非线性螺旋预览
     */
    private void updateNonLinearSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        Vec2d radiusPoint = controlPoints.size() >= 2 ? controlPoints.get(1) : mousePoint;
        
        // 计算起始半径和最大半径
        double startRadius;
        double maxRadius;
        
        if ((configManager.getCurrentType() == SpiralType.LOGARITHMIC || configManager.getCurrentType() == SpiralType.SEMICIRCLE) && controlPoints.size() >= 2) {
            // 对数螺旋和半圆螺旋：使用第一个点和第二个点之间的距离作为起始半径
            startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
            // 最大半径为鼠标位置到中心的距离
            maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
            LOGGER.debug("updateNonLinearSpiralPreview: {}螺旋使用控制点距离作为起始半径 = {}, 最大半径 = {}", 
                configManager.getCurrentType(), startRadius, maxRadius);
        } else {
            // 其他类型：使用配置的起始半径，最大半径为控制点距离
            startRadius = Math.max(0.01, configManager.getStartRadius());
            maxRadius = center.distance(radiusPoint);
        }
        
        // 计算圈数
        double calculatedTurns = calculateTurnsForRadius(maxRadius, startRadius, configManager.getCurrentType());
        
        // 更新螺旋参数
        updateSpiralParameters(preview, center, maxRadius, calculatedTurns, configManager.getSpacing(), configManager.getCurrentType(), startRadius);
        
        LOGGER.debug("updateNonLinearSpiralPreview: 类型={}, 最大半径={}, 起始半径={}, 圈数={}",
            configManager.getCurrentType(), maxRadius, startRadius, calculatedTurns);
    }

    /**
     * 更新斐波那契螺旋预览
     */
    private void updateFibonacciSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        double startRadius;
        double maxRadius;
        double turns;

        switch (controlPoints.size()) {
            case 1 -> {
                // 步骤1：已定义中心，鼠标位置定义起始半径
                startRadius = Math.max(0.01, center.distance(mousePoint));
                maxRadius = startRadius * 2.0; // 默认给一个预览大小，例如2倍起始半径
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.FIBONACCI);
                
                LOGGER.debug("updateFibonacciSpiralPreview: 步骤1 - 起始半径={}, 预览最大半径={}, 圈数={}", startRadius, maxRadius, turns);
            }
            
            case 2 -> {
                // 步骤2：已定义起始半径，鼠标位置定义最外圈半径
                startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
                maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.FIBONACCI);

                LOGGER.debug("updateFibonacciSpiralPreview: 步骤2 - 起始半径={}, 最大半径={}, 圈数={}, 生长因子={}", 
                    startRadius, maxRadius, turns, configManager.getGrowthFactor());
            }
            
            default -> {
                LOGGER.debug("updateFibonacciSpiralPreview: 未知步骤，不更新预览");
                return;
            }
        }
        
        // 使用通用参数更新方法来设置预览对象
        updateSpiralParameters(preview, center, maxRadius, turns, configManager.getSpacing(), SpiralType.FIBONACCI, startRadius);
    }
    
    /**
     * 更新对数螺旋预览
     */
    private void updateLogarithmicSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        double startRadius;
        double maxRadius;
        double turns;

        switch (controlPoints.size()) {
            case 1 -> {
                // 步骤1：已定义中心，鼠标位置定义起始半径
                startRadius = Math.max(0.01, center.distance(mousePoint));
                maxRadius = startRadius * 2.0; // 默认给一个预览大小，例如2倍起始半径
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.LOGARITHMIC);
                
                LOGGER.debug("updateLogarithmicSpiralPreview: 步骤1 - 起始半径={}, 预览最大半径={}, 圈数={}", startRadius, maxRadius, turns);
            }
            
            case 2 -> {
                // 步骤2：已定义起始半径，鼠标位置定义最外圈半径
                startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
                maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.LOGARITHMIC);

                LOGGER.debug("updateLogarithmicSpiralPreview: 步骤2 - 起始半径={}, 最大半径={}, 圈数={}, 生长因子={}", 
                    startRadius, maxRadius, turns, configManager.getGrowthFactor());
            }
            
            default -> {
                LOGGER.debug("updateLogarithmicSpiralPreview: 未知步骤，不更新预览");
                return;
            }
        }
        
        // 使用通用参数更新方法来设置预览对象
        updateSpiralParameters(preview, center, maxRadius, turns, configManager.getSpacing(), SpiralType.LOGARITHMIC, startRadius);
    }
    
    /**
     * 更新半圆螺旋预览
     */
    private void updateSemicircleSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        double startRadius;
        double maxRadius;
        double turns;

        switch (controlPoints.size()) {
            case 1 -> {
                // 步骤1：已定义中心，鼠标位置定义起始半径
                startRadius = Math.max(0.01, center.distance(mousePoint));
                maxRadius = startRadius * 2.0; // 默认给一个预览大小，例如2倍起始半径
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.SEMICIRCLE);
                
                LOGGER.debug("updateSemicircleSpiralPreview: 步骤1 - 起始半径={}, 预览最大半径={}, 圈数={}", startRadius, maxRadius, turns);
            }
            
            case 2 -> {
                // 步骤2：已定义起始半径，鼠标位置定义最外圈半径
                startRadius = Math.max(0.01, center.distance(controlPoints.get(1)));
                maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
                turns = calculateTurnsForRadius(maxRadius, startRadius, SpiralType.SEMICIRCLE);

                LOGGER.debug("updateSemicircleSpiralPreview: 步骤2 - 起始半径={}, 最大半径={}, 圈数={}, 扩张率={}", 
                    startRadius, maxRadius, turns, configManager.getExpansionRate());
            }
            
            default -> {
                LOGGER.debug("updateSemicircleSpiralPreview: 未知步骤，不更新预览");
                return;
            }
        }
        
        // 使用通用参数更新方法来设置预览对象
        updateSpiralParameters(preview, center, maxRadius, turns, configManager.getSpacing(), SpiralType.SEMICIRCLE, startRadius);
    }
    
    /**
     * 更新螺旋参数
     */
    private void updateSpiralParameters(SpiralShape spiral, Vec2d center, double radius, double turns, double spacing) {
        updateSpiralParameters(spiral, center, radius, turns, spacing, SpiralType.LINEAR, null);
    }
    
    /**
     * 更新螺旋参数（带起始半径）
     */
    private void updateSpiralParameters(SpiralShape spiral, Vec2d center, double radius, double turns, double spacing, SpiralType type, Double startRadius) {
        if (spiral == null) return;
        
        spiral.setCenter(center);
        spiral.setRadius(radius);
        spiral.setTurns(turns);
        spiral.setSpacing(spacing);
        spiral.setType(type);
        spiral.setSharpEdged(configManager.isSharpEdged());
        
        // 设置起始半径
        if (startRadius != null) {
            // 使用传入的起始半径
            spiral.setStartRadius(startRadius);
        } else if (type == SpiralType.LINEAR) {
            // 对于线性螺旋，radius 参数实际上是计算出的起始半径
            spiral.setStartRadius(radius);
        } else {
            // 其他类型使用配置的默认值
            spiral.setStartRadius(configManager.getStartRadius());
        }
        
        // 配置其他特定类型的参数
        configureSpiral(spiral);
    }

    /**
     * 配置螺旋线参数
     */
    private void configureSpiral(SpiralShape spiral) {
        if (spiral == null) return;

        // 不要覆盖已经设置的起始半径，特别是对于线性螺旋
        // spiral.setStartRadius(configManager.getStartRadius());
        spiral.setGrowthFactor(configManager.getGrowthFactor());
        spiral.setExpansionRate(configManager.getExpansionRate());
        spiral.setSpiralCoefficient(configManager.getSpiralCoefficient());
        spiral.setClockwise(configManager.isClockwise());

        switch (configManager.getCurrentType()) {
            case LINEAR -> {
                // 线性螺旋无需额外参数约束
            }
            case POLYGON -> spiral.setSides(Math.max(3, configManager.getSides()));
            case LOGARITHMIC -> spiral.setGrowthFactor(Math.max(0.1, Math.min(10.0, configManager.getGrowthFactor())));
            case SEMICIRCLE -> spiral.setExpansionRate(Math.max(0, configManager.getExpansionRate()));
            case FIBONACCI -> {
                spiral.setSpiralCoefficient(Math.max(0.1, Math.min(5.0, configManager.getSpiralCoefficient())));
                spiral.setClockwise(configManager.isClockwise());
            }
            case FERMAT -> {
                spiral.setSpiralCoefficient(Math.max(0.5, Math.min(8.0, configManager.getSpiralCoefficient())));
                spiral.setClockwise(configManager.isClockwise());
            }
        }
    }

    /**
     * 使用 SpiralShape 的 solveTurnsForRadius 方法计算圈数
     * 这是该逻辑的唯一实现，确保数学一致性
     * 
     * @param maxRadius 目标最大半径
     * @param safeStartRadius 安全的起始半径
     * @param type 螺旋类型
     * @return 计算出的圈数
     */
    private double calculateTurnsForRadius(double maxRadius, double safeStartRadius, SpiralType type) {
        // 创建临时预览对象来求解
        SpiralShape tempSpiral = new SpiralShape(new Vec2d(0, 0), maxRadius, 1.0, configManager.getSpacing(), type, false);
        tempSpiral.setStartRadius(safeStartRadius);
        tempSpiral.setGrowthFactor(configManager.getGrowthFactor());
        tempSpiral.setExpansionRate(configManager.getExpansionRate());
        tempSpiral.setSpiralCoefficient(configManager.getSpiralCoefficient());
        tempSpiral.setClockwise(configManager.isClockwise());

        if (type == SpiralType.POLYGON) {
            tempSpiral.setSides(configManager.getSides());
        }

        // 直接调用 tempSpiral 自身的方法来求解
        return tempSpiral.solveTurnsForRadius(maxRadius);
    }

    /**
     * 更新多边形螺旋预览
     */
    private void updatePolygonSpiralPreview(SpiralShape preview, List<Vec2d> controlPoints, Vec2d mousePoint) {
        Vec2d center = controlPoints.getFirst();
        double startRadius = Math.max(0.01, configManager.getStartRadius());
        double maxRadius;
        double turns;

        switch (controlPoints.size()) {
            case 1 -> {
                // 步骤1：已定义中心，鼠标位置定义最外圈半径
                maxRadius = Math.max(startRadius + 0.01, center.distance(mousePoint));
                turns = Math.max(0.1, (maxRadius - startRadius) / configManager.getSpacing());
                
                LOGGER.debug("updatePolygonSpiralPreview: 步骤1 - 起始半径={}, 最大半径={}, 螺距={}, 圈数={}",
                    startRadius, maxRadius, configManager.getSpacing(), turns);
            }
            
            case 2 -> {
                // 步骤2：已定义半径点，使用固定半径
                maxRadius = Math.max(startRadius + 0.01, center.distance(controlPoints.get(1)));
                turns = Math.max(0.1, (maxRadius - startRadius) / configManager.getSpacing());
                
                LOGGER.debug("updatePolygonSpiralPreview: 步骤2 - 起始半径={}, 最大半径={}, 螺距={}, 圈数={}",
                    startRadius, maxRadius, configManager.getSpacing(), turns);
            }
            
            default -> {
                LOGGER.debug("updatePolygonSpiralPreview: 未知步骤，不更新预览");
                return;
            }
        }
        
        // 使用通用参数更新方法来设置预览对象
        updateSpiralParameters(preview, center, maxRadius, turns, configManager.getSpacing(), SpiralType.POLYGON, startRadius);
    }
} 