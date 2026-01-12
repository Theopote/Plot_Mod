package com.masterplanner.infrastructure.event.command;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 命令执行事件
 */
public class CommandExecutedEvent extends Event {
    private final CommandType commandType;
    private final String source;
    
    /**
     * 命令类型枚举
     */
    public enum CommandType {
        UNDO,       // 撤销
        REDO,       // 重做
        EXECUTE     // 执行
    }
    
    public CommandExecutedEvent(CommandType commandType) {
        this("CommandManager", commandType);
    }
    
    public CommandExecutedEvent(String source, CommandType commandType) {
        super(EventType.COMMAND_EXECUTED);  // 使用 COMMAND_EXECUTED 事件类型
        this.source = source;
        this.commandType = commandType;
    }
    
    public CommandType getCommandType() {
        return commandType;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("CommandExecutedEvent[source=%s, type=%s]", source, commandType);
    }
} 