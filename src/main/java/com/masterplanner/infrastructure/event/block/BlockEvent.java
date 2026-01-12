package com.masterplanner.infrastructure.event.block;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 方块事件基类
 */
public abstract class BlockEvent extends Event {
    protected final String blockId;
    protected final String source;

    /**
     * 构造方块事件
     * @param blockId 方块ID
     * @param type 事件类型
     */
    protected BlockEvent(String blockId, EventType type) {
        this("BlockManager", blockId, type);
    }
    
    /**
     * 构造方块事件
     * @param source 事件源
     * @param blockId 方块ID
     * @param type 事件类型
     */
    protected BlockEvent(String source, String blockId, EventType type) {
        super(type);
        this.source = source;
        this.blockId = blockId;
    }

    public String getBlockId() {
        return blockId;
    }
    
    @Override
    public String getSource() {
        return source;
    }
} 