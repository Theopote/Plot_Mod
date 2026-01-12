package com.masterplanner.infrastructure.event.command;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.core.command.Command;

/**
 * 命令事件
 */
public class CommandEvent extends Event {
    private final Command command;
    private final CommandType commandType;
    private final String source;

    /**
     * 命令事件类型
     */
    public enum CommandType {
        EXECUTE,
        UNDO,
        REDO
    }

    public CommandEvent(Command command, CommandType commandType) {
        this("CommandManager", command, commandType);
    }
    
    public CommandEvent(String source, Command command, CommandType commandType) {
        super(EventType.COMMAND_EXECUTED);
        this.source = source;
        this.command = command;
        this.commandType = commandType;
    }

    public Command getCommand() {
        return command;
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
        return String.format("CommandEvent[source=%s, command=%s, type=%s]", 
            source, command, commandType);
    }
} 