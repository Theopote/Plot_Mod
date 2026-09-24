package com.plot.plugin.ui;

import com.plot.api.world.IBlockPlacementService;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.text.NumberFormat;

/**
 * 插件任务进度条（预览分帧、方块落地等）的统一 ImGui 呈现。
 * <p>
 * 图案 / 电力 / 建筑等插件应优先使用本类，避免各插件重复实现进度条样式。
 * <p>
 * <strong>展示约定：</strong>进度条只在各插件顶部工具栏渲染一次；生成页 / 弹窗内仅禁用相关按钮，
 * 不要再重复绘制进度条或同类进度文案。
 */
public final class PluginJobProgressUi {
    private PluginJobProgressUi() {
    }

    public static float fraction(int processed, int total) {
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) processed / total);
    }

    public static int percent(int processed, int total) {
        return Math.round(fraction(processed, total) * 100f);
    }

    public static String formatCounts(int processed, int total) {
        NumberFormat formatter = NumberFormat.getIntegerInstance();
        return formatter.format(processed) + " / " + formatter.format(total);
    }

    /**
     * 渲染带进度条的任务状态。
     *
     * @param statusText 彩色状态行（已翻译）；为 null 时不绘制
     * @param processed  已完成量
     * @param total      总量；&le;0 时仅绘制状态行与取消按钮
     * @param barWidth   进度条宽度，通常 {@link ImGui#getContentRegionAvailX()}
     * @param cancelLabelKey 取消按钮 i18n key；为 null 时不显示取消
     * @param onCancel   取消回调
     */
    public static void renderJobProgress(
            String statusText,
            int processed,
            int total,
            float barWidth,
            String cancelLabelKey,
            Runnable onCancel) {
        if (statusText != null && !statusText.isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_INFO, statusText);
        }
        if (total > 0) {
            float width = barWidth > 0f ? barWidth : ImGui.getContentRegionAvailX();
            ImGui.pushID("job_progress_bar");
            ImGui.progressBar(fraction(processed, total), width, 0);
            ImGui.popID();
        }
        if (cancelLabelKey != null && onCancel != null) {
            if (ImGui.button(PlotI18n.tr(cancelLabelKey), 0, 0)) {
                onCancel.run();
            }
        }
    }

    /** 方块落地调度器的标准进度条 + 取消。 */
    public static void renderPlacementProgress(
            IBlockPlacementService scheduler,
            String progressKey,
            String waitKey,
            String cancelKey) {
        if (scheduler == null || !scheduler.isBusy()) {
            return;
        }
        IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            renderJobProgress(
                PlotI18n.tr(progressKey, progress.processed(), progress.total()),
                progress.processed(),
                progress.total(),
                ImGui.getContentRegionAvailX(),
                cancelKey,
                scheduler::cancelAll);
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr(waitKey));
            if (ImGui.button(PlotI18n.tr(cancelKey), 0, 0)) {
                scheduler.cancelAll();
            }
        }
    }
}
