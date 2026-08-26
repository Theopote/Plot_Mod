package com.plot.core.persistence;

import java.io.IOException;

/**
 * 统一持久化异常（Core / 插件 sidecar 共用）。
 */
public class PersistenceException extends IOException {
    public enum Reason {
        EMPTY_INPUT,
        INVALID_CONTENT,
        VALIDATION_FAILED,
        UNSUPPORTED_VERSION,
        MIGRATION_FAILED,
        IO_ERROR
    }

    private final Reason reason;

    public PersistenceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PersistenceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
