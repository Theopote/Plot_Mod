package com.plot.plugin.road.terrain;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将<strong>已确认</strong>的世界方块变更接到走廊预取失效。
 * <p>
 * 只订阅 {@link PlayerBlockBreakEvents#AFTER}：破坏已发生后才冲刷。
 * 不订阅 UseBlock / AttackBlock（尝试交互 ≠ 世界已改），避免空手右键、未破坏的左键
 * 反复触发无效预取。放置类改地仍由土方/道路建造回调与侧向地表探针兜底。
 * <p>
 * Fabric 事件无法真正注销：用 {@link #setEnabled(boolean)} 在插件禁用时短路；
 * 回调校验维度，避免其它世界同坐标误伤主世界走廊缓存。
 */
public final class RoadCorridorWorldBlockListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadTerrainPrefetch");
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static boolean registered;

    private RoadCorridorWorldBlockListener() {
    }

    public static void register() {
        ENABLED.set(true);
        if (registered) {
            return;
        }
        registered = true;
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (!ENABLED.get() || pos == null || world == null) {
                return;
            }
            RegistryKey<World> worldKey = world.getRegistryKey();
            RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(worldKey, pos);
        });
        LOGGER.debug("走廊世界方块变更监听已注册（仅破坏完成事件）");
    }

    /** 插件启用态守卫：disable 时置 false，避免残留回调继续失效缓存。 */
    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /** 测试：是否已注册原生回调。 */
    static boolean isRegisteredForTest() {
        return registered;
    }
}
