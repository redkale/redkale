/*
 *
 */
package org.redkale.source;

import org.redkale.util.RedkaleException;

/**
 * 数据源自定义异常类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SourceException extends RedkaleException {

    public SourceException() {
        super();
    }

    public SourceException(String s) {
        super(s);
    }

    public SourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public SourceException(Throwable cause) {
        super(cause);
    }
}
