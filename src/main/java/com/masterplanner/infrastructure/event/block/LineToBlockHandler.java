package com.masterplanner.infrastructure.event.block;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.coordinate.CoordinateTransformer;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.Events;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.ui.dialog.BlockConfigDialog.CompactBlockConfigDialog;
import com.masterplanner.ui.dialog.LineToBlockSettingsDialog.ConversionMode;
import com.masterplanner.ui.canvas.Canvas;
import com.masterplanner.ui.canvas.CanvasCamera;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.client.MinecraftClient;

/**
 * 线转方块事件处理器
 * 负责将选中的图形转换为Minecraft方块或幽灵方块
 * <p>
 * 坐标转换说明：
 * 1. Shape.getBlockPositions() 返回画布坐标系位置
 * 2. 通过 CoordinateTransformer 转换为世界坐标 (X, Z)
 * 3. 结合用户指定的高度 (Y) 形成 BlockPos
 * 4. 【优化】支持画布区域在窗口中的位置偏移
 * 5. 【优化】缓存转换结果以提升性能
 * 6. 【优化】进度反馈和标高管理
 */
public class LineToBlockHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/LineToBlockHandler");
    private static LineToBlockHandler INSTANCE;
    private final EventBus eventBus;
    private final AppState appState;
    private final GhostBlockManager ghostBlockManager;
    
    // 【新增】坐标转换缓存
    private final ConcurrentHashMap<String, Vec2d> coordinateCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    // 【新增】标高管理
    private static final double DEFAULT_Y_LEVEL = 64.0;

    /**
     * 获取单例实例
     */
    public static synchronized LineToBlockHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LineToBlockHandler();
        }
        return INSTANCE;
    }

    /**
     * 私有构造函数
     */
    private LineToBlockHandler() {
        this.eventBus = EventBus.getInstance();
        this.appState = AppState.getInstance();
        this.ghostBlockManager = GhostBlockManager.getInstance();
        registerEventListeners();
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        eventBus.subscribe(LineToBlockEvent.class, this::handleLineToBlockEvent);
    }

    /**
     * 处理线转方块事件
     */
    private void handleLineToBlockEvent(Event event) {
        if (!(event instanceof LineToBlockEvent lineToBlockEvent)) {
            return;
        }

        LOGGER.info("收到线转方块事件");

        // 获取要转换的图形
        List<Shape> shapes = lineToBlockEvent.getShapes();
        if (shapes == null || shapes.isEmpty()) {
            shapes = appState.getSelectedShapes();
        }

        if (shapes.isEmpty()) {
            LOGGER.warn("没有选中的图形，无法执行操作");
            eventBus.publish(new Events.WarningEvent("LineToBlockHandler", "请选择需要转换的图形！"));
            return;
        }

        // 验证并获取方块调色盘
        List<String> paletteBlocks = getPaletteBlockTypes();
        if (paletteBlocks.isEmpty()) {
            LOGGER.warn("方块配置无效或为空");
            eventBus.publish(new Events.WarningEvent("LineToBlockHandler", "请先在方块配置中选择要使用的方块！"));
            return;
        }

        // 获取转换参数
        ConversionMode conversionMode = lineToBlockEvent.getConversionMode();
        float simplificationRatio = lineToBlockEvent.getSimplificationRatio();
        double userSpecifiedYLevel = lineToBlockEvent.getCanvasHeight();
        boolean isPreview = lineToBlockEvent.isPreview();

        // 【优化】获取目标标高（优先使用用户指定，否则使用玩家位置）
        double targetYLevel = getTargetYLevel(userSpecifiedYLevel);

        LOGGER.info("线转方块参数: 图形数量={}, 调色盘大小={}, 精简比率={}, 标高={}, 预览模式={}",
                shapes.size(), paletteBlocks.size(), simplificationRatio, targetYLevel, isPreview);

        // 清理之前的幽灵方块
        ghostBlockManager.clearAllGhostBlocks();

        // 定义方块处理逻辑
        BiConsumer<BlockPos, String> blockAction = getBlockPosStringBiConsumer(isPreview);

        // 【优化】执行核心处理流程，带进度反馈
        int totalBlocks = processShapesToBlocksWithProgress(shapes, conversionMode, simplificationRatio, targetYLevel, paletteBlocks, blockAction);

        LOGGER.info("处理完成，共生成 {} 个方块", totalBlocks);

        // 发布成功事件
        String message = String.format("已生成 %d 个%s，使用 %d 种方块，标高=%.0f",
                totalBlocks, isPreview ? "幽灵方块" : "真实方块", paletteBlocks.size(), targetYLevel);
        eventBus.publish(new Events.WarningEvent("LineToBlockHandler", message));
    }

    private @NotNull BiConsumer<BlockPos, String> getBlockPosStringBiConsumer(boolean isPreview) {
        BiConsumer<BlockPos, String> blockAction;
        if (isPreview) {
            // 预览模式：添加幽灵方块
            blockAction = (pos, blockId) -> {
                // 直接传递BlockPos，更清晰！
                ghostBlockManager.addGhostBlock(pos, blockId);
                LOGGER.debug("创建幽灵方块: {} 在位置 {}", blockId, pos.toShortString());
            };
        } else {
            // 真实模式：发布投影事件
            blockAction = (pos, blockId) -> {
                eventBus.publish(new BlockProjectionEvent(
                        blockId,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        0.0f,
                        false // 非预览模式，实际放置方块
                ));
                LOGGER.debug("发布真实方块投影事件: {} 在位置 {}", blockId, pos.toShortString());
            };
        }
        return blockAction;
    }

    /**
     * 【优化】核心处理流程：将图形转换为方块，并对每个方块执行指定操作
     * 新增进度反馈功能
     * @param shapes 图形列表
     * @param targetYLevel 目标Y坐标
     * @param paletteBlocks 方块调色盘
     * @param blockAction 对每个生成的方块坐标和类型执行的操作
     * @return 生成的方块总数
     */
    private int processShapesToBlocksWithProgress(List<Shape> shapes, ConversionMode conversionMode, float simplificationRatio, double targetYLevel, List<String> paletteBlocks, BiConsumer<BlockPos, String> blockAction) {
        int totalBlocks = 0;
        int paletteIndex = 0;
        int processedShapes = 0;
        final int totalShapes = shapes.size();

        // 【新增】发布开始处理事件
        eventBus.publish(new Events.StatusMessageEvent("LineToBlockHandler", 
            String.format("开始处理 %d 个图形...", totalShapes)));

        for (Shape shape : shapes) {
            LOGGER.debug("处理图形: {} ({}/{})", shape.getClass().getSimpleName(), processedShapes + 1, totalShapes);

            // 新逻辑：直接在世界坐标系下光栅化
            List<BlockPos> blockPositions = rasterizeShape(shape, conversionMode, simplificationRatio, targetYLevel);

            if (blockPositions.isEmpty()) {
                LOGGER.warn("图形 {} 光栅化后没有有效的方块位置，跳过", shape.getClass().getSimpleName());
                processedShapes++;
                continue;
            }

            // 【新增】发布图形处理进度
            if (totalShapes > 5) { // 只有图形数量较多时才发布进度
                eventBus.publish(new Events.StatusMessageEvent("LineToBlockHandler", 
                    String.format("处理图形 %d/%d: 生成 %d 个方块", 
                        processedShapes + 1, totalShapes, blockPositions.size())));
            }

            for (BlockPos finalPos : blockPositions) {
                String blockId = paletteBlocks.get(paletteIndex % paletteBlocks.size());
                if (totalBlocks < 5) {
                    LOGGER.info("生成方块 #{}: {} 在位置 {}", totalBlocks + 1, blockId, finalPos.toShortString());
                }
                blockAction.accept(finalPos, blockId);
                paletteIndex++;
                totalBlocks++;
            }
            
            processedShapes++;
        }
        
        // 【新增】发布完成事件
        if (totalShapes > 5) {
            eventBus.publish(new Events.StatusMessageEvent("LineToBlockHandler", 
                String.format("处理完成: %d 个图形，共生成 %d 个方块", totalShapes, totalBlocks)));
        }
        
        return totalBlocks;
    }

    /**
     * 【新方法】光栅化一个图形，将其转换为一系列的BlockPos
     * 【扩展】支持所有图形类型：线段、折线、圆形、矩形、椭圆、圆弧、自由绘制、多边形等
     * 使用画布到Minecraft顶视图的坐标转换
     * @param shape 要处理的图形
     * @param yLevel 方块的目标Y坐标（标高）
     * @return 代表该图形的BlockPos列表
     */
    private List<BlockPos> rasterizeShape(Shape shape, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();

        LOGGER.debug("开始光栅化图形: {} (类型: {})", shape.getId(), shape.getClass().getSimpleName());

        // 处理线段
        switch (shape) {
            case com.masterplanner.core.geometry.shapes.LineShape line ->
                    result.addAll(rasterizeLineShape(line, conversionMode, simplificationRatio, yLevel));

            // 处理折线
            case com.masterplanner.core.geometry.shapes.PolylineShape polyline ->
                    result.addAll(rasterizePolylineShape(polyline, conversionMode, simplificationRatio, yLevel));

            // 处理圆形
            case com.masterplanner.core.geometry.shapes.CircleShape circle ->
                    result.addAll(rasterizeCircleShape(circle, conversionMode, simplificationRatio, yLevel));

            // 处理矩形
            case com.masterplanner.core.geometry.shapes.RectangleShape rectangle ->
                    result.addAll(rasterizeRectangleShape(rectangle, conversionMode, simplificationRatio, yLevel));

            // 处理椭圆
            case com.masterplanner.core.geometry.shapes.EllipseShape ellipse ->
                    result.addAll(rasterizeEllipseShape(ellipse, conversionMode, simplificationRatio, yLevel));

            // 处理圆弧
            case com.masterplanner.core.geometry.shapes.ArcShape arc -> result.addAll(rasterizeArcShape(arc, conversionMode, simplificationRatio, yLevel));

            // 处理自由绘制路径
            case com.masterplanner.core.geometry.shapes.FreeDrawPath freeDraw ->
                    result.addAll(rasterizeFreeDrawPath(freeDraw, conversionMode, simplificationRatio, yLevel));

            // 处理多边形
            case com.masterplanner.core.geometry.shapes.Polygon polygon ->
                    result.addAll(rasterizePolygonShape(polygon, conversionMode, simplificationRatio, yLevel));

            // 处理其他复杂图形（如螺旋、星形等）
            default ->
                // 对于复杂图形，尝试获取其边界或轮廓点进行光栅化
                    result.addAll(rasterizeComplexShape(shape, conversionMode, simplificationRatio, yLevel));
        }

        LOGGER.info("【调试】光栅化完成，生成 {} 个方块位置", result.size());
        return result;
    }

    /**
     * 光栅化线段
     */
    private List<BlockPos> rasterizeLineShape(com.masterplanner.core.geometry.shapes.LineShape line, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        Vec2d canvasStart = line.getStart();
        Vec2d canvasEnd = line.getEnd();
        
        LOGGER.info("【调试】处理线条图形: 画布起点({}, {}), 画布终点({}, {})", 
                canvasStart.x, canvasStart.y, canvasEnd.x, canvasEnd.y);
        
        // 【修复】先将画布世界坐标转换为窗口坐标，再转换为Minecraft坐标
        LOGGER.info("【调试】开始画布坐标转换...");
        Vec2d windowStart = canvasToWindowCoordinates(canvasStart);
        Vec2d windowEnd = canvasToWindowCoordinates(canvasEnd);
        
        if (windowStart != null && windowEnd != null) {
            LOGGER.info("【调试】画布坐标转换为窗口坐标成功: 起点({}, {}) -> ({}, {}), 终点({}, {}) -> ({}, {})", 
                    canvasStart.x, canvasStart.y, windowStart.x, windowStart.y,
                    canvasEnd.x, canvasEnd.y, windowEnd.x, windowEnd.y);
            
            // 使用增强的坐标转换器
            LOGGER.info("【调试】开始窗口坐标到Minecraft坐标转换...");
            Vec2d minecraftStart = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowStart);
            Vec2d minecraftEnd = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowEnd);
            
            if (minecraftStart != null && minecraftEnd != null) {
                LOGGER.info("【调试】坐标转换成功: 窗口坐标({}, {}) -> ({}, {}) → Minecraft坐标({}, {}) -> ({}, {}), 标高={}",
                        windowStart.x, windowStart.y, windowEnd.x, windowEnd.y,
                        minecraftStart.x, minecraftStart.y, minecraftEnd.x, minecraftEnd.y, yLevel);
                
                return rasterizeLineSegment(minecraftStart.x, minecraftStart.y,
                        minecraftEnd.x, minecraftEnd.y, yLevel, conversionMode, simplificationRatio);
            } else {
                LOGGER.error("【调试】窗口坐标到Minecraft坐标转换失败，跳过线条: 起点={}, 终点={}", windowStart, windowEnd);
            }
        } else {
            LOGGER.error("【调试】画布坐标到窗口坐标转换失败，跳过线条: 起点={}, 终点={}", canvasStart, canvasEnd);
        }
        
        return new ArrayList<>();
    }

    /**
     * 光栅化折线
     */
    private List<BlockPos> rasterizePolylineShape(com.masterplanner.core.geometry.shapes.PolylineShape polyline, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        List<Vec2d> canvasPoints = polyline.getPoints();
        
        if (canvasPoints.size() < 2) {
            LOGGER.warn("折线点数不足，至少需要2个点，当前点数: {}", canvasPoints.size());
            return result;
        }

        LOGGER.debug("处理折线图形: {}个画布点", canvasPoints.size());

        // 转换所有点到窗口坐标，再转换为Minecraft坐标
        List<Vec2d> minecraftPoints = new ArrayList<>();
        for (int i = 0; i < canvasPoints.size(); i++) {
            Vec2d canvasPoint = canvasPoints.get(i);
            Vec2d windowPoint = canvasToWindowCoordinates(canvasPoint);
            if (windowPoint != null) {
                Vec2d minecraftPoint = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowPoint);
                if (minecraftPoint != null) {
                    minecraftPoints.add(minecraftPoint);
                    LOGGER.debug("折线点{}转换成功: 画布({}, {}) → 窗口({}, {}) → Minecraft({}, {})", 
                            i + 1, canvasPoint.x, canvasPoint.y, windowPoint.x, windowPoint.y, 
                            minecraftPoint.x, minecraftPoint.y);
                } else {
                    LOGGER.warn("折线点{}窗口到Minecraft坐标转换失败: {}", i + 1, windowPoint);
                }
            } else {
                LOGGER.warn("折线点{}画布到窗口坐标转换失败: {}", i + 1, canvasPoint);
            }
        }

        if (minecraftPoints.size() < 2) {
            LOGGER.warn("转换后的Minecraft点数不足，跳过折线: 原始{}个点，转换后{}个点", 
                    canvasPoints.size(), minecraftPoints.size());
            return result;
        }

        // 连接相邻点形成线段
        for (int i = 0; i < minecraftPoints.size() - 1; i++) {
            Vec2d start = minecraftPoints.get(i);
            Vec2d end = minecraftPoints.get(i + 1);
            LOGGER.debug("处理折线段{}: ({}, {}) -> ({}, {})", 
                    i + 1, start.x, start.y, end.x, end.y);
            result.addAll(rasterizeLineSegment(start.x, start.y, end.x, end.y, yLevel, conversionMode, simplificationRatio));
        }

        // 处理闭合折线
        if (polyline.isClosed() && minecraftPoints.size() >= 3) {
            Vec2d start = minecraftPoints.getLast();
            Vec2d end = minecraftPoints.getFirst();
            LOGGER.debug("处理闭合折线段: ({}, {}) -> ({}, {})", 
                    start.x, start.y, end.x, end.y);
            result.addAll(rasterizeLineSegment(start.x, start.y, end.x, end.y, yLevel, conversionMode, simplificationRatio));
        }

        return result;
    }

    /**
     * 光栅化圆形
     */
    private List<BlockPos> rasterizeCircleShape(com.masterplanner.core.geometry.shapes.CircleShape circle, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        Vec2d canvasCenter = circle.getCenter();
        double canvasRadius = circle.getRadius();
        
        LOGGER.debug("处理圆形图形: 画布中心({}, {}), 半径={}", 
                canvasCenter.x, canvasCenter.y, canvasRadius);
        
        // 转换圆心到Minecraft坐标
        Vec2d windowCenter = canvasToWindowCoordinates(canvasCenter);
        if (windowCenter == null) {
            LOGGER.error("圆形中心坐标转换失败");
            return result;
        }
        
        Vec2d minecraftCenter = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowCenter);
        if (minecraftCenter == null) {
            LOGGER.error("圆形中心窗口到Minecraft坐标转换失败");
            return result;
        }
        
        // 计算Minecraft世界中的半径（需要根据坐标转换比例调整）
        double minecraftRadius = estimateMinecraftRadius(canvasRadius, canvasCenter, windowCenter, minecraftCenter);
        
        LOGGER.debug("圆形转换: 画布半径={} → Minecraft半径={}, 中心=({}, {})", 
                canvasRadius, minecraftRadius, minecraftCenter.x, minecraftCenter.y);
        
        // 使用圆形光栅化算法
        return rasterizeCircleCurve(minecraftCenter.x, minecraftCenter.y, minecraftRadius, yLevel, conversionMode, simplificationRatio);
    }

    /**
     * 光栅化矩形
     */
    private List<BlockPos> rasterizeRectangleShape(com.masterplanner.core.geometry.shapes.RectangleShape rectangle, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        Vec2d canvasCorner = rectangle.getCorner();
        double canvasWidth = rectangle.getWidth();
        double canvasHeight = rectangle.getHeight();
        double canvasRotation = rectangle.getRotation();
        
        LOGGER.debug("处理矩形图形: 画布角点({}, {}), 尺寸={}x{}, 旋转={}", 
                canvasCorner.x, canvasCorner.y, canvasWidth, canvasHeight, canvasRotation);
        
        // 获取矩形的四个角点
        List<Vec2d> canvasCorners = getRectangleCorners(canvasCorner, canvasWidth, canvasHeight, canvasRotation);
        
        // 转换所有角点到Minecraft坐标
        List<Vec2d> minecraftCorners = new ArrayList<>();
        for (Vec2d canvasCornerPoint : canvasCorners) {
            Vec2d windowCorner = canvasToWindowCoordinates(canvasCornerPoint);
            if (windowCorner != null) {
                Vec2d minecraftCorner = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowCorner);
                if (minecraftCorner != null) {
                    minecraftCorners.add(minecraftCorner);
                }
            }
        }
        
        if (minecraftCorners.size() == 4) {
            // 使用矩形光栅化算法
            List<BlockPos> candidates = rasterizePolygon(minecraftCorners, yLevel);
            return filterBlocksByPolygonCoverageIfSimplified(candidates, minecraftCorners, conversionMode, simplificationRatio);
        } else {
            LOGGER.error("矩形角点转换失败，只有{}个有效点", minecraftCorners.size());
        }
        
        return result;
    }

    /**
     * 光栅化椭圆
     */
    private List<BlockPos> rasterizeEllipseShape(com.masterplanner.core.geometry.shapes.EllipseShape ellipse, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        Vec2d canvasCenter = ellipse.getCenter();
        double canvasRadiusX = ellipse.getRadiusX();
        double canvasRadiusY = ellipse.getRadiusY();
        double canvasRotation = ellipse.getRotation();
        
        LOGGER.debug("处理椭圆图形: 画布中心({}, {}), 半径={}x{}, 旋转={}", 
                canvasCenter.x, canvasCenter.y, canvasRadiusX, canvasRadiusY, canvasRotation);
        
        // 转换中心到Minecraft坐标
        Vec2d windowCenter = canvasToWindowCoordinates(canvasCenter);
        if (windowCenter == null) {
            LOGGER.error("椭圆中心坐标转换失败");
            return result;
        }
        
        Vec2d minecraftCenter = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowCenter);
        if (minecraftCenter == null) {
            LOGGER.error("椭圆中心窗口到Minecraft坐标转换失败");
            return result;
        }
        
        // 计算Minecraft世界中的半径
        double minecraftRadiusX = estimateMinecraftRadius(canvasRadiusX, canvasCenter, windowCenter, minecraftCenter);
        double minecraftRadiusY = estimateMinecraftRadius(canvasRadiusY, canvasCenter, windowCenter, minecraftCenter);
        
        LOGGER.debug("椭圆转换: 画布半径={}x{} → Minecraft半径={}x{}, 中心=({}, {})", 
                canvasRadiusX, canvasRadiusY, minecraftRadiusX, minecraftRadiusY, 
                minecraftCenter.x, minecraftCenter.y);
        
        // 使用椭圆光栅化算法
        return rasterizeEllipseCurve(minecraftCenter.x, minecraftCenter.y, minecraftRadiusX, minecraftRadiusY, canvasRotation, yLevel, conversionMode, simplificationRatio);
    }

    /**
     * 光栅化圆弧
     */
    private List<BlockPos> rasterizeArcShape(com.masterplanner.core.geometry.shapes.ArcShape arc, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        Vec2d canvasCenter = arc.getCenter();
        double canvasRadius = arc.getRadius();
        double canvasStartAngle = arc.getStartAngle();
        double canvasEndAngle = arc.getEndAngle();
        
        LOGGER.debug("处理圆弧图形: 画布中心({}, {}), 半径={}, 角度={}~{}", 
                canvasCenter.x, canvasCenter.y, canvasRadius, canvasStartAngle, canvasEndAngle);
        
        // 转换中心到Minecraft坐标
        Vec2d windowCenter = canvasToWindowCoordinates(canvasCenter);
        if (windowCenter == null) {
            LOGGER.error("圆弧中心坐标转换失败");
            return result;
        }
        
        Vec2d minecraftCenter = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowCenter);
        if (minecraftCenter == null) {
            LOGGER.error("圆弧中心窗口到Minecraft坐标转换失败");
            return result;
        }
        
        // 计算Minecraft世界中的半径
        double minecraftRadius = estimateMinecraftRadius(canvasRadius, canvasCenter, windowCenter, minecraftCenter);
        
        LOGGER.debug("圆弧转换: 画布半径={} → Minecraft半径={}, 中心=({}, {})", 
                canvasRadius, minecraftRadius, minecraftCenter.x, minecraftCenter.y);
        
        // 使用圆弧光栅化算法
        return rasterizeArcCurve(minecraftCenter.x, minecraftCenter.y, minecraftRadius, canvasStartAngle, canvasEndAngle, yLevel, conversionMode, simplificationRatio);
    }

    /**
     * 光栅化自由绘制路径
     */
    private List<BlockPos> rasterizeFreeDrawPath(com.masterplanner.core.geometry.shapes.FreeDrawPath freeDraw, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        List<Vec2d> canvasPoints = freeDraw.getPoints();
        
        if (canvasPoints.size() < 2) {
            LOGGER.warn("自由绘制路径点数不足，至少需要2个点，当前点数: {}", canvasPoints.size());
            return result;
        }

        LOGGER.debug("处理自由绘制路径: {}个画布点", canvasPoints.size());

        // 转换所有点到Minecraft坐标
        List<Vec2d> minecraftPoints = new ArrayList<>();
        for (Vec2d canvasPoint : canvasPoints) {
            Vec2d windowPoint = canvasToWindowCoordinates(canvasPoint);
            if (windowPoint != null) {
                Vec2d minecraftPoint = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowPoint);
                if (minecraftPoint != null) {
                    minecraftPoints.add(minecraftPoint);
                }
            }
        }

        if (minecraftPoints.size() < 2) {
            LOGGER.warn("转换后的Minecraft点数不足，跳过自由绘制路径: 原始{}个点，转换后{}个点", 
                    canvasPoints.size(), minecraftPoints.size());
            return result;
        }

        // 连接相邻点形成线段
        for (int i = 0; i < minecraftPoints.size() - 1; i++) {
            Vec2d start = minecraftPoints.get(i);
            Vec2d end = minecraftPoints.get(i + 1);
            result.addAll(rasterizeLineSegment(start.x, start.y, end.x, end.y, yLevel, conversionMode, simplificationRatio));
        }

        return result;
    }

    /**
     * 光栅化多边形
     */
    private List<BlockPos> rasterizePolygonShape(com.masterplanner.core.geometry.shapes.Polygon polygon, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        List<Vec2d> canvasPoints = polygon.getPoints();
        
        if (canvasPoints.size() < 3) {
            LOGGER.warn("多边形点数不足，至少需要3个点，当前点数: {}", canvasPoints.size());
            return result;
        }

        LOGGER.debug("处理多边形图形: {}个画布点", canvasPoints.size());

        // 转换所有点到Minecraft坐标
        List<Vec2d> minecraftPoints = new ArrayList<>();
        for (Vec2d canvasPoint : canvasPoints) {
            Vec2d windowPoint = canvasToWindowCoordinates(canvasPoint);
            if (windowPoint != null) {
                Vec2d minecraftPoint = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowPoint);
                if (minecraftPoint != null) {
                    minecraftPoints.add(minecraftPoint);
                }
            }
        }

        if (minecraftPoints.size() < 3) {
            LOGGER.warn("转换后的Minecraft点数不足，跳过多边形: 原始{}个点，转换后{}个点", 
                    canvasPoints.size(), minecraftPoints.size());
            return result;
        }

        // 使用多边形光栅化算法
        List<BlockPos> candidates = rasterizePolygon(minecraftPoints, yLevel);
        return filterBlocksByPolygonCoverageIfSimplified(candidates, minecraftPoints, conversionMode, simplificationRatio);
    }

    private List<BlockPos> rasterizeCircleCurve(double centerX, double centerZ, double radius, double yLevel, ConversionMode conversionMode, float simplificationRatio) {
        int segments = Math.max(64, (int) Math.ceil(Math.max(8.0, radius * 8.0)));
        double step = 2.0 * Math.PI / segments;

        List<Vec2d> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double angle = i * step;
            points.add(new Vec2d(centerX + radius * Math.cos(angle), centerZ + radius * Math.sin(angle)));
        }

        return rasterizeCurvePolyline(points, yLevel, conversionMode, simplificationRatio, true);
    }

    private List<BlockPos> rasterizeEllipseCurve(double centerX, double centerZ, double radiusX, double radiusZ, double rotation, double yLevel, ConversionMode conversionMode, float simplificationRatio) {
        int segments = Math.max(64, (int) Math.ceil(Math.max(radiusX, radiusZ) * 8.0));
        double step = 2.0 * Math.PI / segments;

        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);

        List<Vec2d> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double angle = i * step;
            double x = radiusX * Math.cos(angle);
            double z = radiusZ * Math.sin(angle);
            double rx = x * cosR - z * sinR;
            double rz = x * sinR + z * cosR;
            points.add(new Vec2d(centerX + rx, centerZ + rz));
        }

        return rasterizeCurvePolyline(points, yLevel, conversionMode, simplificationRatio, true);
    }

    private List<BlockPos> rasterizeArcCurve(double centerX, double centerZ, double radius, double startAngle, double endAngle, double yLevel, ConversionMode conversionMode, float simplificationRatio) {
        double angleRange = Math.abs(endAngle - startAngle);
        int segments = Math.max(16, (int) Math.ceil(Math.max(8.0, radius * angleRange * 4.0)));
        double step = angleRange / segments;
        double direction = endAngle >= startAngle ? 1.0 : -1.0;

        List<Vec2d> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + direction * i * step;
            points.add(new Vec2d(centerX + radius * Math.cos(angle), centerZ + radius * Math.sin(angle)));
        }

        return rasterizeCurvePolyline(points, yLevel, conversionMode, simplificationRatio, false);
    }

    private List<BlockPos> rasterizeCurvePolyline(List<Vec2d> points, double yLevel, ConversionMode conversionMode, float simplificationRatio, boolean closeLoop) {
        if (points == null || points.size() < 2) {
            return List.of();
        }

        java.util.LinkedHashSet<BlockPos> result = new java.util.LinkedHashSet<>();

        int last = points.size() - 1;
        for (int i = 0; i < last; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            result.addAll(rasterizeLineSegment(a.x, a.y, b.x, b.y, yLevel, conversionMode, simplificationRatio));
        }

        if (closeLoop) {
            Vec2d a = points.get(last);
            Vec2d b = points.getFirst();
            result.addAll(rasterizeLineSegment(a.x, a.y, b.x, b.y, yLevel, conversionMode, simplificationRatio));
        }

        return new ArrayList<>(result);
    }

    private List<BlockPos> filterBlocksByPolygonCoverageIfSimplified(List<BlockPos> candidates, List<Vec2d> polygonPoints, ConversionMode conversionMode, float simplificationRatio) {
        if (conversionMode == null || conversionMode == ConversionMode.FULL) {
            return candidates;
        }

        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }

        double threshold = Math.max(0.0, Math.min(1.0, simplificationRatio));
        int samples = 4;
        int totalSamples = samples * samples;

        List<BlockPos> filtered = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            int inside = 0;
            for (int ix = 0; ix < samples; ix++) {
                for (int iz = 0; iz < samples; iz++) {
                    double sx = pos.getX() + (ix + 0.5) / samples;
                    double sz = pos.getZ() + (iz + 0.5) / samples;
                    if (isPointInsidePolygon(new Vec2d(sx, sz), polygonPoints)) {
                        inside++;
                    }
                }
            }

            double coverage = (double) inside / totalSamples;
            if (coverage >= threshold) {
                filtered.add(pos);
            }
        }

        return filtered;
    }

    private boolean isPointInsidePolygon(Vec2d point, List<Vec2d> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);

            boolean intersect = ((pi.y > point.y) != (pj.y > point.y)) &&
                    (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 1e-12) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 光栅化复杂图形（如螺旋、星形等）
     */
    private List<BlockPos> rasterizeComplexShape(Shape shape, ConversionMode conversionMode, float simplificationRatio, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        LOGGER.debug("处理复杂图形: {}", shape.getClass().getSimpleName());
        
        // 尝试获取图形的控制点或边界点
        List<Vec2d> controlPoints = shape.getControlPoints();
        if (controlPoints != null && controlPoints.size() >= 2) {
            // 将控制点转换为线段进行光栅化
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Vec2d start = controlPoints.get(i);
                Vec2d end = controlPoints.get(i + 1);
                
                Vec2d windowStart = canvasToWindowCoordinates(start);
                Vec2d windowEnd = canvasToWindowCoordinates(end);
                
                if (windowStart != null && windowEnd != null) {
                    Vec2d minecraftStart = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowStart);
                    Vec2d minecraftEnd = CoordinateTransformer.getInstance().canvasToMinecraftWorld(windowEnd);
                    
                    if (minecraftStart != null && minecraftEnd != null) {
                        result.addAll(rasterizeLineSegment(minecraftStart.x, minecraftStart.y,
                                minecraftEnd.x, minecraftEnd.y, yLevel, conversionMode, simplificationRatio));
                    }
                }
            }
        } else {
            LOGGER.warn("复杂图形 {} 没有有效的控制点，无法光栅化", shape.getClass().getSimpleName());
        }
        
        return result;
    }

    /**
     * 【优化】将画布世界坐标转换为窗口坐标
     * 【修复】从CanvasRenderer获取画布的实际屏幕位置和尺寸，确保转换准确
     * 【新增】支持缓存机制提升性能
     * @param canvasPos 画布世界坐标
     * @return 窗口坐标，如果转换失败返回null
     */
    private Vec2d canvasToWindowCoordinates(Vec2d canvasPos) {
        try {
            // 【新增】检查缓存
            String cacheKey = String.format("%.2f,%.2f", canvasPos.x, canvasPos.y);
            Vec2d cachedResult = coordinateCache.get(cacheKey);
            if (cachedResult != null) {
                LOGGER.debug("使用缓存的坐标转换结果: 画布({}, {}) → 窗口({}, {})", 
                    canvasPos.x, canvasPos.y, cachedResult.x, cachedResult.y);
                return cachedResult;
            }

            // 获取Canvas和相机
            Canvas canvas = appState.getCanvas();
            if (canvas == null) {
                LOGGER.error("Canvas未初始化");
                return null;
            }

            CanvasCamera camera = canvas.getCamera();
            if (camera == null) {
                LOGGER.error("相机未初始化");
                return null;
            }

            // 【优化】获取画布的实际屏幕位置和尺寸
            Vec2d windowPos = getCanvasWindowPosition(canvas, camera, canvasPos);
            
            if (windowPos != null) {
                // 【新增】缓存结果
                if (coordinateCache.size() < MAX_CACHE_SIZE) {
                    coordinateCache.put(cacheKey, windowPos);
                } else {
                    // 缓存满时清理旧条目
                    clearCoordinateCache();
                    coordinateCache.put(cacheKey, windowPos);
                }
                
                LOGGER.debug("画布世界坐标转窗口坐标: 画布世界({}, {}) → 窗口({}, {})", 
                        canvasPos.x, canvasPos.y, windowPos.x, windowPos.y);
            }
            
            return windowPos;

        } catch (Exception e) {
            LOGGER.error("画布世界坐标转窗口坐标失败", e);
            return null;
        }
    }

    /**
     * 【新增】获取画布在窗口中的实际位置
     * 支持全屏和非全屏模式
     * @param canvas Canvas实例
     * @param camera 相机实例
     * @param canvasPos 画布世界坐标
     * @return 窗口坐标
     */
    private Vec2d getCanvasWindowPosition(Canvas canvas, CanvasCamera camera, Vec2d canvasPos) {
        try {
            // 【修复】正确的坐标转换流程
            // 1. 画布世界坐标 → 画布屏幕坐标
            Vec2d canvasScreenPos = camera.worldToScreen(canvasPos);
            
            // 2. 画布屏幕坐标 → 窗口坐标
            Vec2d windowPos = canvasScreenToWindowCoordinates(canvasScreenPos);
            
            LOGGER.debug("坐标转换: 画布世界({}, {}) → 画布屏幕({}, {}) → 窗口({}, {})", 
                    canvasPos.x, canvasPos.y, canvasScreenPos.x, canvasScreenPos.y, 
                    windowPos.x, windowPos.y);
            
            return windowPos;
            
        } catch (Exception e) {
            LOGGER.error("获取画布窗口位置失败", e);
            return null;
        }
    }

    /**
     * 【新增】将画布屏幕坐标转换为窗口坐标
     * @param canvasScreenPos 画布屏幕坐标
     * @return 窗口坐标
     */
    private Vec2d canvasScreenToWindowCoordinates(Vec2d canvasScreenPos) {
        if (isFullscreenCanvas()) {
            // 全屏模式：画布屏幕坐标直接就是窗口坐标
            return new Vec2d(canvasScreenPos.x, canvasScreenPos.y);
        } else {
            // 非全屏模式：需要加上画布在窗口中的偏移
            float canvasX = getCanvasWindowOffsetX();
            float canvasY = getCanvasWindowOffsetY();
            return new Vec2d(canvasScreenPos.x + canvasX, canvasScreenPos.y + canvasY);
        }
    }

    /**
     * 【新增】获取画布在窗口中的X偏移
     * @return X偏移量
     */
    private float getCanvasWindowOffsetX() {
        // 未来可以从CanvasRenderer获取实际的画布位置
        // 目前假设为0
        return 0.0f;
    }

    /**
     * 【新增】获取画布在窗口中的Y偏移
     * @return Y偏移量
     */
    private float getCanvasWindowOffsetY() {
        // 未来可以从CanvasRenderer获取实际的画布位置
        // 目前假设为0
        return 0.0f;
    }

    /**
     * 【新增】检查是否为全屏画布模式
     * 【修复】更准确的全屏模式检测
     * @return 是否为全屏模式
     */
    private boolean isFullscreenCanvas() {
        try {
            // 【修复】直接检查画布是否占据整个窗口
            // 在全屏模式下，画布确实占据整个窗口，所以直接返回true
            // 这样可以确保坐标转换逻辑的一致性
            
            // 获取当前显示尺寸
            float displayWidth = imgui.ImGui.getIO().getDisplaySizeX();
            float displayHeight = imgui.ImGui.getIO().getDisplaySizeY();
            
            LOGGER.debug("全屏模式检测: 显示尺寸={}x{}", displayWidth, displayHeight);
            
            // 【核心修复】由于CanvasRenderer已经将画布设置为全屏模式，
            // 并且MasterPlannerScreen也确认画布占据整个窗口，
            // 所以这里直接返回true，确保坐标转换逻辑的一致性
            return true;
            
        } catch (Exception e) {
            LOGGER.warn("检查全屏模式失败，默认假设为全屏", e);
            return true;
        }
    }

    /**
     * 【新增】清理坐标转换缓存
     */
    private void clearCoordinateCache() {
        coordinateCache.clear();
        LOGGER.debug("坐标转换缓存已清理");
    }

    /**
     * 使用Bresenham算法精确光栅化线条为方块位置
     * 【修复】确保线条穿过方块的中心，而不是边缘
     * @param x0 起点X (Minecraft世界坐标)
     * @param z0 起点Z (Minecraft世界坐标)
     * @param x1 终点X (Minecraft世界坐标)
     * @param z1 终点Z (Minecraft世界坐标)
     * @param yLevel 方块的Y坐标（标高）
     * @return 构成线段的BlockPos列表
     */
    private List<BlockPos> rasterizeLineSegment(
            double x0,
            double z0,
            double x1,
            double z1,
            double yLevel,
            ConversionMode conversionMode,
            float simplificationRatio
    ) {
        List<BlockPos> candidates = rasterizeLine(x0, z0, x1, z1, yLevel);
        if (conversionMode == null || conversionMode == ConversionMode.FULL) {
            return candidates;
        }

        if (candidates.isEmpty()) {
            return candidates;
        }

        // Keep legacy center-alignment behavior consistent with rasterizeLine().
        double ax0 = x0 + 0.5;
        double az0 = z0 + 0.5;
        double ax1 = x1 + 0.5;
        double az1 = z1 + 0.5;

        double dx = ax1 - ax0;
        double dz = az1 - az0;
        double segmentLength = Math.hypot(dx, dz);
        if (segmentLength < 1e-9) {
            return candidates;
        }

        double threshold = Math.max(0.0, Math.min(1.0, simplificationRatio));
        List<BlockPos> filtered = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            double insideLength = segmentLengthInsideUnitCell(ax0, az0, ax1, az1, pos.getX(), pos.getZ(), segmentLength);
            if (insideLength >= threshold) {
                filtered.add(pos);
            }
        }

        return filtered;
    }

    private double segmentLengthInsideUnitCell(double x0, double z0, double x1, double z1, int cellX, int cellZ, double segmentLength) {
        double dx = x1 - x0;
        double dz = z1 - z0;

        double xmax = cellX + 1.0;
        double zmax = cellZ + 1.0;

        double tMin = 0.0;
        double tMax = 1.0;

        if (Math.abs(dx) < 1e-12) {
            if (x0 < (double) cellX || x0 > xmax) {
                return 0.0;
            }
        } else {
            double tx1 = ((double) cellX - x0) / dx;
            double tx2 = (xmax - x0) / dx;
            double enter = Math.min(tx1, tx2);
            double exit = Math.max(tx1, tx2);
            tMin = Math.max(tMin, enter);
            tMax = Math.min(tMax, exit);
            if (tMin > tMax) {
                return 0.0;
            }
        }

        if (Math.abs(dz) < 1e-12) {
            if (z0 < (double) cellZ || z0 > zmax) {
                return 0.0;
            }
        } else {
            double tz1 = ((double) cellZ - z0) / dz;
            double tz2 = (zmax - z0) / dz;
            double enter = Math.min(tz1, tz2);
            double exit = Math.max(tz1, tz2);
            tMin = Math.max(tMin, enter);
            tMax = Math.min(tMax, exit);
            if (tMin > tMax) {
                return 0.0;
            }
        }

        double clampedEnter = Math.max(0.0, Math.min(1.0, tMin));
        double clampedExit = Math.max(0.0, Math.min(1.0, tMax));
        if (clampedExit <= clampedEnter) {
            return 0.0;
        }

        return (clampedExit - clampedEnter) * segmentLength;
    }

    private List<BlockPos> rasterizeLine(double x0, double z0, double x1, double z1, double yLevel) {
        // 【修复】调整坐标偏移，确保线条穿过方块中心
        // 方块的中心坐标 = 方块坐标 + 0.5
        double adjustedX0 = x0 + 0.5;
        double adjustedZ0 = z0 + 0.5;
        double adjustedX1 = x1 + 0.5;
        double adjustedZ1 = z1 + 0.5;
        
        // 转换为整数方块坐标
        int ix0 = (int) Math.floor(adjustedX0);
        int iz0 = (int) Math.floor(adjustedZ0);
        int ix1 = (int) Math.floor(adjustedX1);
        int iz1 = (int) Math.floor(adjustedZ1);
        int y = (int) Math.floor(yLevel);

        LOGGER.debug("中心对齐光栅化: 原始坐标({}, {}) -> ({}, {}), 调整后({}, {}) -> ({}, {}), 方块坐标({}, {}) -> ({}, {}), 标高={}",
                x0, z0, x1, z1, 
                adjustedX0, adjustedZ0, adjustedX1, adjustedZ1,
                ix0, iz0, ix1, iz1, y);

        // 使用Bresenham算法计算线条经过的所有方块
        return rasterizeLineBresenham(ix0, iz0, ix1, iz1, y);
    }

    /**
     * Bresenham线条光栅化算法
     * 计算线条经过的所有网格点，每个网格点对应一个方块位置
     * @param x0 起始X坐标
     * @param z0 起始Z坐标
     * @param x1 结束X坐标
     * @param z1 结束Z坐标
     * @param y 标高
     * @return 方块位置列表
     */
    private List<BlockPos> rasterizeLineBresenham(int x0, int z0, int x1, int z1, int y) {
        List<BlockPos> positions = new ArrayList<>();

        // 计算方向向量
        int dx = Math.abs(x1 - x0);
        int dz = -Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx + dz;

        int currentX = x0;
        int currentZ = z0;

        while (true) {
            // 添加当前网格点对应的方块位置
            positions.add(new BlockPos(currentX, y, currentZ));

            // 检查是否到达终点
            if (currentX == x1 && currentZ == z1) {
                break;
            }

            // Bresenham算法的误差更新
            int e2 = 2 * err;
            if (e2 >= dz) {
                err += dz;
                currentX += sx;
            }
            if (e2 <= dx) {
                err += dx;
                currentZ += sz;
            }
        }

        LOGGER.debug("Bresenham光栅化完成: 从({},{})到({},{})，标高{}，生成{}个方块",
                x0, z0, x1, z1, y, positions.size());

        return positions;
    }

    /**
     * 【优化】获取目标标高，用于方块转换
     * 优先使用用户指定的标高，否则使用玩家位置
     * 【新增】支持从UI获取用户指定标高
     * 【修复】幽灵方块应该永远都在玩家高度的下一格，也就是玩家的Y值减去1
     * @param userSpecifiedYLevel 用户指定的标高
     * @return 目标Y坐标（网格对齐）
     */
    private double getTargetYLevel(double userSpecifiedYLevel) {
        try {
            // 【优化】优先使用用户指定的标高
            if (userSpecifiedYLevel > 0) {
                double alignedY = Math.floor(userSpecifiedYLevel);
                LOGGER.debug("使用用户指定标高: 原始Y={}, 网格对齐Y={}", userSpecifiedYLevel, alignedY);
                return alignedY;
            }

            // 【新增】尝试从方块配置对话框获取用户指定标高
            double uiSpecifiedYLevel = getUserSpecifiedYLevelFromUI();
            if (uiSpecifiedYLevel != Double.MIN_VALUE) {
                double alignedY = Math.floor(uiSpecifiedYLevel);
                LOGGER.debug("使用UI指定标高: 原始Y={}, 网格对齐Y={}", uiSpecifiedYLevel, alignedY);
                return alignedY;
            }

            // 【修复】使用玩家当前位置作为标高，幽灵方块显示在玩家脚下
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                double playerY = client.player.getY();
                // 幽灵方块应该永远都在玩家高度的下一格，也就是玩家的Y值减去1
                double ghostY = Math.floor(playerY - 1.0);
                LOGGER.debug("使用玩家标高: 原始Y={}, 幽灵方块Y={} (玩家脚下)", playerY, ghostY);
                return ghostY;
            }

            LOGGER.debug("使用默认标高: {}", DEFAULT_Y_LEVEL);
            return DEFAULT_Y_LEVEL;
        } catch (Exception e) {
            LOGGER.warn("获取目标标高失败，使用默认值", e);
            return DEFAULT_Y_LEVEL;
        }
    }

    /**
     * 【新增】从UI获取用户指定的标高
     * @return 用户指定的标高，如果未设置返回Double.MIN_VALUE
     */
    private double getUserSpecifiedYLevelFromUI() {
        try {
            // 目前UI中未实现标高设置功能，返回未设置状态
            // 未来可以在CompactBlockConfigDialog中添加标高设置UI
            LOGGER.debug("UI中未实现标高设置功能");
            return Double.MIN_VALUE;
        } catch (Exception e) {
            LOGGER.debug("从UI获取标高失败: {}", e.getMessage());
            return Double.MIN_VALUE;
        }
    }

    /**
     * 从方块配置管理器获取调色盘中的所有方块类型
     * @return 方块ID列表，如果无效或为空则返回空列表
     */
    private List<String> getPaletteBlockTypes() {
        try {
            var blockConfigManager = CompactBlockConfigDialog.BlockConfigManager.getInstance();

            // 添加详细的调试信息
            LOGGER.debug("检查方块配置管理器状态:");
            LOGGER.debug("  - BlockConfigManager实例: {}", blockConfigManager != null ? "存在" : "null");
            LOGGER.debug("  - 是否有选中方块: {}", blockConfigManager != null ? blockConfigManager.hasSelectedBlocks() : "N/A");
            LOGGER.debug("  - 选中方块数量: {}", blockConfigManager != null ? blockConfigManager.getSelectedBlockCount() : "N/A");

            if (blockConfigManager != null && !blockConfigManager.hasSelectedBlocks()) {
                LOGGER.warn("调色盘中没有选中的方块 - 请先打开方块配置对话框并选择方块");
                LOGGER.debug("  - 调色盘大小: {}", blockConfigManager.getSelectedBlockCount());
                return Collections.emptyList();
            }

            List<String> selectedBlockIds = null;
            if (blockConfigManager != null) {
                selectedBlockIds = blockConfigManager.getSelectedBlockIds();
            }
            LOGGER.debug("原始选中的方块ID: {}", selectedBlockIds);

            // 创建可修改的列表副本，然后进行验证
            List<String> filteredBlockIds = null;
            if (selectedBlockIds != null) {
                filteredBlockIds = new ArrayList<>(selectedBlockIds);
            }

            // 附加的验证，确保ID非空且格式正确
            if (filteredBlockIds != null) {
                filteredBlockIds.removeIf(id -> id == null || id.trim().isEmpty() || !id.contains(":"));
            }

            if (filteredBlockIds != null && filteredBlockIds.isEmpty()) {
                LOGGER.warn("调色盘方块ID列表在清理后为空");
                return Collections.emptyList();
            }

            if (filteredBlockIds != null) {
                LOGGER.info("获取到调色盘方块: {} (共{}个)", filteredBlockIds, filteredBlockIds.size());
            }
            return filteredBlockIds;
        } catch (Exception e) {
            LOGGER.error("获取调色盘方块类型时发生错误", e);
            return Collections.emptyList();
        }
    }

    /**
     * 【新增】估算Minecraft世界中的半径
     * 根据画布坐标和Minecraft坐标的转换比例来估算半径
     */
    private double estimateMinecraftRadius(double canvasRadius, Vec2d canvasCenter, Vec2d windowCenter, Vec2d minecraftCenter) {
        try {
            // 计算画布到窗口的缩放比例
            double canvasToWindowScale = 1.0;
            if (canvasCenter.x != 0) {
                canvasToWindowScale = windowCenter.x / canvasCenter.x;
            }
            
            // 计算窗口到Minecraft的缩放比例
            double windowToMinecraftScale = 1.0;
            if (windowCenter.x != 0) {
                windowToMinecraftScale = minecraftCenter.x / windowCenter.x;
            }
            
            // 总缩放比例
            double totalScale = canvasToWindowScale * windowToMinecraftScale;
            
            // 估算Minecraft世界中的半径
            double minecraftRadius = canvasRadius * totalScale;
            
            LOGGER.debug("半径估算: 画布半径={}, 缩放比例={}, Minecraft半径={}", 
                    canvasRadius, totalScale, minecraftRadius);
            
            return minecraftRadius;
        } catch (Exception e) {
            LOGGER.warn("半径估算失败，使用原始半径: {}", e.getMessage());
            return canvasRadius;
        }
    }

    /**
     * 【新增】获取矩形的四个角点
     */
    private List<Vec2d> getRectangleCorners(Vec2d corner, double width, double height, double rotation) {
        List<Vec2d> corners = new ArrayList<>();
        
        // 计算矩形的四个角点（未旋转）
        Vec2d topLeft = corner;
        Vec2d topRight = new Vec2d(corner.x + width, corner.y);
        Vec2d bottomRight = new Vec2d(corner.x + width, corner.y + height);
        Vec2d bottomLeft = new Vec2d(corner.x, corner.y + height);
        
        // 计算旋转中心（矩形中心）
        Vec2d center = new Vec2d(corner.x + width / 2, corner.y + height / 2);
        
        // 应用旋转
        if (rotation != 0) {
            topLeft = rotatePoint(topLeft, center, rotation);
            topRight = rotatePoint(topRight, center, rotation);
            bottomRight = rotatePoint(bottomRight, center, rotation);
            bottomLeft = rotatePoint(bottomLeft, center, rotation);
        }
        
        corners.add(topLeft);
        corners.add(topRight);
        corners.add(bottomRight);
        corners.add(bottomLeft);
        
        return corners;
    }

    /**
     * 【新增】旋转点
     */
    private Vec2d rotatePoint(Vec2d point, Vec2d center, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        
        double newX = center.x + dx * cos - dy * sin;
        double newY = center.y + dx * sin + dy * cos;
        
        return new Vec2d(newX, newY);
    }

    /**
     * 【新增】光栅化圆形
     * 使用中点圆算法
     */
    private List<BlockPos> rasterizeCircle(double centerX, double centerY, double radius, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        int centerBlockX = (int) Math.floor(centerX + 0.5);
        int centerBlockY = (int) Math.floor(centerY + 0.5);
        int blockRadius = (int) Math.floor(radius + 0.5);
        int y = (int) Math.floor(yLevel);
        
        // 使用中点圆算法
        int x = blockRadius;
        int err = 0;
        
        while (x >= 0) {
            // 添加八个象限的点
            result.add(new BlockPos(centerBlockX + x, y, centerBlockY + blockRadius));
            result.add(new BlockPos(centerBlockX - x, y, centerBlockY + blockRadius));
            result.add(new BlockPos(centerBlockX + x, y, centerBlockY - blockRadius));
            result.add(new BlockPos(centerBlockX - x, y, centerBlockY - blockRadius));
            result.add(new BlockPos(centerBlockX + blockRadius, y, centerBlockY + x));
            result.add(new BlockPos(centerBlockX - blockRadius, y, centerBlockY + x));
            result.add(new BlockPos(centerBlockX + blockRadius, y, centerBlockY - x));
            result.add(new BlockPos(centerBlockX - blockRadius, y, centerBlockY - x));
            
            if (err <= 0) {
                err += 2 * x + 1;
            }
            if (err > 0) {
                err -= 2 * blockRadius + 1;
                blockRadius--;
            }
            x--;
        }
        
        return result;
    }

    /**
     * 【新增】光栅化矩形
     * 使用边界框填充
     */
    private List<BlockPos> rasterizeRectangle(List<Vec2d> corners, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        if (corners.size() != 4) {
            LOGGER.warn("矩形角点数量不正确: {}", corners.size());
            return result;
        }
        
        // 计算边界框
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;
        
        for (Vec2d corner : corners) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.y);
            maxZ = Math.max(maxZ, corner.y);
        }
        
        int startX = (int) Math.floor(minX + 0.5);
        int endX = (int) Math.floor(maxX + 0.5);
        int startZ = (int) Math.floor(minZ + 0.5);
        int endZ = (int) Math.floor(maxZ + 0.5);
        int y = (int) Math.floor(yLevel);
        
        // 填充矩形区域
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                result.add(new BlockPos(x, y, z));
            }
        }
        
        return result;
    }

    /**
     * 【新增】光栅化椭圆
     * 使用椭圆参数方程
     */
    private List<BlockPos> rasterizeEllipse(double centerX, double centerY, double radiusX, double radiusY, double rotation, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        int centerBlockX = (int) Math.floor(centerX + 0.5);
        int centerBlockY = (int) Math.floor(centerY + 0.5);
        int blockRadiusX = (int) Math.floor(radiusX + 0.5);
        int blockRadiusY = (int) Math.floor(radiusY + 0.5);
        int y = (int) Math.floor(yLevel);
        
        // 使用椭圆参数方程生成点
        int segments = Math.max(32, Math.max(blockRadiusX, blockRadiusY) * 2);
        double angleStep = 2 * Math.PI / segments;
        
        for (int i = 0; i < segments; i++) {
            double angle = i * angleStep;
            
            // 椭圆参数方程
            double x = centerBlockX + blockRadiusX * Math.cos(angle) * Math.cos(rotation) - 
                      blockRadiusY * Math.sin(angle) * Math.sin(rotation);
            double z = centerBlockY + blockRadiusX * Math.cos(angle) * Math.sin(rotation) + 
                      blockRadiusY * Math.sin(angle) * Math.cos(rotation);
            
            result.add(new BlockPos((int) Math.floor(x + 0.5), y, (int) Math.floor(z + 0.5)));
        }
        
        return result;
    }

    /**
     * 【新增】光栅化圆弧
     * 使用圆弧参数方程
     */
    private List<BlockPos> rasterizeArc(double centerX, double centerY, double radius, double startAngle, double endAngle, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        int centerBlockX = (int) Math.floor(centerX + 0.5);
        int centerBlockY = (int) Math.floor(centerY + 0.5);
        int blockRadius = (int) Math.floor(radius + 0.5);
        int y = (int) Math.floor(yLevel);
        
        // 计算圆弧的段数
        double angleRange = Math.abs(endAngle - startAngle);
        int segments = Math.max(16, (int)(blockRadius * angleRange / Math.PI));
        double angleStep = angleRange / segments;
        
        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            
            double x = centerBlockX + blockRadius * Math.cos(angle);
            double z = centerBlockY + blockRadius * Math.sin(angle);
            
            result.add(new BlockPos((int) Math.floor(x + 0.5), y, (int) Math.floor(z + 0.5)));
        }
        
        return result;
    }

    /**
     * 【新增】光栅化多边形
     * 使用扫描线算法
     */
    private List<BlockPos> rasterizePolygon(List<Vec2d> points, double yLevel) {
        List<BlockPos> result = new ArrayList<>();
        
        if (points.size() < 3) {
            LOGGER.warn("多边形点数不足: {}", points.size());
            return result;
        }
        
        // 计算边界框
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;
        
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.y);
            maxZ = Math.max(maxZ, point.y);
        }
        
        int startX = (int) Math.floor(minX + 0.5);
        int endX = (int) Math.floor(maxX + 0.5);
        int startZ = (int) Math.floor(minZ + 0.5);
        int endZ = (int) Math.floor(maxZ + 0.5);
        int y = (int) Math.floor(yLevel);
        
        // 扫描线算法填充多边形
        for (int z = startZ; z <= endZ; z++) {
            List<Integer> intersections = getIntegers(points, z);

            // 排序交点
            intersections.sort(Integer::compareTo);
            
            // 填充交点之间的区域
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                int x1 = intersections.get(i);
                int x2 = intersections.get(i + 1);
                
                for (int x = x1; x <= x2; x++) {
                    result.add(new BlockPos(x, y, z));
                }
            }
        }
        
        return result;
    }

    private static @NotNull List<Integer> getIntegers(List<Vec2d> points, int z) {
        List<Integer> intersections = new ArrayList<>();

        // 计算与扫描线的交点
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());

            if ((p1.y <= z && p2.y > z) || (p2.y <= z && p1.y > z)) {
                double x = p1.x + (p2.x - p1.x) * (z - p1.y) / (p2.y - p1.y);
                intersections.add((int) Math.floor(x + 0.5));
            }
        }
        return intersections;
    }
}
