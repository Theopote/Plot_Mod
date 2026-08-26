package com.plot.core.state;

import com.plot.core.tool.BaseTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 当前激活工具状态（与 api.tool.ToolState 生命周期枚举区分）。
 */
public final class ActiveToolState {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ActiveToolState");

    private BaseTool currentTool;

    public void setCurrentTool(BaseTool tool) {
        if (this.currentTool == tool) {
            return;
        }

        BaseTool previousTool = this.currentTool;
        if (previousTool != null) {
            try {
                LOGGER.debug("停用工具: {}", previousTool.getName());
                previousTool.deactivate();
            } catch (Exception e) {
                LOGGER.error("停用工具时发生错误: {}", e.getMessage(), e);
            }
        }

        this.currentTool = tool;

        if (tool != null) {
            try {
                LOGGER.debug("激活工具: {}", tool.getName());
                tool.activate();
            } catch (Exception e) {
                LOGGER.error("激活工具时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    public BaseTool getCurrentTool() {
        return currentTool;
    }
}
