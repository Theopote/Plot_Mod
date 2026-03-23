package com.plot.ui.tools.impl.modify.exception;

/**
 * 变换操作异常
 * 表示变换操作中的参数或状态错误
 */
public class InvalidTransformException extends Exception {
    
    private final ErrorCode errorCode;
    private final Context context;
    
    public InvalidTransformException(String message, ErrorCode errorCode, Context context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }

    public Context getContext() {
        return context;
    }
    
    public String getFullErrorMessage() {
        return String.format("[%s] %s (上下文: %s)", errorCode.name(), getMessage(), context.name());
    }
    
    /**
     * 错误代码枚举
     */
    public enum ErrorCode {
        INVALID_DRAG_VECTOR("拖拽向量无效"),
        INVALID_CONTROL_POINT("控制点无效"),
        INVALID_CONTROL_POINT_INDEX("控制点索引无效"),
        INVALID_BOUNDING_BOX("边界框无效"),
        INVALID_TRANSFORM_MODE("变换模式无效"),
        TRANSFORM_APPLICATION_ERROR("变换应用错误"),
        INVALID_SHAPE("图形无效"),
        UNKNOWN_ERROR("未知错误");
        
        private final String description;
        
        ErrorCode(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 上下文枚举
     */
    public enum Context {
        TRANSFORM_HANDLER("变换处理器"),
        TRANSFORM_COMMAND("变换命令"),
        TRANSFORM_STRATEGY("变换策略"),
        BOUNDING_BOX_MANAGER("边界框管理器"),
        CONTROL_POINT_MANAGER("控制点管理器");
        
        private final String description;
        
        Context(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}