package com.plot.mixin;

import com.plot.camera.PlotRenderDistanceState;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 集成服每 tick 会用客户端 {@code options.renderDistance}（硬顶 32）回写 PlayerManager 视距，
 * 导致 Plot 抬高到 33+ 后立刻被打回 32，外围区块无法下发。
 * 这里让同步读取值至少为 Plot override。
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
            ordinal = 0
        )
    )
    private Object plot$boostSyncedViewDistance(SimpleOption<?> option) {
        Object value = option.getValue();
        int override = PlotRenderDistanceState.getOverrideChunks();
        if (override > 0 && value instanceof Integer current) {
            return Math.max(current, override);
        }
        return value;
    }
}
