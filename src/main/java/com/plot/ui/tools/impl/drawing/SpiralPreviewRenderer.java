package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.SpiralType;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.config.SpiralConfigManager;

import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 螺旋线预览渲染器
 * 负责渲染螺旋线的控制点、连接线、曲线预览和参数信息
 */
public class SpiralPreviewRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralPreviewRenderer.class);

    // ====== 渲染常量 ======
    
    private static final float POINT_SIZE = 5.0f;
    private static final float CURVE_THICKNESS = 3.0f;
    private static final float TEXT_OFFSET_X = 10.0f;
    private static final float TEXT_OFFSET_Y = 10.0f;
    private static final float SNAP_INDICATOR_SIZE = 8.0f;

    private final SpiralTool spiralTool;
    private final SpiralConfigManager configManager;

    public SpiralPreviewRenderer(SpiralTool spiralTool, SpiralConfigManager configManager) {
        this.spiralTool = spiralTool;
        this.configManager = configManager;
    }

    /**
     * 渲染预览（DrawContext版本）
     */
    public void renderPreview(DrawContext context, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        LOGGER.debug("renderPreview: 开始渲染预览，控制点数={}", controlPoints.size());
        renderControlPoints(context, controlPoints, currentMousePoint);
        renderCurvePreview(context);
        renderConnectionLines(context, controlPoints, currentMousePoint);
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        LOGGER.debug("renderPreview(ImGui): 开始渲染预览，控制点数={}", controlPoints.size());
        renderControlPointsImGui(drawList, camera, controlPoints, currentMousePoint);
        renderConnectionLinesImGui(drawList, camera, controlPoints, currentMousePoint);
        renderCurvePreviewImGui(drawList, camera);
        renderParameterInfoImGui(drawList, camera, controlPoints, currentMousePoint);
    }

    /**
     * 渲染控制点（DrawContext版本）
     */
    private void renderControlPoints(DrawContext context, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color infoColor = toColor(theme.infoText, 255);
        java.awt.Color successColor = toColor(theme.successText, 255);
        java.awt.Color warningColor = toColor(theme.warningText, 255);
        java.awt.Color accentColor = toColor(theme.accent, 255);
        java.awt.Color mutedColor = toColor(theme.mutedText, 255);

        SpiralType currentType = configManager.getCurrentType();
        
        if (currentType == SpiralType.LINEAR) {
            // 线性螺旋：渲染所有4个控制点
            for (int i = 0; i < controlPoints.size(); i++) {
                Vec2d point = controlPoints.get(i);
                java.awt.Color color = switch (i) {
                    case 0 -> infoColor; // 中心点
                    case 1 -> successColor; // 起始半径点
                    case 2 -> warningColor; // 螺距点
                    case 3 -> accentColor; // 最大半径点
                    default -> mutedColor;
                };
                context.drawCircle(point, 4.0, color);
            }
            
            // 渲染鼠标预览点
            if (currentMousePoint != null && controlPoints.size() < 4) {
                java.awt.Color previewColor = switch (controlPoints.size()) {
                    case 0 -> infoColor; // 中心预览
                    case 1 -> successColor; // 起始半径预览
                    case 2 -> warningColor; // 螺距预览
                    case 3 -> accentColor; // 最大半径预览
                    default -> mutedColor;
                };
                context.drawCircle(currentMousePoint, 3.0, previewColor);
            }
        } else {
            // 其它类型：保持原有逻辑
            if (!controlPoints.isEmpty()) {
                context.drawCircle(controlPoints.getFirst(), 3.0, infoColor);
            }
            if (controlPoints.size() >= 2) {
                context.drawCircle(controlPoints.get(1), 3.0, successColor);
            } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                context.drawCircle(currentMousePoint, 3.0, warningColor);
            }
        }
    }

    /**
     * 渲染连接线（DrawContext版本）
     */
    private void renderConnectionLines(DrawContext context, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color warningColor = toColor(theme.warningText, 255);
        java.awt.Color accentColor = toColor(theme.accent, 255);
        java.awt.Color mutedColor = toColor(theme.mutedText, 255);

        SpiralType currentType = configManager.getCurrentType();
        
        if (currentType == SpiralType.LINEAR) {
            // 线性螺旋：渲染所有连接线
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Vec2d start = controlPoints.get(i);
                Vec2d end = controlPoints.get(i + 1);
                java.awt.Color color = switch (i) {
                    case 0 -> warningColor; // 中心到起始半径
                    case 1 -> warningColor; // 起始半径到螺距
                    case 2 -> accentColor; // 螺距到最大半径
                    default -> mutedColor;
                };
                context.drawLine(start, end, color);
            }
            
            // 渲染鼠标预览线
            if (currentMousePoint != null && !controlPoints.isEmpty()) {
                Vec2d lastPoint = controlPoints.getLast();
                java.awt.Color previewColor = switch (controlPoints.size()) {
                    case 1 -> warningColor; // 中心到起始半径预览
                    case 2 -> warningColor; // 起始半径到螺距预览
                    case 3 -> accentColor; // 螺距到最大半径预览
                    default -> mutedColor;
                };
                context.drawLine(lastPoint, currentMousePoint, previewColor);
            }
        } else {
            // 其它类型：保持原有逻辑
            if (controlPoints.size() >= 2) {
                context.drawLine(controlPoints.get(0), controlPoints.get(1), mutedColor);
            } else if (controlPoints.size() == 1 && currentMousePoint != null) {
                context.drawLine(controlPoints.getFirst(), currentMousePoint, toColor(theme.mutedText, 220));
            }
        }
    }

    /**
     * 渲染曲线预览（DrawContext版本）
     */
    private void renderCurvePreview(DrawContext context) {
        SpiralShape previewSpiral = spiralTool.getPreviewSpiral();
        if (previewSpiral != null) {
            // 确保预览螺旋使用当前图层颜色
            com.plot.core.graphics.style.ShapeStyle originalStyle = null;
            try {
                if (spiralTool.getStyleHandler() != null) {
                    com.plot.core.graphics.style.ShapeStyle previewStyle = spiralTool.getStyleHandler().getPreviewStyle();
                    if (previewStyle != null) {
                        // 保存当前样式
                        originalStyle = context.getCurrentStyle();
                        // 在DrawContext中设置预览样式，这样SpiralShape.draw()会使用正确的颜色
                        context.setStyle(previewStyle);
                        LOGGER.debug("SpiralPreviewRenderer: 在DrawContext中设置预览样式，颜色: {} (lineColor: {}, strokeColor: {})", 
                                   previewStyle.getLineColor(),
                                   previewStyle.getLineColor(),
                                   previewStyle.getLineStyle() != null ? previewStyle.getLineStyle().getColor() : "null");
                    } else {
                        LOGGER.debug("SpiralPreviewRenderer: 无法获取预览样式");
                    }
                } else {
                    LOGGER.debug("SpiralPreviewRenderer: StyleHandler为null");
                }
                previewSpiral.draw(context);
            } finally {
                // 恢复原始样式，防止副作用
                if (originalStyle != null) {
                    context.setStyle(originalStyle);
                    LOGGER.debug("SpiralPreviewRenderer: 已恢复DrawContext原始样式");
                }
            }
        } else {
            LOGGER.debug("renderCurvePreview: 预览对象为null，无法渲染");
        }
    }

    /**
     * 渲染控制点（ImGui版本）
     */
    private void renderControlPointsImGui(ImDrawList drawList, CanvasCamera camera, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        SpiralType currentType = configManager.getCurrentType();
        
        if (currentType == SpiralType.LINEAR) {
            // 线性螺旋：渲染所有4个控制点
            for (int i = 0; i < controlPoints.size(); i++) {
                Vec2d point = controlPoints.get(i);
                Vec2d screenPoint = camera.worldToScreen(point);
                int color = switch (i) {
                    case 0 -> theme.infoText;
                    case 1 -> theme.successText;
                    case 2 -> theme.warningText;
                    case 3 -> theme.accent;
                    default -> theme.mutedText;
                };
                
                drawList.addCircleFilled(
                    (float) screenPoint.x, (float) screenPoint.y,
                    POINT_SIZE, color
                );
                // 添加吸附指示器
                drawList.addCircle(
                    (float) screenPoint.x, (float) screenPoint.y,
                    SNAP_INDICATOR_SIZE, theme.warningText, 12, 2.0f
                );
            }
            
            // 渲染鼠标预览点
            if (currentMousePoint != null && controlPoints.size() < 4) {
                Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
                int previewColor = switch (controlPoints.size()) {
                    case 0 -> theme.infoText;
                    case 1 -> theme.successText;
                    case 2 -> theme.warningText;
                    case 3 -> theme.accent;
                    default -> theme.mutedText;
                };
                
                drawList.addCircleFilled(
                    (float) screenPoint.x, (float) screenPoint.y,
                    POINT_SIZE, previewColor
                );
                // 半透明吸附指示器
                drawList.addCircle(
                    (float) screenPoint.x, (float) screenPoint.y,
                    SNAP_INDICATOR_SIZE * 0.7f, withAlpha(theme.warningText, 0xCC), 12, 1.5f
                );
            }
        } else {
            // 其它类型：保持原有逻辑
            if (!controlPoints.isEmpty()) {
                Vec2d screenPoint = camera.worldToScreen(controlPoints.getFirst());
                drawList.addCircleFilled(
                    (float) screenPoint.x, (float) screenPoint.y,
                    POINT_SIZE, theme.infoText
                );
                drawList.addCircle(
                    (float) screenPoint.x, (float) screenPoint.y,
                    SNAP_INDICATOR_SIZE, theme.warningText, 12, 2.0f
                );
            }
            
            if (controlPoints.size() >= 2) {
                Vec2d screenPoint = camera.worldToScreen(controlPoints.get(1));
                drawList.addCircleFilled(
                    (float) screenPoint.x, (float) screenPoint.y,
                    POINT_SIZE, theme.successText
                );
                drawList.addCircle(
                    (float) screenPoint.x, (float) screenPoint.y,
                    SNAP_INDICATOR_SIZE, theme.warningText, 12, 2.0f
                );
            } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
                drawList.addCircleFilled(
                    (float) screenPoint.x, (float) screenPoint.y,
                    POINT_SIZE, theme.successText
                );
                drawList.addCircle(
                    (float) screenPoint.x, (float) screenPoint.y,
                    SNAP_INDICATOR_SIZE * 0.7f, withAlpha(theme.warningText, 0xCC), 12, 1.5f
                );
            }
        }
    }

    /**
     * 渲染连接线（ImGui版本）
     */
    private void renderConnectionLinesImGui(ImDrawList drawList, CanvasCamera camera, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        SpiralType currentType = configManager.getCurrentType();
        
        if (currentType == SpiralType.LINEAR) {
            // 线性螺旋：渲染所有连接线
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Vec2d start = controlPoints.get(i);
                Vec2d end = controlPoints.get(i + 1);
                Vec2d screenStart = camera.worldToScreen(start);
                Vec2d screenEnd = camera.worldToScreen(end);
                
                int color = switch (i) {
                    case 0, 1 -> withAlpha(theme.warningText, 0xCC);
                    case 2 -> withAlpha(theme.accent, 0xCC);
                    default -> withAlpha(theme.mutedText, 0x99);
                };
                
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    color, 2.0f
                );
            }
            
            // 渲染鼠标预览线
            if (currentMousePoint != null && !controlPoints.isEmpty()) {
                Vec2d lastPoint = controlPoints.getLast();
                Vec2d screenStart = camera.worldToScreen(lastPoint);
                Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
                
                int previewColor = switch (controlPoints.size()) {
                    case 1, 2 -> withAlpha(theme.warningText, 0x88);
                    case 3 -> withAlpha(theme.accent, 0x88);
                    default -> withAlpha(theme.mutedText, 0x66);
                };
                
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    previewColor, 1.5f
                );
            }
        } else {
            // 其它类型：保持原有逻辑
            if (controlPoints.size() >= 2) {
                Vec2d screenStart = camera.worldToScreen(controlPoints.get(0));
                Vec2d screenEnd = camera.worldToScreen(controlPoints.get(1));
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    withAlpha(theme.warningText, 0x99), 1.5f
                );
            } else if (controlPoints.size() == 1 && currentMousePoint != null) {
                Vec2d screenStart = camera.worldToScreen(controlPoints.getFirst());
                Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    withAlpha(theme.mutedText, 0x88), 1.0f
                );
            }
        }
    }

    /**
     * 渲染曲线预览（ImGui版本）
     */
    private void renderCurvePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        SpiralShape previewSpiral = spiralTool.getPreviewSpiral();
        if (previewSpiral == null || previewSpiral.getPoints() == null) {
            LOGGER.debug("renderCurvePreviewImGui: 无可用螺旋预览对象");
            return;
        }

        List<Vec2d> points = previewSpiral.getPoints();
        if (points.size() < 2) {
            LOGGER.debug("renderCurvePreviewImGui: 螺旋预览点数不足：{}", points.size());
            return;
        }

        // 获取当前图层颜色用于预览
        int previewColor = withAlpha(ThemeManager.getInstance().getCurrentTheme().accent, 0x80);
        
        if (spiralTool.getStyleHandler() != null) {
            com.plot.core.graphics.style.ShapeStyle previewStyle = spiralTool.getStyleHandler().getPreviewStyle();
            if (previewStyle != null && previewStyle.getLineColor() != null) {
                java.awt.Color layerColor = previewStyle.getLineColor();
                // 直接使用getRGB()获取ImGui兼容的整数颜色值
                previewColor = layerColor.getRGB();
                LOGGER.debug("SpiralPreviewRenderer: ImGui预览使用图层颜色: {} -> 0x{}", 
                           layerColor, Integer.toHexString(previewColor));
            } else {
                LOGGER.debug("SpiralPreviewRenderer: 无法获取预览样式或图层颜色 - previewStyle: {}, lineColor: {}", 
                           previewStyle != null ? "非空" : "null", 
                           previewStyle != null ? previewStyle.getLineColor() : "null");
            }
        } else {
            LOGGER.debug("SpiralPreviewRenderer: StyleHandler为null，使用默认预览颜色");
        }

        for (int i = 1; i < points.size(); i++) {
            Vec2d screenPrev = camera.worldToScreen(points.get(i - 1));
            Vec2d screenCurr = camera.worldToScreen(points.get(i));

            // 放宽距离限制，允许较大螺旋渲染
            if (screenPrev.distance(screenCurr) > 2000) {
                LOGGER.debug("renderCurvePreviewImGui: 跳过线段，因距离过大：{} 到 {}", screenPrev, screenCurr);
                continue;
            }

            drawList.addLine(
                (float) screenPrev.x, (float) screenPrev.y,
                (float) screenCurr.x, (float) screenCurr.y,
                previewColor, CURVE_THICKNESS
            );
        }
    }

    /**
     * 渲染参数信息（ImGui版本）
     */
    private void renderParameterInfoImGui(ImDrawList drawList, CanvasCamera camera, List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        SpiralType currentType = configManager.getCurrentType();
        
        if (currentType == SpiralType.LINEAR && !controlPoints.isEmpty() && currentMousePoint != null) {
            // 线性螺旋：显示当前步骤的参数信息
            String info = buildLinearSpiralTooltip(controlPoints, currentMousePoint);
            
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addText(
                (float) screenPoint.x + TEXT_OFFSET_X, 
                (float) screenPoint.y - TEXT_OFFSET_Y,
                theme.text, info
            );
        } else if (!controlPoints.isEmpty() && currentMousePoint != null) {
            // 其它类型：保持原有逻辑
            Vec2d centerPoint = controlPoints.getFirst();
            Vec2d radiusPoint = controlPoints.size() >= 2 ? controlPoints.get(1) : currentMousePoint;
            double radius = centerPoint.distance(radiusPoint);
            
            Vec2d screenPoint = camera.worldToScreen(radiusPoint);
            String info = buildTooltipMessage(radius, controlPoints);
            
            drawList.addText(
                (float) screenPoint.x + TEXT_OFFSET_X, 
                (float) screenPoint.y - TEXT_OFFSET_Y,
                theme.text, info
            );
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static java.awt.Color toColor(int color, int alpha) {
        return new java.awt.Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    /**
     * 构建线性螺旋的工具提示信息
     */
    private String buildLinearSpiralTooltip(List<Vec2d> controlPoints, Vec2d currentMousePoint) {
        StringBuilder message = new StringBuilder(128);
        message.append("线性螺旋绘制\n");
        
        if (!controlPoints.isEmpty()) {
            Vec2d center = controlPoints.get(0);
            if (controlPoints.size() == 1) {
                // 第1步：显示起始半径预览
                double startRadius = center.distance(currentMousePoint);
                message.append("起始半径: ").append(String.format("%.2f", startRadius));
            } else if (controlPoints.size() == 2) {
                // 第2步：显示螺距预览
                Vec2d startRadiusPoint = controlPoints.get(1);
                double startRadius = center.distance(startRadiusPoint);
                double pitch = startRadiusPoint.distance(currentMousePoint);
                message.append("起始半径: ").append(String.format("%.2f", startRadius))
                       .append("\n螺距: ").append(String.format("%.2f", pitch));
            } else if (controlPoints.size() == 3) {
                // 第3步：显示最大半径预览
                Vec2d startRadiusPoint = controlPoints.get(1);
                Vec2d pitchPoint = controlPoints.get(2);
                double startRadius = center.distance(startRadiusPoint);
                double pitch = startRadiusPoint.distance(pitchPoint);
                double maxRadius = center.distance(currentMousePoint);
                double turns = (maxRadius - startRadius) / Math.max(0.01, pitch);
                
                message.append("起始半径: ").append(String.format("%.2f", startRadius))
                       .append("\n螺距: ").append(String.format("%.2f", pitch))
                       .append("\n最大半径: ").append(String.format("%.2f", maxRadius))
                       .append("\n预计圈数: ").append(String.format("%.1f", turns));
            }
        }
        
        return message.toString();
    }

    /**
     * 构建工具提示信息
     */
    private String buildTooltipMessage(double radius, List<Vec2d> controlPoints) {
        double calculatedTurns = getCalculatedTurns(radius, controlPoints);
        StringBuilder message = new StringBuilder(128);
        message.append(configManager.getCurrentType().getDisplayName())
               .append("\n最大半径: ").append(String.format("%.2f", radius))
               .append("\n预计圈数: ").append(String.format("%.1f", calculatedTurns))
               .append("\n尖角样式: ").append(configManager.isSharpEdged() ? "开启" : "关闭");
        
        switch (configManager.getCurrentType()) {
            case LOGARITHMIC -> message.append("\n生长因子: ").append(String.format("%.2f", configManager.getGrowthFactor()))
                               .append("\n起始半径: ").append(String.format("%.2f", configManager.getStartRadius()));
            case LINEAR -> message.append("\n螺距: ").append(String.format("%.2f", configManager.getSpacing()))
                                 .append("\n起始半径: ").append(String.format("%.2f", configManager.getStartRadius()));
            case SEMICIRCLE -> {
                // 半圆螺旋：显示实际的起始半径（来自控制点距离）
                double actualStartRadius = configManager.getStartRadius();
                if (controlPoints.size() >= 2) {
                    actualStartRadius = controlPoints.get(0).distance(controlPoints.get(1));
                }
                double perHalfTurnGrowth = configManager.getExpansionRate() * configManager.getSpacing();
                message.append("\n起始半径: ").append(String.format("%.2f", actualStartRadius))
                       .append("\n扩张率: ").append(String.format("%.2f", configManager.getExpansionRate()))
                       .append("\n每半圈半径增量: ").append(String.format("%.2f", perHalfTurnGrowth))
                       .append("\n半圆数量: ").append(String.format("%.1f", calculatedTurns * 2));
            }
            case FERMAT -> message.append("\n螺旋系数: ").append(String.format("%.2f", configManager.getSpiralCoefficient()))
                                 .append("\n公式: r = a*sqrt(θ)")
                                 .append("\n方向: ").append(configManager.isClockwise() ? "顺时针" : "逆时针");
            case FIBONACCI -> message.append("\n起始半径: ").append(String.format("%.2f", configManager.getStartRadius()))
                                    .append("\n扩展因子: ").append(String.format("%.2f", configManager.getSpiralCoefficient()));
            case POLYGON -> message.append("\n边数: ").append(configManager.getSides())
                                  .append("\n起始半径: ").append(String.format("%.2f", configManager.getStartRadius()));
        }
        return message.toString();
    }

    /**
     * 获取计算出的圈数（用于工具提示显示）
     */
    private double getCalculatedTurns(double radius, List<Vec2d> controlPoints) {
        double safeStartRadius;
        if ((configManager.getCurrentType() == SpiralType.LOGARITHMIC || configManager.getCurrentType() == SpiralType.SEMICIRCLE) && controlPoints.size() >= 2) {
            // 对数螺旋和半圆螺旋：使用控制点距离作为起始半径
            safeStartRadius = Math.max(0.01, controlPoints.get(0).distance(controlPoints.get(1)));
        } else {
            // 其他类型：使用配置的起始半径
            safeStartRadius = Math.max(0.01, configManager.getStartRadius());
        }
        return calculateTurnsForRadius(radius, safeStartRadius, configManager.getCurrentType());
    }

    /**
     * 使用 SpiralShape 的 solveTurnsForRadius 方法计算圈数
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
        
        return tempSpiral.solveTurnsForRadius(maxRadius);
    }
} 