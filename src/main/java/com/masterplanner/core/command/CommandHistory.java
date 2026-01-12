package com.masterplanner.core.command;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令历史管理器
 * 用于管理撤销/重做操作
 */
public class CommandHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/CommandHistory");
    private static volatile CommandHistory INSTANCE;
    
    private final List<Command> commands = new ArrayList<>();
    private int currentIndex = -1;
    private static final int MAX_HISTORY = 50;  // 最大历史记录数
    
    private CommandHistory() {}
    
    public static CommandHistory getInstance() {
        if (INSTANCE == null) {
            synchronized (CommandHistory.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CommandHistory();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 执行命令
     */
    public void execute(Command command) {
        if (command == null) return;
        
        try {
            // 如果当前不是在最新状态，清除当前位置之后的所有命令
            if (currentIndex < commands.size() - 1) {
                commands.subList(currentIndex + 1, commands.size()).clear();
            }
            
            // 执行命令
            command.execute();
            
            // 添加到历史记录
            commands.add(command);
            currentIndex++;
            
            // 如果超出最大历史记录数，移除最早的记录
            if (commands.size() > MAX_HISTORY) {
                commands.removeFirst();
                currentIndex--;
            }
            
            LOGGER.debug("Executed command: {}", command.getDescription());
            
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command.getDescription(), e);
        }
    }
    
    /**
     * 撤销操作
     */
    public void undo() {
        if (!canUndo()) return;
        
        try {
            Command command = commands.get(currentIndex);
            command.undo();
            currentIndex--;
            LOGGER.debug("Undid command: {}", command.getDescription());
        } catch (Exception e) {
            LOGGER.error("Failed to undo command at index {}", currentIndex, e);
        }
    }
    
    /**
     * 重做操作
     */
    public void redo() {
        if (!canRedo()) return;
        
        try {
            currentIndex++;
            Command command = commands.get(currentIndex);
            command.execute();
            LOGGER.debug("Redid command: {}", command.getDescription());
        } catch (Exception e) {
            LOGGER.error("Failed to redo command at index {}", currentIndex + 1, e);
        }
    }
    
    /**
     * 是否可以撤销
     */
    public boolean canUndo() {
        return currentIndex >= 0;
    }
    
    /**
     * 是否可以重做
     */
    public boolean canRedo() {
        return currentIndex < commands.size() - 1;
    }
    
    /**
     * 清空历史记录
     */
    public void clear() {
        commands.clear();
        currentIndex = -1;
        LOGGER.debug("Cleared command history");
    }
    
    /**
     * 获取当前命令索引
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * 获取历史记录列表
     * @return 只读的命令历史记录列表
     */
    public List<Command> getHistory() {
        return new ArrayList<>(commands);  // 返回副本以防止外部修改
    }
    
    /**
     * 获取历史记录大小
     */
    public int size() {
        return commands.size();
    }
}
