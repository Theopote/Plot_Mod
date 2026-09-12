package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.parametric.ParameterRange;
import com.plot.plugin.powerline.design.parametric.TowerArmTemplate;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;

/** Converts arm level scales to user-facing block heights for the designer UI. */
public final class TowerArmLevelUiMath {
    private TowerArmLevelUiMath() {
    }

    public static double resolvedArmHeight(
            double towerHeight,
            TowerArmTemplate template,
            double levelScale) {
        return towerHeight * template.heightRatio() * levelScale;
    }

    public static double scaleFromArmHeight(
            double requestedArmHeight,
            double towerHeight,
            TowerArmTemplate template) {
        double reference = towerHeight * template.heightRatio();
        if (reference <= 1e-6) {
            return ParameterRange.ARM_LEVEL.defaultValue();
        }
        return ParameterRange.ARM_LEVEL.clamp(requestedArmHeight / reference);
    }

    public static float sliderMin(double towerHeight, TowerArmTemplate template) {
        return (float) resolvedArmHeight(towerHeight, template, ParameterRange.ARM_LEVEL.min());
    }

    public static float sliderMax(double towerHeight, TowerArmTemplate template) {
        return (float) resolvedArmHeight(towerHeight, template, ParameterRange.ARM_LEVEL.max());
    }

    public static double displayArmHeight(
            TowerParameterProfile profile,
            TowerParameterSet parameters,
            TowerArmTemplate template,
            int armIndex) {
        return resolvedArmHeight(
            parameters.height(),
            template,
            parameters.armLevelScaleAt(armIndex));
    }
}
