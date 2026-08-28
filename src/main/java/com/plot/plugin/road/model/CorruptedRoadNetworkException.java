package com.plot.plugin.road.model;

/**
 * Sidecar 文件存在但内容为空或截断（非合法 JSON 文档）。
 */
public final class CorruptedRoadNetworkException extends RoadNetworkFormatException {

    public CorruptedRoadNetworkException(String message) {
        super(Reason.CORRUPTED_FILE, message);
    }

    public CorruptedRoadNetworkException(String message, Throwable cause) {
        super(Reason.CORRUPTED_FILE, message, cause);
    }
}
