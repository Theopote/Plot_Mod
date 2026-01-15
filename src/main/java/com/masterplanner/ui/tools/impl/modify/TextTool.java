package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.model.ICanvas;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.api.graphics.IShapeStyle;
import com.masterplanner.core.geometry.shapes.TextShape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.log.LogManager;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.core.graphics.style.TextStyle;
import com.masterplanner.core.graphics.style.TextAlignment;
import com.masterplanner.ui.dialog.TextDialog;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 增强文字工具 - 支持多行文字和性能优化
 * <p>
 * 功能特性：
 * 1. 点击画布直接输入文字（类似CAD的DTEXT命令）
 * 2. 支持字体大小调整（Shift+Plus/Minus）
 * 3. 支持转换为图形路径
 * 4. 双击现有文字进行编辑
 * 5. 支持多行文字输入
 * 6. 性能优化的缓存机制和空间索引
 */
public class TextTool extends BaseTool {
    
    // ---- 工具标识 ----
    private static final String TOOL_ID = "text";
    private static final String TOOL_NAME = "文字";
    private static final String TOOL_DESCRIPTION = "添加多行文字";
    private static final String DEFAULT_TEXT = "文字";
    
    // ---- 光标定义 ----
    private static final String CURSOR_TEXT = "text";
    private static final String CURSOR_CROSSHAIR = "crosshair";
    private static final String CURSOR_DEFAULT = "default";
    
    // ---- 键盘快捷键 ----
    private static final int KEY_ENTER = KeyEvent.VK_ENTER;
    private static final int KEY_ESCAPE = KeyEvent.VK_ESCAPE;
    private static final int KEY_BACKSPACE = KeyEvent.VK_BACK_SPACE;
    private static final int KEY_DELETE = KeyEvent.VK_DELETE;
    private static final int KEY_PLUS = KeyEvent.VK_PLUS;
    private static final int KEY_MINUS = KeyEvent.VK_MINUS;
    private static final int KEY_EQUALS = KeyEvent.VK_EQUALS;

    // ---- 字体配置 ----
    private static final float FONT_SIZE_STEP = 2.0f;
    private static final float MIN_FONT_SIZE = TextStyle.MIN_FONT_SIZE;
    private static final float MAX_FONT_SIZE = TextStyle.MAX_FONT_SIZE;
    private static final float DEFAULT_FONT_SIZE = TextStyle.DEFAULT_FONT_SIZE;

    // ---- 空间索引配置 ----
    private static final double SPATIAL_INDEX_GRID_SIZE = 100.0; // 网格大小
    private static final double TEXT_SELECTION_TOLERANCE = 5.0; // 文字选择容差

    /**
     * 工具状态枚举 - 提高代码清晰度和可维护性
     */
    private enum ToolState {
        IDLE,      // 空闲状态
        PLACING,   // 正在放置预览文字
        EDITING    // 正在编辑现有文字
    }

    // ---- 配置相关 ----
    private boolean useDialog = true; // 默认启用对话框
    private float configFontSize = DEFAULT_FONT_SIZE;
    private boolean configBold = false;
    private boolean configItalic = false;
    private Color configColor = TextStyle.DEFAULT_COLOR;
    private TextAlignment.Horizontal configHorizontalAlignment = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical configVerticalAlignment = TextAlignment.Vertical.TOP;
    private float configLineHeight = TextStyle.DEFAULT_LINE_HEIGHT;

    // ---- 状态管理 ----
    private final ICanvas canvas;
    private final Component parentComponent; // 父组件引用，提高健壮性
    private TextShape activeText;
    private ToolState currentState; // 使用枚举替代多个布尔值
    private String pendingText;
    private TextShape previewText;
    private TextStyle pendingTextStyle; // 用于保存对话框样式，等待用户点击画布定位

    // ---- 空间索引支持 ----
    private final ConcurrentHashMap<String, java.util.Set<TextShape>> spatialIndex = new ConcurrentHashMap<>();
    private final AtomicInteger spatialIndexVersion = new AtomicInteger(0);

    // ---- 事件处理器 ----
    private final EventListener toolConfigHandler = this::handleToolConfig;

    /**
     * 构造函数 - 支持父组件注入，提高健壮性
     */
    public TextTool(ICanvas canvas, Component parentComponent) {
        super(TOOL_ID, TOOL_DESCRIPTION, Icons.TEXT_IDENTIFIER, TOOL_NAME);
        this.canvas = canvas;
        this.parentComponent = parentComponent;
        this.currentState = ToolState.IDLE;

        // 配置工具
        config.setDescription("Add multi-line text");
        config.setTooltip("Click to add text, double-click to edit");
        config.setIcon(Icons.TEXT);
        config.setShortcutKey("T");
        config.setPriority(50);

        // 订阅配置事件
        subscribeToEvents();
        
        // 初始化默认配置
        initializeDefaultConfig();
        
        // 验证初始配置
        LogManager.getInstance().debug("TextTool: 初始化完成，配置状态 - {}", getDebugInfo());
    }

    /**
     * 向后兼容的构造函数
     */
    public TextTool(ICanvas canvas) {
        this(canvas, null);
    }

    /**
     * 初始化默认配置
     */
    private void initializeDefaultConfig() {
        // 确保默认配置被正确设置
        useDialog = true;
        configFontSize = DEFAULT_FONT_SIZE;
        configBold = false;
        configItalic = false;
        configColor = TextStyle.DEFAULT_COLOR;
        configHorizontalAlignment = TextAlignment.Horizontal.LEFT;
        configVerticalAlignment = TextAlignment.Vertical.TOP;
        configLineHeight = TextStyle.DEFAULT_LINE_HEIGHT;
        
        LogManager.getInstance().debug("TextTool: 初始化默认配置完成 - useDialog: {}", true);
    }

    /**
     * 订阅相关事件
     */
    private void subscribeToEvents() {
        try {
            eventBus.subscribe(ToolConfigEvent.class, toolConfigHandler);
            LogManager.getInstance().debug("TextTool 事件订阅完成");
        } catch (Exception e) {
            LogManager.getInstance().error("TextTool 事件订阅失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理工具配置事件
     */
    private void handleToolConfig(Event event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if (!TOOL_ID.equals(configEvent.getToolId())) {
                return; // 不是文字工具的配置事件
            }

            String key = configEvent.getOptionName();
            String value = String.valueOf(configEvent.getValue());

            LogManager.getInstance().debug("TextTool: 收到配置事件 - key: {}, value: {}", key, value);

            try {
                switch (key) {
                    case "fontSize":
                        configFontSize = Float.parseFloat(value);
                        LogManager.getInstance().debug("TextTool: 字体大小配置更新为 {}", configFontSize);
                        break;
                    case "bold":
                        configBold = Boolean.parseBoolean(value);
                        LogManager.getInstance().debug("TextTool: 粗体配置更新为 {}", configBold);
                        break;
                    case "italic":
                        configItalic = Boolean.parseBoolean(value);
                        LogManager.getInstance().debug("TextTool: 斜体配置更新为 {}", configItalic);
                        break;
                    case "color":
                        configColor = new Color(Integer.parseInt(value));
                        LogManager.getInstance().debug("TextTool: 颜色配置更新为 {}", configColor);
                        break;
                    case "useDialog":
                        boolean oldUseDialog = useDialog;
                        useDialog = Boolean.parseBoolean(value);
                        LogManager.getInstance().debug("TextTool: 对话框配置从 {} 更新为 {}", oldUseDialog, useDialog);
                        break;
                    case "horizontalAlignment":
                        try {
                            configHorizontalAlignment = TextAlignment.Horizontal.valueOf(value);
                            LogManager.getInstance().debug("TextTool: 水平对齐更新为 {}", configHorizontalAlignment);
                        } catch (IllegalArgumentException ex) {
                            LogManager.getInstance().warn("TextTool: 非法的水平对齐值: {}", value);
                        }
                        break;
                    case "verticalAlignment":
                        try {
                            configVerticalAlignment = TextAlignment.Vertical.valueOf(value);
                            LogManager.getInstance().debug("TextTool: 垂直对齐更新为 {}", configVerticalAlignment);
                        } catch (IllegalArgumentException ex) {
                            LogManager.getInstance().warn("TextTool: 非法的垂直对齐值: {}", value);
                        }
                        break;
                    case "lineHeight":
                        try {
                            configLineHeight = Float.parseFloat(value);
                            LogManager.getInstance().debug("TextTool: 行高更新为 {}", configLineHeight);
                        } catch (NumberFormatException ex) {
                            LogManager.getInstance().warn("TextTool: 非法的行高值: {}", value);
                        }
                        break;
                    case "convertSelected":
                        if (activeText != null) {
                            convertTextToGraphics();
                        }
                        break;
                    default:
                        LogManager.getInstance().debug("TextTool: 未知配置键: {}", key);
                        break;
                }
            } catch (Exception e) {
                LogManager.getInstance().error("TextTool: 处理配置事件失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 判断是否应该使用对话框
     */
    private boolean shouldUseDialog() {
        // 仅依据配置决定是否使用对话框，同时记录当前环境信息
        boolean headless = GraphicsEnvironment.isHeadless();
        LogManager.getInstance().debug("TextTool: 当前useDialog配置: {}, headless: {}", useDialog, headless);
        return useDialog;
    }

    /**
     * 调试方法：获取当前配置状态
     */
    public String getDebugInfo() {
        return String.format("TextTool配置 - useDialog: %s, fontSize: %.1f, bold: %s, italic: %s", 
                           useDialog, configFontSize, configBold, configItalic);
    }

    // ====== 配置Getter方法 ======
    
    /**
     * 获取字体大小
     */
    public float getFontSize() {
        return configFontSize;
    }
    
    /**
     * 获取粗体设置
     */
    public boolean isBold() {
        return configBold;
    }
    
    /**
     * 获取斜体设置
     */
    public boolean isItalic() {
        return configItalic;
    }
    
    /**
     * 获取颜色设置
     */
    public Color getColor() {
        return configColor;
    }
    
    /**
     * 获取对话框使用设置
     */
    public boolean isUseDialog() {
        return useDialog;
    }
    
    /**
     * 获取水平对齐设置
     */
    public TextAlignment.Horizontal getHorizontalAlignment() {
        return configHorizontalAlignment;
    }
    
    /**
     * 获取垂直对齐设置
     */
    public TextAlignment.Vertical getVerticalAlignment() {
        return configVerticalAlignment;
    }
    
    /**
     * 获取行高设置
     */
    public float getLineHeight() {
        return configLineHeight;
    }

    /**
     * 创建默认文字样式
     */
    private TextStyle createDefaultTextStyle() {
        return new TextStyle.Builder()
            .fontSize(configFontSize)
            .color(getCurrentLayerColor())
            .bold(configBold)
            .italic(configItalic)
            .horizontalAlignment(configHorizontalAlignment)
            .verticalAlignment(configVerticalAlignment)
            .lineHeight(configLineHeight)
            .build();
    }

    /**
     * 获取当前图层（当前样式）的线条颜色作为文字颜色
     */
    private Color getCurrentLayerColor() {
        try {
            // 1) 优先使用 AppState 当前样式的线条颜色
            var style = appState != null ? appState.getCurrentShapeStyle() : null;
            if (style != null && style.getLineStyle() != null && style.getLineStyle().getColor() != null) {
                return style.getLineStyle().getColor();
            }

            // 2) 回退到当前图层：优先图层的 LineStyle 颜色，其次图层颜色
            ILayer layer = canvas != null ? canvas.getCurrentLayer() : null;
            if (layer != null) {
                if (layer.getLineStyle() != null && layer.getLineStyle().getColor() != null) {
                    return layer.getLineStyle().getColor();
                }
                if (layer.getColor() != null) {
                    return layer.getColor();
                }
            }
        } catch (Exception ignored) {}
        // 3) 最后回退到默认颜色
        return TextStyle.DEFAULT_COLOR;
    }

    @Override
    public boolean onMouseDown(Vec2d worldPoint, int button) {
        LogManager.getInstance().debug("TextTool: 鼠标按下，当前配置状态 - {}", getDebugInfo());
        
        if (button == 0) { // 左键
            if (currentState == ToolState.IDLE) {
                // 点击画布时，如果启用了对话框，直接打开对话框
                if (shouldUseDialog()) {
                    LogManager.getInstance().debug("TextTool: 点击画布，打开文字输入对话框");
                    showTextDialog(worldPoint);
                } else {
                    // 如果未启用对话框，使用默认文字模式
                    startTextInput(worldPoint);
                }
                return true;
            } else if (currentState == ToolState.PLACING) {
                if (previewText == null && pendingText != null && pendingTextStyle != null) {
                    // 第一次点击：创建预览文字
                    previewText = new TextShape(worldPoint, pendingText);
                    previewText.setTextStyle(pendingTextStyle);
                    LogManager.getInstance().debug("TextTool: 创建预览文字在位置: {}", worldPoint);
                    return true;
                } else if (previewText != null) {
                    // 第二次点击：完成放置
                    finalizePlacement(worldPoint);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onMouseMove(Vec2d pos) {
        if (currentState == ToolState.PLACING && previewText != null) {
            previewText.setPosition(pos);
            canvas.refresh(); // 立即刷新，提供更好的用户体验
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDoubleClick(Vec2d pos, int button) {
        LogManager.getInstance().debug("TextTool.onMouseDoubleClick: pos={}, button={}", pos, button);
        
        String textId = findTextAtPoint(pos);
        if (textId != null) {
            // 开始编辑文字
            startEditing(textId);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        if (currentState == ToolState.PLACING) {
            if (keyCode == KEY_ESCAPE) {
                resetToIdle();
                return true;
            }
        } else if (currentState == ToolState.EDITING && activeText != null) {
            switch (keyCode) {
                case KEY_ENTER:
                    if (isControlDown) {
                        // Ctrl+Enter 完成编辑
                        finishEditing();
                        return true;
                    }
                    // 普通Enter添加换行符
                    return false;
                case KEY_ESCAPE:
                    cancelEditing();
                    return true;
                case KEY_BACKSPACE:
                    deleteLastChar();
                    return true;
                case KEY_DELETE:
                    deleteFirstChar(); // 重命名方法，明确行为
                    return true;
            }
        } else if (activeText != null) {
            // 非编辑状态下调整字体大小
            switch (keyCode) {
                case KEY_PLUS:
                case KEY_EQUALS:
                    if (isShiftDown) {
                        increaseFontSize();
                        return true;
                    }
                    break;
                case KEY_MINUS:
                    if (isShiftDown) {
                        decreaseFontSize();
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char character) {
        if (currentState == ToolState.EDITING && activeText != null) {
            // 处理可打印字符和换行符
            if (character == '\n' || !Character.isISOControl(character)) {
                updateTextAndRefresh(activeText.getText() + character);
                return true;
            }
        }
        return false;
    }

    /**
     * 开始文字输入
     */
    private void startTextInput(Vec2d point) {
        LogManager.getInstance().debug("TextTool: 开始文字输入");
        LogManager.getInstance().debug("TextTool: 当前配置状态 - {}", getDebugInfo());
        
        // 询问用户是否使用对话框输入
        if (shouldUseDialog()) {
            LogManager.getInstance().debug("TextTool: 使用对话框模式");
            showTextDialog(point);
        } else {
            LogManager.getInstance().debug("TextTool: 使用默认文字模式");
            // 使用默认文字开始
            useDefaultText(point);
        }
    }

    /**
     * 显示文字输入对话框 - 改进的父窗口获取方式
     */
    private void showTextDialog(Vec2d point) {
        LogManager.getInstance().debug("TextTool: 显示文字输入对话框");
        
        SwingUtilities.invokeLater(() -> {
            try {
                // 优先尝试 Swing 对话框
                if (!GraphicsEnvironment.isHeadless()) {
                    Frame owner = getParentFrame();
                    TextDialog dialog = new TextDialog(owner);
                    // 设置对话框的初始值为当前配置
                    dialog.setInitialFontSize(configFontSize);
                    dialog.setInitialBold(configBold);
                    dialog.setInitialItalic(configItalic);
                    dialog.setInitialHorizontalAlignment(configHorizontalAlignment);
                    dialog.setInitialVerticalAlignment(configVerticalAlignment);
                    dialog.setVisible(true);

                    if (dialog.isConfirmed()) {
                        pendingText = dialog.getInputText();
                        if (pendingText != null && !pendingText.isEmpty()) {
                            var dlgStyle = dialog.getTextStyle();
                            TextStyle finalStyle = new TextStyle.Builder()
                                    .fontFamily(dlgStyle.getFontFamily())
                                    .fontSize(dlgStyle.getFontSize())
                                    .bold(dlgStyle.isBold())
                                    .italic(dlgStyle.isItalic())
                                    .horizontalAlignment(getHorizontalAlignment())
                                    .verticalAlignment(getVerticalAlignment())
                                    .lineHeight(getLineHeight())
                                    .color(getCurrentLayerColor())
                                    .build();

                            TextShape newText = new TextShape(point, pendingText);
                            newText.setTextStyle(finalStyle);
                            this.previewShape = newText;
                            commit();
                            rebuildSpatialIndex();
                            LogManager.getInstance().info("TextTool: 用户输入文字: {}，已放置到画布", pendingText);
                            resetToIdle();
                            return;
                        }
                    } else {
                        LogManager.getInstance().debug("TextTool: 用户取消文字输入");
                        resetToIdle();
                        return;
                    }
                }
            } catch (Exception e) {
                LogManager.getInstance().warn("TextTool: Swing 对话框不可用，切换到 ImGui 对话框: {}", e.toString());
            }

            // 回退：使用 ImGui 对话框（无 Swing 依赖）
            try {
                com.masterplanner.ui.dialog.TextInputDialog.getInstance().scheduleOpen(
                        point,
                        "",
                        result -> {
                            if (result == null) {
                                resetToIdle();
                                return;
                            }
                            String text = result.text;
                            if (text == null || text.isEmpty()) {
                                resetToIdle();
                                return;
                            }
                            pendingText = text;
                            TextStyle finalStyle = new TextStyle.Builder()
                                    .fontSize(result.fontSize)
                                    .bold(result.bold)
                                    .italic(result.italic)
                                    .horizontalAlignment(result.hAlign)
                                    .verticalAlignment(result.vAlign)
                                    .lineHeight(result.lineHeight)
                                    .color(getCurrentLayerColor())
                                    .build();
                            TextShape newText = new TextShape(point, pendingText);
                            newText.setTextStyle(finalStyle);
                            this.previewShape = newText;
                            commit();
                            rebuildSpatialIndex();
                            canvas.refresh();
                            resetToIdle();
                        },
                        this::resetToIdle
                );
            } catch (Exception ex) {
                LogManager.getInstance().error("TextTool: ImGui 回退对话框也不可用，使用默认文字", ex);
                useDefaultText(point);
            }
        });
    }

    /**
     * 安全地获取父Frame - 改进的健壮性
     */
    private Frame getParentFrame() {
        // 优先使用注入的父组件
        if (parentComponent != null) {
            if (parentComponent instanceof Frame) {
                return (Frame) parentComponent;
            }
            
            // 尝试从父组件层次结构中查找Frame
            Frame frame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parentComponent);
            if (frame != null) {
                return frame;
            }
        }
        
        // 回退到全局查找（只选择可见窗口，避免选择隐藏或临时窗口导致对话框不可见）
        Frame[] frames = JFrame.getFrames();
        if (frames.length > 0) {
            // 优先选择可见的主窗口
            for (Frame frame : frames) {
                if (frame.isVisible() && frame.getTitle() != null && !frame.getTitle().isEmpty()) {
                    return frame;
                }
            }
            // 如果没有找到合适的主窗口，避免返回一个不可见/未初始化的Frame
            // 返回null，让对话框使用无父窗口模式，避免被隐藏
            return null;
        }
        
        // 最后回退：不创建临时窗口，直接返回null
        LogManager.getInstance().warn("TextTool: 未找到合适的父窗口，采用无父窗口对话框");
        return null;
    }

    /**
     * 使用默认文字
     */
    private void useDefaultText(Vec2d point) {
        LogManager.getInstance().info("TextTool: 使用默认文字");
        
        pendingText = DEFAULT_TEXT;
        currentState = ToolState.PLACING;
        
        // 创建预览文字
        previewText = new TextShape(point, pendingText);
        previewText.setTextStyle(createDefaultTextStyle());
        
        LogManager.getInstance().debug("TextTool: 使用默认文字: {}", pendingText);
        
        // 设置鼠标光标为文字放置模式
        canvas.setCursor(CURSOR_CROSSHAIR);
    }

    /**
     * 完成放置并将文字作为图形提交到当前图层
     * 注意：文字会自动转换为线图形
     */
    private void finalizePlacement(Vec2d point) {
        if (previewText == null) {
            return;
        }
        // 最终位置
        previewText.setPosition(point);
        // 使用BaseTool的提交机制
        this.previewShape = previewText;
        commit();
        // 提交后重建本地索引，保证双击编辑可及时命中
        rebuildSpatialIndex();
        // 重置到可继续放置的空闲态
        currentState = ToolState.IDLE;
        previewText = null;
        pendingText = null;
        canvas.setCursor(CURSOR_TEXT);
        canvas.refresh();
    }

    /**
     * 查找指定点的文字 - 使用空间索引优化性能
     */
    private String findTextAtPoint(Vec2d point) {
        // 使用空间索引进行快速查找
        java.util.Set<TextShape> candidates = getCandidatesFromSpatialIndex(point);
        
        // 在候选形状中查找精确匹配
        for (TextShape textShape : candidates) {
            if (textShape.containsPoint(point, TEXT_SELECTION_TOLERANCE)) {
                // 找到匹配的文字，返回其ID
                return textShape.getId();
            }
        }
        
        // 如果空间索引未命中，回退到传统方法
        return findTextAtPointFallback(point);
    }

    /**
     * 从空间索引获取候选形状
     */
    private java.util.Set<TextShape> getCandidatesFromSpatialIndex(Vec2d point) {
        String gridKey = getGridKey(point);
        return spatialIndex.getOrDefault(gridKey, java.util.Set.of());
    }

    /**
     * 获取网格键
     */
    private String getGridKey(Vec2d point) {
        int gridX = (int) (point.x / SPATIAL_INDEX_GRID_SIZE);
        int gridY = (int) (point.y / SPATIAL_INDEX_GRID_SIZE);
        return gridX + "," + gridY;
    }

    /**
     * 回退的文字查找方法
     */
    private String findTextAtPointFallback(Vec2d point) {
        for (ILayer layer : canvas.getLayers()) {
            if (!layer.isVisible()) continue;

            for (String shapeId : layer.getShapeIds()) {
                var shape = layer.getShape(shapeId);
                if (shape instanceof TextShape && shape.containsPoint(point, TEXT_SELECTION_TOLERANCE)) {
                    return shapeId;
                }
            }
        }
        return null;
    }

    /**
     * 添加到空间索引
     */
    private void addToSpatialIndex(TextShape textShape) {
        String gridKey = getGridKey(textShape.getPosition());
        spatialIndex.computeIfAbsent(gridKey, k -> ConcurrentHashMap.newKeySet()).add(textShape);
        spatialIndexVersion.incrementAndGet();
    }

    /**
     * 从空间索引移除
     */
    private void removeFromSpatialIndex(TextShape textShape) {
        String gridKey = getGridKey(textShape.getPosition());
        java.util.Set<TextShape> shapes = spatialIndex.get(gridKey);
        if (shapes != null) {
            shapes.remove(textShape);
            if (shapes.isEmpty()) {
                spatialIndex.remove(gridKey);
            }
            spatialIndexVersion.incrementAndGet();
        }
    }

    /**
     * 重建空间索引
     */
    private void rebuildSpatialIndex() {
        spatialIndex.clear();
        for (ILayer layer : canvas.getLayers()) {
            if (!layer.isVisible()) continue;
            
            for (String shapeId : layer.getShapeIds()) {
                var shape = layer.getShape(shapeId);
                if (shape instanceof TextShape) {
                    addToSpatialIndex((TextShape) shape);
                }
            }
        }
        LogManager.getInstance().debug("TextTool: 空间索引重建完成，包含 {} 个网格", spatialIndex.size());
    }

    private void selectText(String textId) {
        for (ILayer layer : canvas.getLayers()) {
            if (layer.hasShape(textId)) {
                var shape = layer.getShape(textId);
                if (shape instanceof TextShape) {
                    activeText = (TextShape) shape;
                    LogManager.getInstance().debug("TextTool: 选中文字: {}", activeText.getText());
                    break;
                }
            }
        }
    }

    private void startEditing(String textId) {
        selectText(textId);
        if (activeText != null) {
            // 保存原始文字用于取消编辑
            activeText.saveOriginalText();
            
            currentState = ToolState.EDITING;
            canvas.setCursor(CURSOR_TEXT);
            LogManager.getInstance().info("TextTool: 开始编辑文字: {}", activeText.getText());
        }
    }

    private void finishEditing() {
        if (activeText != null) {
            String text = activeText.getText().trim();
            if (text.isEmpty()) {
                // 如果文字为空，删除形状
                ILayer currentLayer = canvas.getCurrentLayer();
                if (currentLayer != null) {
                    currentLayer.removeShape(activeText);
                    removeFromSpatialIndex(activeText);
                    LogManager.getInstance().info("TextTool: 删除空文字");
                }
            } else {
                LogManager.getInstance().info("TextTool: 完成编辑文字: {}", text);
            }
        }
        
        resetToIdle();
    }

    private void cancelEditing() {
        if (activeText != null) {
            // 恢复原始文字
            activeText.restoreOriginalText();
            
            // 如果恢复后文字为空，删除形状
            if (activeText.getText().isEmpty()) {
                ILayer currentLayer = canvas.getCurrentLayer();
                if (currentLayer != null) {
                    currentLayer.removeShape(activeText);
                    removeFromSpatialIndex(activeText);
                    LogManager.getInstance().info("TextTool: 取消编辑，删除空文字");
                }
            } else {
                LogManager.getInstance().info("TextTool: 取消编辑，恢复原始文字: {}", activeText.getText());
            }
        }
        
        resetToIdle();
    }

    private void deleteLastChar() {
        if (activeText != null) {
            String currentText = activeText.getText();
            if (!currentText.isEmpty()) {
                updateTextAndRefresh(currentText.substring(0, currentText.length() - 1));
            }
        }
    }

    /**
     * 删除第一个字符 - 重命名方法，明确行为
     */
    private void deleteFirstChar() {
        if (activeText != null) {
            String currentText = activeText.getText();
            if (!currentText.isEmpty()) {
                updateTextAndRefresh(currentText.substring(1));
            }
        }
    }

    /**
     * 统一的文本更新和画布刷新方法
     */
    private void updateTextAndRefresh(String newText) {
        if (activeText != null) {
            activeText.setText(newText);
            canvas.refresh(); // 立即刷新，提供更好的用户体验
        }
    }

    /**
     * 重置到空闲状态
     */
    private void resetToIdle() {
        currentState = ToolState.IDLE;
        activeText = null;
        previewText = null;
        pendingText = null;
        pendingTextStyle = null;
        canvas.setCursor(CURSOR_DEFAULT);
        canvas.refresh();
    }

    /**
     * 增大字体大小 - 修复刷新问题
     */
    private void increaseFontSize() {
        if (activeText != null && activeText.getTextStyle() != null) {
            float currentSize = activeText.getFontSize();
            float newSize = Math.min(currentSize + FONT_SIZE_STEP, MAX_FONT_SIZE);
            if (newSize != currentSize) { // 仅在值改变时刷新
                activeText.setFontSize(newSize);
                canvas.refresh(); // 立即刷新
                LogManager.getInstance().debug("TextTool: 字体大小增加到 {}", newSize);
            }
        }
    }

    /**
     * 减小字体大小 - 修复刷新问题
     */
    private void decreaseFontSize() {
        if (activeText != null && activeText.getTextStyle() != null) {
            float currentSize = activeText.getFontSize();
            float newSize = Math.max(currentSize - FONT_SIZE_STEP, MIN_FONT_SIZE);
            if (newSize != currentSize) { // 仅在值改变时刷新
                activeText.setFontSize(newSize);
                canvas.refresh(); // 立即刷新
                LogManager.getInstance().debug("TextTool: 字体大小减少到 {}", newSize);
            }
        }
    }

    /**
     * 将文字转换为图形
     */
    public void convertTextToGraphics() {
        if (activeText == null) return;
        
        List<com.masterplanner.core.model.Shape> graphics = activeText.convertToGraphics();
        if (!graphics.isEmpty()) {
            ILayer currentLayer = canvas.getCurrentLayer();
            if (currentLayer != null) {
                // 移除原文字
                currentLayer.removeShape(activeText);
                removeFromSpatialIndex(activeText);
                
                // 添加转换后的图形
                for (com.masterplanner.core.model.Shape graphic : graphics) {
                    currentLayer.addShape(graphic);
                }
                
                LogManager.getInstance().info("TextTool: 文字已转换为 {} 个图形", graphics.size());
                canvas.refresh();
            }
        }
    }

    /**
     * 重写commit方法，使文字在提交到画布时自动转换为线图形
     */
    @Override
    public void commit() {
        // 检查预览图形是否是TextShape
        if (previewShape instanceof TextShape textShape) {
            // 将文字转换为图形路径
            List<com.masterplanner.core.model.Shape> graphics = textShape.convertToGraphics();
            
            if (!graphics.isEmpty()) {
                ILayer activeLayer = appState.getActiveLayer();
                
                if (activeLayer == null) {
                    LogManager.getInstance().error("没有活动图层，无法提交图形");
                    return;
                }
                
                if (activeLayer.isLocked()) {
                    LogManager.getInstance().warn("活动图层 '{}' 已锁定，无法提交图形", activeLayer.getName());
                    return;
                }
                
                try {
                    LogManager.getInstance().debug("TextTool: 将文字转换为 {} 个图形并提交到活动图层 '{}'", 
                        graphics.size(), activeLayer.getName());
                    
                    // 为每个转换后的图形设置样式
                    IShapeStyle defaultStyle = appState.getCurrentShapeStyle();
                    
                    // 添加转换后的图形到图层
                    for (com.masterplanner.core.model.Shape graphic : graphics) {
                        // 确保图形有样式
                        if (graphic.getStyle() == null) {
                            if (defaultStyle != null) {
                                graphic.setStyle(defaultStyle.clone());
                            } else {
                                // 使用文字的颜色创建默认样式
                                com.masterplanner.core.graphics.style.ShapeStyle shapeStyle = 
                                    new com.masterplanner.core.graphics.style.ShapeStyle();
                                if (textShape.getTextStyle() != null && textShape.getTextStyle().getColor() != null) {
                                    shapeStyle.setStrokeColor(textShape.getTextStyle().getColor());
                                } else {
                                    shapeStyle.setStrokeColor(java.awt.Color.BLACK);
                                }
                                shapeStyle.setStrokeWidth(1.0f);
                                graphic.setStyle(shapeStyle);
                            }
                        }
                        
                        // 通过AppState添加图形，确保完整的业务逻辑
                        appState.addShape(graphic);
                    }
                    
                    LogManager.getInstance().info("TextTool: 文字已转换为 {} 个图形并添加到画布", graphics.size());
                    
                    // 清理预览状态
                    clearPreview();
                    markDirty();
                    
                } catch (Exception e) {
                    LogManager.getInstance().error("TextTool: 提交转换后的图形失败", e);
                }
            } else {
                LogManager.getInstance().warn("TextTool: 文字转换后没有生成图形");
                clearPreview();
            }
        } else {
            // 如果不是TextShape，使用父类的默认行为
            super.commit();
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 绘制预览文字
        if (currentState == ToolState.PLACING && previewText != null) {
            previewText.draw(context);
        }
    }

    @Override
    protected void onActivate() {
        super.onActivate();
        LogManager.getInstance().debug("TextTool: 激活文字工具");
        canvas.setCursor(CURSOR_TEXT);
        
        // 重建空间索引
        rebuildSpatialIndex();
        
        LogManager.getInstance().debug("TextTool: 文字工具已激活，点击画布开始输入文字");
    }

    @Override
    protected void onDeactivate() {
        super.onDeactivate();
        LogManager.getInstance().debug("TextTool: 停用文字工具");
        if (currentState == ToolState.EDITING) {
            finishEditing();
        }
        resetToIdle();

        // 取消事件订阅
        try {
            eventBus.unsubscribe(ToolConfigEvent.class, toolConfigHandler);
            LogManager.getInstance().debug("TextTool 事件取消订阅完成");
        } catch (Exception e) {
            LogManager.getInstance().error("TextTool 事件取消订阅失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void reset() {
        super.reset();
        if (currentState == ToolState.EDITING) {
            cancelEditing();
        }
        resetToIdle();
    }

    @Override
    public String getDefaultCursor() {
        return CURSOR_TEXT;
    }

    /**
     * 获取当前状态 - 用于调试和监控
     */
    public ToolState getCurrentState() {
        return currentState;
    }
}
