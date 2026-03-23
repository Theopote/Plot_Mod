package com.plot.ui.panel.tool;

import com.plot.PlotMod;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.tool.ToolChangedEvent;
import com.plot.infrastructure.event.tool.ToolStatusEvent;
import com.plot.ui.component.UIComponent;
import com.plot.ui.panel.tool.renderer.ToolOptionRenderer;
import com.plot.ui.panel.tool.renderer.ToolOptionRendererFactory;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * 工具选项面板，负责渲染当前选中工具的所有选项和配置界面
 * 包括工具信息显示和各种工具特定的选项控件
 */
public class ToolOptionsPanel implements UIComponent, AutoCloseable, EventListener {
    // 界面布局常量
    private static final float LABEL_WIDTH = 60.0f;         // 标签文本宽度
    private static final float PANEL_PADDING = 4.0f;        // 面板内边距
    private static final float MIN_PANEL_HEIGHT = 1.0f;     // 面板最小高度

    // 核心组件引用
    private final AppState appState;    // 应用状态管理器
    private final EventBus eventBus;    // 事件总线

    // 状态消息管理
    private String currentToolStatusMessage = "";  // 当前工具的状态消息
    private final Map<ToolType, Float> toolPanelHeightCache = new EnumMap<>(ToolType.class);

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
            PlotMod.LOGGER.debug("尝试匹配工具类型: '{}' (normalized: '{}')", name, normalizedName);
            
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
                    PlotMod.LOGGER.warn("未知工具类型: '{}'", name);
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
        var theme = ThemeManager.getInstance().getCurrentTheme();

        PlotMod.LOGGER.debug("当前选中工具: {} (ID: {})", 
            currentTool.getName(), currentTool.getId());

        // 设置面板样式
        // 注意：beginChild 的边框应紧贴窗口边缘，内容边距由窗口级 WindowPadding 控制
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);  // 边框无内边距，与标题边距一致
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.tooltipBackground);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.tooltipText);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);

        // 允许滚动：当内容高度超过可用高度时可使用滚轮浏览
        int flags = ImGuiWindowFlags.None;

        // 获取可用区域
        float availableWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY();
        
        // 预先计算内容高度（首次渲染使用估算值，后续使用实际测量值）
        float infoHeight = HeightCalculator.getToolInfoHeight();
        float separatorHeight = HeightCalculator.getSeparatorHeight();
        float optionsHeight = calculateOptionsHeight(currentTool);
        
        // 为修改工具添加额外的高度缓冲
        ToolType toolType = ToolType.fromString(currentTool.getId());
        float extraHeight = getExtraHeightForTool(toolType);
        // 使用固定的边距值计算目标高度，因为边框无内边距
        float estimatedHeight = infoHeight + separatorHeight + optionsHeight + extraHeight + PANEL_PADDING * 2;
        float desiredHeight = toolPanelHeightCache.getOrDefault(toolType, estimatedHeight);
        float totalHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(desiredHeight, availableHeight));
        
        PlotMod.LOGGER.debug("工具面板高度计算 - 信息: {}, 分隔线: {}, 选项: {}, 额外: {}, 目标: {}, 实际: {}", 
            infoHeight, separatorHeight, optionsHeight, extraHeight, desiredHeight, totalHeight);
        
        // 使用计算出的精确高度
        // 在 beginChild 内部设置内容边距，保持与标题边距一致
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PANEL_PADDING, PANEL_PADDING);
        if (ImGui.beginChild("##tool_panel", availableWidth, totalHeight, true, flags)) {
            float contentStartY = ImGui.getCursorPosY();

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

            // 将“使用方法”统一放在控件区域下方
            ImGui.separator();
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
            renderToolUsage(currentTool);
            ImGui.popStyleVar();
            
            // 记录实际渲染高度与预计算高度的差异，用于调试
            if (Math.abs(actualOptionsHeight - optionsHeight) > 1.0f) {
                PlotMod.LOGGER.debug("工具选项高度差异 - 预计算: {}, 实际: {}, 差异: {}", 
                    optionsHeight, actualOptionsHeight, actualOptionsHeight - optionsHeight);
            }

            float contentEndY = ImGui.getCursorPosY();
            float measuredContentHeight = Math.max(
                MIN_PANEL_HEIGHT,
                (contentEndY - contentStartY) + PANEL_PADDING * 2
            );
            // 每帧按真实内容高度回写，确保所有工具都严格自适应
            toolPanelHeightCache.put(toolType, measuredContentHeight);
        }
        ImGui.endChild();
        ImGui.popStyleVar(1);  // 弹出 beginChild 内部的 WindowPadding(4,4)

        ImGui.popStyleColor(3);
        ImGui.popStyleVar(2);  // 弹出外部的 WindowPadding(0,0) 和 FramePadding(4,4)
    }

    @Override
    public void init() {
        PlotMod.LOGGER.debug("ToolOptionsPanel初始化");
        
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
            PlotMod.LOGGER.debug("正在关闭ToolOptionsPanel...");

            // 取消事件订阅
            eventBus.unsubscribe(ToolChangedEvent.class, this);
            eventBus.unsubscribe(ToolStatusEvent.class, this);

            // 清理所有渲染器资源
            ToolOptionRendererFactory.cleanup();

            PlotMod.LOGGER.debug("ToolOptionsPanel关闭完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("ToolOptionsPanel关闭失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void renderToolInfo(BaseTool tool) {
        ImGui.text("当前工具: " + getToolDisplayName(tool));
        ImGui.textWrapped(getToolDescription(tool));
    }

    private void renderToolUsage(BaseTool tool) {
        String usageHint = currentToolStatusMessage.isEmpty()
            ? getDefaultToolUsageHint(tool)
            : currentToolStatusMessage;

        // 显示当前工具的使用方法提示（实时消息优先，否则显示默认使用方法）
        if (!usageHint.isEmpty()) {
            ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, "使用方法:");
            ImGui.textWrapped(usageHint);
        }
    }

    private String getDefaultToolUsageHint(BaseTool tool) {
        if (tool == null) {
            return "";
        }

        return switch (ToolType.fromString(tool.getId())) {
            case SELECT -> "左键单击选择图形，拖拽可框选；按住Shift可多选，按住Ctrl可取消选中。";
            case LINE -> "左键依次指定起点和终点完成直线；按住Shift可约束到水平/垂直/45°。";
            case CIRCLE -> "左键按当前模式确定圆心与半径或关键点；右键可取消当前步骤。";
            case RECTANGLE -> "左键按当前模式依次指定角点或中心点；按住Shift可约束为正方形。";
            case ELLIPSE -> "左键按当前模式依次指定主轴与副轴（或中心与轴点）；右键可取消当前步骤。";
            case ARC -> "左键按当前模式依次指定圆弧关键点（如起点、终点、控制点/半径）完成绘制。";
            case POLYGON -> "左键连续添加顶点，双击或按Enter完成多边形；右键可撤销当前步骤。";
            case POLYLINE -> "左键连续添加节点形成多段线，双击或按Enter完成；右键可结束当前段。";
            case SPLINE -> "左键依次添加控制点，按Enter或右键结束输入并生成样条曲线。";
            case CATENARY -> "左键先指定两端点，再拖拽或输入参数调整垂度，确认后生成悬链线。";
            case FREE_DRAW -> "按住左键拖动进行自由绘制，松开鼠标结束当前笔画。";
            case ERASER -> "左键点击删除单个图形，按住左键拖动可连续擦除经过的图形。";
            case SEMICIRCLE -> "左键按当前模式指定关键点生成半圆；右键可取消当前输入。";
            case STAR -> "左键指定中心并拖拽确定外接半径，再确认角数/比例后完成星形。";
            case SPIRAL -> "左键指定起点或中心后拖拽确定尺寸，按当前螺旋模式完成绘制。";
            case SINE -> "左键指定基线范围，拖拽或在选项中调整振幅、波长和相位后完成。";
            case MOVE -> "先选择图形，左键依次指定基点与目标点完成移动；Shift可正交约束，Esc/右键可取消当前操作。";
            case ROTATE -> "先选择图形，左键指定旋转中心与参考方向，再确定目标角度完成旋转。";
            case MIRROR -> "先选择图形，左键指定镜像轴的两个点完成镜像；可在选项中设置保留原图。";
            case SCALE -> "先选择图形后进入缩放，按提示依次确定中心点与参考点完成缩放；Shift可保持比例，Esc可取消。";
            case ALIGN -> "先选择多个图形，再选择对齐/分布方式执行；常用模式可配合快捷键快速切换。";
            case ARRAY -> "先选择图形，再按阵列类型设置参数（矩形/环形/路径）并确认生成复制。";
            case OFFSET -> "左键选择对象并在目标侧指定偏移方向；可启用多重偏移进行连续偏移。";
            case FILLET -> "左键依次选择两条对象，按半径创建圆角过渡；右键确认，Esc可取消当前步骤。";
            case CHAMFER -> "左键依次选择两条对象，按设定距离创建倒角连接；右键确认，Esc可取消当前步骤。";
            case TRANSFORM -> "先选择图形，右键进入变换后拖拽控制点进行缩放/旋转；Shift可等比约束，Esc可返回。";
            case TRIM -> "先选择边界后执行修剪；C/F可切换修剪模式，Shift可连续修剪，Esc可取消。";
            case EXTEND -> "先选择边界，再点击要延伸的对象；工具自动选择合适延伸方式，Esc可取消。";
            case BREAK -> "选择对象后按模式指定一个或两个打断点完成打断；Esc可取消当前打断流程。";
            case TEXT -> "点击或拖拽放置文本对象并输入内容；可通过属性面板调整文本样式与输入方式。";
            case ANNOTATION -> "先选择标注类型，再按提示点选几何对象或关键点完成标注。";
            case UNKNOWN -> "按左键进行绘制或编辑，右键取消当前步骤。";
        };
    }

    private String getToolDisplayName(BaseTool tool) {
        return ToolType.fromString(tool.getId()).displayName;
    }

    private String getToolDescription(BaseTool tool) {
        return switch (ToolType.fromString(tool.getId())) {
            case SELECT -> "用于选择图形并进行后续编辑操作（如多选、移动、批量处理）。";
            case LINE -> "用于绘制直线，支持吸附与角度约束等基础绘图能力。";
            case CIRCLE -> "用于绘制圆形，支持多种常见圆定义方式。";
            case RECTANGLE -> "用于绘制矩形，支持常见矩形输入方式与正方形约束。";
            case ELLIPSE -> "用于绘制椭圆，支持不同椭圆构造模式。";
            case ARC -> "用于绘制圆弧，支持多种圆弧构造方式。";
            case POLYGON -> "用于绘制多边形，通过连续点位定义轮廓。";
            case POLYLINE -> "用于绘制多段线与路径，可按模式生成折线或曲线段。";
            case SPLINE -> "用于绘制样条曲线，支持拟合与控制点等曲线生成方式。";
            case CATENARY -> "用于绘制悬链线形状，适合表达下垂弧线结构。";
            case FREE_DRAW -> "用于自由手绘路径，快速记录不规则线条。";
            case ERASER -> "用于删除图形，支持单个删除与连续擦除。";
            case SEMICIRCLE -> "用于绘制半圆图形。";
            case STAR -> "用于绘制星形图案，并可调整星形参数。";
            case SPIRAL -> "用于绘制螺旋线，支持多种螺旋类型与参数。";
            case SINE -> "用于绘制正弦曲线，并可调整振幅、波长等参数。";
            case MOVE -> "用于移动已选图形到目标位置。";
            case ROTATE -> "用于旋转已选图形。";
            case MIRROR -> "用于对已选图形执行镜像操作。";
            case SCALE -> "用于按比例缩放已选图形。";
            case ALIGN -> "用于对多个图形执行对齐与分布。";
            case ARRAY -> "用于生成图形阵列复制（如矩形阵列、环形阵列、路径阵列）。";
            case OFFSET -> "用于对图形执行等距偏移。";
            case FILLET -> "用于在对象之间创建圆角过渡。";
            case CHAMFER -> "用于在对象之间创建倒角连接。";
            case TRANSFORM -> "用于对已选图形执行综合变换操作。";

            case TRIM -> "用于按边界修剪图形。";
            case EXTEND -> "用于将图形延伸到指定边界。";
            case BREAK -> "用于在指定点位打断图形。";
            case TEXT -> "用于创建与编辑文本对象。";
            case ANNOTATION -> "用于添加尺寸、角度、半径、面积等标注信息。";

            case UNKNOWN -> "未知工具类型，请检查工具配置。";
        };
    }

    private float renderToolOptions() {
        float height = 0;
        float availableWidth = ImGui.getContentRegionAvail().x;
        float availableHeight = ImGui.getContentRegionAvail().y;
        float labelColumnWidth = Math.min(LABEL_WIDTH, availableWidth * 0.3f);
        var theme = ThemeManager.getInstance().getCurrentTheme();
        
        PlotMod.LOGGER.debug("渲染工具选项 - 可用区域: {}x{}, 标签列宽度: {}", 
            availableWidth, availableHeight, labelColumnWidth);
        
        BaseTool currentTool = appState.getCurrentTool();
        if (currentTool == null) {
            PlotMod.LOGGER.warn("当前没有选中的工具");
            return 0;
        }
        
        int tableFlags = ImGuiTableFlags.None;
        boolean tableCreated = ImGui.beginTable("tool_options_table", 2, tableFlags);
        PlotMod.LOGGER.debug("工具选项表格创建状态: {}", tableCreated);
        
        if (tableCreated) {
            try {
                ImGui.tableSetupColumn("Label", ImGuiTableColumnFlags.WidthFixed, labelColumnWidth);
                ImGui.tableSetupColumn("Control", ImGuiTableColumnFlags.WidthStretch);

                ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
                ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
                ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
                ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
                ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
                ImGui.pushStyleColor(ImGuiCol.Border, theme.inputBorder);
                
                try {
                    // 使用工具选项渲染器
                    ToolOptionRenderer renderer = ToolOptionRendererFactory.getRenderer(currentTool);
                    if (renderer != null) {
                        PlotMod.LOGGER.debug("使用渲染器: {}", renderer.getClass().getSimpleName());
                        height = renderer.render();
                        PlotMod.LOGGER.debug("渲染器返回高度: {}", height);
                    } else {
                        PlotMod.LOGGER.warn("未找到工具 {} 的选项渲染器", currentTool.getId());
                    }
                } finally {
                    ImGui.popStyleColor(6);
                }
            } finally {
                // 确保在表格创建成功的情况下，无论渲染过程中是否发生异常，都会调用endTable
                ImGui.endTable();
            }
        } else {
            PlotMod.LOGGER.error("创建工具选项表格失败，可能的原因：可用区域不足");
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
        PlotMod.LOGGER.debug("初始化工具选项: {}", toolId);
        
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
            PlotMod.LOGGER.debug("ToolOptionsPanel: 收到工具切换事件: {}", event.getToolName());
            
            // 获取当前工具并初始化选项
            BaseTool currentTool = appState.getCurrentTool();
            if (currentTool != null) {
                initializeToolOptions(currentTool);
            }
            
            // 重置为当前工具默认使用方法（后续可被实时状态消息覆盖）
            currentToolStatusMessage = currentTool != null ? getDefaultToolUsageHint(currentTool) : "";
            
        } catch (Exception e) {
            PlotMod.LOGGER.error("处理工具切换事件时发生错误: {}", e.getMessage(), e);
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
                PlotMod.LOGGER.debug("ToolOptionsPanel: 更新工具状态消息: {}", currentToolStatusMessage);
            }
        } catch (Exception e) {
            PlotMod.LOGGER.error("处理工具状态消息事件时发生错误: {}", e.getMessage(), e);
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