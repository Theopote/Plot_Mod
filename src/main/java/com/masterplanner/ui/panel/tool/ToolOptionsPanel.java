package com.masterplanner.ui.panel.tool;

import com.masterplanner.MasterPlannerMod;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.infrastructure.event.tool.ToolChangedEvent;
import com.masterplanner.infrastructure.event.tool.ToolStatusEvent;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.panel.tool.renderer.ToolOptionRenderer;
import com.masterplanner.ui.panel.tool.renderer.ToolOptionRendererFactory;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;

/**
 * 工具选项面板，负责渲染当前选中工具的所有选项和配置界面
 * 包括工具信息显示和各种工具特定的选项控件
 */
public class ToolOptionsPanel implements UIComponent, AutoCloseable, EventListener {
    // 界面布局常量
    private static final float LABEL_WIDTH = 60.0f;         // 标签文本宽度
    private static final float PANEL_PADDING = 4.0f;        // 面板内边距

    // 核心组件引用
    private final AppState appState;    // 应用状态管理器
    private final EventBus eventBus;    // 事件总线

    // 状态消息管理
    private String currentToolStatusMessage = "";  // 当前工具的状态消息

    /**
     * 工具类型枚举，定义所有支持的工具类型及其显示名称
     */
    private enum ToolType {
        UNKNOWN("unknown", "未知工具"),
        SELECT("select", "选择"),
        LINE("line", "直线"),
        CIRCLE("circle", "圆形"),
        RECTANGLE("rectangle", "矩形"),
        ELLIPSE("ellipse", "椭圆"),
        ARC("arc", "圆弧"),
        POLYGON("polygon", "多边形"),
        POLYLINE("polyline", "多段线"),
        SPLINE("spline", "样条曲线"),
        CATENARY("catenary", "悬链线"),
        FREE_DRAW("freedraw", "自由绘制"),
        ERASER("eraser", "橡皮擦"),
        SEMICIRCLE("semicircle", "半圆"),
        STAR("star", "星形"),
        SPIRAL("spiral", "螺旋线"),
        SINE("sine", "正弦曲线"),
        MOVE("move", "移动"),
        ROTATE("rotate", "旋转"),
        MIRROR("mirror", "镜像"),
        SCALE("scale", "缩放"),
        ALIGN("align", "对齐"),
        ARRAY("array", "阵列"),
        OFFSET("offset", "偏移"),
        FILLET("fillet", "倒角"),
        CHAMFER("chamfer", "倒角"),
        TRANSFORM("transform", "变换"),

        TRIM("trim", "修剪"),
        EXTEND("extend", "延伸"),
        BREAK("break", "打断"),
        TEXT("text", "文本"),
        ANNOTATION("annotation", "标注");

        private final String displayName;

        ToolType(String id, String displayName) {
            this.displayName = displayName;
        }

        /**
         * 从字符串获取工具类型
         * @param name 工具名称
         * @return 对应的工具类型，如果未找到则返回 UNKNOWN
         */
        public static ToolType fromString(String name) {
            if (name == null) return UNKNOWN;
            
            // 转换为小写并去除空白字符
            String normalizedName = name.toLowerCase().trim();
            
            // 添加详细的日志
            MasterPlannerMod.LOGGER.debug("尝试匹配工具类型: '{}' (normalized: '{}')", name, normalizedName);
            
            return switch (normalizedName) {
                case "select", "选择", "selecttool" -> SELECT;
                case "line", "直线", "linetool" -> LINE;
                case "circle", "圆形", "circletool" -> CIRCLE;
                case "rectangle", "矩形", "rectangletool" -> RECTANGLE;
                case "ellipse", "椭圆", "ellipsetool" -> ELLIPSE;
                case "arc", "圆弧", "arctool" -> ARC;
                case "polygon", "多边形", "polygontool" -> POLYGON;
                case "polyline", "多段线", "polylinetool" -> POLYLINE;
                case "spline", "样条曲线", "splinetool" -> SPLINE;
                case "catenary", "悬链线", "catenarytool", "catenarylinetool" -> CATENARY;
                case "freedraw", "自由绘制", "freedrawtool" -> FREE_DRAW;
                case "eraser", "橡皮擦", "erasertool" -> ERASER;
                case "semicircle", "半圆", "semicircletool" -> SEMICIRCLE;
                case "star", "星形", "startool" -> STAR;
                case "spiral", "螺旋线", "spiraltool" -> SPIRAL;
                case "sine", "正弦曲线", "sinetool" -> SINE;
                case "move", "移动", "movetool" -> MOVE;
                case "rotate", "旋转", "rotatetool" -> ROTATE;
                case "mirror", "镜像", "mirrortool" -> MIRROR;
                case "scale", "缩放", "scaletool" -> SCALE;
                case "align", "对齐", "aligntool" -> ALIGN;
                case "array", "阵列", "arraytool" -> ARRAY;
                case "offset", "偏移", "offsettool" -> OFFSET;
                case "fillet", "圆角", "fillettool" -> FILLET;
                case "chamfer", "倒角", "chamfertool" -> CHAMFER;
                case "transform", "变换", "transformtool" -> TRANSFORM;
    
                case "trim", "修剪", "trimtool" -> TRIM;
                case "extend", "延伸", "extendtool" -> EXTEND;
                case "break", "打断", "breaktool" -> BREAK;
                case "text", "文本", "texttool" -> TEXT;
                case "annotation", "标注", "annotationtool" -> ANNOTATION;
    
                default -> {
                    MasterPlannerMod.LOGGER.warn("未知工具类型: '{}'", name);
                    yield UNKNOWN;
                }
            };
        }
    }

    /**
     * 高度计算器内部类
     */
    private static class HeightCalculator {
        private static float getSeparatorHeight() {
            return ImGui.getStyle().getItemSpacing().y + 2.0f;
        }

        private static float getToolInfoHeight() {
            return ImGui.getTextLineHeight() * 3 + ImGui.getStyle().getItemSpacing().y * 2;
        }
    }

    public ToolOptionsPanel() {
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        
        // 注册工具切换事件监听
        this.eventBus.subscribe(ToolChangedEvent.class, this);
        
        // 注册工具状态消息事件监听
        this.eventBus.subscribe(ToolStatusEvent.class, this);
    }

    /**
     * 渲染工具选项面板
     * 包括工具信息和特定工具的选项
     */
    @Override
    public void render() {
        BaseTool currentTool = appState.getCurrentTool();
        if (currentTool == null) return;

        MasterPlannerMod.LOGGER.debug("当前选中工具: {} (ID: {})", 
            currentTool.getName(), currentTool.getId());

        // 设置面板样式
        // 注意：beginChild 的边框应紧贴窗口边缘，内容边距由窗口级 WindowPadding 控制
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);  // 边框无内边距，与标题边距一致
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);

        // 使用正确的 ImGuiWindowFlags 枚举值
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        // 获取可用区域
        float availableWidth = ImGui.getContentRegionAvailX();
        
        // 预先计算内容高度
        float infoHeight = HeightCalculator.getToolInfoHeight();
        float separatorHeight = HeightCalculator.getSeparatorHeight();
        float optionsHeight = calculateOptionsHeight(currentTool);
        
        // 为修改工具添加额外的高度缓冲
        ToolType toolType = ToolType.fromString(currentTool.getId());
        float extraHeight = getExtraHeightForTool(toolType);
        // 使用固定的边距值计算高度，因为边框无内边距
        float totalHeight = infoHeight + separatorHeight + optionsHeight + extraHeight + PANEL_PADDING * 2;
        
        MasterPlannerMod.LOGGER.debug("工具面板高度计算 - 信息: {}, 分隔线: {}, 选项: {}, 额外: {}, 总计: {}", 
            infoHeight, separatorHeight, optionsHeight, extraHeight, totalHeight);
        
        // 使用计算出的精确高度
        // 在 beginChild 内部设置内容边距，保持与标题边距一致
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PANEL_PADDING, PANEL_PADDING);
        if (ImGui.beginChild("##tool_panel", availableWidth, totalHeight, true, flags)) {
            // 工具信息部分
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
            renderToolInfo(currentTool);
            ImGui.popStyleVar();

            // 分隔线
            ImGui.separator();

            // 工具选项部分
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
            float actualOptionsHeight = renderToolOptions();
            ImGui.popStyleVar();
            
            // 记录实际渲染高度与预计算高度的差异，用于调试
            if (Math.abs(actualOptionsHeight - optionsHeight) > 1.0f) {
                MasterPlannerMod.LOGGER.debug("工具选项高度差异 - 预计算: {}, 实际: {}, 差异: {}", 
                    optionsHeight, actualOptionsHeight, actualOptionsHeight - optionsHeight);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(1);  // 弹出 beginChild 内部的 WindowPadding(4,4)

        ImGui.popStyleVar(2);  // 弹出外部的 WindowPadding(0,0) 和 FramePadding(4,4)
    }

    @Override
    public void init() {
        MasterPlannerMod.LOGGER.debug("ToolOptionsPanel初始化");
        
        // 初始化当前选中的工具选项
        BaseTool currentTool = appState.getCurrentTool();
        if (currentTool != null) {
            initializeToolOptions(currentTool);
        }
    }

    /**
     * 释放资源
     * 清理所有加载的纹理资源
     */
    @Override
    public void close() throws Exception {
        try {
            MasterPlannerMod.LOGGER.debug("正在关闭ToolOptionsPanel...");

            // 取消事件订阅
            eventBus.unsubscribe(ToolChangedEvent.class, this);
            eventBus.unsubscribe(ToolStatusEvent.class, this);

            // 清理所有渲染器资源
            ToolOptionRendererFactory.cleanup();

            MasterPlannerMod.LOGGER.debug("ToolOptionsPanel关闭完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ToolOptionsPanel关闭失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void renderToolInfo(BaseTool tool) {
        ImGui.text("当前工具: " + getToolDisplayName(tool));
        ImGui.textWrapped(getToolDescription(tool));
        
        // 显示当前工具的使用方法提示
        if (!currentToolStatusMessage.isEmpty()) {
            ImGui.separator();
            ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, "使用方法:");
            ImGui.textWrapped(currentToolStatusMessage);
        }
    }

    private String getToolDisplayName(BaseTool tool) {
        return ToolType.fromString(tool.getId()).displayName;
    }

    private String getToolDescription(BaseTool tool) {
        return switch (ToolType.fromString(tool.getId())) {
            case SELECT -> "使用选择工具可以选择、移动和编辑图形。按住Shift键可以多选，按住Ctrl键可以取消选择。";
            case LINE -> "直线工具用于绘制直线。单击确定起点，再次单击确定终点。按住Shift键可以绘制水平、垂直或45度角的线条。";
            case CIRCLE -> "圆形工具用于绘制圆形。可以通过圆心和半径、两点或三点来定义圆。";
            case RECTANGLE -> "矩形工具用于绘制矩形。可以通过两点、三点或中心点来定义矩形。按住Shift键可以绘制正方形。";
            case ELLIPSE -> "椭圆工具用于绘制椭圆。可以通过三点轴、三点中心或两点来定义椭圆。";
            case ARC -> "圆弧工具用于绘制圆弧。可以通过三点或起点-终点-半径来定义圆弧。";
            case POLYGON -> "多边形工具用于绘制多边形。单击添加顶点，双击或按Enter键完成绘制。";
            case POLYLINE -> "多段线工具用于绘制由直线段组成的连续线条。单击添加顶点，双击或按Enter键完成绘制。";
            case SPLINE -> "样条曲线工具用于绘制光滑的曲线，通过控制点定义曲线形状。";
            case CATENARY -> "悬链线工具用于绘制悬链线形状，常用于表示自然悬垂的线条。";
            case FREE_DRAW -> "自由绘制工具允许您手绘任意形状和线条。";
            case ERASER -> "橡皮擦工具用于删除图形。点击图形可以删除，按住拖动可以删除路径上的所有图形。";
            case SEMICIRCLE -> "半圆工具用于绘制半圆。可以通过两点或三点来定义半圆。";
            case STAR -> "星形工具用于绘制星形。可以通过顶点数量和圆角半径来定义星形。";
            case SPIRAL -> "螺旋线工具用于绘制螺旋线。支持线性、衰减、半圆、反半圆、斐波那契和多边形等多种螺旋类型。";
            case SINE -> "正弦曲线工具用于绘制正弦波形。可调整波长、振幅和相位。";
            case MOVE -> "移动工具用于移动图形。点击并拖动图形可以移动它。";
            case ROTATE -> "旋转工具用于旋转图形。点击并拖动图形可以旋转它。";
            case MIRROR -> "镜像工具用于镜像图形。点击并拖动图形可以镜像它。";
            case SCALE -> "缩放工具用于缩放图形。点击并拖动图形可以缩放它。";
            case ALIGN -> "对齐工具用于对齐图形。支持左对齐、右对齐、中心对齐、顶部对齐、底部对齐、中间对齐以及水平分布和垂直分布。";
            case ARRAY -> "阵列工具用于创建图形的阵列复制。支持矩形阵列、环形阵列和路径阵列三种模式。";
            case OFFSET -> "偏移工具用于偏移图形。点击并拖动图形可以偏移它。";
            case FILLET -> "倒角工具用于在两条直线之间创建圆角倒角。支持半径设置和实时预览。";
            case CHAMFER -> "倒角工具用于在两条直线之间创建斜面倒角。支持距离设置和实时预览。";
            case TRANSFORM -> "变换工具用于对选中的图形进行专业的变换操作。支持缩放、旋转、中心缩放和数值输入等功能。";

            case TRIM -> "修剪工具用于修剪图形。点击并拖动图形可以修剪它。";
            case EXTEND -> "延伸工具用于延伸图形。点击并拖动图形可以延伸它。";
            case BREAK -> "打断工具用于打断图形。支持单点打断和两点打断两种模式，可以连续操作多个图形。";
            case TEXT -> "文本工具用于在图纸上添加文本。点击并拖动可以放置文本框，双击可以编辑文本。";
            case ANNOTATION -> "标注工具用于在图纸上添加标注信息。支持距离标注、角度标注、半径标注和面积标注四种模式。";

            case UNKNOWN -> "未知工具类型，请检查工具配置。";
        };
    }

    private float renderToolOptions() {
        float height = 0;
        float availableWidth = ImGui.getContentRegionAvail().x;
        float availableHeight = ImGui.getContentRegionAvail().y;
        float labelColumnWidth = Math.min(LABEL_WIDTH, availableWidth * 0.3f);
        
        MasterPlannerMod.LOGGER.debug("渲染工具选项 - 可用区域: {}x{}, 标签列宽度: {}", 
            availableWidth, availableHeight, labelColumnWidth);
        
        BaseTool currentTool = appState.getCurrentTool();
        if (currentTool == null) {
            MasterPlannerMod.LOGGER.warn("当前没有选中的工具");
            return 0;
        }
        
        int tableFlags = ImGuiTableFlags.None;
        boolean tableCreated = ImGui.beginTable("tool_options_table", 2, tableFlags);
        MasterPlannerMod.LOGGER.debug("工具选项表格创建状态: {}", tableCreated);
        
        if (tableCreated) {
            try {
                ImGui.tableSetupColumn("Label", ImGuiTableColumnFlags.WidthFixed, labelColumnWidth);
                ImGui.tableSetupColumn("Control", ImGuiTableColumnFlags.WidthStretch);
                
                // 使用工具选项渲染器
                ToolOptionRenderer renderer = ToolOptionRendererFactory.getRenderer(currentTool);
                if (renderer != null) {
                    MasterPlannerMod.LOGGER.debug("使用渲染器: {}", renderer.getClass().getSimpleName());
                    height = renderer.render();
                    MasterPlannerMod.LOGGER.debug("渲染器返回高度: {}", height);
                } else {
                    MasterPlannerMod.LOGGER.warn("未找到工具 {} 的选项渲染器", currentTool.getId());
                }
            } finally {
                // 确保在表格创建成功的情况下，无论渲染过程中是否发生异常，都会调用endTable
                ImGui.endTable();
            }
        } else {
            MasterPlannerMod.LOGGER.error("创建工具选项表格失败，可能的原因：可用区域不足");
        }
        
        return height;
    }
    
    /**
     * 初始化工具选项，当工具切换时调用
     * @param tool 当前选中的工具
     */
    private void initializeToolOptions(BaseTool tool) {
        if (tool == null) return;
        
        String toolId = tool.getId();
        MasterPlannerMod.LOGGER.debug("初始化工具选项: {}", toolId);
        
        // 使用工具选项渲染器初始化
        ToolOptionRenderer renderer = ToolOptionRendererFactory.getRenderer(tool);
        if (renderer != null) {
            renderer.initialize();
        }
    }
    
    /**
     * 处理通用事件
     * @param event 事件对象
     */
    @Override
    public void onEvent(Event event) {
        if (event instanceof ToolChangedEvent) {
            onEvent((ToolChangedEvent) event);
        } else if (event instanceof ToolStatusEvent) {
            onEvent((ToolStatusEvent) event);
        }
    }

    /**
     * 处理工具切换事件
     * @param event 工具切换事件
     */
    public void onEvent(ToolChangedEvent event) {
        try {
            MasterPlannerMod.LOGGER.debug("ToolOptionsPanel: 收到工具切换事件: {}", event.getToolName());
            
            // 获取当前工具并初始化选项
            BaseTool currentTool = appState.getCurrentTool();
            if (currentTool != null) {
                initializeToolOptions(currentTool);
            }
            
            // 清空状态消息
            currentToolStatusMessage = "";
            
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("处理工具切换事件时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理工具状态消息事件
     * @param event 工具状态消息事件
     */
    public void onEvent(ToolStatusEvent event) {
        try {
            // 检查是否是当前工具的状态消息
            BaseTool currentTool = appState.getCurrentTool();
            if (currentTool != null && currentTool.getId().equals(event.getToolId())) {
                currentToolStatusMessage = event.getMessage();
                MasterPlannerMod.LOGGER.debug("ToolOptionsPanel: 更新工具状态消息: {}", currentToolStatusMessage);
            }
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("处理工具状态消息事件时发生错误: {}", e.getMessage(), e);
        }
    }

    private float calculateOptionsHeight(BaseTool tool) {
        if (tool == null) return 0;
        
        ToolType toolType = ToolType.fromString(tool.getId());
        float baseHeight = getBaseOptionsHeight(toolType);
        
        // 根据工具类型调整高度
        return switch (toolType) {
            case SELECT -> baseHeight * 0.5f;  // 选择工具选项较少
            case LINE, CIRCLE, RECTANGLE -> baseHeight * 0.8f;  // 基础绘图工具
            case ELLIPSE, ARC, POLYGON -> baseHeight * 1.2f;  // 复杂绘图工具
            case POLYLINE -> baseHeight * 1.3f;  // 多段线工具
            case SPLINE, CATENARY -> baseHeight * 1.5f;  // 高级绘图工具
            case FREE_DRAW -> baseHeight * 0.6f;  // 自由绘制工具
            case ERASER -> baseHeight * 0.3f;  // 橡皮擦工具
            case SEMICIRCLE, STAR, SPIRAL, SINE -> baseHeight;  // 特殊绘图工具
            case MOVE -> baseHeight * 0.8f;  // 移动工具
            case ROTATE -> baseHeight * 1.2f;  // 旋转工具（有角度设置）
            case MIRROR -> baseHeight * 1.4f;  // 镜像工具（有模式选择和约束）
            case SCALE -> baseHeight * 1.5f;  // 缩放工具（有模式、中心、约束设置）
            case ALIGN -> baseHeight * 1.3f;  // 对齐工具（有模式选择和参考设置）
            case ARRAY -> baseHeight * 1.4f;  // 阵列工具（有模式选择和参数设置）
            case OFFSET -> baseHeight * 1.1f;  // 偏移工具
            case FILLET -> baseHeight * 1.2f;  // 倒角工具（有模式选择和距离设置）
            case CHAMFER -> baseHeight * 1.2f;  // 倒角工具（有距离设置和预览选项）
            case TRANSFORM -> baseHeight * 1.6f;  // 变换工具（有模式选择、功能开关和状态显示）

            case TRIM -> baseHeight;  // 修剪工具
            case EXTEND -> baseHeight * 1.1f;  // 延伸工具
            case BREAK -> baseHeight * 1.2f;  // 打断工具
            case TEXT -> baseHeight;  // 文本工具
            case ANNOTATION -> baseHeight * 0.8f;  // 标注工具

            case UNKNOWN -> baseHeight * 0.5f;  // 未知工具
        };
    }

    private float getBaseOptionsHeight(ToolType toolType) {
        // 基础高度计算
        float lineHeight = ImGui.getTextLineHeight();
        float framePadding = ImGui.getStyle().getFramePadding().y;
        float itemSpacing = ImGui.getStyle().getItemSpacing().y;
        
        // 根据工具类型返回不同的基础高度
        return switch (toolType) {
            case SELECT -> lineHeight * 10 + framePadding * 2 + itemSpacing;
            case LINE, CIRCLE, RECTANGLE -> lineHeight * 10 + framePadding * 4 + itemSpacing * 3;
            case ELLIPSE, ARC, POLYGON -> lineHeight * 10 + framePadding * 6 + itemSpacing * 5;
            case POLYLINE -> lineHeight * 10 + framePadding * 7 + itemSpacing * 6;
            case SPLINE, CATENARY -> lineHeight * 8 + framePadding * 8 + itemSpacing * 7;
            case FREE_DRAW -> lineHeight * 10 + framePadding * 3 + itemSpacing * 2;
            case ERASER -> lineHeight * 5 + framePadding * 1;
            case SEMICIRCLE, STAR, SPIRAL, SINE -> lineHeight * 15 + framePadding * 5 + itemSpacing * 4;
            case MOVE -> lineHeight * 10 + framePadding * 4 + itemSpacing * 3;  // 移动工具有详细说明
            case ROTATE -> lineHeight * 6 + framePadding * 6 + itemSpacing * 5;  // 旋转工具有角度设置
            case MIRROR -> lineHeight * 8 + framePadding * 8 + itemSpacing * 7;  // 镜像工具有模式选择和约束
            case SCALE -> lineHeight * 10 + framePadding * 9 + itemSpacing * 8;  // 缩放工具有模式、中心、约束
            case ALIGN -> lineHeight * 10 + framePadding * 7 + itemSpacing * 6;  // 对齐工具有模式选择和参考设置
            case ARRAY -> lineHeight * 8 + framePadding * 8 + itemSpacing * 7;  // 阵列工具有模式选择和参数设置
            case OFFSET -> lineHeight * 10 + framePadding * 5 + itemSpacing * 4;  // 偏移工具
            case FILLET -> lineHeight * 10 + framePadding * 6 + itemSpacing * 5;  // 倒角工具有模式选择和距离设置
            case CHAMFER -> lineHeight * 10 + framePadding * 6 + itemSpacing * 5;  // 倒角工具有距离设置和预览选项
            case TRANSFORM -> lineHeight * 15 + framePadding * 10 + itemSpacing * 9;  // 变换工具有模式选择、功能开关和状态显示

            case TRIM -> lineHeight * 10 + framePadding * 4 + itemSpacing * 3;  // 修剪工具
            case EXTEND -> lineHeight * 10 + framePadding * 5 + itemSpacing * 4;  // 延伸工具
            case BREAK -> lineHeight * 8 + framePadding * 6 + itemSpacing * 5;  // 打断工具
            case TEXT -> lineHeight * 20 + framePadding * 3 + itemSpacing * 2;  // 文本工具
            case ANNOTATION -> lineHeight * 8 + framePadding * 4 + itemSpacing * 3;  // 标注工具
            case UNKNOWN -> lineHeight * 5 + framePadding * 2 + itemSpacing;  // 未知工具

        };
    }
    
    /**
     * 为特定工具类型获取额外的高度缓冲
     */
    private float getExtraHeightForTool(ToolType toolType) {
        float lineHeight = ImGui.getTextLineHeight();
        float itemSpacing = ImGui.getStyle().getItemSpacing().y;
        
        return switch (toolType) {
            case MIRROR -> lineHeight * 4 + itemSpacing * 3;  // 镜像工具有模式选择和详细说明
            case SCALE -> lineHeight * 5 + itemSpacing * 4;   // 缩放工具有多种设置选项
            case ROTATE -> lineHeight * 3 + itemSpacing * 2;  // 旋转工具有角度设置
            case MOVE -> lineHeight * 2 + itemSpacing;        // 移动工具有详细说明
            case ALIGN -> lineHeight * 3 + itemSpacing * 2;   // 对齐工具有模式选择和参考设置
            case OFFSET, EXTEND -> lineHeight * 2 + itemSpacing; // 偏移和延伸工具
            case TRANSFORM -> lineHeight * 4 + itemSpacing * 3;  // 变换工具有多种功能开关和状态显示
            case TRIM -> lineHeight * 1 + itemSpacing;  // 修剪工具
            case BREAK -> lineHeight * 2 + itemSpacing;  // 打断工具
            case TEXT -> lineHeight * 2 + itemSpacing;  // 文本工具
            case ANNOTATION -> lineHeight * 1 + itemSpacing;  // 标注工具

            default -> 0;  // 其他工具不需要额外高度
        };
    }
}