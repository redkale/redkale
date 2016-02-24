/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
public class ConvertException extends RuntimeException {

    public ConvertException() {
        super();
    }

    public ConvertException(String s) {
        super(s);
    }

    public ConvertException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConvertException(Throwable cause) {
        super(cause);
    }
}
