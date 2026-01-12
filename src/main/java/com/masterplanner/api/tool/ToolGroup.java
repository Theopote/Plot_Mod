package com.masterplanner.api.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具组抽象类
 */
public abstract class ToolGroup {
    protected final String id;
    protected final String name;
    
    protected ToolGroup(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public abstract String getId();
    public abstract String getName();
    public abstract void addTool(ITool tool);
    public abstract void removeTool(ITool tool);
    public abstract void removeTool(String toolId);
    public abstract ITool getTool(String toolId);
    public abstract List<ITool> getTools();
    public abstract boolean isExclusive();
    public abstract void setExclusive(boolean exclusive);
    public abstract ITool getActiveTool();
    public abstract void setActiveTool(ITool tool);
    public abstract void clearActiveTool();
    public abstract void enableAllTools();
    public abstract void disableAllTools();
    public abstract void resetAllTools();
}
