package com.masterplanner.api.command;

import java.util.List;

/**
 * 命令管理器接口，负责管理命令的执行、撤销和重做
 */
public interface ICommandManager {
    /**
     * 执行命令
     * @param command 要执行的命令
     * @return 命令是否执行成功
     */
    boolean executeCommand(ICommand command);

    /**
     * 撤销最后一个命令
     * @return 是否撤销成功
     */
    boolean undo();

    /**
     * 重做上一个撤销的命令
     * @return 是否重做成功
     */
    boolean redo();

    /**
     * 获取可撤销的命令列表
     * @return 可撤销的命令列表
     */
    List<ICommand> getUndoStack();

    /**
     * 获取可重做的命令列表
     * @return 可重做的命令列表
     */
    List<ICommand> getRedoStack();

    /**
     * 清空命令历史
     */
    void clearHistory();

    /**
     * 是否可以撤销
     * @return 是否可以撤销
     */
    boolean canUndo();

    /**
     * 是否可以重做
     * @return 是否可以重做
     */
    boolean canRedo();

    /**
     * 获取最后执行的命令
     * @return 最后执行的命令
     */
    ICommand getLastCommand();

    /**
     * 开始命令组
     * 命令组中的多个命令将被视为一个命令
     */
    void beginCommandGroup();

    /**
     * 结束命令组
     */
    void endCommandGroup();

    /**
     * 取消当前命令组
     */
    void cancelCommandGroup();

    /**
     * 是否正在记录命令组
     * @return 是否正在记录命令组
     */
    boolean isGrouping();

    /**
     * 添加命令监听器
     * @param listener 命令监听器
     */
    void addCommandListener(ICommandListener listener);

    /**
     * 移除命令监听器
     * @param listener 命令监听器
     */
    void removeCommandListener(ICommandListener listener);
}
