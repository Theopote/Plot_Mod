package com.plot.plugin.earthwork.design;

import java.util.Objects;
import java.util.Optional;

/**
 * 带状态的解析结果：下游可区分真实设计值与错误/推荐回退值。
 *
 * @param <T> 解析值类型
 */
public record ResolutionResult<T>(Status status, T value, String detail) {

    public enum Status {
        /** 按设计语义成功解析。 */
        RESOLVED,
        /** 有意回退（例如建筑地坪推荐值、手动坑底未设时用场地默认）。 */
        FALLBACK,
        /** 需要建筑引用但为空。 */
        MISSING_REFERENCE,
        /** 引用非空但 lookup 无法解析。 */
        INVALID_REFERENCE
    }

    public ResolutionResult {
        Objects.requireNonNull(status, "status");
        detail = detail != null ? detail : "";
    }

    public static <T> ResolutionResult<T> resolved(T value) {
        return new ResolutionResult<>(Status.RESOLVED, value, "");
    }

    public static <T> ResolutionResult<T> resolved(T value, String detail) {
        return new ResolutionResult<>(Status.RESOLVED, value, detail);
    }

    public static <T> ResolutionResult<T> fallback(T value, String detail) {
        return new ResolutionResult<>(Status.FALLBACK, value, detail);
    }

    public static <T> ResolutionResult<T> missingReference(T fallbackValue, String detail) {
        return new ResolutionResult<>(Status.MISSING_REFERENCE, fallbackValue, detail);
    }

    public static <T> ResolutionResult<T> invalidReference(T fallbackValue, String detail) {
        return new ResolutionResult<>(Status.INVALID_REFERENCE, fallbackValue, detail);
    }

    public boolean isResolved() {
        return status == Status.RESOLVED;
    }

    public boolean isFallback() {
        return status == Status.FALLBACK;
    }

    /** 引用缺失或无效（不是成功解析，也不是有意 FALLBACK）。 */
    public boolean isReferenceFailure() {
        return status == Status.MISSING_REFERENCE || status == Status.INVALID_REFERENCE;
    }

    /**
     * 建筑地坪等允许推荐回退：{@link Status#RESOLVED} / {@link Status#FALLBACK} /
     * 引用失败时仍可用 {@code value}（通常为场地默认）。
     */
    public T valueOrFallback(T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }

    /**
     * 基坑建筑联动等 fail-closed 路径：仅 {@link Status#RESOLVED} 可继续。
     */
    public T requireResolved(String context) {
        if (status == Status.RESOLVED) {
            return value;
        }
        String message = detail != null && !detail.isBlank()
            ? detail
            : context + " is not RESOLVED (status=" + status + ")";
        throw new BuildingFootprintResolver.UnresolvedBuildingReferenceException(message);
    }

    /** 映射成功值，保留失败/回退状态与 detail。 */
    public <U> ResolutionResult<U> map(java.util.function.Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        U mapped = value != null ? mapper.apply(value) : null;
        return new ResolutionResult<>(status, mapped, detail);
    }
}
