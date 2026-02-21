package com.masterplanner.infrastructure.event.block;

/**
 * 方块配置事件
 */
public class BlockConfigEvent extends BlockEvent {
    private final String configType;
    private final String configValue;

    public BlockConfigEvent(String blockId, String configType, String configValue) {
        super(blockId, null);
        this.configType = configType;
        this.configValue = configValue;
    }

    @Override
    public String toString() {
        return String.format("BlockConfigEvent[blockId=%s, type=%s, value=%s]", 
            getBlockId(), configType, configValue);
    }
} 