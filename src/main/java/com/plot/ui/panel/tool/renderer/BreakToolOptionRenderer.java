package com.plot.ui.panel.tool.renderer;

import com.plot.utils.PlotI18n;
import com.plot.ui.tools.impl.modify.BreakTool;
import com.plot.ui.tools.impl.modify.strategy.BreakStrategy;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打断工具属性面板渲染器
 * 
 * <p>提供打断工具的配置选项，采用单向数据流设计：</p>
 * <ul>
 *   <li>无状态设计：UI不维护内部状态，直接从工具获取最新状态</li>
 *   <li>单一事实来源：所有状态都存储在BreakTool中</li>
 *   <li>强封装：UI不直接访问策略对象，只通过BreakTool的公共API</li>
 *   <li>仅提供两种模式：单点打断、两点打断</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 单向数据流版本
 */
public class BreakToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_MODE = "mode";
    
    
    // 打断模式常量
    private static final String BREAK_MODE_SINGLE = "single";
    private static final String BREAK_MODE_TWO_POINT = "two_point";
    
    // 图标ID
    private final int singleBreakIconId;
    private final int twoPointBreakIconId;

    // 本地模式状态（对齐圆形/填充工具，点击后立刻更新本地以获得即时选中高亮）
    private String currentMode = BREAK_MODE_SINGLE;
    
    public BreakToolOptionRenderer() {
        super("break");
        
        // 加载图标
        this.singleBreakIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/break_single.png"));
        this.twoPointBreakIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/break_two_point.png"));

        // 添加图标加载调试信息
        LOGGER.debug("打断工具图标加载完成 - 单点: {}, 两点: {}", 
            singleBreakIconId, twoPointBreakIconId);
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("break_options");
        
        try {
            // 从ToolOptionsPanel传入的工具参数获取BreakTool
            BreakTool currentTool = getCurrentToolFromContext();
            if (currentTool == null) {
                LOGGER.debug("当前工具不是BreakTool，跳过渲染");
                return 0;
            }

            // 修复：每次渲染从工具同步模式，确保与键盘切换一致
            String modeFromTool = (currentTool.getCurrentBreakMode() == BreakStrategy.BreakMode.TWO_POINT)
                ? BREAK_MODE_TWO_POINT : BREAK_MODE_SINGLE;
            if (!currentMode.equals(modeFromTool)) {
                currentMode = modeFromTool;
                LOGGER.debug("从工具同步模式: {}", currentMode);
            }

            LOGGER.debug("开始渲染打断工具选项面板，当前工具: {}", currentTool.getClass().getSimpleName());
            LOGGER.debug("可用区域: {}x{}", ImGui.getContentRegionAvail().x, ImGui.getContentRegionAvail().y);

            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // === 打断模式选择 ===
            height += renderBreakModeSelection(currentTheme);
            LOGGER.debug("打断模式选择渲染完成，高度: {}", height);
            
        } catch (Exception e) {
            LOGGER.error("渲染打断工具选项面板时发生错误: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染打断模式选择
     */
    private float renderBreakModeSelection(UITheme.ThemeColors currentTheme) {
        float height = 0;

        // 与填充工具保持一致的表格布局与样式
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr("option.plot.break_mode"));

        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);

        ImGui.tableNextColumn();
        float firstButtonX = ImGui.getCursorPosX();

        // 单点打断模式
        boolean isSingleMode = BREAK_MODE_SINGLE.equals(currentMode);
        int pushedColorCountSingle = 0;
        if (isSingleMode) {
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected); pushedColorCountSingle++;
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered); pushedColorCountSingle++;
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive); pushedColorCountSingle++;
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder); pushedColorCountSingle++;
        }
        ImGui.pushID("break_mode_single");
        if (com.plot.ui.component.UIUtils.imageButtonNoPadding(singleBreakIconId, BUTTON_SIZE, BUTTON_SIZE)) {
            // 修复：移除条件判断，允许重复点击当前模式
            currentMode = BREAK_MODE_SINGLE; // 先更新本地，立即反映选中样式
            updateToolConfig(CONFIG_KEY_MODE, BREAK_MODE_SINGLE);
            LOGGER.debug("切换到单点打断模式 - 发送配置更新: key={}, value={}", CONFIG_KEY_MODE, BREAK_MODE_SINGLE);
        }
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("单点打断：在任何图形的轮廓上点击，图形会从点击位置一分为二");
        }
        if (pushedColorCountSingle > 0) { ImGui.popStyleColor(pushedColorCountSingle); }

        ImGui.sameLine(0, BUTTON_SPACING);
        ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING));

        // 两点打断模式
        boolean isTwoPointMode = BREAK_MODE_TWO_POINT.equals(currentMode);
        int pushedColorCountTwo = 0;
        if (isTwoPointMode) {
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected); pushedColorCountTwo++;
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered); pushedColorCountTwo++;
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive); pushedColorCountTwo++;
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder); pushedColorCountTwo++;
        }
        ImGui.pushID("break_mode_two_point");
        if (com.plot.ui.component.UIUtils.imageButtonNoPadding(twoPointBreakIconId, BUTTON_SIZE, BUTTON_SIZE)) {
            // 修复：移除条件判断，允许重复点击当前模式
            currentMode = BREAK_MODE_TWO_POINT; // 先更新本地，立即反映选中样式
            updateToolConfig(CONFIG_KEY_MODE, BREAK_MODE_TWO_POINT);
            LOGGER.debug("切换到两点打断模式 - 发送配置更新: key={}, value={}", CONFIG_KEY_MODE, BREAK_MODE_TWO_POINT);
        }
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("两点打断：在同一条线上点击两点，删除两点之间的图形部分");
        }
        if (pushedColorCountTwo > 0) { ImGui.popStyleColor(pushedColorCountTwo); }

        // 恢复样式（FrameBorderSize, FrameRounding）
        ImGui.popStyleVar();
        ImGui.popStyleVar();
        height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
        
        return height;
    }

    /**
     * 从上下文中获取当前的BreakTool
     */
    private BreakTool getCurrentToolFromContext() {
        try {
            // 从AppState获取当前工具
            com.plot.core.state.AppState appState = com.plot.core.state.AppState.getInstance();
            com.plot.core.tool.BaseTool currentTool = appState.getCurrentTool();
            
            if (currentTool instanceof BreakTool) {
                return (BreakTool) currentTool;
            } else {
                LOGGER.debug("当前工具不是BreakTool: {}", 
                    currentTool != null ? currentTool.getClass().getSimpleName() : "null");
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("获取当前工具失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void initialize() {
        LOGGER.debug("初始化打断工具选项渲染器");
        // 修改：同步模式，但不强制更新配置（避免覆盖）
        BreakTool tool = getCurrentToolFromContext();
        if (tool != null) {
            String modeFromTool = (tool.getCurrentBreakMode() == BreakStrategy.BreakMode.TWO_POINT)
                ? BREAK_MODE_TWO_POINT : BREAK_MODE_SINGLE;
            if (!currentMode.equals(modeFromTool)) {
                currentMode = modeFromTool;
                LOGGER.debug("初始化同步模式: {}", currentMode);
            }
        } else {
            currentMode = BREAK_MODE_SINGLE;
        }
    }
    
    @Override
    public void cleanup() {
        LOGGER.debug("清理打断工具选项渲染器资源");
        // 清理图标资源等
    }
} 