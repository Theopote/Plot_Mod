package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.PowerLineSagUtils;

/** 导线垂度规则参数（规划默认值）。 */
public class SagRules {
    private double maxSagDepth = PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;

    public double getMaxSagDepth() {
        return maxSagDepth;
    }

    /**
     * @param maxSagDepth 单跨最大下垂深度（格），{@code <= 0} 表示不限制
     */
    public void setMaxSagDepth(double maxSagDepth) {
        this.maxSagDepth = maxSagDepth <= 0.0 ? 0.0 : Math.max(1.0, Math.min(64.0, maxSagDepth));
    }

    public SagRules copy() {
        SagRules copy = new SagRules();
        copy.maxSagDepth = maxSagDepth;
        return copy;
    }
}
