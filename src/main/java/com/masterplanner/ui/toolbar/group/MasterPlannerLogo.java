package com.masterplanner.ui.toolbar.group;

import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * MasterPlanner Logo组件
 * 渲染工具栏左侧的Logo
 */
public class MasterPlannerLogo extends AbstractToolbarGroup {
    
    public MasterPlannerLogo() {
        super("Logo");
    }
    
    @Override
    protected void renderGroupContent() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();

        // logo 宽度应该是两倍按钮高度加按钮之间的间隙
        float logoWidth = UILayout.Toolbar.BUTTON_SIZE * 2 + UILayout.Toolbar.ITEM_SPACING;
        // 高度应该和工具按钮一致
        float logoHeight = UILayout.Toolbar.BUTTON_SIZE;

        // 设置提示文字背景颜色
        ImGui.pushStyleColor(ImGuiCol.PopupBg, currentTheme.panelBackground);

        try {
            // logo 不需要边框
            boolean clicked = UIUtils.imageButton(ControlPanelIcons.getIdentifier(ControlPanelIcons.LOGO),
                    "MasterPlanner", logoWidth, logoHeight, false, false);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("MasterPlanner 设置与帮助");
            }
            if (clicked) {
                com.masterplanner.ui.dialog.SettingsAndHelpDialog.getInstance().open();
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering MasterPlanner logo", e);
        } finally {
            ImGui.popStyleColor();
        }
    }
    
    @Override
    public float getGroupWidth() {
        return UILayout.Toolbar.BUTTON_SIZE * 2 + UILayout.Toolbar.ITEM_SPACING;
    }
    
    @Override
    public boolean needsSeparator() {
        return super.needsSeparator();
    }
}