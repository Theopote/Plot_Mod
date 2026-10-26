package com.plot.plugin.earthwork.model;

/** 两种玩家可选的推荐方式；不自动覆盖旧工程保存的自定义策略。 */
public enum EarthworkBalanceChoice {
    PER_REGION, WHOLE_SITE;

    public String i18nKey() {
        return "plugin.earthwork.balance_choice." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static EarthworkBalanceChoice fromPolicy(CompositionPolicy policy) {
        if (policy.getBalanceScopeEnum() == BalanceScope.ZONE && policy.isNoneOptimization()) {
            return PER_REGION;
        }
        if (policy.getBalanceScopeEnum() == BalanceScope.SITE && policy.isUniformVerticalShift()) {
            return WHOLE_SITE;
        }
        return null;
    }

    public void applyTo(CompositionPolicy policy) {
        policy.setBalanceScope(this == PER_REGION ? BalanceScope.ZONE : BalanceScope.SITE);
        policy.setOptimizationMode(this == PER_REGION ? OptimizationMode.NONE : OptimizationMode.UNIFORM_VERTICAL_SHIFT);
    }
}
