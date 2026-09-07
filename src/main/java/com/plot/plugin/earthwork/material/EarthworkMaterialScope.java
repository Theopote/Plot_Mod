package com.plot.plugin.earthwork.material;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;

/**
 * 材料换算模型的作用域：工程 {@link EarthworkSite} / {@link GradingRegion} 为运行时真源；
 * {@link EarthworkConfig} 仅保存认领新区块时的默认副本。
 */
public final class EarthworkMaterialScope {

    private EarthworkMaterialScope() {
    }

    public static boolean usesDefaultMaterialModel(EarthworkSite site) {
        if (site == null) {
            return true;
        }
        MaterialConversionModel model = site.getMaterialModel();
        return model == null || model == MaterialConversionModel.DEFAULT;
    }

    /**
     * 更新场地材料模型（写入工程，不触碰 plugin config）。
     */
    public static void updateSiteMaterial(
            EarthworkUiContext ctx,
            EarthworkSite site,
            MaterialConversionModel model) {
        if (site == null || model == null) {
            return;
        }
        site.setMaterialModel(model);
        if (ctx != null) {
            ctx.invalidatePreview();
        }
    }

    /**
     * 将场地材料复制到所有分区（覆盖分区 override）。
     */
    public static void applySiteMaterialToAllRegions(
            EarthworkUiContext ctx,
            EarthworkProject project,
            EarthworkSite site) {
        if (project == null || site == null) {
            return;
        }
        MaterialConversionModel siteModel = site.getMaterialModel();
        for (GradingRegion region : project.getRegions().values()) {
            region.setMaterialProperties(siteModel);
        }
        if (ctx != null) {
            ctx.invalidatePreview();
        }
    }

    /**
     * 将当前场地材料提升为「认领新区块」的插件默认（显式用户操作，非自动双写）。
     */
    public static void promoteSiteMaterialToAdoptDefault(
            EarthworkUiContext ctx,
            EarthworkSite site) {
        if (ctx == null || site == null || ctx.config() == null) {
            return;
        }
        EarthworkConfig config = ctx.config();
        config.setDefaultMaterialProperties(site.getMaterialModel());
        config.save();
    }

    /**
     * Learn 示例：场地 + 全分区同步，仍不写 config。
     */
    public static void applyExampleToProject(
            EarthworkUiContext ctx,
            EarthworkProject project,
            EarthworkSite site,
            MaterialConversionModel model) {
        if (project == null || site == null || model == null) {
            return;
        }
        site.setMaterialModel(model);
        for (GradingRegion region : project.getRegions().values()) {
            region.setMaterialProperties(model);
        }
        if (ctx != null) {
            ctx.recalculatePreview();
        }
    }
}
