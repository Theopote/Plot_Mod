package com.masterplanner.core.tool;

import com.masterplanner.api.tool.ITool;
import com.masterplanner.api.tool.ToolGroup;
import com.masterplanner.api.tool.ToolState;
import com.masterplanner.core.log.LogManager;

import java.util.*;

/**
 * 工具组实现类
 */
public class
ToolGroupImpl extends ToolGroup {
    private final List<ITool> tools;
    private final Map<String, ITool> toolMap;
    private boolean exclusive;
    private ITool activeTool;

    public ToolGroupImpl(String name) {
        this(name, true);
    }

    public ToolGroupImpl(String name, boolean exclusive) {
        super(UUID.randomUUID().toString(), name);
        this.tools = new ArrayList<>();
        this.toolMap = new HashMap<>();
        this.exclusive = exclusive;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void addTool(ITool tool) {
        if (tool != null && !toolMap.containsKey(tool.getId())) {
            tools.add(tool);
            toolMap.put(tool.getId(), tool);
            tool.setGroup(this);
            
            // 按优先级排序
            tools.sort((t1, t2) -> 
                Integer.compare(t2.getPriority(), t1.getPriority()));
            
            LogManager.getInstance().debug("Added tool " + tool.getName() + " to group " + name);
        }
    }

    @Override
    public void removeTool(ITool tool) {
        if (tool != null) {
            tools.remove(tool);
            toolMap.remove(tool.getId());
            tool.setGroup(null);
            
            if (tool == activeTool) {
                activeTool = null;
            }
            
            LogManager.getInstance().debug("Removed tool " + tool.getName() + " from group " + name);
        }
    }

    @Override
    public void removeTool(String toolId) {
        ITool tool = toolMap.get(toolId);
        if (tool != null) {
            removeTool(tool);
        }
    }

    @Override
    public ITool getTool(String toolId) {
        return toolMap.get(toolId);
    }

    @Override
    public List<ITool> getTools() {
        return Collections.unmodifiableList(tools);
    }

    @Override
    public boolean isExclusive() {
        return exclusive;
    }

    @Override
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    @Override
    public ITool getActiveTool() {
        return activeTool;
    }

    @Override
    public void setActiveTool(ITool tool) {
        if (tool != null && !toolMap.containsKey(tool.getId())) {
            throw new IllegalArgumentException("Tool does not belong to this group: " + tool.getName());
        }

        if (tool == activeTool) {
            return;
        }

        // 如果是互斥组，停用当前活动工具
        if (exclusive && activeTool != null) {
            activeTool.setState(ToolState.INACTIVE);
        }

        // 激活新工具
        activeTool = tool;
        if (tool != null) {
            tool.setState(ToolState.ACTIVE);
        }

        LogManager.getInstance().debug("Active tool changed to " + 
            (tool != null ? tool.getName() : "null") + " in group " + name);
    }

    @Override
    public void clearActiveTool() {
        setActiveTool(null);
    }

    @Override
    public void enableAllTools() {
        for (ITool tool : tools) {
            tool.setEnabled(true);
        }
    }

    @Override
    public void disableAllTools() {
        for (ITool tool : tools) {
            tool.setEnabled(false);
        }
        clearActiveTool();
    }

    @Override
    public void resetAllTools() {
        for (ITool tool : tools) {
            tool.reset();
        }
    }

    @Override
    public String toString() {
        return String.format("ToolGroup[name=%s, tools=%d, exclusive=%s]", 
            name, tools.size(), exclusive);
    }
}
