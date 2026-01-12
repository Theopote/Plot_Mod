package com.masterplanner.core.command;

import java.util.Date;

/**
 * 命令接口
 * 定义了可撤销/重做操作的基本结构
 */
public interface Command {
    /**
     * 执行命令
     */
    void execute();
    
    /**
     * 撤销命令
     */
    void undo();
    
    /**
     * 重做命令
     */
    void redo();
    
    /**
     * 获取命令描述
     * @return 命令的描述信息
     */
    String getDescription();
    
    /**
     * 获取详细描述
     */
    String getDetailedDescription();
    
    /**
     * 获取命令执行时间戳
     */
    default Date getTimestamp() {
        return new Date();
    }
}
