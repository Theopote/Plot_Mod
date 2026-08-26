package com.plot.core.model;

import java.io.IOException;

/**
 * 项目文件格式错误或无法解析时抛出；不得用默认空工程掩盖加载失败。
 */
public class ProjectFormatException extends IOException {

    public enum Reason {
        EMPTY_INPUT,
        INVALID_JSON,
        UNSUPPORTED_FORMAT_VERSION,
        MIGRATION_FAILED,
        VALIDATION_FAILED
    }

    private final Reason reason;

    public ProjectFormatException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ProjectFormatException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
