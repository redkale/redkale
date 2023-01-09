/*
 *
 */
package org.redkale.net.sncp;

/**
 * Sncp自定义异常类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpException extends RuntimeException {

    public SncpException() {
        super();
    }

    public SncpException(String s) {
        super(s);
    }

    public SncpException(String message, Throwable cause) {
        super(message, cause);
    }

    public SncpException(Throwable cause) {
        super(cause);
    }
}
