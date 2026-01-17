package com.masterplanner.ui.toolbar.group;

import com.masterplanner.ui.component.ControlPanelIcons;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;
import com.masterplanner.ui.layout.UILayout;

/**
 * MasterPlanner Logo组件
 * 渲染工具栏左侧的Logo
 * 现在使用标准按钮大小和样式，与其他按钮一致
 */
public class MasterPlannerLogo extends AbstractToolbarGroup {
    
    public MasterPlannerLogo() {
        super("Logo");
    }
    
    @Override
    protected void renderGroupContent() {
        // 使用标准按钮样式和大小
        pushButtonStyles();
        
        try {
            // 使用标准的工具栏按钮渲染方法，大小和样式与其他按钮一致
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(ControlPanelIcons.LOGO),
                    "MasterPlanner 设置与帮助")) {
                com.masterplanner.ui.dialog.SettingsAndHelpDialog.getInstance().open();
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering MasterPlanner logo", e);
        } finally {
            popButtonStyles();
        }
    }
    
    @Override
    public float getGroupWidth() {
        // 返回标准按钮宽度，与其他按钮一致
        return UILayout.Toolbar.BUTTON_SIZE;
    }
    
    @Override
    public boolean needsSeparator() {
        // 不再需要分隔符，按钮按顺序排列
        return false;
    }
}