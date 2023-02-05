/*
 */
package org.redkale.util;

/**
 * 带retcode错误码的异常  <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class RetcodeException extends RedkaleException {

    protected int retcode;

    public RetcodeException(int retcode) {
        this.retcode = retcode;
    }

    public RetcodeException(int retcode, Throwable cause) {
        super(cause);
        this.retcode = retcode;
    }

    public RetcodeException(int retcode, String message) {
        super(message);
        this.retcode = retcode;
    }

    public RetcodeException(int retcode, String message, Throwable cause) {
        super(message, cause);
        this.retcode = retcode;
    }

    public int getRetcode() {
        return retcode;
    }

}
