package com.plot.infrastructure.event.command;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;

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

    public CommandExecutedEvent(String source, CommandType commandType) {
        super(EventType.COMMAND_EXECUTED);  // 使用 COMMAND_EXECUTED 事件类型
        this.source = source;
        this.commandType = commandType;
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