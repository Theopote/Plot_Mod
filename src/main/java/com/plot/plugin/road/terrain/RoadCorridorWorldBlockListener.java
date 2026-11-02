package com.plot.plugin.road.terrain;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将<strong>已确认</strong>的世界方块变更接到走廊预取失效。
 * <p>
 * 只订阅 {@link PlayerBlockBreakEvents#AFTER}：破坏已发生后才冲刷。
 * 不订阅 UseBlock / AttackBlock（尝试交互 ≠ 世界已改），避免空手右键、未破坏的左键
 * 反复触发无效预取。放置类改地仍由土方/道路建造回调与侧向地表探针兜底。
 */
public final class RoadCorridorWorldBlockListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTerrainPrefetch");
    private static boolean registered;

    private RoadCorridorWorldBlockListener() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (pos != null) {
                RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(pos);
            }
        });
        LOGGER.debug("走廊世界方块变更监听已注册（仅破坏完成事件）");
    }

    /** 测试：是否已注册。 */
    static boolean isRegisteredForTest() {
        return registered;
    }
}
