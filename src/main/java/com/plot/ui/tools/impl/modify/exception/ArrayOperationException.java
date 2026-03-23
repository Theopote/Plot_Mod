package com.plot.ui.tools.impl.modify.exception;

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
        INVALID_SOURCE_SHAPE("无效的源图形"),
        INVALID_BASE_POINT("无效的基准点"),
        INVALID_PATH_POINTS("无效的路径点"),
        CLONE_FAILED("图形克隆失败"),
        INSUFFICIENT_PATH_POINTS("路径点不足"),
        INVALID_ARRAY_PARAMETERS("无效的阵列参数"),
        PREVIEW_CALCULATION_FAILED("预览计算失败");
        
        private final String message;
        
        ErrorType(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    private final ErrorType errorType;

    /**
     * 构造函数
     * @param errorType 错误类型
     * @param message 详细错误信息
     */
    public ArrayOperationException(ErrorType errorType, String message) {
        super(String.format("%s: %s", errorType.getMessage(), message));
        this.errorType = errorType;
    }

    /**
     * 构造函数
     * @param errorType 错误类型
     * @param message 详细错误信息
     * @param cause 原因异常
     */
    public ArrayOperationException(ErrorType errorType, String message, Throwable cause) {
        super(String.format("%s: %s", errorType.getMessage(), message), cause);
        this.errorType = errorType;
    }

}
