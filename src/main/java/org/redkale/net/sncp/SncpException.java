/*
 *
 */
package org.redkale.net.sncp;

import org.redkale.util.RedkaleException;

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
public class SncpException extends RedkaleException {

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
