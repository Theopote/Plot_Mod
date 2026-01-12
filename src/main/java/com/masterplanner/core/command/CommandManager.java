package com.masterplanner.core.command;

import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.command.UndoEvent;
import com.masterplanner.infrastructure.event.command.RedoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;

/**
 * 命令管理器
 * 负责管理和执行所有的命令操作，包括撤销和重做功能
 */
public class CommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/CommandManager");
    private static CommandManager instance;

    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();
    private final EventBus eventBus;

    private static final int MAX_HISTORY_SIZE = 100; // 最大历史记录数

    private CommandManager() {
        this.eventBus = EventBus.getInstance();
        registerEventListeners();
    }

    /**
     * 获取CommandManager单例实例
     * @return CommandManager实例
     */
    public static synchronized CommandManager getInstance() {
        if (instance == null) {
            instance = new CommandManager();
        }
        return instance;
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        eventBus.subscribe(UndoEvent.class, event -> undo());
        eventBus.subscribe(RedoEvent.class, event -> redo());
    }

    /**
     * 执行命令
     * @param command 要执行的命令
     */
    public void executeCommand(Command command) {
        try {
            LOGGER.debug("执行命令: {}", command.getDescription());
            command.execute();
            undoStack.push(command);
            redoStack.clear(); // 清空重做栈

            // 如果历史记录超过最大值，移除最早的记录
            if (undoStack.size() > MAX_HISTORY_SIZE) {
                undoStack.removeFirst();
            }
        } catch (Exception e) {
            LOGGER.error("执行命令时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 撤销上一个命令
     */
    public void undo() {
        if (!canUndo()) {
            LOGGER.debug("没有可撤销的命令");
            return;
        }

        try {
            Command command = undoStack.pop();
            LOGGER.debug("撤销命令: {}", command.getDescription());
            command.undo();
            redoStack.push(command);
        } catch (Exception e) {
            LOGGER.error("撤销命令时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 重做上一个被撤销的命令
     */
    public void redo() {
        if (!canRedo()) {
            LOGGER.debug("没有可重做的命令");
            return;
        }

        try {
            Command command = redoStack.pop();
            LOGGER.debug("重做命令: {}", command.getDescription());
            command.redo();
            undoStack.push(command);
        } catch (Exception e) {
            LOGGER.error("重做命令时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查是否可以撤销
     * @return 如果有可撤销的命令返回true
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * 检查是否可以重做
     * @return 如果有可重做的命令返回true
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * 清空所有命令历史
     */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        LOGGER.debug("已清空命令历史");
    }

    /**
     * 获取可撤销的命令数量
     * @return 可撤销的命令数量
     */
    public int getUndoCount() {
        return undoStack.size();
    }

    /**
     * 获取可重做的命令数量
     * @return 可重做的命令数量
     */
    public int getRedoCount() {
        return redoStack.size();
    }
} 