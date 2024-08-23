/*
 *
 */
package org.redkale.service;

import org.redkale.util.RedkaleException;

/**
 * 错误码自定义异常类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class RetException extends RedkaleException {

    private int code;

    public RetException(int code) {
        super(RetCodes.retInfo(code));
        this.code = code;
    }

    public RetException(int code, String message) {
        super(message);
        this.code = code;
    }

    public RetException(int code, Throwable cause) {
        super(RetCodes.retInfo(code), cause);
        this.code = code;
    }

    public RetException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public RetResult retResult() {
        return new RetResult(code, getMessage());
    }
}
