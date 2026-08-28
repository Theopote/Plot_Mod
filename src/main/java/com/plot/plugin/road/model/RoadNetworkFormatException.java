package com.plot.plugin.road.model;

import java.io.IOException;

/**
 * 道路网络 sidecar JSON 无法解析或内容无效时抛出；不得用默认空路网掩盖加载失败。
 */
public final class RoadNetworkFormatException extends IOException {

    public enum Reason {
        EMPTY_INPUT,
        INVALID_JSON,
        VALIDATION_FAILED
    }

    private final Reason reason;

    public RoadNetworkFormatException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RoadNetworkFormatException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
