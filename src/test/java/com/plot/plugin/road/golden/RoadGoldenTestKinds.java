package com.plot.plugin.road.golden;

/**
 * Golden / Acceptance 测试分层约定。
 *
 * <h2>A. Regression Golden</h2>
 * 用途：行为有没有变化。入口 {@link RoadGoldenRegressionTest}；
 * 刷新 {@link RoadGoldenSnapshotGeneratorTest}。
 *
 * <h2>B. Semantic Acceptance</h2>
 * 用途：行为是不是正确。断言必须手写，不能由 Snapshot 自动生成。
 * 入口 {@link RoadSemanticAcceptanceTest}；规则 {@link RoadSemanticAcceptanceAssertions}。
 */
public final class RoadGoldenTestKinds {
    private RoadGoldenTestKinds() {
    }
}
