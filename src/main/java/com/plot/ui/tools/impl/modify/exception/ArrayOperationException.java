package com.plot.ui.tools.impl.modify.exception;

import com.plot.utils.PlotI18n;

/**
 * 阵列操作异常
 * 
 * <p>专门处理阵列操作中的边界情况和错误。</p>
 * 
 * @author Plot Team
 * @version 1.0 - 阵列操作异常
 */
public class ArrayOperationException extends RuntimeException {
    
    /**
     * 异常类型枚举
     */
    public enum ErrorType {
        INVALID_SOURCE_SHAPE("status.plot.array.error.invalid_source"),
        INVALID_BASE_POINT("status.plot.array.error.invalid_base_point"),
        INVALID_PATH_POINTS("status.plot.array.error.invalid_path_points"),
        CLONE_FAILED("status.plot.array.error.clone_failed"),
        INSUFFICIENT_PATH_POINTS("status.plot.array.error.insufficient_path_points"),
        INVALID_ARRAY_PARAMETERS("status.plot.array.error.invalid_parameters"),
        PREVIEW_CALCULATION_FAILED("status.plot.array.error.preview_failed");
        
        private final String messageKey;
        
        ErrorType(String messageKey) {
            this.messageKey = messageKey;
        }
        
        public String getMessage() {
            return PlotI18n.status(messageKey);
        }
    }
    
    private final ErrorType errorType;

    public ArrayOperationException(ErrorType errorType, String detailKey, Object... detailArgs) {
        super(formatMessage(errorType, detailKey, detailArgs));
        this.errorType = errorType;
    }

    public ArrayOperationException(ErrorType errorType, String detailKey, Throwable cause) {
        super(formatMessage(errorType, detailKey), cause);
        this.errorType = errorType;
    }

    private static String formatMessage(ErrorType errorType, String detailKey, Object... detailArgs) {
        String detail = detailKey != null && detailKey.startsWith("status.plot.")
                ? PlotI18n.status(detailKey, detailArgs)
                : (detailKey != null ? detailKey : "");
        return PlotI18n.status("status.plot.array.error.combined", errorType.getMessage(), detail);
    }

}
