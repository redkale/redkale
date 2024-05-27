/*
 *
 */
package org.redkale.net.http;

/**
 * Rest自定义异常类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class RestException extends HttpException {

    public RestException() {
        super();
    }

    public RestException(String s) {
        super(s);
    }

    public RestException(String message, Throwable cause) {
        super(message, cause);
    }

    public RestException(Throwable cause) {
        super(cause);
    }
}
