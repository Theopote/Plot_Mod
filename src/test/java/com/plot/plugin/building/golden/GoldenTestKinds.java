package com.plot.plugin.building.golden;

/**
 * Golden / Acceptance 测试分层约定。
 *
 * <h2>A. Regression Golden</h2>
 * 用途：行为有没有变化。可 Snapshot 生成 expected（{@link GoldenBuildingExpectations}）。
 * 入口：{@link GoldenBuildingRegressionTest}；刷新：{@link GoldenBuildingSnapshotGeneratorTest}。
 *
 * <h2>B. Semantic Acceptance</h2>
 * 用途：行为是不是正确。断言必须手写，不能由 Snapshot 自动生成。
 * 入口：{@link BuildingSemanticAcceptanceTest}；规则：{@link SemanticAcceptanceAssertions}。
 *
 * <h2>为何必须有 B 层：B07</h2>
 * 窄廊 inner-offset 失败时，错误实现曾产出「零墙体」；若只比对 Snapshot，
 * 下一次刷新会把错误固化成 expected。B07 因此强制语义断言，例如
 * {@code assertTrue(metrics.wallBlocks() > 0)}，而不是仅
 * {@code actual == snapshot}。
 *
 * <p>两类共享同一组标准案例 {@link GoldenBuildingCaseFactory} 与 harness，但断言职责分离。
 */
public final class GoldenTestKinds {
    private GoldenTestKinds() {
    }
}
