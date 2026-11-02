package com.plot.plugin.road.terrain;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将玩家放置/破坏方块接到走廊预取失效：仅当变更落在最近预取足迹内时冲刷缓存。
 * <p>
 * 探针指纹仍覆盖无事件路径；本监听优先处理「路肩旁改地、中心线未变」的情况。
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
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world != null && hitResult != null && hitResult.getBlockPos() != null) {
                BlockPos against = hitResult.getBlockPos();
                BlockPos placed = against.offset(hitResult.getSide());
                RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(against);
                RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(placed);
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (pos != null) {
                RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(pos);
            }
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (pos != null) {
                RoadCorridorTerrainPrefetch.notifyWorldBlockChanged(pos);
            }
        });
        LOGGER.debug("走廊世界方块变更监听已注册");
    }
}
