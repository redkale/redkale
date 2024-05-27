/*
 *
 */
package org.redkale.net.http;

import org.redkale.util.RedkaleException;

/**
 * Http自定义异常类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class HttpException extends RedkaleException {

    public HttpException() {
        super();
    }

    public HttpException(String s) {
        super(s);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpException(Throwable cause) {
        super(cause);
    }
}
