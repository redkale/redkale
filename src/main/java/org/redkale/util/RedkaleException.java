/*
 *
 */
package org.redkale.util;

/**
 * redkale的异常基础类 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class RedkaleException extends RuntimeException {

    public RedkaleException() {
        super();
    }

    public RedkaleException(String s) {
        super(s);
    }

    public RedkaleException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedkaleException(Throwable cause) {
        super(cause);
    }
}
