package com.plot.core.persistence;

/**
 * 加载结果：区分「成功（含缺文件空文档）」与「失败（勿绑定路径 / 勿清空历史）」。
 */
public record LoadResult<T>(boolean success, T value, Exception error) {
    public static <T> LoadResult<T> ok(T value) {
        return new LoadResult<>(true, value, null);
    }

    public static <T> LoadResult<T> fail(Exception error) {
        return new LoadResult<>(false, null, error);
    }

    public boolean isSuccess() {
        return success;
    }
}
