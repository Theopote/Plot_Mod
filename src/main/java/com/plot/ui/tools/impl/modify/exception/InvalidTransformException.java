package com.plot.ui.tools.impl.modify.exception;

import com.plot.utils.PlotI18n;

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

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Context getContext() {
        return context;
    }

    public String getFullErrorMessage() {
        return PlotI18n.error(
                "error.plot.transform.full_message",
                errorCode.getDescription(),
                getMessage(),
                context.getDescription());
    }

    /**
     * 错误代码枚举
     */
    public enum ErrorCode {
        INVALID_DRAG_VECTOR("error.plot.transform.code.invalid_drag_vector"),
        INVALID_CONTROL_POINT("error.plot.transform.code.invalid_control_point"),
        INVALID_CONTROL_POINT_INDEX("error.plot.transform.code.invalid_control_point_index"),
        INVALID_BOUNDING_BOX("error.plot.transform.code.invalid_bounding_box"),
        INVALID_TRANSFORM_MODE("error.plot.transform.code.invalid_transform_mode"),
        TRANSFORM_APPLICATION_ERROR("error.plot.transform.code.application_error"),
        INVALID_SHAPE("error.plot.transform.code.invalid_shape"),
        UNKNOWN_ERROR("error.plot.transform.code.unknown");

        private final String i18nKey;

        ErrorCode(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String getDescription() {
            return PlotI18n.error(i18nKey);
        }
    }

    /**
     * 上下文枚举
     */
    public enum Context {
        TRANSFORM_HANDLER("error.plot.transform.context.transform_handler"),
        TRANSFORM_COMMAND("error.plot.transform.context.transform_command"),
        TRANSFORM_STRATEGY("error.plot.transform.context.transform_strategy"),
        BOUNDING_BOX_MANAGER("error.plot.transform.context.bounding_box_manager"),
        CONTROL_POINT_MANAGER("error.plot.transform.context.control_point_manager");

        private final String i18nKey;

        Context(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String getDescription() {
            return PlotI18n.tr(i18nKey);
        }
    }
}
